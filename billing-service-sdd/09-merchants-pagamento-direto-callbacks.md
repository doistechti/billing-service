# SDD 09 — Merchants, Pagamento Direto e Callbacks

## Objetivo

Evoluir o billing-service de POC com uma única conta para um serviço que atende várias contas recebedoras
(lojas da plataforma doistech-store e os sistemas clientes atuais), com pagamento direto por Pix, cartão e boleto,
idempotência e notificação ativa ao sistema cliente.

## Compatibilidade

Os sistemas clientes atuais (Mater Ecclesiae, AJS Express, Oficina) continuam funcionando sem mudança:

- a `X-API-Key` configurada em `INTERNAL_API_KEY` passa a identificar o merchant `legacy`;
- as credenciais `MERCADO_PAGO_*` do ambiente passam a ser as credenciais do merchant `legacy`;
- `POST /api/charges` sem `method` continua gerando Checkout Pro com `paymentUrl`;
- `POST /api/webhooks/mercado-pago` continua recebendo notificações do merchant `legacy`.

## Merchant

Merchant representa a conta recebedora. Cada merchant possui:

- nome e código (`code`), único;
- API key própria — só o hash SHA-256 é armazenado; o valor aberto é exibido uma única vez;
- credenciais Mercado Pago próprias (access token, public key, webhook secret), cifradas com AES-GCM
  usando `BILLING_ENCRYPTION_KEY`;
- flag `sandbox` para usar `sandbox_init_point` no Checkout Pro;
- `callbackUrl` e segredo de callback (cifrado) para notificações ativas.

Toda leitura e escrita de customer, invoice, charge e payment é filtrada pelo merchant autenticado.
Um merchant nunca enxerga registros de outro, mesmo conhecendo o UUID.

A unicidade de `sourceSystem + externalReference` passa a ser por merchant.

### Administração

Endpoints `/api/admin/merchants` autenticados por `X-Admin-Key` (`ADMIN_API_KEY`). Sem `ADMIN_API_KEY`
configurada, a administração fica desabilitada.

| Método | Caminho | Descrição |
|---|---|---|
| POST | /api/admin/merchants | Cria merchant e devolve `apiKey` e `callbackSecret` abertos (uma vez) |
| GET | /api/admin/merchants | Lista merchants |
| GET | /api/admin/merchants/{id} | Detalha merchant (sem segredos) |
| PATCH | /api/admin/merchants/{id} | Atualiza nome, credenciais Mercado Pago, sandbox, callbackUrl, ativo |
| POST | /api/admin/merchants/{id}/rotate-api-key | Gera nova API key |
| POST | /api/admin/merchants/{id}/rotate-callback-secret | Gera novo segredo de callback |

O merchant autenticado consulta a própria configuração pública em `GET /api/merchant` (inclui a
`mercadoPagoPublicKey`, necessária para tokenizar cartão no navegador, se o Mercado Pago está configurado, se a
assinatura de webhook está ativa, a URL de notificação a cadastrar no Mercado Pago e a conta verificada).

### Autoatendimento das credenciais Mercado Pago

Papéis: a **administração** (quem opera a plataforma, ex.: admin SaaS da doistech-store) cria o merchant; o
**próprio merchant** (ex.: o lojista, via store-api) cadastra as credenciais Mercado Pago da conta dele, sem
usar a `X-Admin-Key`.

`PUT /api/merchant/mercado-pago` (X-API-Key do merchant):

```json
{ "accessToken": "APP_USR-…", "publicKey": "APP_USR-…", "webhookSecret": "…", "sandbox": null }
```

1. `accessToken` é obrigatório no primeiro cadastro; vazio depois mantém o atual. `webhookSecret` vazio mantém o atual.
2. Token e public key precisam ser do mesmo ambiente (ambos `TEST-` ou ambos de produção).
3. O token é validado em `GET https://api.mercadopago.com/users/me`; recusado → 400. A conta (`id`, `nickname`)
   e a data da verificação ficam registradas.
4. `sandbox` sem valor é deduzido do prefixo `TEST-`.
5. Segredos nunca voltam na resposta. Indisponível para o merchant `legacy` (credenciais do ambiente).

## Pagamento direto

`POST /api/charges` aceita `method`:

| method | Mercado Pago | Retorno relevante |
|---|---|---|
| CHECKOUT_PRO (padrão) | `POST /checkout/preferences` | `paymentUrl` |
| PIX | `POST /v1/payments` (`payment_method_id=pix`) | `pix.qrCode`, `pix.qrCodeBase64`, `pix.ticketUrl`, `expiresAt` |
| CREDIT_CARD | `POST /v1/payments` com `token` do MP.js | status síncrono (`PAID` ou `REJECTED`) e `statusDetail` |
| BOLETO | `POST /v1/payments` (`payment_method_id=bolbradesco`) | `boleto.barcode`, `boleto.digitableLine`, `boleto.pdfUrl`, `expiresAt` |

Regras:

1. Pix, cartão e boleto exigem `payer.document` (CPF/CNPJ). Boleto exige endereço do pagador.
2. Cartão nunca trafega aberto: o navegador tokeniza com a public key do merchant e envia só o `token`.
3. Cartão aprovado marca charge e invoice como `PAID` na própria requisição e registra o Payment.
4. Cartão recusado deixa a charge `REJECTED` — a invoice continua aberta para nova tentativa.
5. Uma invoice continua com no máximo uma charge ativa (`CREATED`/`WAITING_PAYMENT`).
6. A quantidade dos itens precisa ser inteira apenas no Checkout Pro.

### Idempotência

`POST /api/charges` aceita o header `Idempotency-Key`. Repetir a mesma chave para o mesmo merchant devolve a
mesma charge, sem nova chamada ao gateway. A chave é repassada ao Mercado Pago em `X-Idempotency-Key`.

### Estorno e cancelamento

| Método | Caminho | Efeito |
|---|---|---|
| POST | /api/charges/{id}/refund | Estorno total ou parcial (`amount`) de charge `PAID`. Total → charge e invoice `REFUNDED` |
| POST | /api/charges/{id}/cancel | Cancela Pix/boleto pendente no gateway. Charge `CANCELED` |

Novos status: `ChargeStatus.REFUNDED`, `ChargeStatus.EXPIRED`, `InvoiceStatus.REFUNDED`.

## Webhook do Mercado Pago

- Rota por merchant: `POST /api/webhooks/mercado-pago/{merchantId}`. A rota antiga atende o merchant `legacy`.
- Com webhook secret configurado, o header `x-signature` (`ts=...,v1=...`) é validado com HMAC-SHA256 sobre
  `id:{data.id};request-id:{x-request-id};ts:{ts};`. Assinatura inválida → 401.
- A charge é localizada pelo `gateway_payment_id` (pagamento direto) ou pela invoice no `external_reference`
  (Checkout Pro).
- Falha transitória ao consultar o gateway → 502, para o Mercado Pago reenviar. Pagamento desconhecido → 200.

## Callback ao sistema cliente

Toda mudança de status relevante grava um registro em `callback_deliveries` na mesma transação (outbox).
Um agendador envia os pendentes para o `callbackUrl` do merchant.

Eventos: `charge.paid`, `charge.rejected`, `charge.canceled`, `charge.expired`, `charge.refunded`.

```http
POST {callbackUrl}
Content-Type: application/json
X-Billing-Event: charge.paid
X-Billing-Delivery: 5b0f...uuid
X-Billing-Timestamp: 1790000000
X-Billing-Signature: v1=<hex HMAC-SHA256(callbackSecret, timestamp + "." + body)>
```

```json
{
  "id": "5b0f...uuid",
  "event": "charge.paid",
  "occurredAt": "2026-09-25T21:00:00Z",
  "data": {
    "chargeId": "uuid",
    "invoiceId": "uuid",
    "sourceSystem": "STORE",
    "externalReference": "PED-10491",
    "method": "PIX",
    "status": "PAID",
    "invoiceStatus": "PAID",
    "amount": 369.70,
    "refundedAmount": 0,
    "paidAt": "2026-09-25T20:59:58"
  }
}
```

- Resposta 2xx → `DELIVERED`. Outra resposta ou erro de rede → nova tentativa com backoff exponencial
  (30 s, 1 min, 2 min, 4 min… até 8 tentativas) e depois `FAILED`.
- O receptor deve rejeitar assinaturas inválidas e timestamps com mais de 5 minutos, e tratar o `id` como
  chave de idempotência.
- `GET /api/callbacks?status=FAILED` e `POST /api/callbacks/{id}/redeliver` permitem reprocessar.

## Segurança

- Nenhum segredo tem valor padrão no `application.yml`.
- `BILLING_ENCRYPTION_KEY` (32 bytes em Base64) é obrigatória.
- Comparação de chaves em tempo constante.

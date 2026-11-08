# SDD 10 — Gateway Pagar.me e Multigateway

> Status: **em planejamento** (branch `feature/gateway-pagar-me`). Este documento é a fonte única da mudança.
> Atualize a tabela "Andamento" ao fim de cada passo.

## Objetivo

Permitir que um merchant receba por **Pagar.me** em vez de Mercado Pago, sem alterar nada do que os
sistemas clientes (doistech-store/Bilins, Mater Ecclesiae, AJS Express, Oficina) usam hoje.

Motivação imediata: gestão financeira dos **cursos livres** do Mater Ecclesiae, em que **cada curso poderá
ter uma conta Pagar.me distinta**. O modelo de merchants (SDD 09) já isola contas recebedoras, então
**um curso = um merchant**, cada um com o próprio gateway e as próprias credenciais.

## Regra de ouro: compatibilidade

A mudança é **somente aditiva**. O que existe hoje continua idêntico.

1. Nenhuma rota, campo JSON, enum ou status existente muda de nome, tipo ou significado.
2. Gateway é **opt-in por merchant**. Sem escolha explícita, vale `MERCADO_PAGO`.
3. A migration V4 só adiciona colunas **nulas ou com default**. Não renomeia nem apaga as colunas `mp_*`.
   O jar anterior continua subindo sobre o banco migrado (rollback seguro).
4. Os campos de resposta existentes (`mercadoPagoConfigured`, `sandbox`, `webhookSignatureEnabled`…) mantêm o
   significado. Campos novos são acrescentados; clientes que não os conhecem os ignoram.
5. Os webhooks do Mercado Pago não mudam. O do Pagar.me é uma rota nova.
6. O callback ao sistema cliente mantém o mesmo envelope, eventos e assinatura, seja qual for o gateway.
7. A refatoração (passo 1) só move e renomeia tipos **internos**, sem mexer em rotas nem em JSON.

### Contrato que a doistech-store consome (não pode quebrar)

Cliente: `store-api/.../infrastructure/payment/BilinsBillingGateway.java`. Fake de teste:
`store-api/src/test/.../support/FakeBilins.java`.

| Chamada | Observação |
|---|---|
| `POST /api/customers`, `GET /api/customers/by-reference` | `sourceSystem`, `externalReference`, `name`, `email`, `document`, `phone` |
| `POST /api/invoices`, `GET /api/invoices/by-reference` | `customerId`, `items[]`, `totalAmount`, `status` |
| `POST /api/charges` | `invoiceId`, `gateway` (a store envia `"MERCADO_PAGO"`), `method`, `payer`, `card`, `expiresInMinutes`, `amount`; header `Idempotency-Key` |
| `GET /api/charges/{id}`, `POST …/sync`, `…/refund`, `…/cancel` | resposta com `pix`, `boleto`, `installments`, `status` |
| `GET /api/merchant`, `PUT /api/merchant/mercado-pago` | `mercadoPagoConfigured`, `sandbox`, `webhookSignatureEnabled`; corpo `accessToken`, `publicKey`, `webhookSecret`, `sandbox` |
| `POST /api/admin/merchants` | devolve `merchant.id`, `apiKey`, `callbackSecret` |
| Callbacks para a store | `X-Billing-Event`, `X-Billing-Delivery`, `X-Billing-Timestamp`, `X-Billing-Signature` |

Validação: testes de contrato no billing que fixam o JSON dessas rotas, mais os `*IntegrationTest` do
store-api (checkout, assinatura, configuração de pagamento) rodados contra o billing local.

## Desenho

### 1. Multigateway

- `Gateway` ganha o valor `PAGAR_ME`. `MERCADO_PAGO` permanece.
- `merchants.gateway` (`varchar`, default `MERCADO_PAGO`, not null) define o gateway do merchant.
- `PaymentGatewayService` e os tipos neutros (`ChargeGatewayResponse`, `GatewayPaymentResponse`,
  `GatewayRefundResponse`, `DirectPaymentRequest`) saem de `gateway.mercadopago` para `gateway`.
- `MercadoPagoAccount` vira `GatewayAccount` (`id`, `nickname`, `siteId` opcional).
- Um `PaymentGatewayRouter` escolhe a implementação pelo `gateway` do merchant autenticado.
- `POST /api/charges` com `gateway` ausente usa o gateway do merchant. Se vier preenchido e **diferente** do
  gateway do merchant → 422 `gateway_mismatch` (a store manda `MERCADO_PAGO` para merchants MP, então segue válido).

### 2. Credenciais Pagar.me no merchant (V4)

Colunas novas, todas nulas:

| Coluna | Conteúdo |
|---|---|
| `gateway` | `MERCADO_PAGO` (default) ou `PAGAR_ME` |
| `pm_secret_key_encrypted` | secret key (`sk_…`) cifrada AES-GCM com `BILLING_ENCRYPTION_KEY` |
| `pm_public_key` | public key (`pk_…`), usada no navegador para tokenizar cartão |
| `pm_webhook_secret_encrypted` | segredo/credencial de validação do webhook, cifrado |
| `pm_account_id`, `pm_account_name`, `pm_verified_at` | conta verificada no cadastro |

`MerchantCredentials` continua com os mesmos campos. Para Pagar.me, `accessToken` carrega a secret key e
`publicKey` a public key (o record é só portador em memória; o mapeamento fica na `MerchantService`).

### 3. `PagarMeGatewayService`

API v5, base `https://api.pagar.me/core/v5`, autenticação HTTP Basic com a secret key como usuário e senha vazia.
**Valores monetários em centavos** (inteiro). Converter na borda; o domínio segue em `BigDecimal`.

| Operação do billing | Pagar.me |
|---|---|
| `createCharge` (CHECKOUT_PRO equivalente) | `POST /paymentlinks` → `paymentUrl` |
| `createPayment` PIX | `POST /orders` com `payments[].payment_method = pix` |
| `createPayment` BOLETO | `POST /orders` com `payment_method = boleto` |
| `createPayment` CREDIT_CARD | `POST /orders` com `payment_method = credit_card` e `card_token` |
| `getPayment` | `GET /charges/{id}` |
| `refund` / `cancelPayment` | `DELETE /charges/{id}` (corpo com `amount` no estorno parcial) |
| `verifyAccount` | `GET` autenticado simples; falha de autenticação → 400 |

Mapa de status (`PagarMeStatusMapper`):

```text
paid                  → PAID
pending, processing   → WAITING_PAYMENT
failed                → REJECTED
canceled              → CANCELED
refunded              → REFUNDED
overpaid, underpaid   → tratar como PAID parcial: registrar e sinalizar (decisão em aberto, ver Pendências)
```

Cartão: o navegador tokeniza com a public key (`pk_…`) e envia só o `token`. Nunca trafega número aberto.

### 4. Webhook do Pagar.me

- Rota nova: `POST /api/webhooks/pagar-me/{merchantId}`. Não há rota "legacy" para Pagar.me.
- Reusa `WebhookEventRecorder` e `WebhookPaymentProcessor`. **Regra mantida:** o payload nunca confirma
  pagamento; o billing consulta `GET /charges/{id}` antes de atualizar `payments`, `charges` e `invoices`.
- Eventos tratados: `order.paid`, `charge.paid`, `charge.payment_failed`, `charge.refunded`, `charge.canceled`.
- Idempotência por `webhook_events` (mesmo contrato do SDD 07).
- Validação de autenticidade: ver Pendências. Enquanto a validação não estiver confirmada com a documentação
  oficial, o endpoint exige o segredo do merchant e a confirmação ativa por consulta ao gateway.

### 5. Autoatendimento

`PUT /api/merchant/pagar-me` (X-API-Key do merchant), espelho do `PUT /api/merchant/mercado-pago`:

```json
{ "secretKey": "sk_…", "publicKey": "pk_…", "webhookSecret": "…", "sandbox": null }
```

Regras: `secretKey` obrigatória no primeiro cadastro, vazia depois mantém a atual; validada na API; segredos
nunca voltam na resposta; só vale para merchant com `gateway = PAGAR_ME`.

`GET /api/merchant` ganha, **sem alterar os campos atuais**: `gateway`, `pagarMeConfigured`,
`pagarMePublicKey`, `pagarMeAccountName`.

`POST /api/admin/merchants` aceita `gateway` opcional (default `MERCADO_PAGO`). Resposta continua a mesma.

### 6. Integração futura com o Mater (fora desta entrega)

- Cada curso livre guarda o `merchantId`/API key do merchant Pagar.me dele (cifrada na api-mater).
- Admin do curso cadastra a conta via `PUT /api/merchant/pagar-me`, repassada pela api-mater.
- Inscrição cria customer + invoice + charge com `sourceSystem = MATER_ECCLESIAE` e
  `externalReference` por inscrição (ex.: `CURSO-{cursoId}-ED-{edicaoId}-INSC-{id}`).
- Callbacks `charge.*` atualizam o status financeiro da inscrição.
- Será detalhado em SDD próprio do lado do Mater.

## Variáveis de ambiente

Nenhuma obrigatória nova. Credenciais Pagar.me vivem **por merchant, no banco, cifradas**; não há conta
Pagar.me global no ambiente. `BILLING_ENCRYPTION_KEY` continua obrigatória.

Opcional: `PAGAR_ME_BASE_URL` (default `https://api.pagar.me/core/v5`), útil para testes com servidor fake.

## Passos e andamento

| # | Passo | Estado |
|---|---|---|
| 0 | Documentação (este SDD e acréscimos nos SDD 02, 04, 05, 06, 07, 09, README) | feito |
| 1 | Linha de base: `./mvnw verify` verde na `main` antes de mexer | pendente |
| 2 | Testes de contrato fixando o JSON atual das rotas usadas pela store | pendente |
| 3 | Refatoração: mover interface e tipos neutros para `gateway`, `GatewayAccount`, router | pendente |
| 4 | Migration V4 (`merchants.gateway` e colunas `pm_*`) | pendente |
| 5 | `PagarMeGatewayService` + `PagarMeStatusMapper` + testes | pendente |
| 6 | Webhook `POST /api/webhooks/pagar-me/{merchantId}` + testes | pendente |
| 7 | `PUT /api/merchant/pagar-me`, `GET /api/merchant` ampliado, `gateway` no admin de merchants | pendente |
| 8 | Teste de isolamento entre merchants de gateways diferentes | pendente |
| 9 | Rodar `*IntegrationTest` do store-api contra o billing local | pendente |
| 10 | Roteiro de testes e `pagar-me-development-setup.md` | pendente |

Critério para fechar cada passo: `./mvnw verify` verde e nenhum teste de contrato alterado.

## Pendências de validação (confirmar na documentação oficial do Pagar.me antes de implementar)

1. Esquema de **assinatura/autenticação do webhook** v5 (header, algoritmo e o que entra no HMAC).
2. Comportamento de `overpaid` e `underpaid`: como mapear para `ChargeStatus`.
3. Suporte e nome do header de **idempotência** na v5 (`Idempotency-Key`).
4. Endpoint e campos exatos de **tokenização** de cartão no navegador com a public key.
5. Endpoint mais adequado para **verificar a conta** no cadastro das credenciais.
6. Campos obrigatórios do pagador no boleto e no Pix (documento, endereço, telefone).
7. Credenciais de **sandbox** disponíveis para validar contra a API real.

## Fora do escopo

- Recorrência/assinatura do Pagar.me, split de pagamento e marketplace/recebedores.
- Conciliação financeira e relatórios (virão na gestão financeira do Mater).
- Migração de merchants existentes de gateway.

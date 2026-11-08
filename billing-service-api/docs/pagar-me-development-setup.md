# Setup de Desenvolvimento do Pagar.me

Guia para usar o gateway Pagar.me no `billing-service-api`: como rodar a suíte, como montar o fluxo de um
merchant (por exemplo, **um curso livre do Mater Ecclesiae**) e o que ainda precisa ser validado contra a API real.

Especificação completa e andamento: [SDD 10](../../billing-service-sdd/10-integracao-pagar-me.md).

## Estado da validação (leia antes)

| Item | Situação |
|---|---|
| Código, migration V4, rotas, isolamento, callbacks | Coberto por testes automatizados (52) |
| Respostas do Pagar.me | **Servidor falso escrito a partir do que conhecemos da API v5. Nada foi validado contra a API real** |
| Pontos de incerteza | 9 pendências no SDD 10, repetidas na seção [Validar no sandbox](#4-validar-no-sandbox) |
| Store ↔ billing real | Checagem manual pendente, ver [Antes de qualquer deploy](#5-antes-de-qualquer-deploy) |

Não use credenciais de produção até concluir a seção de sandbox.

## 1. Rodar os testes

Os testes de integração usam Testcontainers (Postgres). No macOS com **Colima**, o Docker não fica em
`/var/run/docker.sock`; aponte o Testcontainers para o socket do Colima:

```bash
colima start
export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
export TESTCONTAINERS_RYUK_DISABLED=true

cd billing-service-api
./mvnw verify
```

Sem isso, `MerchantPaymentsIntegrationTest` e `PagarMePaymentsIntegrationTest` falham com
`Could not find a valid Docker environment`. Com Docker Desktop ou Linux, basta `./mvnw verify`.

Teste só o Pagar.me: `./mvnw test -Dtest='PagarMe*'`.

## 2. Variáveis de ambiente

Nenhuma é obrigatória para o Pagar.me. As credenciais vivem **por merchant, cifradas no banco**
(`BILLING_ENCRYPTION_KEY` continua obrigatória).

| Variável | Uso |
|---|---|
| `PAGAR_ME_BASE_URL` | Opcional. Padrão `https://api.pagar.me/core/v5`. Aponte para um servidor falso para testes locais |
| `APP_BASE_URL` | URL pública do billing. Forma o `notificationUrl` que o Pagar.me chama |
| `ADMIN_API_KEY` | Habilita `POST /api/admin/merchants` |

## 3. Fluxo de um merchant Pagar.me (um curso)

Quem faz cada passo: o **admin da plataforma** cria o merchant (passo A); o **admin do curso** cadastra a conta dele
(passo B) e a aplicação do curso cobra (passos D e E).

Os exemplos leem os segredos de variáveis de ambiente e montam o JSON com `jq`, para que nenhuma chave fique digitada
no comando nem no histórico do shell:

```bash
export BASE=http://localhost:8080
export ADMIN_KEY=...            # ADMIN_API_KEY do billing
export PM_SECRET_KEY=...        # sk_test_… do Pagar.me
export PM_PUBLIC_KEY=...        # pk_test_… do Pagar.me
export HOOK_TOKEN=$(openssl rand -hex 24)   # autentica o webhook
```

### A. Criar o merchant do curso (admin da plataforma)

```bash
jq -n '{code:"curso-biblia-2026", name:"Curso de Bíblia 2026", gateway:"PAGAR_ME",
        callbackUrl:"https://api.mater.example/billing/callback"}' \
  | curl -s -X POST $BASE/api/admin/merchants -H "X-Admin-Key: $ADMIN_KEY" \
         -H 'Content-Type: application/json' -d @-
```

A resposta traz `apiKey` e `callbackSecret` **uma única vez**: guarde-os (`export MERCHANT_KEY=...`). Também traz
`notificationUrl` (`…/api/webhooks/pagar-me/{merchantId}`), que vai para o painel do Pagar.me.

### B. Cadastrar a conta Pagar.me do curso (admin do curso)

```bash
jq -n --arg s "$PM_SECRET_KEY" --arg p "$PM_PUBLIC_KEY" --arg w "$HOOK_TOKEN" \
   '{secretKey:$s, publicKey:$p, webhookSecret:$w}' \
  | curl -s -X PUT $BASE/api/merchant/pagar-me -H "X-API-Key: $MERCHANT_KEY" \
         -H 'Content-Type: application/json' -d @-
```

A chave é validada no Pagar.me antes de salvar. Chave de teste exige chave pública de teste. Nenhum segredo volta
na resposta. Para trocar só a public key, omita `secretKey`: a atual é mantida e revalidada.

### C. Webhook no painel do Pagar.me

Cadastre o `notificationUrl` com o segredo (`$HOOK_TOKEN`) de uma destas formas:

- parâmetro: `<notificationUrl>?token=<HOOK_TOKEN>`
- Basic auth na URL, com o segredo como **senha**: `https://hook:<HOOK_TOKEN>@<host>/api/webhooks/pagar-me/<merchantId>`

Eventos úteis: `charge.*` e `order.*`. Outros são registrados e ignorados. **O payload nunca confirma pagamento**: o
billing consulta a charge no Pagar.me antes de atualizar. O formato oficial de assinatura do Pagar.me ainda não foi
adotado (pendência 1).

### D. Cobrar (aplicação do curso)

`sourceSystem` + `externalReference` são únicos por merchant.

```bash
jq -n '{sourceSystem:"MATER_ECCLESIAE", externalReference:"ALUNO-123", name:"Maria Silva",
        email:"maria@example.com", document:"123.456.789-09"}' \
  | curl -s -X POST $BASE/api/customers -H "X-API-Key: $MERCHANT_KEY" -H 'Content-Type: application/json' -d @-

jq -n --arg c "$CUSTOMER_ID" '{customerId:$c, sourceSystem:"MATER_ECCLESIAE",
        externalReference:"CURSO-1-ED-2-INSC-3", description:"Inscrição Curso de Bíblia",
        items:[{description:"Curso de Bíblia", quantity:1, unitAmount:369.70}]}' \
  | curl -s -X POST $BASE/api/invoices -H "X-API-Key: $MERCHANT_KEY" -H 'Content-Type: application/json' -d @-

# Pix
jq -n --arg i "$INVOICE_ID" '{invoiceId:$i, gateway:"PAGAR_ME", method:"PIX"}' \
  | curl -s -X POST $BASE/api/charges -H "X-API-Key: $MERCHANT_KEY" -H 'Idempotency-Key: insc-3-pix-1' \
         -H 'Content-Type: application/json' -d @-
```

| `method` | Exige | Retorno relevante |
|---|---|---|
| `PIX` | `payer.document` (usa o do customer se omitido) | `pix.qrCode`, `pix.ticketUrl`, `expiresAt` |
| `BOLETO` | documento e `payer.address` completo | `boleto.digitableLine`, `boleto.barcode`, `boleto.pdfUrl` |
| `CREDIT_CARD` | `card.token` (tokenizado no navegador com a **public key**) e `card.installments` | status síncrono (`PAID` ou `REJECTED`) |

**Não existe link hospedado** para Pagar.me ainda: `POST /api/charges` sem `method` responde 400 pedindo PIX, BOLETO ou
CREDIT_CARD (pendência 8). `gateway` precisa ser `PAGAR_ME`; outro valor responde 400 `gateway mismatch`.

### E. Acompanhar, estornar e cancelar

```bash
curl -s $BASE/api/charges/<chargeId> -H "X-API-Key: $MERCHANT_KEY"
curl -s -X POST $BASE/api/charges/<chargeId>/sync   -H "X-API-Key: $MERCHANT_KEY"   # consulta o gateway
curl -s -X POST $BASE/api/charges/<chargeId>/cancel -H "X-API-Key: $MERCHANT_KEY"   # Pix/boleto pendente
echo '{"amount":50}' | curl -s -X POST $BASE/api/charges/<chargeId>/refund \
  -H "X-API-Key: $MERCHANT_KEY" -H 'Content-Type: application/json' -d @-             # sem amount: estorno total
```

O sistema cliente recebe `charge.paid`, `charge.rejected`, `charge.canceled`, `charge.expired` e `charge.refunded` no
`callbackUrl`, assinados com o `callbackSecret` (mesmo envelope do Mercado Pago, SDD 09).

## 4. Validar no sandbox

Tudo abaixo foi implementado a partir de conhecimento da API v5, sem acesso à documentação oficial nem a credenciais.
Com uma conta de teste do Pagar.me, valide **cada item** e corrija o código e o servidor falso se a API real
divergir. Marque aqui e no SDD 10 ao concluir.

| # | Validar | Como |
|---|---|---|
| 1 | Assinatura/autenticação de webhook v5 (header, algoritmo, o que entra no HMAC) | Cadastrar o webhook e inspecionar os headers de um envio real. Hoje só vale o segredo do merchant |
| 2 | `overpaid` e `underpaid` | Pagar Pix com valor diferente no sandbox; conferir o status da charge. Hoje `underpaid` não libera a invoice |
| 3 | Header de idempotência (`Idempotency-Key`) | Repetir um `POST /orders` com a mesma chave; deve devolver o mesmo order |
| 4 | Tokenização do cartão no navegador com a public key | Gerar um `card.token` pelo endpoint de tokens do Pagar.me e usar em `CREDIT_CARD` |
| 5 | Verificação da conta no cadastro (`GET /customers?size=1`) | Salvar uma secret key válida e uma inválida (esperado: 200 e 400) |
| 6 | Campos obrigatórios do pagador (documento, endereço, telefone) | Criar Pix, boleto e cartão e anotar o que o Pagar.me recusa |
| 7 | Credenciais de sandbox disponíveis e prefixos `sk_test_`/`pk_test_` | Confirmar os prefixos exigidos pela validação do `PUT /api/merchant/pagar-me` |
| 8 | Link hospedado (`/paymentlinks`) e reconciliação com a invoice | Decidir como o pedido do link volta para a invoice antes de liberar `CHECKOUT_PRO` |
| 9 | Status de transação de estorno e expiração (`refunded`, `voided`, `partial_refunded`, `expired`) | Estornar e deixar vencer um Pix; conferir `last_transaction.status` e o `ChargeStatus` resultante |

Também conferir: o formato real das respostas de order/charge (campos `qr_code`, `qr_code_url`, `pdf`, `line`,
`barcode`, `expires_at`, `paid_at`, `canceled_amount`) e que os horários em UTC chegam como esperado (o billing converte
para `America/Sao_Paulo`).

## 5. Antes de qualquer deploy

Os testes da store usam um billing falso (`FakeBilins`), então **não exercitam este código**. O billing real tem o
contrato da store fixado por teste (JSON das rotas e callback assinado), mas a prova ponta a ponta é manual:

1. Subir este billing local (`compose.yaml`) com `ADMIN_API_KEY`, `BILLING_ENCRYPTION_KEY` e Mercado Pago apontando
   para um servidor falso ou credenciais de teste.
2. Subir a `store-api` com `BILLING_BASE_URL` apontando para ele e `BILLING_ADMIN_API_KEY` igual à `ADMIN_API_KEY`.
3. Percorrer o que a store faz: provisionar loja (cria merchant), cadastrar credenciais do Mercado Pago, criar pedido,
   cobrar Pix e cartão e receber o callback assinado.
4. Confirmar que nada mudou para a store: mesmos campos de `GET /api/merchant` e mesmos eventos de callback.

## 6. Limitações conhecidas

- Sem link hospedado (Checkout Pro) para Pagar.me.
- `GatewayAccount` vem vazio no Pagar.me: a v5 não expõe id ou nome da conta; `pagarMeAccountName` fica nulo.
- Um merchant não troca de gateway depois de criado.
- Webhook autenticado pelo segredo do merchant, não pela assinatura oficial do Pagar.me.
- Defeito anterior ao Pagar.me, não corrigido: o `GlobalExceptionHandler` responde 500 para método HTTP não
  suportado (405).

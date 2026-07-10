# Roteiro de Testes da API

Este documento descreve como testar o `billing-service-api` manualmente, em ordem, validando:

- conectividade
- autenticacao
- persistencia no banco
- regras de negocio
- criacao de charge
- recebimento de webhook

O foco aqui e um teste funcional da POC.

## Pre-requisitos

Antes de testar, confirme:

1. a API esta no ar
2. o PostgreSQL esta no ar
3. o banco ja recebeu as migrations
4. voce sabe qual API key usar

## Enderecos usados neste roteiro

Exemplo local:

```text
API_BASE_URL=http://localhost:8080
```

Banco local esperado:

```text
jdbc:postgresql://localhost:5433/billing_service
```

## Autenticacao

Os endpoints internos exigem API key.

Header padrao esperado pela documentacao:

```http
X-API-Key: {sua-chave}
```

Se voce alterou `app.security.api-key-header`, use o valor configurado no ambiente.

## Fluxo de teste recomendado

Siga nesta ordem:

1. `health`
2. `customer`
3. `invoice`
4. `charge`
5. consultas de `invoice`, `charge` e `payment`
6. webhook

---

## 1. Teste de health

Objetivo:

- validar que a API esta respondendo

Comando:

```bash
curl -s http://localhost:8080/api/health
```

Resposta esperada:

```json
{
  "status": "UP",
  "service": "billing-service"
}
```

---

## 2. Teste de autenticacao

Objetivo:

- validar que os endpoints internos estao protegidos

### Sem API key

```bash
curl -i http://localhost:8080/api/customers/00000000-0000-0000-0000-000000000000
```

Resultado esperado:

- `401 Unauthorized`

### Com API key

Substitua `SUA_API_KEY` pelo valor real:

```bash
curl -i \
  -H "X-API-Key: SUA_API_KEY" \
  http://localhost:8080/api/customers/00000000-0000-0000-0000-000000000000
```

Resultado esperado:

- nao precisa retornar `200`
- o importante aqui e nao retornar `401`
- normalmente vira `404 customer not found`

---

## 3. Criar customer

Objetivo:

- validar escrita na tabela `customers`

Comando:

```bash
curl -s -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -H "X-API-Key: SUA_API_KEY" \
  -d '{
    "sourceSystem": "MATER_ECCLESIAE",
    "externalReference": "ALUNO-123",
    "name": "Joao da Silva",
    "email": "joao@email.com",
    "document": "12345678900",
    "phone": "21999999999"
  }'
```

Resposta esperada:

- `id` preenchido
- `active = true`

Exemplo:

```json
{
  "id": "uuid",
  "sourceSystem": "MATER_ECCLESIAE",
  "externalReference": "ALUNO-123",
  "name": "Joao da Silva",
  "email": "joao@email.com",
  "active": true
}
```

Guarde o `id` retornado em `CUSTOMER_ID`.

---

## 4. Buscar customer

Objetivo:

- validar leitura da tabela `customers`

Comando:

```bash
curl -s \
  -H "X-API-Key: SUA_API_KEY" \
  http://localhost:8080/api/customers/CUSTOMER_ID
```

Resultado esperado:

- retorno do mesmo customer criado

---

## 5. Criar invoice

Objetivo:

- validar escrita em `invoices` e `invoice_items`
- validar calculo automatico de `totalAmount`

Comando:

```bash
curl -s -X POST http://localhost:8080/api/invoices \
  -H "Content-Type: application/json" \
  -H "X-API-Key: SUA_API_KEY" \
  -d '{
    "customerId": "CUSTOMER_ID",
    "sourceSystem": "MATER_ECCLESIAE",
    "externalReference": "ALUNO-123-MENSALIDADE-2026-07",
    "description": "Mensalidade Julho/2026",
    "dueDate": "2026-07-15",
    "items": [
      {
        "description": "Mensalidade Julho/2026",
        "quantity": 1,
        "unitAmount": 150.00
      }
    ]
  }'
```

Resposta esperada:

- `status = OPEN`
- `totalAmount = 150.00`

Guarde o `id` retornado em `INVOICE_ID`.

---

## 6. Buscar invoice por id

Objetivo:

- validar leitura da invoice e dos itens

Comando:

```bash
curl -s \
  -H "X-API-Key: SUA_API_KEY" \
  http://localhost:8080/api/invoices/INVOICE_ID
```

Resultado esperado:

- invoice com itens
- `status = OPEN`

---

## 7. Buscar invoice por referencia externa

Objetivo:

- validar rastreabilidade por `sourceSystem + externalReference`

Comando:

```bash
curl -s \
  -H "X-API-Key: SUA_API_KEY" \
  "http://localhost:8080/api/invoices/by-reference?sourceSystem=MATER_ECCLESIAE&externalReference=ALUNO-123-MENSALIDADE-2026-07"
```

Resultado esperado:

- mesma invoice criada anteriormente

---

## 8. Testar regra: invoice sem item

Objetivo:

- validar regra de negocio

Comando:

```bash
curl -i -X POST http://localhost:8080/api/invoices \
  -H "Content-Type: application/json" \
  -H "X-API-Key: SUA_API_KEY" \
  -d '{
    "customerId": "CUSTOMER_ID",
    "sourceSystem": "MATER_ECCLESIAE",
    "externalReference": "TESTE-SEM-ITEM",
    "description": "Teste sem item",
    "items": []
  }'
```

Resultado esperado:

- `400 Bad Request`

---

## 9. Criar charge

Objetivo:

- validar criacao de cobranca
- validar escrita em `charges`
- validar mudanca da invoice para `WAITING_PAYMENT`

Comando:

```bash
curl -s -X POST http://localhost:8080/api/charges \
  -H "Content-Type: application/json" \
  -H "X-API-Key: SUA_API_KEY" \
  -d '{
    "invoiceId": "INVOICE_ID",
    "gateway": "MERCADO_PAGO"
  }'
```

Resultado esperado:

- `status = WAITING_PAYMENT`
- `paymentUrl` preenchida
- `gatewayPreferenceId` preenchido

Guarde o `id` retornado em `CHARGE_ID`.

## Se falhar aqui

As causas mais comuns sao:

- `MERCADO_PAGO_ACCESS_TOKEN` invalido
- credencial de producao sendo usada no ambiente errado
- sem acesso externo para Mercado Pago

---

## 10. Buscar charge por id

Objetivo:

- validar leitura da tabela `charges`

Comando:

```bash
curl -s \
  -H "X-API-Key: SUA_API_KEY" \
  http://localhost:8080/api/charges/CHARGE_ID
```

Resultado esperado:

- mesma charge criada

---

## 11. Buscar charges por invoice

Objetivo:

- validar relacao `invoice -> charges`

Comando:

```bash
curl -s \
  -H "X-API-Key: SUA_API_KEY" \
  http://localhost:8080/api/charges/invoice/INVOICE_ID
```

Resultado esperado:

- lista com pelo menos uma charge

---

## 12. Verificar status da invoice apos charge

Objetivo:

- validar atualizacao para `WAITING_PAYMENT`

Comando:

```bash
curl -s \
  -H "X-API-Key: SUA_API_KEY" \
  http://localhost:8080/api/invoices/INVOICE_ID
```

Resultado esperado:

- `status = WAITING_PAYMENT`

---

## 13. Testar regra: nao criar segunda charge ativa

Objetivo:

- validar bloqueio de charge duplicada ativa

Comando:

```bash
curl -i -X POST http://localhost:8080/api/charges \
  -H "Content-Type: application/json" \
  -H "X-API-Key: SUA_API_KEY" \
  -d '{
    "invoiceId": "INVOICE_ID",
    "gateway": "MERCADO_PAGO"
  }'
```

Resultado esperado:

- `400 Bad Request`

---

## 14. Buscar pagamentos por invoice antes do webhook

Objetivo:

- validar que a API nao cria `payment` antes da confirmacao

Comando:

```bash
curl -s \
  -H "X-API-Key: SUA_API_KEY" \
  http://localhost:8080/api/payments/invoice/INVOICE_ID
```

Resultado esperado:

- lista vazia

---

## 15. Teste manual do checkout Mercado Pago

Objetivo:

- validar o `paymentUrl`

Passos:

1. copie o `paymentUrl` retornado na charge
2. abra no navegador
3. use conta de teste comprador
4. conclua o fluxo do Checkout Pro

Resultado esperado:

- checkout abre corretamente
- pagamento pode ser simulado

Observacao:

- em ambiente de teste, o recebimento de notificacao deve ser validado conforme o fluxo documentado no setup do Mercado Pago

Documento relacionado:

[`mercado-pago-development-setup.md`](</Users/doistechti/Projetos/DOISTECH/billing-service/billing-service-api/docs/mercado-pago-development-setup.md>)

---

## 16. Testar webhook manualmente

Objetivo:

- validar que o endpoint recebe payload
- validar persistencia em `webhook_events`

Comando:

```bash
curl -s -X POST http://localhost:8080/api/webhooks/mercado-pago \
  -H "Content-Type: application/json" \
  -d '{
    "action": "payment.updated",
    "data": {
      "id": "123456789"
    }
  }'
```

Resposta esperada:

```json
{
  "status": "received"
}
```

Observacao:

- isso valida recepcao do webhook
- nao garante pagamento aprovado
- para processamento real, o `paymentId` precisa existir no Mercado Pago

---

## 17. Validar pagamento apos webhook real

Objetivo:

- validar criacao de `payment`
- validar atualizacao de `charge`
- validar atualizacao de `invoice`

Comandos:

```bash
curl -s \
  -H "X-API-Key: SUA_API_KEY" \
  http://localhost:8080/api/payments/invoice/INVOICE_ID
```

```bash
curl -s \
  -H "X-API-Key: SUA_API_KEY" \
  http://localhost:8080/api/charges/CHARGE_ID
```

```bash
curl -s \
  -H "X-API-Key: SUA_API_KEY" \
  http://localhost:8080/api/invoices/INVOICE_ID
```

Resultado esperado para pagamento aprovado:

- existe registro em `payments`
- `charge.status = PAID`
- `invoice.status = PAID`
- `invoice.paidAt` preenchido

---

## 18. Testar cancelamento de invoice

Objetivo:

- validar endpoint de cancelamento

Crie uma nova invoice sem charge e use:

```bash
curl -s -X PATCH \
  -H "X-API-Key: SUA_API_KEY" \
  http://localhost:8080/api/invoices/NOVA_INVOICE_ID/cancel
```

Resultado esperado:

- `status = CANCELED`

---

## 19. Testar regra: invoice paga nao pode ser cancelada

Objetivo:

- validar protecao de regra de negocio

Comando:

```bash
curl -i -X PATCH \
  -H "X-API-Key: SUA_API_KEY" \
  http://localhost:8080/api/invoices/INVOICE_ID/cancel
```

Resultado esperado:

- se a invoice ja estiver paga, retornar `400`

---

## O que checar no banco

Se quiser validar no banco, confira:

- `customers`
- `invoices`
- `invoice_items`
- `charges`
- `payments`
- `webhook_events`

Mapa esperado por etapa:

- criar customer -> `customers`
- criar invoice -> `invoices`, `invoice_items`
- criar charge -> `charges`
- receber webhook -> `webhook_events`
- confirmar pagamento -> `payments`, update em `charges` e `invoices`

---

## Checklist final de aceite

Voce pode considerar a POC testada quando conseguir:

1. subir banco e API
2. autenticar com API key
3. criar customer
4. criar invoice com item
5. buscar invoice por id e por referencia
6. criar charge
7. receber `paymentUrl`
8. abrir o checkout de teste
9. receber webhook
10. consultar pagamento
11. criar `payment`
12. atualizar charge para `PAID`
13. atualizar invoice para `PAID`

---

## Observacoes importantes

1. Se o endpoint interno estiver retornando `401`, revise o nome do header e o valor da API key.
2. Se `POST /charges` falhar, revise as credenciais do Mercado Pago.
3. Se o webhook responder `received` mas nada atualizar, o `paymentId` provavelmente nao existe ou a consulta ao Mercado Pago falhou.
4. Se a API nao subir, revise:
   - `DB_URL`
   - `DB_USERNAME`
   - `DB_PASSWORD`
   - logs do Flyway

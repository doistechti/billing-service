# SDD 08 — Integração com Sistemas Clientes

## Objetivo

Definir como outros sistemas irão consumir o billing-service.

## Princípio

O billing-service deve ser genérico.

Ele não deve conter regra específica de aluno, matrícula, oficina, logística ou assinatura.

Cada sistema cliente envia:

- sourceSystem;
- externalReference;
- customer;
- invoice;
- items.

## Exemplo — Mater Ecclesiae

O Mater Ecclesiae cria uma mensalidade no billing-service:

```json
{
  "customerId": "uuid",
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
}
```

## Exemplo — AJS Express

```json
{
  "customerId": "uuid",
  "sourceSystem": "AJS_EXPRESS",
  "externalReference": "FECHAMENTO-CLIENTE-55-2026-07-01",
  "description": "Fechamento quinzenal de entregas",
  "dueDate": "2026-07-20",
  "items": [
    {
      "description": "Serviços de entrega",
      "quantity": 48,
      "unitAmount": 12.50
    }
  ]
}
```

## Exemplo — Oficina

```json
{
  "customerId": "uuid",
  "sourceSystem": "OFICINA_PRO",
  "externalReference": "OS-998",
  "description": "Ordem de serviço aprovada",
  "dueDate": "2026-07-10",
  "items": [
    {
      "description": "Troca de óleo",
      "quantity": 1,
      "unitAmount": 180.00
    }
  ]
}
```

## Retorno Esperado ao Criar Charge

```json
{
  "invoiceId": "uuid",
  "chargeId": "uuid",
  "paymentUrl": "https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=xxxx",
  "status": "WAITING_PAYMENT"
}
```

## Callback Futuro

Na POC, o sistema cliente poderá consultar o billing-service.

Em uma fase futura, o billing-service poderá avisar o sistema cliente via callback:

```text
POST {clientCallbackUrl}
```

Payload futuro:

```json
{
  "sourceSystem": "MATER_ECCLESIAE",
  "externalReference": "ALUNO-123-MENSALIDADE-2026-07",
  "invoiceId": "uuid",
  "status": "PAID",
  "paidAt": "2026-07-09T10:30:00"
}
```

## Segurança entre Sistemas

Para a POC:

- usar API Key simples por sistema cliente.

Futuro:

- OAuth2 Client Credentials;
- JWT interno;
- assinatura HMAC nas chamadas;
- rate limit;
- auditoria.

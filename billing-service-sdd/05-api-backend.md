# SDD 05 — API Backend

## Base URL

/api

## Customers

### Criar customer

POST /customers

Request:

```json
{
  "sourceSystem": "MATER_ECCLESIAE",
  "externalReference": "ALUNO-123",
  "name": "João da Silva",
  "email": "joao@email.com",
  "document": "12345678900",
  "phone": "21999999999"
}
```

Response:

```json
{
  "id": "uuid",
  "sourceSystem": "MATER_ECCLESIAE",
  "externalReference": "ALUNO-123",
  "name": "João da Silva",
  "email": "joao@email.com",
  "active": true
}
```

## Invoices

### Criar invoice

POST /invoices

Request:

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

Response:

```json
{
  "id": "uuid",
  "customerId": "uuid",
  "description": "Mensalidade Julho/2026",
  "totalAmount": 150.00,
  "status": "OPEN"
}
```

### Buscar invoice

GET /invoices/{invoiceId}

### Buscar invoice por referência externa

GET /invoices/by-reference?sourceSystem=MATER_ECCLESIAE&externalReference=ALUNO-123-MENSALIDADE-2026-07

### Cancelar invoice

PATCH /invoices/{invoiceId}/cancel

## Charges

### Criar cobrança Mercado Pago

POST /charges

Request:

```json
{
  "invoiceId": "uuid",
  "gateway": "MERCADO_PAGO"
}
```

Response:

```json
{
  "id": "uuid",
  "invoiceId": "uuid",
  "gateway": "MERCADO_PAGO",
  "paymentUrl": "https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=xxxx",
  "status": "WAITING_PAYMENT",
  "amount": 150.00
}
```

### Buscar charge

GET /charges/{chargeId}

### Buscar charges por invoice

GET /charges/invoice/{invoiceId}

## Payments

### Buscar pagamento

GET /payments/{paymentId}

### Buscar pagamentos por invoice

GET /payments/invoice/{invoiceId}

## Webhooks

### Webhook Mercado Pago

POST /webhooks/mercado-pago

Response:

```json
{
  "status": "received"
}
```

## Health Check

GET /health

Response:

```json
{
  "status": "UP",
  "service": "billing-service"
}
```

## Regras da API

1. Nenhum endpoint deve expor tokens do gateway.
2. Toda criação de charge deve passar pela regra de negócio.
3. O sistema cliente não cria Payment diretamente.
4. Payment só nasce após retorno/consulta do gateway.
5. Invoice paga não pode ser cancelada pela POC.
6. Invoice cancelada não pode gerar charge.

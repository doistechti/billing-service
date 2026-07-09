# Prompt — Implementar Billing Service com Mercado Pago

## Objetivo

Implementar um microsserviço chamado `billing-service`.

Este serviço será responsável por faturamento, faturas, cobranças, pagamentos, integração com Mercado Pago e webhooks.

A POC deve usar:

- Java 17
- Spring Boot
- PostgreSQL
- JPA/Hibernate
- Flyway ou Liquibase
- Mercado Pago Checkout Pro
- Swagger/OpenAPI

## Antes de implementar

Analise o projeto atual e siga o padrão já existente de:

- packages;
- controllers;
- services;
- repositories;
- records/DTOs;
- exceptions;
- responses;
- migrations;
- logs;
- testes.

Se não existir padrão, crie uma estrutura limpa e modular.

## Estrutura sugerida

```text
billing-service
├── core
│   ├── customer
│   ├── invoice
│   ├── charge
│   └── payment
├── gateway
│   └── mercadopago
├── webhook
├── api
├── config
├── security
└── shared
```

## Entidades

Criar as entidades:

- Customer
- Invoice
- InvoiceItem
- Charge
- Payment
- WebhookEvent

Usar UUID como ID principal.

Usar PostgreSQL.

Criar migrations para todas as tabelas.

## Regras

Implementar as regras dos SDDs:

- invoice deve possuir customer;
- invoice deve possuir pelo menos um item;
- invoice deve calcular total_amount a partir dos itens;
- invoice paga não pode gerar nova charge;
- invoice cancelada não pode gerar charge;
- charge deve salvar gateway;
- charge Mercado Pago deve salvar preferenceId e paymentUrl;
- webhook deve ser idempotente;
- pagamento só deve ser confirmado após consulta no Mercado Pago;
- não confiar apenas no payload recebido no webhook.

## Gateway

Criar interface:

```java
public interface PaymentGatewayService {
    ChargeGatewayResponse createCharge(Invoice invoice);
    GatewayPaymentResponse getPayment(String gatewayPaymentId);
}
```

Criar implementação:

```java
MercadoPagoGatewayService
```

Toda comunicação com Mercado Pago deve ficar isolada nessa classe ou pacote.

A camada de negócio não deve depender diretamente do SDK Mercado Pago.

## API REST

Implementar endpoints:

```text
POST /api/customers
GET  /api/customers/{id}

POST /api/invoices
GET  /api/invoices/{id}
GET  /api/invoices/by-reference
PATCH /api/invoices/{id}/cancel

POST /api/charges
GET  /api/charges/{id}
GET  /api/charges/invoice/{invoiceId}

GET  /api/payments/{id}
GET  /api/payments/invoice/{invoiceId}

POST /api/webhooks/mercado-pago

GET /api/health
```

## Mercado Pago

Usar variáveis de ambiente:

```env
MERCADO_PAGO_ACCESS_TOKEN=TEST-xxxxxxxx
MERCADO_PAGO_PUBLIC_KEY=TEST-xxxxxxxx
MERCADO_PAGO_WEBHOOK_SECRET=xxxxxxxx
APP_BASE_URL=https://billing-dev.seudominio.com
```

Criar preference com:

- invoice description;
- invoice items;
- customer email;
- notification_url;
- external_reference.

Salvar:

- preferenceId;
- paymentUrl;
- gateway;
- status.

## Webhook

O webhook deve:

1. receber payload;
2. salvar payload bruto em webhook_events;
3. identificar paymentId;
4. consultar pagamento no Mercado Pago;
5. localizar invoice pela external_reference;
6. localizar charge;
7. criar payment se ainda não existir;
8. atualizar charge;
9. atualizar invoice;
10. marcar webhook como processado.

## Status

InvoiceStatus:

```text
DRAFT
OPEN
WAITING_PAYMENT
PAID
OVERDUE
CANCELED
```

ChargeStatus:

```text
CREATED
WAITING_PAYMENT
PAID
REJECTED
CANCELED
ERROR
```

Gateway:

```text
MERCADO_PAGO
```

## Segurança

Para a POC:

- proteger endpoints internos com API Key simples;
- deixar webhook público, mas registrar payload;
- futuramente validar assinatura do Mercado Pago;
- nunca expor Access Token em responses.

## Critérios de Aceite

A POC estará pronta quando for possível:

1. criar customer;
2. criar invoice com itens;
3. criar charge Mercado Pago;
4. receber paymentUrl;
5. abrir checkout de teste;
6. realizar pagamento em ambiente de teste;
7. receber webhook;
8. consultar pagamento no Mercado Pago;
9. criar payment;
10. atualizar charge para PAID;
11. atualizar invoice para PAID.

## Observação importante

Não implementar telas administrativas agora.

O módulo `finance-admin` será etapa futura.

O foco desta entrega é backend funcional, modular e testável.

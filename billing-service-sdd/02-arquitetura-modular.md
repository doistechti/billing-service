# SDD 02 — Arquitetura Modular

## Microsserviço

Nome sugerido:

`billing-service`

## Organização Interna

O billing-service será um único microsserviço Spring Boot, separado em módulos internos.

Estrutura sugerida:

billing-service
- core
- api
- gateway
- webhook
- config
- security
- shared

## Módulo Core

Responsável pelas regras de negócio.

Contém:

- Customer;
- Invoice;
- InvoiceItem;
- Charge;
- Payment;
- regras de status;
- validações financeiras.

## Módulo API

Responsável pelos controllers REST.

Contém endpoints para:

- customers;
- invoices;
- charges;
- payments;
- health check.

## Módulo Gateway

Responsável pela comunicação com gateways de pagamento.

Primeira implementação:

- Mercado Pago.

Deve existir uma interface genérica:

PaymentGatewayService

Implementações futuras:

- MercadoPagoGatewayService;
- AsaasGatewayService;
- EfiGatewayService;
- PagarMeGatewayService.

## Módulo Webhook

Responsável por receber notificações externas de gateways.

Contém:

- MercadoPagoWebhookController;
- WebhookEventService;
- processamento idempotente;
- registro de payload bruto.

## Comunicação com Outros Sistemas

O billing-service será chamado via API REST.

Exemplo:

Mater Ecclesiae cria uma fatura:

POST /api/invoices

O billing-service retorna:

- invoiceId;
- chargeId;
- paymentUrl;
- status.

## Princípio Importante

O sistema cliente não deve conhecer detalhes do Mercado Pago.

O sistema cliente apenas sabe que existe uma fatura e um link de pagamento.

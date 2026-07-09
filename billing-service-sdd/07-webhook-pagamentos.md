# SDD 07 — Webhook de Pagamentos

## Objetivo

Definir o processamento de webhooks enviados pelo Mercado Pago.

## Endpoint

POST /api/webhooks/mercado-pago

## Responsabilidades

O webhook deve:

- receber o payload bruto;
- salvar o evento em `webhook_events`;
- identificar o `paymentId`;
- consultar o pagamento diretamente no Mercado Pago;
- localizar a invoice pela `external_reference`;
- localizar a charge vinculada;
- atualizar payment, charge e invoice;
- garantir idempotência.

## Regra Principal

O billing-service nunca deve marcar uma invoice como paga apenas com base no payload do webhook.

Sempre consultar o pagamento no Mercado Pago antes de confirmar.

## Status Mercado Pago

Mapeamento inicial:

```text
approved    → PAID
pending     → WAITING_PAYMENT
in_process  → WAITING_PAYMENT
rejected    → REJECTED
cancelled   → CANCELED
refunded    → CANCELED
```

## Fluxo Aprovado

```text
1. Mercado Pago envia webhook
2. Billing salva webhook_events
3. Billing identifica gateway_payment_id
4. Billing consulta pagamento no Mercado Pago
5. Mercado Pago retorna status approved
6. Billing cria registro em payments
7. Billing atualiza charge para PAID
8. Billing atualiza invoice para PAID
9. Billing preenche paid_at
10. Billing marca webhook como processed
```

## Idempotência

Se o mesmo webhook chegar mais de uma vez:

- não duplicar payment;
- não alterar invoice já paga;
- registrar evento repetido;
- responder `received`.

## Falhas

Se o pagamento não for encontrado:

- registrar erro em `webhook_events.error_message`;
- manter `processed = false`;
- não alterar invoice.

Se a invoice não for encontrada:

- registrar erro;
- não criar payment.

## Resposta

```json
{
  "status": "received"
}
```

# SDD 06 — Integração Mercado Pago

## Objetivo

Integrar o billing-service ao Mercado Pago usando Checkout Pro.

## Variáveis de Ambiente

```env
MERCADO_PAGO_ACCESS_TOKEN=TEST-xxxxxxxxxxxxxxxx
MERCADO_PAGO_PUBLIC_KEY=TEST-xxxxxxxxxxxxxxxx
MERCADO_PAGO_WEBHOOK_SECRET=xxxxxxxxxxxxxxxx
APP_BASE_URL=https://billing-dev.seudominio.com
```

## Configuração

application.yml:

```yaml
mercado-pago:
  access-token: ${MERCADO_PAGO_ACCESS_TOKEN}
  public-key: ${MERCADO_PAGO_PUBLIC_KEY}
  webhook-secret: ${MERCADO_PAGO_WEBHOOK_SECRET}
  notification-url: ${APP_BASE_URL}/api/webhooks/mercado-pago
```

## Interface

Criar interface:

PaymentGatewayService

Métodos mínimos:

- createCharge(Invoice invoice)
- getPayment(String gatewayPaymentId)
- processWebhook(Map payload)

## Implementação

Criar classe:

MercadoPagoGatewayService

Responsabilidades:

1. receber invoice;
2. montar preference;
3. enviar preference para Mercado Pago;
4. obter preferenceId;
5. obter initPoint ou sandboxInitPoint;
6. retornar dados da cobrança.

## Dados da Preference

A preference deve conter:

- description da invoice;
- itens da invoice;
- valor total;
- e-mail do customer;
- notification_url;
- external_reference;
- back_urls, se necessário.

## External Reference

Usar o ID da invoice e a referência externa.

Formato sugerido:

INVOICE:{invoiceId}|SOURCE:{sourceSystem}|REF:{externalReference}

## Retorno Esperado

A criação de cobrança deve retornar:

- gateway;
- gatewayPreferenceId;
- paymentUrl;
- amount;
- status.

## Ambiente de Teste

Na POC, usar credenciais TEST.

O paymentUrl deve preferencialmente usar sandboxInitPoint quando disponível.

## Ambiente de Produção

Produção deve ser ativada apenas trocando variáveis de ambiente.

Nunca deixar token fixo no código.

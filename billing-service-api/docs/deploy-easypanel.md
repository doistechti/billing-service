# Deploy no EasyPanel

## Build

Use o `Dockerfile` da raiz do repositorio.

Porta exposta pela aplicacao:

```text
8080
```

## Variaveis de ambiente

Configure no EasyPanel:

```env
DB_URL=jdbc:postgresql://host:5432/billing_service
DB_USERNAME=postgres
DB_PASSWORD=postgres

INTERNAL_API_KEY=troque-este-valor

MERCADO_PAGO_BASE_URL=https://api.mercadopago.com
MERCADO_PAGO_ACCESS_TOKEN=TEST-xxxxxxxxxxxxxxxx
MERCADO_PAGO_PUBLIC_KEY=TEST-xxxxxxxxxxxxxxxx
MERCADO_PAGO_WEBHOOK_SECRET=xxxxxxxxxxxxxxxx
APP_BASE_URL=https://seu-dominio.com
```

## Webhook Mercado Pago

Cadastre no Mercado Pago:

```text
https://seu-dominio.com/api/webhooks/mercado-pago
```

O valor de `APP_BASE_URL` deve ser a URL publica do servico sem barra final.

## Health check

Endpoint publico:

```text
GET /api/health
```

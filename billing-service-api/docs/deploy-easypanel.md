# Deploy no EasyPanel

## Porta da aplicacao

```text
8080
```

## Variaveis de ambiente

Configure no EasyPanel com um destes formatos.

Formato recomendado:

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

Formatos alternativos aceitos pela aplicacao:

```env
DATABASE_URL=jdbc:postgresql://host:5432/billing_service
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=postgres
```

ou

```env
DB_HOST=host
DB_PORT=5432
DB_NAME=billing_service
DB_USER=postgres
DB_PASSWORD=postgres
```

ou

```env
POSTGRES_HOST=host
POSTGRES_PORT=5432
POSTGRES_DB=billing_service
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
```

Se o banco estiver em outro servico do EasyPanel, confirme que a aplicacao recebe as variaveis com um desses nomes. Se nao receber, ela cai no default local e tenta `localhost:5433/billing_service`.

## Webhook Mercado Pago

Cadastre no Mercado Pago:

```text
https://seu-dominio.com/api/webhooks/mercado-pago
```

O valor de `APP_BASE_URL` deve ser a URL publica do servico sem barra final.

## Observacao importante sobre atualizacao de status

Criar `customer`, `invoice` e `charge` grava no banco imediatamente.

A mudanca de status da cobranca para `PAID` depende de duas coisas:

1. o Mercado Pago chamar `POST /api/webhooks/mercado-pago`
2. a aplicacao conseguir consultar a API do Mercado Pago com `MERCADO_PAGO_ACCESS_TOKEN`

Se o webhook nao chegar ou a consulta ao Mercado Pago falhar, a `invoice` continua sem atualizar para `PAID`, mas o evento fica salvo em `webhook_events`.

## Verificacao rapida

1. confira os logs de inicializacao e procure por erros de conexao com Postgres ou Flyway
2. crie um customer e confirme que a linha aparece em `customers`
3. apos pagar, confira se existe linha em `webhook_events`
4. se existir webhook e nao houver atualizacao em `payments` ou `invoices`, o problema esta no processamento do webhook ou nas credenciais do Mercado Pago

## Health check

Endpoint publico:

```text
GET /api/health
```

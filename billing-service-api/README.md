# billing-service-api

API backend do `billing-service` para gerenciamento de clientes, invoices, charges, payments, integração com Mercado Pago e processamento de webhooks.

## Stack

- Java 17
- Spring Boot 3
- Spring Web
- Spring Data JPA
- Spring Security
- PostgreSQL
- Flyway
- OpenAPI / Swagger

## Estrutura

```text
billing-service-api
├── docs
│   └── modelo-de-dominio.md
├── src
│   ├── main
│   │   ├── java/com/doistech/billingservice
│   │   │   ├── api
│   │   │   ├── config
│   │   │   ├── core
│   │   │   │   ├── customer
│   │   │   │   ├── invoice
│   │   │   │   ├── charge
│   │   │   │   └── payment
│   │   │   ├── gateway/mercadopago
│   │   │   ├── security
│   │   │   ├── shared
│   │   │   └── webhook
│   │   └── resources
│   │       ├── application.yml
│   │       └── db/migration
│   └── test
└── pom.xml
```

## Objetivo

A API foi desenhada para ser genérica. Ela não carrega regra de negócio de um cliente específico.

Sistemas externos enviam:

- `sourceSystem`
- `externalReference`
- dados do customer
- dados da invoice
- itens da cobrança

## Domínios

### Customer

Guarda o pagador.

### Invoice

Guarda a fatura e seus itens.

### Charge

Guarda a tentativa de cobrança gerada em um gateway.

### Payment

Guarda o pagamento confirmado após consulta no gateway.

### WebhookEvent

Guarda o payload recebido do gateway e o estado do processamento.

Detalhamento completo em [`docs/modelo-de-dominio.md`](</Users/doistechti/Projetos/DOISTECH/billing-service/billing-service-api/docs/modelo-de-dominio.md>).

## Regras principais da POC

- invoice deve ter customer
- invoice deve ter pelo menos um item
- invoice total é calculada a partir dos itens
- invoice paga não pode gerar nova charge
- invoice cancelada não pode gerar charge
- payment não é criado diretamente pelo cliente
- webhook não confirma pagamento sem consulta no Mercado Pago
- webhook deve ser idempotente

## Variáveis de ambiente

```env
DB_URL=jdbc:postgresql://localhost:5432/billing_service
DB_USERNAME=postgres
DB_PASSWORD=postgres

INTERNAL_API_KEY=change-me

MERCADO_PAGO_BASE_URL=https://api.mercadopago.com
MERCADO_PAGO_ACCESS_TOKEN=TEST-xxxxxxxxxxxxxxxx
MERCADO_PAGO_PUBLIC_KEY=TEST-xxxxxxxxxxxxxxxx
MERCADO_PAGO_WEBHOOK_SECRET=xxxxxxxxxxxxxxxx
APP_BASE_URL=http://localhost:8080
```

## Configuração

O arquivo principal é [`src/main/resources/application.yml`](</Users/doistechti/Projetos/DOISTECH/billing-service/billing-service-api/src/main/resources/application.yml>).

Pontos importantes:

- endpoints internos usam API key no header `X-API-Key`
- webhook e health são públicos
- Flyway está habilitado
- `notification-url` do Mercado Pago é montada a partir de `APP_BASE_URL`

## Banco de dados

As tabelas são criadas pela migration:

[`src/main/resources/db/migration/V1__create_billing_tables.sql`](</Users/doistechti/Projetos/DOISTECH/billing-service/billing-service-api/src/main/resources/db/migration/V1__create_billing_tables.sql>)

Tabelas da POC:

- `customers`
- `invoices`
- `invoice_items`
- `charges`
- `payments`
- `webhook_events`

## Endpoints

Base path: `/api`

### Customers

- `POST /customers`
- `GET /customers/{id}`

### Invoices

- `POST /invoices`
- `GET /invoices/{id}`
- `GET /invoices/by-reference`
- `PATCH /invoices/{id}/cancel`

### Charges

- `POST /charges`
- `GET /charges/{id}`
- `GET /charges/invoice/{invoiceId}`

### Payments

- `GET /payments/{id}`
- `GET /payments/invoice/{invoiceId}`

### Webhooks

- `POST /webhooks/mercado-pago`

### Health

- `GET /health`

## Autenticação

Todos os endpoints internos exigem:

```http
X-API-Key: {INTERNAL_API_KEY}
```

Endpoints públicos na POC:

- `/api/health`
- `/api/webhooks/mercado-pago`
- documentação Swagger

## Swagger

Quando a aplicação estiver rodando:

- OpenAPI JSON: `/api/docs`
- Swagger UI: `/swagger-ui.html`

## Fluxo principal

## 1. Criar customer

Registra o pagador.

## 2. Criar invoice

Registra a fatura com seus itens e calcula o total.

## 3. Criar charge

Gera a preferência no Mercado Pago, salva os dados de checkout e devolve `paymentUrl`.

## 4. Aguardar pagamento

O pagador acessa o checkout do Mercado Pago.

## 5. Receber webhook

O evento é salvo em `webhook_events` e a API consulta o Mercado Pago para validar o pagamento.

## 6. Atualizar estado interno

Se o pagamento estiver aprovado:

- cria `payment` se ainda não existir
- atualiza `charge`
- atualiza `invoice`

## Exemplo de uso

### Criar customer

```bash
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -H "X-API-Key: change-me" \
  -d '{
    "sourceSystem": "MATER_ECCLESIAE",
    "externalReference": "ALUNO-123",
    "name": "João da Silva",
    "email": "joao@email.com",
    "document": "12345678900",
    "phone": "21999999999"
  }'
```

### Criar invoice

```bash
curl -X POST http://localhost:8080/api/invoices \
  -H "Content-Type: application/json" \
  -H "X-API-Key: change-me" \
  -d '{
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
  }'
```

### Criar charge

```bash
curl -X POST http://localhost:8080/api/charges \
  -H "Content-Type: application/json" \
  -H "X-API-Key: change-me" \
  -d '{
    "invoiceId": "uuid",
    "gateway": "MERCADO_PAGO"
  }'
```

## Como rodar

Pré-requisitos:

- Java 17
- PostgreSQL
- Maven 3.9+ ou wrapper Maven

Passos:

1. criar o banco PostgreSQL
2. exportar as variáveis de ambiente
3. iniciar a aplicação

Exemplo:

```bash
mvn spring-boot:run
```

## Testes

Testes unitários adicionados:

- regras de criação e cancelamento de invoice
- bloqueio de charge inválida
- processamento de webhook aprovado

Execução esperada:

```bash
mvn test
```

## Limitação atual do workspace

No estado atual deste workspace, o projeto não tem `mvnw` e o ambiente em que a implementação foi feita não tinha `mvn` instalado. A documentação acima assume um ambiente com Maven disponível.

# SDD 04 — Modelagem PostgreSQL

## Banco de Dados

Banco utilizado:

PostgreSQL

## Padrão de IDs

Usar UUID como identificador principal das entidades.

## Tabela: customers

Representa o pagador.

Campos:

- id UUID primary key
- source_system varchar(100) not null
- external_reference varchar(255) not null
- name varchar(255) not null
- email varchar(255) not null
- document varchar(30)
- phone varchar(30)
- active boolean not null default true
- created_at timestamp not null
- updated_at timestamp not null

Índice recomendado:

- source_system + external_reference

## Tabela: invoices

Representa a fatura.

Campos:

- id UUID primary key
- customer_id UUID not null
- source_system varchar(100) not null
- external_reference varchar(255) not null
- description varchar(255) not null
- total_amount numeric(15,2) not null
- due_date date
- paid_at timestamp
- canceled_at timestamp
- status varchar(50) not null
- created_at timestamp not null
- updated_at timestamp not null

Status possíveis:

- DRAFT
- OPEN
- WAITING_PAYMENT
- PAID
- OVERDUE
- CANCELED

## Tabela: invoice_items

Representa os itens da fatura.

Campos:

- id UUID primary key
- invoice_id UUID not null
- description varchar(255) not null
- quantity numeric(15,2) not null
- unit_amount numeric(15,2) not null
- total_amount numeric(15,2) not null
- created_at timestamp not null

## Tabela: charges

Representa a cobrança gerada para uma invoice.

Campos:

- id UUID primary key
- invoice_id UUID not null
- gateway varchar(50) not null
- gateway_preference_id varchar(255)
- gateway_payment_id varchar(255)
- payment_url text
- amount numeric(15,2) not null
- status varchar(50) not null
- created_at timestamp not null
- updated_at timestamp not null

Status possíveis:

- CREATED
- WAITING_PAYMENT
- PAID
- REJECTED
- CANCELED
- ERROR

## Tabela: payments

Representa o pagamento retornado pelo gateway.

Campos:

- id UUID primary key
- invoice_id UUID not null
- charge_id UUID not null
- gateway varchar(50) not null
- gateway_payment_id varchar(255) not null
- gateway_status varchar(100)
- amount numeric(15,2) not null
- paid_at timestamp
- raw_response jsonb
- created_at timestamp not null

Índice recomendado:

- gateway + gateway_payment_id

## Tabela: webhook_events

Representa eventos recebidos de gateways.

Campos:

- id UUID primary key
- gateway varchar(50) not null
- event_id varchar(255)
- event_type varchar(100)
- gateway_payment_id varchar(255)
- payload jsonb not null
- processed boolean not null default false
- error_message text
- created_at timestamp not null
- processed_at timestamp

Índices recomendados:

- gateway + event_id
- gateway + gateway_payment_id

## Relacionamentos

customers 1:N invoices

invoices 1:N invoice_items

invoices 1:N charges

charges 1:N payments

invoices 1:N payments

## Observação

Na POC, uma invoice terá apenas uma charge ativa.

No futuro, poderá ter múltiplas tentativas de cobrança.

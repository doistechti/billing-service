create extension if not exists "pgcrypto";

create table customers (
    id uuid primary key,
    source_system varchar(100) not null,
    external_reference varchar(255) not null,
    name varchar(255) not null,
    email varchar(255) not null,
    document varchar(30),
    phone varchar(30),
    active boolean not null default true,
    created_at timestamp not null,
    updated_at timestamp not null
);

create unique index uk_customers_source_external_reference
    on customers (source_system, external_reference);

create table invoices (
    id uuid primary key,
    customer_id uuid not null references customers(id),
    source_system varchar(100) not null,
    external_reference varchar(255) not null,
    description varchar(255) not null,
    total_amount numeric(15, 2) not null,
    due_date date,
    paid_at timestamp,
    canceled_at timestamp,
    status varchar(50) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create unique index uk_invoices_source_external_reference
    on invoices (source_system, external_reference);

create table invoice_items (
    id uuid primary key,
    invoice_id uuid not null references invoices(id) on delete cascade,
    description varchar(255) not null,
    quantity numeric(15, 2) not null,
    unit_amount numeric(15, 2) not null,
    total_amount numeric(15, 2) not null,
    created_at timestamp not null
);

create table charges (
    id uuid primary key,
    invoice_id uuid not null references invoices(id),
    gateway varchar(50) not null,
    gateway_preference_id varchar(255),
    gateway_payment_id varchar(255),
    payment_url text,
    amount numeric(15, 2) not null,
    status varchar(50) not null,
    created_at timestamp not null,
    updated_at timestamp not null
);

create table payments (
    id uuid primary key,
    invoice_id uuid not null references invoices(id),
    charge_id uuid not null references charges(id),
    gateway varchar(50) not null,
    gateway_payment_id varchar(255) not null,
    gateway_status varchar(100),
    amount numeric(15, 2) not null,
    paid_at timestamp,
    raw_response jsonb,
    created_at timestamp not null
);

create unique index uk_payments_gateway_payment_id
    on payments (gateway, gateway_payment_id);

create table webhook_events (
    id uuid primary key,
    gateway varchar(50) not null,
    event_id varchar(255),
    event_type varchar(100),
    gateway_payment_id varchar(255),
    payload jsonb not null,
    processed boolean not null default false,
    error_message text,
    created_at timestamp not null,
    processed_at timestamp
);

create index idx_webhook_events_gateway_event_id
    on webhook_events (gateway, event_id);

create index idx_webhook_events_gateway_payment_id
    on webhook_events (gateway, gateway_payment_id);

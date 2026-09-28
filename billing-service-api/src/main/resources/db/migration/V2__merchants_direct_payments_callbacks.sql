create table merchants (
    id uuid primary key,
    code varchar(60) not null,
    name varchar(255) not null,
    api_key_hash varchar(64),
    api_key_prefix varchar(16),
    mp_access_token_encrypted text,
    mp_public_key varchar(255),
    mp_webhook_secret_encrypted text,
    sandbox boolean not null default false,
    callback_url text,
    callback_secret_encrypted text,
    active boolean not null default true,
    created_at timestamp not null,
    updated_at timestamp not null
);

create unique index uk_merchants_code on merchants (code);
create unique index uk_merchants_api_key_hash on merchants (api_key_hash);

-- Merchant que representa a instalação anterior (INTERNAL_API_KEY + MERCADO_PAGO_* do ambiente).
insert into merchants (id, code, name, sandbox, active, created_at, updated_at)
values ('00000000-0000-0000-0000-000000000001', 'legacy', 'Legacy', false, true, now(), now());

alter table customers add column merchant_id uuid references merchants(id);
update customers set merchant_id = '00000000-0000-0000-0000-000000000001';
alter table customers alter column merchant_id set not null;
drop index uk_customers_source_external_reference;
create unique index uk_customers_merchant_source_external_reference
    on customers (merchant_id, source_system, external_reference);

alter table invoices add column merchant_id uuid references merchants(id);
update invoices set merchant_id = '00000000-0000-0000-0000-000000000001';
alter table invoices alter column merchant_id set not null;
drop index uk_invoices_source_external_reference;
create unique index uk_invoices_merchant_source_external_reference
    on invoices (merchant_id, source_system, external_reference);

alter table charges add column merchant_id uuid references merchants(id);
update charges set merchant_id = '00000000-0000-0000-0000-000000000001';
alter table charges alter column merchant_id set not null;
alter table charges add column method varchar(30) not null default 'CHECKOUT_PRO';
alter table charges add column idempotency_key varchar(255);
alter table charges add column status_detail varchar(255);
alter table charges add column installments integer;
alter table charges add column pix_qr_code text;
alter table charges add column pix_qr_code_base64 text;
alter table charges add column ticket_url text;
alter table charges add column boleto_barcode varchar(255);
alter table charges add column boleto_digitable_line varchar(255);
alter table charges add column expires_at timestamp;
alter table charges add column paid_at timestamp;
alter table charges add column refunded_amount numeric(15, 2) not null default 0;

create unique index uk_charges_merchant_idempotency_key
    on charges (merchant_id, idempotency_key)
    where idempotency_key is not null;
create index idx_charges_merchant_gateway_payment_id
    on charges (merchant_id, gateway, gateway_payment_id);
create index idx_charges_invoice_id on charges (invoice_id);

alter table payments add column merchant_id uuid references merchants(id);
update payments set merchant_id = '00000000-0000-0000-0000-000000000001';
alter table payments alter column merchant_id set not null;

alter table webhook_events add column merchant_id uuid references merchants(id);
update webhook_events set merchant_id = '00000000-0000-0000-0000-000000000001';

create table callback_deliveries (
    id uuid primary key,
    merchant_id uuid not null references merchants(id),
    event_type varchar(60) not null,
    charge_id uuid references charges(id),
    target_url text not null,
    payload text not null,
    status varchar(20) not null,
    attempts integer not null default 0,
    next_attempt_at timestamp not null,
    last_status_code integer,
    last_error text,
    delivered_at timestamp,
    created_at timestamp not null,
    updated_at timestamp not null
);

create index idx_callback_deliveries_due
    on callback_deliveries (status, next_attempt_at);
create index idx_callback_deliveries_merchant_status
    on callback_deliveries (merchant_id, status);

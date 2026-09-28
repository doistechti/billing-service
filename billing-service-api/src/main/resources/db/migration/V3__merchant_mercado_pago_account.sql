-- Conta Mercado Pago verificada (GET /users/me) quando o próprio merchant cadastra as credenciais.
alter table merchants add column mp_account_id varchar(40);
alter table merchants add column mp_account_nickname varchar(120);
alter table merchants add column mp_verified_at timestamp;

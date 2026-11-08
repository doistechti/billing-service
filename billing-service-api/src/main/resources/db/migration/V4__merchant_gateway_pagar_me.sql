-- Gateway por merchant (SDD 10). Somente aditiva: os merchants existentes continuam MERCADO_PAGO e as
-- colunas mp_* não mudam, então a versão anterior do serviço segue funcionando sobre este esquema.
alter table merchants add column gateway varchar(50) not null default 'MERCADO_PAGO';

-- Credenciais Pagar.me (secret key e segredo do webhook cifrados com BILLING_ENCRYPTION_KEY).
alter table merchants add column pm_secret_key_encrypted text;
alter table merchants add column pm_public_key text;
alter table merchants add column pm_webhook_secret_encrypted text;

-- Conta Pagar.me verificada quando o próprio merchant cadastra as credenciais.
alter table merchants add column pm_account_id varchar(60);
alter table merchants add column pm_account_name varchar(120);
alter table merchants add column pm_verified_at timestamp;

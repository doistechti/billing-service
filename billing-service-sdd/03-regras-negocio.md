# SDD 03 — Regras de Negócio

## Customer

Customer representa o pagador.

Não deve ser chamado de Aluno, Cliente da Oficina ou Cliente da AJS.

Campos principais:

- name;
- email;
- document;
- phone;
- externalReference;
- sourceSystem.

## Invoice

Invoice representa uma fatura.

Exemplos:

- mensalidade escolar;
- fechamento quinzenal;
- orçamento aprovado;
- assinatura SaaS.

## Status da Invoice

- DRAFT
- OPEN
- WAITING_PAYMENT
- PAID
- OVERDUE
- CANCELED

## Charge

Charge representa uma tentativa de cobrança da invoice.

Uma invoice pode ter uma ou mais charges, mas na POC deve existir apenas uma cobrança ativa por invoice.

## Status da Charge

- CREATED
- WAITING_PAYMENT
- PAID
- REJECTED
- CANCELED
- ERROR

## Payment

Payment representa o pagamento confirmado ou recusado pelo gateway.

## Regras Principais

1. Customer pode ter várias invoices.
2. Invoice deve possuir pelo menos um item.
3. Invoice deve possuir valor total maior que zero.
4. Invoice cancelada não pode gerar charge.
5. Invoice paga não pode gerar nova charge.
6. Charge deve estar vinculada a uma invoice.
7. Charge deve salvar o gateway usado.
8. Charge Mercado Pago deve salvar preferenceId.
9. Charge deve salvar paymentUrl.
10. Webhook deve ser idempotente.
11. Pagamento só pode ser confirmado após consulta no gateway.
12. Payload do webhook não deve ser usado como única fonte de verdade.
13. Ao confirmar pagamento aprovado, a charge muda para PAID.
14. Ao confirmar pagamento aprovado, a invoice muda para PAID.
15. O sistema deve registrar data/hora do pagamento.

## External Reference

Toda entidade principal deve permitir rastreio externo.

Exemplo para Mater:

sourceSystem = MATER_ECCLESIAE  
externalReference = ALUNO-123-MENSALIDADE-2026-07

Exemplo para AJS:

sourceSystem = AJS_EXPRESS  
externalReference = FECHAMENTO-CLIENTE-55-2026-07-1

## Idempotência

Se o Mercado Pago enviar o mesmo webhook mais de uma vez, o sistema não deve duplicar pagamentos.

Se a invoice já estiver PAID, o evento deve ser ignorado com segurança.

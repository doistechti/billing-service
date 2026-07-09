# SDD 01 — Visão Geral do Billing Service

## Objetivo

Criar um microsserviço de faturamento reutilizável chamado `billing-service`.

Este serviço será responsável por gerenciar clientes/pagadores, faturas, cobranças, pagamentos, gateways de pagamento e webhooks.

A primeira integração será com Mercado Pago usando Checkout Pro.

## Escopo Inicial da POC

O billing-service deverá permitir:

- cadastrar customer/pagador;
- criar invoice/fatura;
- criar charge/cobrança;
- gerar link de pagamento Mercado Pago;
- receber webhook de pagamento;
- consultar pagamento no Mercado Pago;
- atualizar status da cobrança;
- atualizar status da fatura.

## Banco de Dados

A POC usará PostgreSQL.

## Conceito Principal

O billing-service não deve conhecer regras específicas do Mater Ecclesiae, AJS Express, Oficina ou qualquer outro sistema.

Ele deve trabalhar com entidades genéricas:

- Customer;
- Invoice;
- InvoiceItem;
- Charge;
- Payment;
- WebhookEvent.

## Sistemas Consumidores

Exemplos de sistemas que poderão usar o billing-service:

- Mater Ecclesiae;
- AJS Express;
- Oficina mecânica;
- SaaS de cobranças;
- Portal de mensalidades;
- Sistemas internos DoisTech.

## Fluxo Principal

Sistema cliente → Billing Service → Mercado Pago → Webhook → Billing Service → Sistema cliente.

## Fora do Escopo Inicial

Não fazem parte da primeira POC:

- assinatura recorrente automática;
- split de pagamento;
- nota fiscal;
- juros e multa automáticos;
- conciliação bancária completa;
- múltiplos gateways ativos;
- telas administrativas;
- dashboard financeiro completo.

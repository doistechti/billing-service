# Revisao de Escopo SDD - Billing Service

Data: 2026-07-09

## Objetivo

Registrar a revisao feita sobre a aderencia do `billing-service-api` ao SDD e as correcoes aplicadas para aproximar a POC dos criterios de aceite.

## Correcoes aplicadas

### 1. Testes unitarios alinhados com a implementacao

Os testes estavam configurando mocks para `InvoiceRepository.findById`, mas os servicos usam `findDetailedById`.

Arquivos ajustados:

- `src/test/java/com/doistech/billingservice/core/charge/ChargeServiceTest.java`
- `src/test/java/com/doistech/billingservice/core/invoice/InvoiceServiceTest.java`
- `src/test/java/com/doistech/billingservice/webhook/MercadoPagoWebhookServiceTest.java`

Tambem foi usado `ReflectionTestUtils` para preencher o `id` das entidades nos testes unitarios, simulando o UUID que normalmente seria definido pelo JPA no `@PrePersist`.

### 2. Idempotencia do webhook para invoice ja paga

O webhook agora consulta o pagamento no gateway, resolve a invoice pelo `external_reference` e, se a invoice ja estiver `PAID`, marca o evento como processado e encerra o fluxo.

Isso evita que um webhook repetido ou tardio altere `charge`, crie `payment` indevido ou sobrescreva estado de uma fatura ja liquidada.

Arquivo ajustado:

- `src/main/java/com/doistech/billingservice/webhook/MercadoPagoWebhookService.java`

Teste adicionado:

- `shouldIgnoreWebhookWhenInvoiceIsAlreadyPaid`

### 3. Validacao explicita de gateway no fluxo de charge

O servico de criacao de charge agora rejeita gateways diferentes de `MERCADO_PAGO`, mantendo a POC restrita ao gateway previsto no SDD.

Arquivo ajustado:

- `src/main/java/com/doistech/billingservice/core/charge/ChargeService.java`

## Evidencia de validacao

Comando executado:

```bash
mvn test
```

Resultado:

```text
Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Escopo atualmente coberto

- Cadastro e consulta de customer.
- Criacao e consulta de invoice.
- Busca de invoice por referencia externa.
- Cancelamento de invoice, bloqueando invoice paga.
- Criacao de charge Mercado Pago.
- Bloqueio de charge para invoice paga ou cancelada.
- Bloqueio de nova charge quando ja existe charge ativa.
- Geracao de preference no Mercado Pago via API isolada em gateway service.
- Registro de webhook com payload bruto.
- Consulta do pagamento no Mercado Pago antes de confirmar status.
- Criacao idempotente de payment por `gateway + gateway_payment_id`.
- Atualizacao de charge e invoice para `PAID` quando o pagamento aprovado e confirmado pelo gateway.
- API key simples nos endpoints internos, mantendo webhook, health e docs publicos.
- Migrations PostgreSQL para as tabelas principais do SDD.

## Pendencias para garantia completa do escopo

Estas pendencias nao impedem a compilacao nem a suite atual, mas ainda sao recomendadas para declarar a POC como validada ponta a ponta:

1. Criar testes de controller/API para validar status HTTP, payloads e seguranca `X-API-Key`.
2. Criar teste de integracao com PostgreSQL real ou Testcontainers para validar Flyway, JPA e tipos `jsonb`.
3. Executar teste manual ou automatizado em sandbox Mercado Pago:
   - criar customer;
   - criar invoice;
   - criar charge;
   - abrir `paymentUrl`;
   - realizar pagamento de teste;
   - receber webhook;
   - confirmar `payment`, `charge` e `invoice` atualizados.
4. Avaliar regra de quantidade decimal em itens. O banco e o SDD aceitam `numeric(15,2)`, mas o payload atual para Mercado Pago usa quantidade inteira.
5. Futuramente validar assinatura do webhook Mercado Pago antes de producao.

## Parecer

A aderencia ao SDD melhorou e a suite automatizada voltou a ficar verde. O backend cobre o escopo principal da POC em nivel de servico e dominio.

Para considerar o escopo completamente garantido, o proximo passo tecnico e adicionar testes de API/integracao e executar o fluxo real em sandbox Mercado Pago.

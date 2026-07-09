# Modelo de Domínio

Este documento descreve o que cada domínio da API guarda, qual é a responsabilidade de cada entidade e como o fluxo de faturamento funciona na POC.

## Visão geral

O `billing-service-api` foi separado em domínios de negócio que representam o ciclo completo de cobrança:

- `customer`: quem vai pagar
- `invoice`: o que será cobrado
- `charge`: a tentativa de cobrança em um gateway
- `payment`: o resultado confirmado do pagamento
- `webhook`: os eventos recebidos do gateway
- `gateway/mercadopago`: integração externa isolada

O objetivo é manter a regra de negócio no serviço e deixar a integração externa encapsulada.

## Customer

O domínio `customer` guarda o pagador.

Ele não representa um tipo de cliente específico do sistema chamador. Pode ser:

- aluno
- responsável financeiro
- cliente de oficina
- cliente de logística
- assinante

### O que guarda

- `id`: identificador UUID interno
- `sourceSystem`: sistema de origem, por exemplo `MATER_ECCLESIAE`
- `externalReference`: identificador externo no sistema de origem
- `name`
- `email`
- `document`
- `phone`
- `active`
- `createdAt`
- `updatedAt`

### Papel no fluxo

Um `Customer` é o dono financeiro da `Invoice`. A invoice sempre precisa apontar para um customer existente.

## Invoice

O domínio `invoice` guarda a fatura.

Essa é a entidade central do negócio. Ela representa a obrigação financeira que precisa ser cobrada.

### O que guarda

- `id`
- `customer`
- `sourceSystem`
- `externalReference`
- `description`
- `totalAmount`
- `dueDate`
- `paidAt`
- `canceledAt`
- `status`
- `items`

### InvoiceItem

`InvoiceItem` guarda os itens que formam a invoice.

Cada item possui:

- `id`
- `description`
- `quantity`
- `unitAmount`
- `totalAmount`
- `createdAt`

### Regras principais

- uma invoice deve ter customer
- uma invoice deve ter pelo menos um item
- o `totalAmount` é calculado a partir dos itens
- o valor total deve ser maior que zero
- invoice paga não pode ser cancelada
- invoice cancelada não pode gerar charge
- invoice paga não pode gerar nova charge

### Status

- `DRAFT`
- `OPEN`
- `WAITING_PAYMENT`
- `PAID`
- `OVERDUE`
- `CANCELED`

### Papel no fluxo

A invoice nasce com os dados do sistema cliente, consolida os itens e define o valor que será enviado ao gateway quando uma cobrança for criada.

## Charge

O domínio `charge` guarda a tentativa de cobrança criada para uma invoice.

Na POC, a regra é existir apenas uma cobrança ativa por invoice.

### O que guarda

- `id`
- `invoice`
- `gateway`
- `gatewayPreferenceId`
- `gatewayPaymentId`
- `paymentUrl`
- `amount`
- `status`
- `createdAt`
- `updatedAt`

### Regras principais

- charge sempre pertence a uma invoice
- charge salva o gateway usado
- charge do Mercado Pago salva `gatewayPreferenceId`
- charge salva `paymentUrl`
- não pode existir nova charge ativa para invoice paga
- não pode existir nova charge ativa para invoice cancelada

### Status

- `CREATED`
- `WAITING_PAYMENT`
- `PAID`
- `REJECTED`
- `CANCELED`
- `ERROR`

### Papel no fluxo

A charge é criada quando o sistema pede a geração da cobrança. Nessa etapa a API chama o gateway, cria a preferência do Mercado Pago e devolve a URL de pagamento.

## Payment

O domínio `payment` guarda o pagamento confirmado a partir de consulta no gateway.

O sistema cliente não cria `Payment` diretamente.

### O que guarda

- `id`
- `invoice`
- `charge`
- `gateway`
- `gatewayPaymentId`
- `gatewayStatus`
- `amount`
- `paidAt`
- `rawResponse`
- `createdAt`

### Regras principais

- payment só nasce após consulta no gateway
- o payload do webhook não é usado como única fonte de verdade
- payment precisa ficar vinculado à invoice e à charge
- a resposta bruta do gateway é guardada em `rawResponse`

### Papel no fluxo

Quando o webhook chega, a API identifica o `paymentId`, consulta o Mercado Pago e só então cria ou reaproveita o `Payment`.

## WebhookEvent

O domínio `webhook` guarda os eventos recebidos do gateway.

Essa entidade existe para auditoria, rastreabilidade e idempotência operacional.

### O que guarda

- `id`
- `gateway`
- `eventId`
- `eventType`
- `gatewayPaymentId`
- `payload`
- `processed`
- `errorMessage`
- `createdAt`
- `processedAt`

### Regras principais

- todo webhook recebido deve ser persistido
- o payload bruto deve ser armazenado
- o processamento deve ser idempotente
- erros de processamento devem ser registrados

### Papel no fluxo

O webhook é salvo primeiro. Depois disso a API tenta enriquecer o evento consultando o gateway. Se falhar, o erro fica registrado no evento sem marcar `processed = true`.

## Gateway Mercado Pago

O pacote `gateway/mercadopago` isola toda comunicação com o Mercado Pago.

### Responsabilidades

- criar a preferência de checkout
- gerar `paymentUrl`
- consultar pagamento por `gatewayPaymentId`
- traduzir dados externos para objetos internos da aplicação

### O que não deve acontecer

A camada de negócio não deve depender do contrato HTTP do Mercado Pago. Ela só consome a interface `PaymentGatewayService`.

## Fluxo de negócio

## 1. Criar customer

O sistema cliente registra o pagador com `sourceSystem` e `externalReference`.

## 2. Criar invoice

A API valida os itens, calcula o total e cria a fatura em `OPEN`.

## 3. Criar charge

A API valida se a invoice pode ser cobrada.

Depois:

- chama o gateway
- cria a preferência no Mercado Pago
- salva `gatewayPreferenceId`
- salva `paymentUrl`
- muda a invoice para `WAITING_PAYMENT`

## 4. Checkout

O pagador abre a URL retornada e realiza o pagamento.

## 5. Webhook

O Mercado Pago chama `/api/webhooks/mercado-pago`.

A API:

- salva o payload bruto
- identifica `paymentId`
- consulta o pagamento no Mercado Pago
- resolve a invoice a partir de `external_reference`
- localiza a charge
- cria payment se ainda não existir
- atualiza charge
- atualiza invoice
- marca o evento como processado

## 6. Confirmação de pagamento

Se o gateway retornar `approved`:

- `charge.status = PAID`
- `invoice.status = PAID`
- `invoice.paidAt` é preenchido

## Rastreabilidade

O modelo depende de dois identificadores para integração:

- `sourceSystem`: identifica quem chamou a API
- `externalReference`: identifica o objeto no sistema de origem

Além disso, o Mercado Pago recebe uma `external_reference` enriquecida no formato:

`INVOICE:{invoiceId}|SOURCE:{sourceSystem}|REF:{externalReference}`

Isso permite localizar a invoice internamente sem confiar apenas no payload original do webhook.

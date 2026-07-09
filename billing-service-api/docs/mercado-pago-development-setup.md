# Tutorial de Setup no Mercado Pago Developers

Este documento explica, passo a passo, o que precisa ser configurado no Mercado Pago Developers para esta API funcionar em ambiente de desenvolvimento.

Ele foi escrito para a POC atual do `billing-service-api`, que usa:

- `Checkout Pro`
- criação de `preference`
- consulta de pagamentos via API
- recebimento de `webhook`

## Objetivo deste setup

No painel do Mercado Pago, você precisa preparar o ambiente para quatro coisas:

1. identificar a integração com uma aplicação
2. obter credenciais de teste
3. configurar notificações Webhook
4. criar ou localizar contas de teste para simular compra

## O que esta POC realmente precisa

Para o cenário atual, você precisa de:

- `1 aplicação` do tipo `Pagamentos online > Checkouts > Checkout Pro`
- `credenciais de teste` dessa aplicação
- `1 configuração de Webhook` para o evento `Pagamentos`
- `1 conta de teste comprador`
- opcionalmente `1 conta de teste vendedor`, se quiser isolar melhor os testes ou testar outros países

## O que você não precisa criar agora

Para esta POC, você não precisa criar:

- aplicação de `OAuth`
- aplicação de `marketplace`
- conta `integrador`
- configuração de split de pagamento
- aplicação separada para frontend

Motivo: a integração atual é uma loja própria usando `Checkout Pro`, com backend único e sem fluxo de autorização entre múltiplas contas.

## Visão rápida: o que é cada coisa

## 1. Aplicação

É o registro da integração dentro do Mercado Pago. Segundo a documentação oficial, a aplicação é o vínculo entre seu desenvolvimento e o Mercado Pago, e é ela que organiza autenticação, autorização e credenciais da integração.

Na prática, para esta API, a aplicação representa:

- a integração do `billing-service-api` com `Checkout Pro`
- a origem das credenciais que o backend vai usar
- o lugar onde você configura Webhooks e contas de teste

## 2. Credenciais

As credenciais são as chaves ligadas à aplicação.

As relevantes aqui são:

- `Public Key`
- `Access Token`

Para esta API backend:

- o `Access Token` é obrigatório
- a `Public Key` é opcional no estado atual, porque ainda não existe frontend próprio usando SDK client-side

## 3. Contas de teste

São usuários de teste usados para simular pagamentos sem usar dinheiro real.

Tipos possíveis:

- `Vendedor`
- `Comprador`
- `Integrador`

Para esta POC:

- `Comprador` é necessário para testar compra
- `Vendedor` pode ser útil se você quiser separar a conta operacional da sua conta principal
- `Integrador` não é necessário agora

## 4. Webhook

É a configuração que permite ao Mercado Pago chamar seu backend quando um pagamento muda de estado.

Na nossa API isso alimenta:

- `webhook_events`
- consulta do pagamento no gateway
- atualização de `payment`, `charge` e `invoice`

## Arquitetura desta POC no Mercado Pago

```text
Mercado Pago Developers
└── Aplicação: billing-service-api
    ├── Credenciais de teste
    │   ├── Public Key
    │   └── Access Token
    ├── Webhooks
    │   ├── URL de teste
    │   ├── URL de produção
    │   └── chave secreta
    └── Contas de teste
        ├── Comprador
        └── Vendedor (opcional)
```

## Passo 1: criar a conta Mercado Pago que vai ser dona da integração

Se você ainda não tem uma conta Mercado Pago para desenvolvimento:

1. crie uma conta Mercado Pago
2. conclua login no Mercado Pago Developers
3. faça verificação de identidade se o painel solicitar

### Por que isso existe

Sem essa conta você não consegue:

- criar aplicação
- obter credenciais
- configurar Webhooks
- acessar contas de teste

## Passo 2: criar a aplicação da integração

No fluxo oficial do `Checkout Pro`, o Mercado Pago orienta criar uma aplicação e selecionar o tipo da solução que será integrada.

### O que criar

Crie `1 aplicação` para esta API.

Nome sugerido:

- `billing-service-api-dev`

Se quiser separar ambientes:

- `billing-service-api-dev`
- `billing-service-api-hml`
- `billing-service-api-prod`

Para a POC atual, apenas `billing-service-api-dev` já resolve.

### Como criar

No Mercado Pago Developers:

1. faça login
2. entre em `Suas integrações`
3. clique em `Criar aplicação`
4. informe um nome para identificar a aplicação
5. selecione `Pagamentos online`
6. informe que está integrando uma loja desenvolvida por conta própria
7. selecione `Checkouts`
8. escolha `Checkout Pro`
9. confirme a criação

### Por que esta aplicação é necessária

Porque ela:

- gera as credenciais da integração
- vincula as configurações de Webhook
- concentra as contas de teste relacionadas
- identifica formalmente a solução integrada

### Por que só uma aplicação basta aqui

Segundo a documentação, é necessário criar uma aplicação para cada solução do Mercado Pago integrada.

Como esta POC usa apenas `Checkout Pro`, uma única aplicação é suficiente.

## Passo 3: pegar as credenciais de teste

Após criar a aplicação, as credenciais de teste ficam disponíveis automaticamente.

### Onde encontrar

No Mercado Pago Developers:

1. entre em `Suas integrações`
2. abra a aplicação criada
3. vá na seção `Credenciais`

### O que você vai encontrar

Pares de credenciais:

- `Public Key`
- `Access Token`

Também podem aparecer:

- `Client ID`
- `Client Secret`

### Quais você realmente usa nesta API

## Obrigatória

- `Access Token`

Ela deve ser configurada em:

```env
MERCADO_PAGO_ACCESS_TOKEN=TEST-xxxxxxxxxxxxxxxx
```

## Opcional por enquanto

- `Public Key`

Ela pode ser configurada em:

```env
MERCADO_PAGO_PUBLIC_KEY=TEST-xxxxxxxxxxxxxxxx
```

Hoje a API não precisa dela para o backend. Ela fica útil quando existir frontend próprio consumindo o SDK do Mercado Pago.

## Não necessária nesta POC

- `Client ID`
- `Client Secret`

Essas credenciais são mais ligadas a fluxos com `OAuth` e integrações que precisam autorização entre contas. Esse não é o caso desta POC.

### Por que não usar credencial de produção agora

As credenciais de produção recebem pagamentos reais. Em desenvolvimento, você deve usar somente as credenciais de teste.

## Passo 4: mapear as credenciais para a API

Na aplicação Java, as variáveis relevantes são:

```env
MERCADO_PAGO_ACCESS_TOKEN=TEST-xxxxxxxxxxxxxxxx
MERCADO_PAGO_PUBLIC_KEY=TEST-xxxxxxxxxxxxxxxx
MERCADO_PAGO_WEBHOOK_SECRET=xxxxxxxxxxxxxxxx
APP_BASE_URL=https://seu-endereco-publico
```

### Como cada uma é usada

- `MERCADO_PAGO_ACCESS_TOKEN`
  - usada pelo backend para criar a `preference`
  - usada pelo backend para consultar `/v1/payments/{id}`

- `MERCADO_PAGO_PUBLIC_KEY`
  - reservada para uso futuro em frontend

- `MERCADO_PAGO_WEBHOOK_SECRET`
  - usada para validar a autenticidade do `x-signature` do Webhook
  - hoje a API já tem a propriedade configurada, mas a validação ainda está documentada como passo futuro

- `APP_BASE_URL`
  - usada para compor:
  - `notification_url = {APP_BASE_URL}/api/webhooks/mercado-pago`

## Passo 5: configurar o Webhook

O Webhook é obrigatório para o fluxo completo da POC, porque é ele que informa ao sistema que houve atualização de pagamento.

### O que configurar

Na aplicação criada:

1. entre em `Webhooks > Configurar notificações`
2. configure a URL
3. selecione o evento `Pagamentos`
4. salve a configuração

### Qual URL usar nesta API

Produção:

```text
https://seu-dominio/api/webhooks/mercado-pago
```

Desenvolvimento com túnel público:

```text
https://seu-tunnel.ngrok-free.app/api/webhooks/mercado-pago
```

### Requisito importante

Segundo a documentação oficial, a URL de notificação deve usar `HTTPS`.

Isso afeta diretamente seu ambiente local: `localhost` puro não basta para receber Webhooks do Mercado Pago. Você vai precisar expor sua API com algum túnel HTTPS.

### O que esta configuração gera

Ao salvar a configuração, o Mercado Pago gera uma chave secreta exclusiva da aplicação para validar autenticidade das notificações.

Essa chave deve ser copiada para:

```env
MERCADO_PAGO_WEBHOOK_SECRET=xxxxxxxxxxxxxxxx
```

### Por que essa chave é importante

O Mercado Pago envia a assinatura no header `x-signature`. A chave secreta permite verificar se a chamada realmente veio do Mercado Pago.

Na POC atual:

- o webhook já é público
- o payload já é persistido
- a validação de assinatura ainda é um próximo passo de endurecimento

## Passo 6: testar o recebimento do Webhook pelo painel

O painel do Mercado Pago permite simular notificação.

### Como fazer

Depois de salvar a configuração:

1. clique em `Simular`
2. selecione a URL de teste ou de produção
3. escolha o evento
4. informe um `Data ID`
5. envie o teste

### Por que fazer isso antes das compras de teste

Porque isso valida primeiro a parte de rede:

- sua URL está acessível
- o endpoint responde
- a aplicação consegue receber POST

Isso evita misturar problema de infraestrutura com problema de pagamento.

## Passo 7: entender a limitação dos pagamentos de teste

Esse ponto é importante para o desenho da sua validação.

Segundo a documentação atual do Mercado Pago, pagamentos de teste criados com credenciais de teste não enviam notificações. Para testar a recepção de notificações, a forma indicada é usar a simulação em `Suas integrações`.

### Consequência prática

Em desenvolvimento você precisa validar duas coisas separadamente:

## A. Fluxo de checkout

Use:

- credenciais de teste
- conta de teste comprador

Para validar:

- criação de `preference`
- abertura do checkout
- retorno do `paymentUrl`
- consulta posterior do pagamento

## B. Fluxo de webhook

Use:

- painel `Webhooks > Simular`

Para validar:

- recepção do POST
- persistência em `webhook_events`
- comportamento do endpoint

Não trate o checkout de teste como prova de que o webhook real de teste vai chegar automaticamente, porque a documentação atual diz o contrário.

## Passo 8: localizar ou criar a conta de teste comprador

O guia de teste de integração informa que a conta de teste comprador é criada automaticamente com a aplicação.

### Onde encontrar

1. entre na aplicação
2. vá em `Contas de teste`
3. selecione `Comprador`

Você verá:

- país
- `User ID`
- usuário
- senha

### Por que essa conta existe

Ela serve para simular o comportamento do pagador no checkout sem usar uma conta real.

## Passo 9: quando criar contas adicionais de teste

Além da conta comprador automática, você pode criar mais contas em `Contas de teste`.

### Tipos possíveis

- `Vendedor`
- `Comprador`
- `Integrador`

### O que faz sentido para esta POC

## Criar `Vendedor` quando:

- quiser separar melhor os testes da sua conta principal
- precisar testar outro país
- quiser um cenário mais controlado entre vendedor e comprador

## Criar `Comprador` extra quando:

- quiser múltiplos cenários de usuário
- quiser separar testes por país

## Não criar `Integrador` agora

Essa conta faz sentido em modelos de marketplace. Esta POC não faz split nem intermedia múltiplos sellers.

### Regra importante de país

Segundo a documentação, comprador e vendedor precisam ser do mesmo país.

Para esta POC no Brasil, mantenha tudo em `Brasil`.

## Passo 10: testar compra com a conta comprador

Depois de criar customer, invoice e charge na API:

1. copie o `paymentUrl`
2. abra o checkout
3. faça login com a conta de teste comprador
4. simule o pagamento com os meios de teste adequados

### O que você estará validando

- a aplicação foi criada corretamente
- o `Access Token` está válido
- a API consegue criar `preference`
- o `Checkout Pro` abre corretamente
- o comprador de teste consegue seguir o fluxo

## Passo 11: preparar a volta do checkout

Além do Webhook, o `Checkout Pro` permite `back_urls`.

### O que isso resolve

Depois de pagar, o usuário é redirecionado para uma URL de:

- sucesso
- pendência
- falha

### Importante para esta POC

Hoje a API backend já usa `notification_url`, mas ainda não definiu `back_urls`.

Quando você adicionar isso, o ideal é apontar para páginas ou rotas do sistema consumidor, por exemplo:

```text
success: https://app.seudominio.com/pagamento/sucesso
pending: https://app.seudominio.com/pagamento/pendente
failure: https://app.seudominio.com/pagamento/falha
```

Isso não substitui o Webhook. O redirect serve para UX. A confirmação real do pagamento continua vindo da consulta ao gateway.

## Passo 12: o que será necessário para produção depois

Quando sair de desenvolvimento, além de trocar as variáveis, você terá de ativar credenciais de produção.

Segundo a documentação atual, isso exige informar dados do negócio, incluindo:

- indústria
- website

### O que muda na prática

Desenvolvimento:

- `TEST-...`
- compras simuladas
- webhook testado por simulação

Produção:

- credenciais produtivas
- pagamentos reais
- Webhooks produtivos
- HTTPS e observabilidade obrigatórios

## Checklist objetivo

Se você quiser fazer só o necessário para colocar a POC de pé, faça isso:

1. criar a conta Mercado Pago
2. criar `1 aplicação` de `Pagamentos online > Checkout Pro`
3. copiar `Access Token` de teste
4. opcionalmente copiar `Public Key` de teste
5. expor sua API com URL HTTPS pública
6. configurar `Webhooks > Pagamentos`
7. copiar a `secret` do Webhook
8. localizar a conta de teste `Comprador`
9. testar o endpoint de webhook com `Simular`
10. criar charge pela API e abrir o `paymentUrl`

## Mapeamento final para o projeto

O que você cria no Mercado Pago e onde isso entra na API:

| Mercado Pago | Finalidade | Variável / uso na API |
|---|---|---|
| Aplicação Checkout Pro | Identifica a integração | base de tudo |
| Access Token de teste | autenticar backend | `MERCADO_PAGO_ACCESS_TOKEN` |
| Public Key de teste | uso futuro no frontend | `MERCADO_PAGO_PUBLIC_KEY` |
| Webhook secret | validar assinatura | `MERCADO_PAGO_WEBHOOK_SECRET` |
| URL HTTPS do webhook | receber eventos | `APP_BASE_URL` + `/api/webhooks/mercado-pago` |
| Conta de teste comprador | simular pagamento | uso manual no checkout |
| Conta de teste vendedor | opcional | cenário de teste ampliado |

## Fontes oficiais usadas

Este tutorial foi escrito com base na documentação oficial do Mercado Pago Developers consultada em 9 de julho de 2026:

- Criar aplicação: https://www.mercadopago.com.br/developers/pt/docs/checkout-pro/create-application
- Configurar ambiente de desenvolvimento: https://www.mercadopago.com.br/developers/pt/docs/checkout-pro/configure-development-enviroment
- Credenciais: https://www.mercadopago.com.br/developers/pt/docs/your-integrations/credentials
- Detalhes da aplicação: https://www.mercadopago.com.br/developers/pt/docs/your-integrations/application-details
- Contas de teste: https://www.mercadopago.com.br/developers/pt/docs/your-integrations/test/accounts
- Configurar notificações de pagamento: https://www.mercadopago.com.br/developers/pt/docs/checkout-pro/payment-notifications
- Teste de integração: https://www.mercadopago.com.br/developers/pt/docs/checkout-pro/integration-test
- Configurar URLs de retorno: https://www.mercadopago.com.br/developers/pt/docs/checkout-pro/configure-back-urls

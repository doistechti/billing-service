# Frontend de teste

Aplicacao React separada para testar o fluxo de pagamento do `billing-service-api`.

## Rodar localmente

```bash
npm install
npm run dev
```

Por padrao, em desenvolvimento a aplicacao fala com `http://localhost:8080`.

Se a API estiver em outro endereco:

```bash
VITE_API_BASE_URL=http://localhost:8081 npm run dev
```

## Build

```bash
npm run build
```

## Deploy no EasyPanel

Este frontend pode subir como servico separado usando o [`Dockerfile`](</Users/doistechti/Projetos/DOISTECH/billing-service/billing-service-api/frontend/Dockerfile>).

Variavel recomendada no container:

```env
API_BASE_URL=https://billing-service-billing-service-api.moftjl.easypanel.host
```

Se essa variavel nao for informada, o frontend usa como padrao:

```text
https://billing-service-billing-service-api.moftjl.easypanel.host
```

Essa URL precisa apontar para o backend Java publicado.

Se o frontend e a API estiverem em dominios diferentes, configure tambem no backend:

```env
APP_CORS_ALLOWED_ORIGINS=https://frontend.seu-dominio.com
```

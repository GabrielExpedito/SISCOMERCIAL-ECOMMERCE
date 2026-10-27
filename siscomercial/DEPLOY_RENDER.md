# Preparação de deploy — Render

Esta preparação empacota o frontend React/Vite e o backend Spring Boot no mesmo serviço web. Assim, a interface e a API usam a mesma origem, mantendo as chamadas relativas `/api`, o fluxo OAuth Google e as sessões sem introduzir CORS entre dois subdomínios.

## Arquivos adicionados/modificados
- `Dockerfile`: build multi-stage Node 20 + Java 17 e empacotamento do `frontend/dist` em `backend/src/main/resources/static`.
- `render.yaml`: Blueprint do serviço web e PostgreSQL, com variáveis sensíveis solicitadas no painel.
- `backend/src/main/resources/application.yml`: fallback de configuração do banco por host/porta/nome e remoção do endereço ngrok como padrão de imagens.

## Antes de criar o Blueprint
1. Faça commit/push desses arquivos para o repositório Git remoto.
2. No Render, use **New > Blueprint** e selecione o repositório.
3. Revise os recursos e custos/limitações dos planos antes de confirmar a criação.
4. Após o primeiro deploy, copie o hostname real atribuído pelo Render e substitua `https://siscomercial-app.onrender.com` em `FRONTEND_URL`, `UPLOAD_PUBLIC_BASE_URL`, `CATALOGO_PUBLIC_BASE_URL`, `MERCADO_LIVRE_REDIRECT_URI` e `SHOPEE_REDIRECT_URI` caso o nome gerado seja diferente.
5. Preencha no painel os secrets solicitados. Não os coloque no Git nem neste arquivo.
6. Atualize o OAuth do Google com o callback do backend: `https://SEU-HOST.onrender.com/login/oauth2/code/google` e a origem pública correspondente.
7. Atualize os redirect URIs nas aplicações Mercado Livre e Shopee para os valores do serviço público.

## Persistência de imagens
`UPLOAD_DIRECTORY` aponta para `/var/data/uploads`. Para preservar imagens entre redeploys, configure um Persistent Disk do Render montado em `/var/data` e confirme o custo/plano aplicável antes de aceitar uploads em produção. Sem disco persistente ou armazenamento externo, arquivos locais podem desaparecer em redeploy/reinicialização.

## Banco de dados
O serviço usa host, porta, nome, usuário e senha do PostgreSQL criado pelo Blueprint e mantém Flyway como responsável pelas migrations. Confira o período de disponibilidade do plano escolhido e faça backup antes de usar dados reais.

## Health check
O Blueprint usa `/api/auth/csrf` como health check. Se o Render considerar respostas 4xx como falha para esse endpoint, substitua por um endpoint de saúde dedicado e não autenticado antes de concluir o deploy.

## Limitações desta etapa
- Não implementa a autenticação Magalu.
- Não publica nem altera integrações existentes por conta própria; os redirects externos só funcionarão após atualizar os respectivos portais.
- Não foram executados build Docker ou testes de integração contra Render nesta preparação.
- O hostname do serviço pode ser diferente do nome sugerido; confirme o endereço real no painel antes de cadastrar OAuths.

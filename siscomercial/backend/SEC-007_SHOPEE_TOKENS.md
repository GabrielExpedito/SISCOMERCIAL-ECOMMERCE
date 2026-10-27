# SEC-007 — Exposição de credenciais Shopee

## Revisão do código

- As chamadas Shopee Open Platform v2 autenticadas montam `partner_id`, `timestamp`, `access_token`, `shop_id` e `sign` na query string. O gateway também assina `partner_id + path + timestamp + access_token + shop_id` com HMAC-SHA256. Esses parâmetros foram mantidos para não alterar o contrato da API. A chamada de autorização pública usa `partner_id`, `timestamp` e `sign`.
- A troca OAuth envia `code`, `shop_id` e `partner_id` no corpo JSON; a URL contém `partner_id`, `timestamp` e `sign`. O `partner_key` não é incluído na requisição. Tokens retornados são protegidos antes de serem persistidos; o `state` temporário é persistido com expiração de dez minutos e removido ao concluir a autorização.
- Os logs do gateway e do serviço de integração não incluem URLs, queries, corpos, tokens, assinaturas ou mensagens das exceções HTTP. Logs de falha registram apenas o tipo da causa. Mensagens de erro usam texto genérico ou o status HTTP e não repassam o corpo remoto.
- Não foi necessário alterar o código de produção. Foram adicionados testes para os parâmetros/query e assinatura da publicação, além de verificar que valores simulados presentes numa exceção de transporte não aparecem na mensagem nem nos logs.

## Dependências de infraestrutura

As queries das chamadas autenticadas contêm access token e assinatura por exigência do formato atualmente utilizado pela integração. Mesmo sem logging na aplicação, proxies reversos, gateways, WAFs, APM, tracing HTTP, balanceadores e logs de acesso podem registrar a URL completa. Configurar esses componentes para omitir ou redigir os parâmetros `access_token`, `sign`, `code` e `state`; restringir acesso e retenção dos logs; e impedir captura de URLs completas em traces.

O callback do navegador recebe `code`, `shop_id` e `state` como query parameters do protocolo Shopee. Confirmar que o proxy de entrada, servidor web, CDN e observabilidade não armazenam os valores desses parâmetros. A aplicação não registra esses valores.

## Validação operacional pendente

- Conferir na documentação oficial da versão Shopee Open Platform habilitada para a conta que os campos de autenticação continuam exigidos na query e que o formato de assinatura permanece o mesmo. A documentação oficial consultada durante esta revisão respondeu HTTP 403, portanto não foi possível confirmar o contrato externamente nesta sessão; nenhuma alteração de formato foi feita.
- Inspecionar as configurações efetivas de access logs, proxy/WAF/CDN, APM e tracing no ambiente implantado e executar um fluxo de autorização/publicação com credenciais de teste, confirmando a redação dos parâmetros e a ausência dos valores nos destinos de log.
- Confirmar política de retenção/rotação dos logs e da chave `MARKETPLACE_CRYPTO_KEY`; a revisão local não comprova a configuração do ambiente real.

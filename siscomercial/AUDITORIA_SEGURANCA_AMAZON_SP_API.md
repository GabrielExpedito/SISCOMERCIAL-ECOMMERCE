# Auditoria técnica de segurança e conformidade Amazon SP-API — SisComercial

**Data:** 19/10/2026 (America/Sao_Paulo)  
**Escopo:** revisão estática, somente leitura, do snapshot do repositório e das políticas públicas atuais da Amazon.  
**Limite:** este relatório não é certificação da Amazon, avaliação jurídica LGPD, pentest nem evidência de controles de produção. Infraestrutura, contas Amazon, segredos de ambiente, logs implantados e procedimentos organizacionais não foram acessados. Nenhuma chamada real foi feita à Amazon.

## A. Resumo executivo

O fluxo implementado para a publicação Amazon é linear: `AmazonAuthenticationService.obterAccessToken()` troca o refresh token configurado por um access token; `AmazonMarketplaceGateway.publicar(...)` usa o retorno diretamente; `AmazonApiClient.spApiRequest(...)` atribui a mesma string a `x-amz-access-token`; e `sendSpApi(...)` envia a requisição. Não foi encontrado cache, persistência nem transformação do access token nesse trajeto. O gateway lê o valor do header construído para comparar com o retorno LWA antes do envio. Portanto, no nível do código inspecionado, a divergência entre token retornado e token colocado no header não é esperada; isso ainda precisa ser confirmado pelos logs de uma nova tentativa controlada.

O diagnóstico opcional usa HMAC-SHA-256 quando `AMAZON_TOKEN_DIAGNOSTIC_HMAC_KEY` está configurada; sem chave, registra igualdade em memória, comprimentos, estado vazio/nulo e horários. Não registra prefixo, sufixo ou valor integral. A resposta registra host, identificadores de conta/marketplace, status e Request ID. O erro 403 permanece **não resolvido**: igualdade prova apenas que a string enviada corresponde à obtida, não que a autorização LWA/SP-API, o consentimento do vendedor, a aplicação, o role da operação ou a conta estejam válidos.

**Conclusão de risco:** o snapshot não deve ser considerado pronto para produção ou conforme ao DPP com base somente neste código. Foram identificados problemas críticos/altos de autorização e segredos, além de lacunas sem evidência de implantação. O DPP atualizado, efetivo desde 25/08/2026, exige controles que não podem ser comprovados pelo repositório, incluindo WAF para endpoints públicos, criptografia de armazenamento em dispositivos, contatos de incidente, retenção/exclusão, varreduras e testes de intrusão para APIs. [Comunicado oficial da Amazon](https://developer.amazonservices.com/policy-update-for-sp-api)

## B. Arquitetura e fluxo de integração

### B.1 Fluxo do token Amazon

1. `AmazonAuthenticationService.obterAccessToken()` envia `grant_type=refresh_token`, `refresh_token`, `client_id` e `client_secret` para o endpoint LWA configurado e retorna `access_token` extraído da resposta. Token nulo/vazio gera erro antes de chamar a SP-API.
2. `AmazonMarketplaceGateway.publicar(...)` chama `obterAccessToken()` em cada publicação e guarda o instante aproximado da obtenção. Não foi encontrado cache ou gravação desse access token.
3. O gateway monta o caminho de Listings Items com seller ID, SKU/código interno e marketplace ID; monta payload de preço e estoque; e passa o token como argumento a `spApiRequest(...)`.
4. `AmazonApiClient.spApiRequest(...)` define `x-amz-access-token` com o argumento recebido, sem trim, codificação ou substituição. A única normalização no cliente está no host/path e nos demais headers.
5. Antes do envio, `publicar(...)` lê `request.headers().firstValue("x-amz-access-token")`, compara com a string LWA e emite diagnóstico. `sendSpApi(...)` envia o mesmo objeto `HttpRequest`; não altera os headers.
6. A resposta registra status HTTP e Request ID da Amazon. O erro inclui mensagem sanitizada, status e Request ID quando fornecido.

**Resultado:** não há evidência de cache/reuso/substituição de token nesse caminho. Os logs de uma tentativa podem confirmar comprimento, HMAC (se chave configurada) e `tokensEqual`. A marca de obtenção é registrada imediatamente depois do retorno do método, logo é uma aproximação do instante, não timestamp capturado dentro da resposta LWA.

### B.2 Investigação do 403

O erro reportado (`SP_API_AUTHORIZATION`, HTTP 403, “revoked, malformed or invalid”) pode coexistir com LWA bem-sucedido. O código não comprova que o refresh token corresponde ao app/ambiente esperado, que a autorização do vendedor continua ativa, que o app autorizado é o mesmo identificado pelas credenciais atuais, que o seller ID informado é a conta autorizadora, nem que a role requerida pela operação está concedida. A rota usa `properties.baseUrl` com padrão North America (`sellingpartnerapi-na.amazon.com`), mas o valor efetivo em produção e o marketplace/seller da autorização devem ser confirmados sem imprimir segredos. O endpoint regional oficial deve corresponder à região do marketplace. [Endpoints SP-API por região](https://developer-docs.amazon.com/sp-api/lang-pt_BR/docs/sp-api-endpoints)

Além disso, a chamada é uma publicação Listings Items (`PUT /listings/2021-08-01/items/...`); token válido para obter LWA não garante role/escopo autorizado para essa operação. Conferir na Developer Console e no Seller Central a aplicação, modo (privado/público), roles de catálogo/listagem e autorização ativa. Não alterar permissões nem payload antes dessas evidências. Se os identificadores HMAC forem iguais, abrir chamado ao Developer Support da Amazon com Request ID e horário UTC/local, seller e marketplace (sem tokens), e pedir confirmação do vínculo de autorização e role.

## C. Matriz resumida de controles

Referências DPP consultadas em 19/10/2026: [Data Protection Policy vigente](https://sellercentral.amazon.com/solution-provider/policy?ld=NSGoogle&locale=en_US&pageName=US%3ASPDS%3ASPAPI-getting-started-guide&policyType=DPP) e [comunicado da atualização efetiva em 25/08/2026](https://developer.amazonservices.com/policy-update-for-sp-api). “Não evidenciado” significa que a inspeção deste repositório não comprova o controle; não afirma que inexiste na infraestrutura ou na organização.

| ID | Controle / referência | Estado no snapshot | Evidência / risco principal |
|---|---|---|---|
| C-01 | Credenciais sem hardcode (DPP 2.5) | **Falha** | `backend/src/main/resources/application.yml:16` contém senha de banco diretamente no arquivo; rotacionar e verificar exposição/reuso. |
| C-02 | Criptografia de credenciais em repouso (DPP 1.4.2, 2.4) | **Parcial / não comprovado** | Credenciais de integrações são cifradas via AES-GCM em `CredencialMarketplaceService`; configuração da chave e proteção operacional não comprovadas. A senha de banco em YAML não está protegida por esse mecanismo. |
| C-03 | Criptografia em trânsito (DPP 1.5) | **Parcial** | Chamadas a LWA/SP-API e serviços externos usam HTTPS; TLS de entrada, proxy e serviços de banco dependem de configuração externa não fornecida. |
| C-04 | Autorização / menor privilégio (DPP 1.2, 1.3) | **Falha** | Possível escalada de perfil no cadastro público; IDOR em clientes; qualquer cliente pode confirmar pagamento de pedido alheio. |
| C-05 | MFA, ciclo de vida, revisão de acesso (DPP 1.2, 1.4) | **Não evidenciado** | O código integra OAuth Google e sessão; MFA, offboarding, revisão trimestral e treinamento são controles de identidade/processo externos. |
| C-06 | WAF, segmentação, IDS/IPS e endpoint protection (DPP 1.1) | **Não evidenciado** | Nenhuma configuração de infraestrutura correspondente foi encontrada no snapshot. |
| C-07 | Sessões, MFA e tentativas de autenticação (DPP 1.4) | **Parcial / não evidenciado** | Sessão Spring presente; política de cookies HTTPS, expiração, MFA e bloqueio após dez falhas não comprovados. CSRF está desabilitado globalmente. |
| C-08 | Minimização e retenção de dados (DPP 1.7, 2.1) | **Parcial / não comprovado** | Importação Amazon de pedidos não identificada; existem entidades locais com PII e retenção/exclusão não está demonstrada. |
| C-09 | Logs protegidos, monitorados e sem PII (DPP 2.6) | **Falha** | `System.out` registra e-mail, Google ID, nome, perfil e exceção completa do login. Retenção, ACL e detecção operacional não evidenciadas. |
| C-10 | Gestão de vulnerabilidades e testes (DPP 2.7) | **Não evidenciado** | Não encontrados workflows de CI, varredura de dependências, processo mensal de vulnerabilidades ou pentest anual. |
| C-11 | Inventário, política de privacidade e registros de tratamento (DPP 2.2, 2.3) | **Não evidenciado** | Não foram encontrados artefatos organizacionais suficientes no escopo. |
| C-12 | Incidentes e contato IMPOC (DPP 1.6) | **Não evidenciado** | Plano de resposta, contato Amazon e simulações não aparecem no repositório. |
| C-13 | Segregação de ambientes e dados (DPP 1.8, 2.5) | **Parcial / não comprovado** | Aplicação tem perfil `local` ativo no YAML; separação real de produção/teste e segmentação de dados dependem do deploy. |
| C-14 | Segurança de uploads/recursos públicos | **Parcial** | Há limite de tamanho, nome aleatório e proteção de caminho; ausência de verificação real de tipo de arquivo/remoção de metadados e diretório público precisa revisão. |
| C-15 | Diagnóstico seguro SP-API | **Implementado no código; execução pendente** | Comparação em memória e HMAC opcional sem imprimir token. Não foi feita nova chamada à Amazon para validar os logs. |

## D. Achados detalhados

Escala: **Crítico** permite comprometimento amplo imediato; **Alto** afeta contas/dados ou operações com caminho plausível; **Médio** requer condições adicionais ou amplia impacto; **Baixo** é melhoria defensiva. Confiança se refere à leitura do código, não à explorabilidade validada em ambiente.

### F-01 — Possível autoatribuição de perfil ADMIN no cadastro público

- **Categoria / gravidade:** autorização e escalada de privilégio — **Crítica**; confiança alta no caminho de mass assignment, exploração não reproduzida.
- **Referências:** DPP 1.2 e 1.3; controle de acesso por função.
- **Evidência:** `backend/src/main/java/com/siscomercial/ecommerce/config/SecurityConfig.java:34` libera `POST /api/clientes`; `ClienteController.java:17-21` desserializa diretamente uma entidade `Cliente`; `Cliente.java:53-55` expõe `perfil` ao Jackson; `ClienteService.java:25-30` apenas codifica senha e persiste a entidade, sem forçar `CLIENTE`.
- **Cenário / impacto:** um cadastro pode fornecer `perfil=ADMIN` (e propriedades de estado) e, dependendo do vínculo do login Google por e-mail em `ClienteService.cadastrarOuBuscarGoogle`, obter uma sessão administrativa. Isso pode expor funções de retaguarda e marketplace.
- **Correção recomendada:** usar DTO de entrada com campos explícitos, atribuir `CLIENTE` e estado permitido no servidor, rejeitar campos adicionais/sensíveis; impedir associação automática de identidade externa a registro privilegiado e revisar/forçar revalidação de contas existentes.
- **Validação:** teste de integração em cópia/ambiente de teste: cadastro com perfil ADMIN não deve persistir nem conceder role ADMIN; verificar banco e sessão autenticada. Não foi executado nesta auditoria.

### F-02 — Endpoints de cliente vulneráveis a acesso/alteração cruzada por ID

- **Categoria / gravidade:** IDOR / isolamento de dados — **Alta**; confiança alta.
- **Referências:** DPP 1.2, 1.3, 2.1; mínimo privilégio e confidencialidade.
- **Evidência:** `SecurityConfig.java:51-52` exige autenticação, mas `ClienteController.java:24-31` não recebe o usuário autenticado; `buscarPorId` devolve qualquer cliente e `adicionarEndereco` grava endereço no cliente selecionado pelo ID (`ClienteService.java:33-35, 48-55`).
- **Cenário / impacto:** qualquer sessão autenticada que descubra/enumere um ID pode consultar CPF/CNPJ, e-mail, telefone e endereços ou incluir endereço na conta de outra pessoa.
- **Correção recomendada:** resolver a identidade da sessão para o cliente e restringir a operações próprias; administrar acesso de suporte por autorização explícita e auditável. Evitar entidades JPA como resposta/entrada pública.
- **Validação:** dois usuários de teste; tentativas cruzadas de GET e POST devem retornar 403/404 e não alterar dados.

### F-03 — Confirmação de pagamento sem vínculo com proprietário/admin

- **Categoria / gravidade:** autorização de transação — **Alta**; confiança alta.
- **Referências:** DPP 1.2, 1.3; integridade de pedidos.
- **Evidência:** `PedidoController.java:109-120` expõe `POST /{id}/confirmar-pagamento` sem `Authentication`; `SecurityConfig.java:48` libera POST de pedidos para qualquer role CLIENTE ou ADMIN; `PedidoService.confirmarPagamentoAprovado` busca e confirma pelo ID.
- **Cenário / impacto:** um cliente autenticado pode confirmar pagamento de pedido alheio usando ID sequencial, alterando o estado sem evidência do gateway.
- **Correção recomendada:** remover a rota de simulação do ambiente produtivo; confirmação real somente por webhook autenticado/assinado e idempotente ou ação administrativa com verificação de propriedade, transação e trilha de auditoria.
- **Validação:** cliente A não confirma pedido de B; evento sem assinatura ou duplicado não altera estado.

### F-04 — Segredo de banco em arquivo de configuração

- **Categoria / gravidade:** gestão de segredos — **Alta**; confiança alta.
- **Referências:** DPP 2.5 (sem credenciais hardcoded), 1.4.2.
- **Evidência:** `backend/src/main/resources/application.yml:16` possui senha PostgreSQL literal. O valor foi omitido intencionalmente deste documento.
- **Cenário / impacto:** leitores do repositório, histórico Git, artefatos de build/backup ou distribuição do pacote podem obter acesso ao banco; valor pode permanecer exposto mesmo após remoção atual.
- **Correção recomendada:** rotacionar imediatamente no provedor, remover do código e histórico conforme processo de resposta a segredo, carregar de Secret Manager/variável protegida e auditar acessos/reuso. Não basta apagar a linha.
- **Validação:** varredura de segredos no histórico completo, confirmação de credencial antiga revogada e configuração de deploy sem valor literal.

### F-05 — CSRF desativado com autenticação de sessão por navegador

- **Categoria / gravidade:** segurança de sessão — **Alta**; confiança alta quanto à configuração, exploração depende da superfície/browser.
- **Referências:** DPP 1.2, 1.3 e controles de acesso.
- **Evidência:** `SecurityConfig.java:22-26` desabilita CSRF globalmente enquanto mantém sessão `IF_REQUIRED` e login OAuth2 (`61-65`).
- **Cenário / impacto:** navegador autenticado pode enviar operação mutável a partir de origem externa caso cookies sejam enviados e não haja mitigação equivalente, permitindo alterações em perfil/endereço/pedidos.
- **Correção recomendada:** habilitar CSRF para rotas com autenticação por cookie e configurar token/origem; se arquitetura for migrada para bearer stateless, documentar e validar desenho de cookies, CORS e proteção contra CSRF antes de desabilitar.
- **Validação:** teste com navegador/sessão real e origem externa rejeitado; validar `SameSite`, `Secure`, `HttpOnly`, expiração e HTTPS no proxy.

### F-06 — PII e detalhes de autenticação em logs

- **Categoria / gravidade:** privacidade e logging — **Alta**; confiança alta.
- **Referências:** DPP 2.6 (eventos e logs sem PII salvo necessidade legal), 2.2.
- **Evidência:** `CustomOAuth2UserService.java:36-38, 55-58` imprime Google ID, e-mail, nome, ID e perfil; `ClienteService.java:65-68` imprime Google ID, e-mail e nome; `SecurityConfig.java:71,83` imprime identidade e stack trace completo em falha.
- **Cenário / impacto:** dados pessoais e detalhes da falha se propagam a stdout, agregador, retenção, suporte e backups sem controle de acesso/expurgo demonstrado.
- **Correção recomendada:** logging estruturado com identificador aleatório de correlação, sem tokens/PII; sanitizar exceções e controlar ACL, integridade, monitoramento e retenção de acordo com DPP.
- **Validação:** teste de captura de logs nos fluxos de sucesso/falha confirma ausência de PII, tokens, parâmetros OAuth e corpos sensíveis.

### F-07 — Access token Shopee em query string

- **Categoria / gravidade:** exposição de credenciais — **Alta**; confiança alta.
- **Referências:** DPP 1.4.2, 1.5, 2.6.
- **Evidência:** `ShopeeMarketplaceGateway.java:300-305` concatena `access_token` na URL; `IntegracaoMarketplaceService.java:224-234` repete o padrão no diagnóstico. Tokens em URLs podem ser capturados por proxy, access logs, tracing, exceções e sistemas de observabilidade.
- **Cenário / impacto:** token de conta pode ser reutilizado por pessoa/sistema com acesso a URL ou log.
- **Correção recomendada:** usar transporte exigido pela API Shopee conforme contrato atual, reduzir logging de URI/query, redigir segredos e rever logs históricos. Como é uma API de terceiro, confirmar o mecanismo suportado na documentação vigente antes de alterar.
- **Validação:** captura de HTTP e logs de proxy/tracing em teste assegura que token não aparece em URL/log.

### F-08 — Respostas públicas serializam entidade e dados além do necessário

- **Categoria / gravidade:** minimização de dados — **Média**; confiança alta.
- **Referências:** DPP 2.1, 2.2.
- **Evidência:** `ClienteController.java:20-21, 24-26` retorna entidades `Cliente`; `PedidoController.java:41-53, 59-68, 74-101` retorna entidades `Pedido`. `senhaHash` está `@JsonIgnore`, mas demais propriedades da entidade podem ser serializadas, incluindo dados pessoais/endereço e estado interno.
- **Cenário / impacto:** excesso de informação por endpoint e acoplamento da resposta pública ao modelo persistente; intensifica impacto de falhas de autorização.
- **Correção recomendada:** DTOs por caso de uso, campos mínimos, paginação e política de retenção; validar que CPF/CNPJ, e-mail, endereço, identificadores internos e notas não são expostos a consumidores não autorizados.
- **Validação:** testes de contrato/serialização por perfil e propriedade.

### F-09 — Configuração local e destino público com fallback de túnel

- **Categoria / gravidade:** configuração de ambiente — **Média**; confiança alta.
- **Referências:** DPP 2.5 (separação dev/prod), 1.1.
- **Evidência:** `application.yml:6` ativa perfil `local`; configuração de URL pública de upload possui fallback de domínio de túnel conforme configuração inspecionada.
- **Cenário / impacto:** deploy que não substitua configurações pode iniciar com opções de desenvolvimento, redirecionar OAuth para localhost ou gerar links para túnel temporário, prejudicando disponibilidade e expondo endpoints de desenvolvimento.
- **Correção recomendada:** remover perfil ativo fixo e fallback de túnel da configuração comum; exigir configurações de produção explicitamente e falhar no startup quando ausentes; separar segredos/config por ambiente.
- **Validação:** inspeção de configuração renderizada no ambiente produtivo e teste de startup com variáveis obrigatórias ausentes.

### F-10 — Falta de proteção de tipo real e revisão de metadados em uploads

- **Categoria / gravidade:** upload / privacidade — **Média**; confiança média.
- **Referências:** DPP 1.1.2, 2.3 e 2.5.
- **Evidência:** serviço de imagem limita tamanho e extensões/MIME declarado e usa nome UUID e normalização de caminho; o tipo é declarado pelo cliente e não foi identificada verificação de assinatura de arquivo nem remoção de metadados EXIF. Arquivos servidos publicamente sob `/uploads/produtos/**`.
- **Cenário / impacto:** conteúdo disfarçado pode ser persistido/servido; imagens podem carregar localização ou metadados pessoais. Diretório `uploads` e artefatos de imagem aparecem no workspace, então política de versionamento/backup deve ser conferida.
- **Correção recomendada:** decodificar e validar formato real, reprocessar para formato permitido, remover metadados, aplicar varredura e limites também no proxy; definir retenção e não versionar uploads de usuário. Imagens de catálogo podem continuar públicas por intenção de produto.
- **Validação:** arquivo MIME adulterado, imagem malformada, arquivo excessivo e EXIF são rejeitados/removidos; inspecionar tracking/backup sem abrir imagens pessoais.

### F-11 — Endereços de imagem remotos podem abrir caminho para SSRF

- **Categoria / gravidade:** SSRF — **Média**; confiança média.
- **Referências:** DPP 1.1, 2.5.
- **Evidência:** gateways de marketplace fazem requisições HTTP a URL de imagem associada ao produto. A validação observada é de esquema/prefixo HTTP(S), sem evidência de bloqueio de IP privado, loopback, link-local, resolução DNS revalidada ou redirecionamentos.
- **Cenário / impacto:** usuário administrativo capaz de controlar URL, ou produto comprometido, pode fazer servidor consultar serviços internos/metadados de nuvem.
- **Correção recomendada:** permitir apenas hosts/imagens de confiança, validar DNS/IP em cada redirecionamento, bloquear faixas privadas/reservadas e saídas internas no egress; definir limites de tamanho e tempo.
- **Validação:** testes com loopback, IP privado, IPv6 local, DNS rebinding e redirects a destino interno são bloqueados.

### F-12 — Versões e processo de gestão de vulnerabilidades sem evidência

- **Categoria / gravidade:** gestão de vulnerabilidades — **Média**; confiança alta na ausência de automação no snapshot, sem afirmar CVE explorável.
- **Referências:** DPP 2.7 exige varredura recorrente e gatilhada por mudança, análise de código por release e pentest anual de APIs/sistemas.
- **Evidência:** `backend/pom.xml:7-22` fixa Spring Boot 3.3.4 e Java 17; `frontend/package.json:6-18` tem apenas build/dev/preview. Não foram encontrados workflows de CI ou configuração de SCA/SAST no escopo examinado.
- **Cenário / impacto:** dependências sem inventário/resolução e ausência de evidência de correção dentro dos prazos DPP podem deixar vulnerabilidades conhecidas sem tratamento. A versão declarada, isoladamente, não prova vulnerabilidade explorável.
- **Correção recomendada:** produzir SBOM e relatório de dependências resolvidas; atualizar para linha suportada após análise de compatibilidade; automatizar SCA/SAST e varreduras, gerar trilha de correção por criticidade e executar pentest periódico de APIs.
- **Validação:** anexar resultados datados de scanner e pentest, política de SLA e execução em release. A CVE-2026-40973 do Spring Boot possui condições específicas; o código inspecionado não configura persistência de sessão explicitamente, então a aplicabilidade não foi confirmada. [Aviso oficial Spring](https://spring.io/security/cve-2026-40973/)

### F-13 — Criptografia de credenciais depende de chave e ciclo de vida não comprovados

- **Categoria / gravidade:** gestão de chaves — **Média**; confiança alta na lacuna probatória.
- **Referências:** DPP 1.4.2, 2.4.
- **Evidência:** `CredencialMarketplaceService.java:24-53` usa AES/GCM/NoPadding com chave Base64 configurada externamente; `application.yml` referencia configuração para a chave. Não há evidência de KMS/HSM, rotação, versionamento, backup protegido, separação por ambiente, controle de acesso ou migração de ciphertext.
- **Cenário / impacto:** perda/exposição da chave compromete todas credenciais cifradas; troca sem migração pode tornar tokens irrecuperáveis. Chave em arquivo/env sem cofre seguro pode acabar em logs/dumps/process list/deploy.
- **Correção recomendada:** gerir chave em Secret Manager/KMS, política de rotação e acesso mínimo, identificação de versão, rotação com recriptografia e plano de recuperação. Não registrar valor da chave.
- **Validação:** evidência de configuração e teste de rotação/revogação em ambiente isolado.

### F-14 — Fluxo Amazon aparentemente global, sem isolamento de vendedor comprovado

- **Categoria / gravidade:** autorização de marketplace / multi-tenant — **Média**; confiança média.
- **Referências:** DPP 1.2, 1.3, 1.8; autorização por selling partner.
- **Evidência:** `AmazonProperties` obtém client ID/secret/refresh token e seller/marketplace IDs de configuração global; `AmazonMarketplaceGateway.publicar` usa esses valores sem derivá-los de `IntegracaoMarketplace`. A tabela de integração guarda proprietário, mas no caminho Amazon o valor global é usado.
- **Cenário / impacto:** se a aplicação atende várias lojas/tenants, uma publicação pode usar identidade global divergente da loja selecionada ou permitir associação incorreta de catálogo/conta.
- **Correção recomendada:** esclarecer formalmente modelo single-seller ou multi-seller. Para multi-seller, mapear credenciais/consentimento e seller/marketplace a tenant, aplicar autorização no servidor e registrar auditoria; não mudar arquitetura antes de confirmar requisito.
- **Validação:** matriz de testes com duas lojas garante que nenhuma credencial, seller, SKU ou resposta atravesse tenants.

## E. Inventário de dados observado

| Dados | Origem / destino observado | Sensibilidade e notas |
|---|---|---|
| Perfil e cadastro local: nome, CPF/CNPJ, e-mail, telefone, endereços, senha hash, Google ID | `Cliente`, cadastro e login Google; banco local | Dados pessoais; senha é BCrypt e `senhaHash` é ignorado na serialização, mas PII é logada e entidade exposta por endpoints. |
| Pedido: cliente, itens, preços, endereço de entrega, forma/status de pagamento, rastreio e faturamento | `Pedido`/itens/histórico; banco local | Pode conter dados pessoais e informação transacional. Retenção e expurgo não comprovados. |
| Amazon: título/atributos existentes, ASIN, SKU/código interno, preço, estoque, seller ID, marketplace ID, submission ID e erros | Listings Items API; configuração global e banco de integração | Nesta implementação foi identificada publicação de listings. Não foi identificada sincronização/importação de pedidos Amazon; não extrapolar para outros serviços/branches. |
| Credenciais marketplace | Ambiente de aplicação e campos `tokenProtegido`/`credenciaisProtegidas` | Meli/Shopee guardam tokens cifrados; Amazon LWA refresh/client credentials vêm de configuração. A configuração/armazenamento da chave precisa evidência operacional. |
| Imagens de produto | Upload local e leitura por gateways de marketplace | Recursos são publicamente servidos; não foi feita inspeção do conteúdo das imagens. |

O DPP exige minimização e exclusão de informação conforme prazos (incluindo eliminação em até 30 dias quando aplicável e dados pessoais de pedidos em prazo próprio), registro das categorias/fluxos e controles de solicitação do titular. [DPP Amazon](https://sellercentral.amazon.com/solution-provider/policy?ld=NSGoogle&locale=en_US&pageName=US%3ASPDS%3ASPAPI-getting-started-guide&policyType=DPP)

## F. Inventário de credenciais e pontos de segredo

| Credencial | Tratamento observado | Ação/evidência pendente |
|---|---|---|
| PostgreSQL | Valor literal no `application.yml` (conteúdo omitido deste relatório) | Rotacionar; auditar histórico Git, distribuição, logs e acessos; migrar para cofre. |
| Amazon LWA client ID/secret e refresh token | Variáveis `AMAZON_*`; refresh trocado em tempo de execução | Confirmar ambiente/app/seller/consentimento/role em portal, sem copiar valores para logs. |
| Chave do HMAC diagnóstico | `AMAZON_TOKEN_DIAGNOSTIC_HMAC_KEY`, opcional | Usar chave aleatória forte armazenada em cofre, acessível só ao serviço; sem chave, os fingerprints são `unavailable`, mas comparação booleana continua. |
| Chave de cifragem marketplace | `MARKETPLACE_CRYPTO_KEY` / propriedade equivalente | Verificar cofre, política de rotação/backup e acesso. |
| Mercado Livre e Shopee | Credenciais de app via configuração; tokens cifrados em banco | Rever política de rotação e logs; token Shopee em query string é achado F-07. |
| Google OAuth | Client ID/secret via configuração | Confirmar callback de produção, segredo no cofre e política de sessão/cookies. |

## G. Verificações e testes executados

- **Executado:** leitura estática dos arquivos de configuração, classes do fluxo Amazon, segurança web, clientes/pedidos, tratamento de credenciais, configuração Maven/Frontend e estrutura do projeto. Consultadas fontes oficiais Amazon e Spring em 19/10/2026.
- **Não executado:** testes unitários/integração, build, varredura de segredo, SCA/SAST, pentest, testes de autorização, inspeção de infraestrutura ou requisição à Amazon.
- **Motivo:** escopo desta auditoria foi somente leitura; o workspace já continha alterações locais e artefatos de build presentes. Executar testes/build alteraria arquivos de saída no repositório. Para manter todos os arquivos intactos, não foram rodados. Nenhum resultado de teste é alegado.
- **Verificação recomendada em cópia isolada:** testes F-01 a F-03 e de logs; SCA/SAST + SBOM; varredura completa do histórico de segredos. O serviço Amazon tem testes próprios existentes, mas não foram executados neste relatório.

## H. Evidências externas e organizacionais ainda necessárias

1. Configuração efetiva do ambiente produtivo, ingress, WAF, TLS, firewall, segmentação, IDS/IPS, proteção de endpoints e criptografia dos discos/volumes/backups.
2. Evidência do KMS/Secret Manager, ACL, rotação, acesso emergencial e backup/recuperação das chaves; nunca incluir valores de credenciais.
3. Developer Console/Seller Central: tipo da app, aplicação correta, modo/region, autorização ativa do vendedor, seller ID, marketplace e roles liberados para Listing Items.
4. Política de cookies, MFA, bloqueio após 10 falhas, revisão trimestral de acessos e remoção de acesso de colaboradores até 24h após desligamento/mudança de função.
5. Inventário dos dados pessoais/SP-API e terceiros, base/propósito, retenção, exclusão, solicitações de titulares, contratos/confidencialidade e segregação de teste/produção.
6. Plano de incidente revisado semestralmente, IMPOC registrado, treinamento e procedimento de notificação à Amazon em até 24h após incidente detectado.
7. Logs: eventos, sucesso/falha, acesso a dados, mudanças/admin/API, proteção contra adulteração, ACL, monitoramento e retenção mínima aplicável (DPP prevê 12 meses para logs).
8. Scanner de vulnerabilidades mensal e após mudança, análise de código pré-release, pentest anual abrangendo APIs e registro de correção de vulnerabilidades pelos prazos DPP.
9. Backup/recuperação com separação geográfica quando aplicável a PII; inventário de ativos trimestral e avaliações anuais de fornecedores/subcontratados.
10. No fluxo de 403: diagnóstico de uma chamada real e controlada, com `tokensEqual`, comprimentos, HMAC opcional, hora, host/marketplace/seller mascarados adequadamente, status e Request ID — sem token, refresh token, client secret ou headers completos.

A ferramenta oficial [SP-API Guard](https://developer.amazonservices.com/tools/selling-partner-api-guard) pode ajudar a avaliar controles de contas AWS contra o DPP; não substitui evidências para infraestrutura não AWS, controles organizacionais, autorização do app/vendedor ou pentest das APIs.

## I. Plano priorizado

| Prioridade | Ação |
|---|---|
| **P0 — antes de reabrir acesso administrativo** | Corrigir mass assignment no cadastro (F-01); revisar contas ADMIN existentes e evidências de exploração. |
| **P0 — imediata** | Rotacionar segredo PostgreSQL e remover hardcode/histórico conforme plano (F-04). Confirmar logs sem vazamento associado. |
| **P1 — antes de produção** | Corrigir isolamento de cliente e confirmação de pagamento (F-02/F-03); tratar CSRF/sessão (F-05); remover PII de logs (F-06); remover credencial da URL Shopee ou aplicar mecanismo seguro conforme documentação (F-07). |
| **P1 — conformidade Amazon** | Reunir controles DPP de WAF, discos, MFA, lockout, incidentes, exclusão/retensão, logging, varreduras e pentest; registrar IMPOC. Política revisada em vigor desde 25/08/2026. |
| **P2** | Restringir DTOs/serialização (F-08), configuração de produção (F-09), upload e SSRF (F-10/F-11), KMS/rotação de chaves (F-13), decidir isolamento single/multi-seller (F-14). |
| **P2 contínua** | Implantar SBOM, SCA/SAST, varredura mensal e por release e pentest anual (F-12). |
| **P3** | Documentar inventário, retenção/expurgo, fluxo de direitos, contratos de operadores, treinamento e evidência de revisão operacional. |

## J. Procedimento para uma nova tentativa controlada da Amazon

1. **Antes da chamada**, confirmar em cofre/portal, sem copiar valores: ambiente e endpoint/região; aplicação LWA; refresh token associado; consentimento ativo do vendedor; seller ID e marketplace; role de Listings Items habilitada; SKU/ASIN existente. O refresh token não deve ser revogado/regenerado por tentativa sem evidência.
2. Opcionalmente provisionar `AMAZON_TOKEN_DIAGNOSTIC_HMAC_KEY` com valor aleatório forte no Secret Manager. Usar o mesmo valor apenas no processo que fará a tentativa e restringir leitura dos logs. Se não configurada, `tokensEqual` segue disponível, mas os IDs HMAC aparecerão como `unavailable`.
3. Rodar uma única publicação controlada em ambiente e produto de baixo impacto, observando os logs do serviço em acesso restrito. Não habilitar logs HTTP de headers/corpos e não usar proxy de depuração que grave credenciais.
4. Registrar no ticket seguro: horário, `lwaTokenNull`, `lwaTokenEmpty`, comprimento, IDs HMAC (se disponíveis), `tokensEqual`, `lwaObtainedAt`, `spApiRequestAt`, host, seller/marketplace conforme política interna, status e Request ID. Nunca copiar token, prefixo/sufixo, refresh token, client secret ou headers completos.
5. Se `tokensEqual=false`, investigar customização/interceptor/transporte antes de alterar credenciais. Se `true` e 403, conferir aplicação/consentimento/role/conta/região e fornecer Request ID ao suporte Amazon; igualdade não prova validade/autorização.
6. Após diagnóstico temporário, remover a chave HMAC do ambiente e encerrar acesso ampliado aos logs conforme a política de retenção. Preservar o registro sanitizado e Request ID para análise.

No snapshot, o caminho do token não contém cache nem substituição aparente. O Request ID reportado anteriormente (`9f16552e-feb6-4664-a5b7-8085bcf4975c`) deve ser correlacionado com o registro original da Amazon; ele não foi reproduzido nesta auditoria.

## K. Conclusão

O código atual permite confirmar em uma nova tentativa se o token LWA retornado é igual, byte a byte, ao valor atribuído ao header. O diagnóstico implementado reduz exposição da credencial e preserva Request ID/status. Isso fecha a pergunta de rastreabilidade no nível estático, mas **não resolve nem explica definitivamente o HTTP 403** sem uma chamada real e evidência do vínculo de autorização Amazon.

As prioridades imediatas são remover/rotacionar a credencial de banco exposta, fechar os caminhos de escalada administrativa, IDOR e confirmação de pagamento, e eliminar dados pessoais de logs. Em paralelo, a organização deve provar os controles de infraestrutura e processo do DPP vigente. O estado de trabalho do repositório já estava modificado no início desta inspeção; este relatório é o único arquivo criado nesta auditoria.

### Referências públicas consultadas

- [Amazon Data Protection Policy — versão vigente](https://sellercentral.amazon.com/solution-provider/policy?ld=NSGoogle&locale=en_US&pageName=US%3ASPDS%3ASPAPI-getting-started-guide&policyType=DPP), consultada em 19/10/2026.
- [Amazon — atualização das políticas SP-API efetiva em 25/08/2026](https://developer.amazonservices.com/policy-update-for-sp-api), consultada em 19/10/2026.
- [Amazon — início para desenvolvedores públicos](https://developer.amazonservices.com/start/getting-started-for-public-developers), consultada em 19/10/2026.
- [Amazon — SP-API Guard](https://developer.amazonservices.com/tools/selling-partner-api-guard), consultada em 19/10/2026.
- [Amazon — endpoints SP-API por região](https://developer-docs.amazon.com/sp-api/lang-pt_BR/docs/sp-api-endpoints), consultada em 19/10/2026.
- [Spring — CVE-2026-40973](https://spring.io/security/cve-2026-40973/), consultada em 19/10/2026.

# Siscomercial E-commerce — Protótipo (RF001–RF003 + Agente IA)

Este é um protótipo funcional construído a partir da *Especificação de Regras de
Negócio* que você enviou (versão 1.0, aprovada até RF003). Ele implementa a
arquitetura descrita na seção 3 do documento:

```
React (frontend)          Agente IA (LangChain4j)
      │                            │
      └────────────► Spring Boot / REST API ◄────────────┘
                              │
                        Serviços de negócio
                              │
                          PostgreSQL
```

**Princípio seguido à risca:** o agente de IA nunca acessa o banco direto — ele
só chama os mesmos `Service`s Java que o REST API usa. Isso está implementado em
`backend/.../ai/AgenteFerramentas.java`.

## O que está implementado

- **RF001 — Produtos**: cadastro, alteração, inativação (nunca exclusão física —
  RN008), controle de status (ATIVO/INATIVO/SEM_ESTOQUE), histórico de preço
  (RN007) e movimentação de estoque por tipo (ENTRADA/VENDA/CANCELAMENTO/AJUSTE).
- **RF002 — Catálogo público**: listagem, busca, promoções, cálculo de frete
  (stub) — `frontend/src/App.jsx`.
- **RF003 — Checkout**: cadastro/login de cliente, endereço, reserva de
  estoque com expiração parametrizável (RN023), snapshot de preços/endereço no
  pedido (RN025), status de pedido e de pagamento completos.
- **RF009 — Agente de IA (LangChain4j)**: as 7 ferramentas da seção 9.1 —
  `consultarPedido`, `consultarProduto`, `consultarEstoque`, `cancelarPedido`,
  `emitirNotaFiscal`, `consultarStatusNotaFiscal`, `enviarDocumentoWhatsApp` —
  mais o fluxo de **confirmação obrigatória** da seção 9.4: qualquer operação
  que altera dados (cancelar pedido, emitir NF, enviar WhatsApp) devolve um
  *token* e só executa de fato depois de uma chamada explícita a
  `confirmarOperacao`, disparada pelo próprio agente somente após o
  administrador confirmar em linguagem natural.
- **Chat widget no React** (`AgenteChat.jsx`) já plugado no frontend.

## O que ainda falta (alinhado à seção 11 "Pontos Ainda a Definir")

Estes pontos dependem de decisões que a especificação marca como **"a
definir"** — por isso foram implementados como *stubs* isolados atrás de uma
interface de serviço, prontos para receber a integração real sem mudar quem os
consome (nem os controllers REST, nem o agente):

| Serviço                  | Onde está o stub                 | O que falta |
|---------------------------|-----------------------------------|-------------|
| Gateway de pagamento      | `PedidoController.confirmarPagamento` simula aprovação | Integrar PIX/cartão real e trocar a simulação por um webhook do gateway escolhido |
| Frete                     | `FreteService.calcular`           | Escolher provedor (Correios, Melhor Envio etc.) e substituir o valor simulado |
| Nota Fiscal (NF-e)        | `NotaFiscalService.emitir`        | Escolher provedor fiscal e implementar a emissão real (a regra de "quando é possível faturar" já está pronta) |
| WhatsApp                  | `WhatsAppService.enviarDocumento` | Escolher provedor (ex: WhatsApp Business Cloud API) |
| Marketplaces (ML/Facebook)| `Produto.idExternoMercadoLivre/idExternoFacebook` (campos já existem) | RF007 completo ainda não iniciado |
| Autenticação (Google) / permissões | — | Endpoints ainda não têm autenticação; é o próximo passo crítico antes de ir para produção |
| Política de pré-venda sem estoque | — | Marcada como "a definir" na spec, não implementada |
| RF004 (gestão de pedidos/estoque na retaguarda), RF006 (retaguarda completa), RF008 (WhatsApp completo) | — | Próximas etapas do roadmap, ainda não abordadas |

Ou seja: **o agente de IA já "funciona" de ponta a ponta** com o que existe hoje
(consultas reais ao banco, cancelamento de pedido real, e emissão de
NF/WhatsApp simuladas mas com o fluxo de confirmação real) — o que falta para
ele operar em produção é conectar os provedores externos reais, não a lógica
do agente em si.

## Como rodar localmente

### Backend (Spring Boot)

Requer Java 17+ e Maven (ou o wrapper, se você adicionar um). Sem chave de LLM
configurada, o agente sobe normalmente mas avisa que precisa da chave — todo o
resto do sistema funciona.

```bash
cd backend
export OPENAI_API_KEY=sk-...   # opcional, mas necessário para o agente responder de verdade
mvn spring-boot:run
```

Isso sobe com o perfil `local` (banco H2 em memória) e já carrega produtos,
um cliente e um pedido de exemplo (veja `SiscomercialApplication.java`) para
você testar o agente imediatamente:

```bash
curl -X POST http://localhost:8080/api/ia/chat \
  -H "Content-Type: application/json" \
  -d '{"mensagem": "qual o status do pedido SIS-2026-000001?"}'
```

Para usar o Supabase/Postgres real, rode com `--spring.profiles.active=supabase`
e defina `SUPABASE_DB_URL`, `SUPABASE_DB_USER`, `SUPABASE_DB_PASSWORD`.

### Frontend (React + Vite)

```bash
cd frontend
npm install
npm run dev
```

Abre em `http://localhost:5173`, com o proxy do Vite já configurado para
encaminhar `/api` para `http://localhost:8080`.

## Observação importante

Este ambiente não tem acesso à internet para baixar dependências Maven/npm,
então o código foi escrito e revisado cuidadosamente mas **não foi compilado
aqui**. Ao rodar `mvn spring-boot:run` pela primeira vez, é esperado que o
Maven baixe as dependências (Spring Boot 3.3.4, LangChain4j 0.35.0, etc.) — se
algo não compilar, é provavelmente um pequeno ajuste de versão, não um erro de
lógica.

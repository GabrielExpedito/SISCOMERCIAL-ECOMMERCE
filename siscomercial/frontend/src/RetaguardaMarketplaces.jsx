import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { api } from "./api";

const statusLabel = (status) =>
  (status || "").replaceAll("_", " ").toLowerCase().replace(/(^|\s)\S/g, (l) => l.toUpperCase());

const dataHora = (valor) =>
  valor
    ? new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(valor))
    : "—";

const statusClass = (status) => `status-marketplace status-marketplace-${status || ""}`;

function IndicadorMarketplace({ titulo, quantidade, destaque }) {
  return (
    <article className={`indicador-retaguarda ${destaque ? "destaque" : ""}`}>
      <span>{titulo}</span>
      <strong>{quantidade}</strong>
    </article>
  );
}

export default function RetaguardaMarketplaces() {
  const [integracoes, setIntegracoes] = useState([]);
  const [produtos, setProdutos] = useState([]);
  const [produtoSelecionado, setProdutoSelecionado] = useState("");
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState("");
  const [mensagem, setMensagem] = useState("");
  const [modalNova, setModalNova] = useState(false);
  const [lojaProprietaria, setLojaProprietaria] = useState("Minha loja");
  const [identificadorExterno, setIdentificadorExterno] = useState("");
  const [processando, setProcessando] = useState(false);
  const [integracaoSelecionada, setIntegracaoSelecionada] = useState(null);
  const popupRef = useRef(null);
  const pollingRef = useRef(null);

  const carregarDados = useCallback(async () => {
    try {
      setCarregando(true);
      setErro("");
      const [listaIntegracoes, listaProdutos] = await Promise.all([
        api.listarMarketplaces(),
        api.listarProdutosRetaguarda(),
      ]);
      setIntegracoes(listaIntegracoes || []);
      setProdutos(listaProdutos || []);
    } catch (e) {
      setErro(e.message);
    } finally {
      setCarregando(false);
    }
  }, []);

  useEffect(() => {
    carregarDados();
    return () => {
      if (pollingRef.current) window.clearInterval(pollingRef.current);
    };
  }, [carregarDados]);

  const indicadores = useMemo(() => ({
    total: integracoes.length,
    ativas: integracoes.filter((i) => i.status === "ATIVA").length,
    configuradas: integracoes.filter((i) => i.status === "CONFIGURADA").length,
    erros: integracoes.filter((i) => i.status === "ERRO").length,
  }), [integracoes]);

  function limparFeedback() {
    setErro("");
    setMensagem("");
  }

  async function criarIntegracao(event) {
    event.preventDefault();
    limparFeedback();
    try {
      setProcessando(true);
      const criada = await api.criarIntegracaoMercadoLivre({
        lojaProprietaria: lojaProprietaria.trim(),
        identificadorExterno: identificadorExterno.trim(),
      });
      setModalNova(false);
      setLojaProprietaria("Minha loja");
      setIdentificadorExterno("");
      setIntegracoes((atual) => [...atual, criada]);
      setIntegracaoSelecionada(criada);
      setMensagem("Integração criada. Agora autorize o acesso à conta do Mercado Livre.");
    } catch (e) {
      setErro(e.message);
    } finally {
      setProcessando(false);
    }
  }

  async function autorizar(integracao) {
    limparFeedback();
    try {
      setProcessando(true);
      const resposta = await api.iniciarAutorizacaoMercadoLivre(integracao.id);
      const popup = window.open(
        resposta.urlAutorizacao,
        "mercadoLivreOAuth",
        "width=900,height=760,resizable=yes,scrollbars=yes"
      );
      popupRef.current = popup;

      if (!popup) {
        setErro("O navegador bloqueou a janela de autorização. Permita pop-ups para continuar.");
        return;
      }

      setIntegracaoSelecionada(integracao);
      setMensagem("A autorização foi aberta. Conclua o login e a autorização no Mercado Livre.");
      iniciarAcompanhamento(integracao.id);
    } catch (e) {
      setErro(e.message);
    } finally {
      setProcessando(false);
    }
  }

  function iniciarAcompanhamento(integracaoId) {
    if (pollingRef.current) window.clearInterval(pollingRef.current);
    pollingRef.current = window.setInterval(async () => {
      try {
        const lista = await api.listarMarketplaces();
        const atualizada = lista.find((item) => item.id === integracaoId);
        if (!atualizada) return;
        setIntegracoes(lista);
        setIntegracaoSelecionada(atualizada);
        if (atualizada.status === "ATIVA" || atualizada.status === "ERRO") {
          window.clearInterval(pollingRef.current);
          pollingRef.current = null;
          if (popupRef.current && !popupRef.current.closed) popupRef.current.close();
          setMensagem(
            atualizada.status === "ATIVA"
              ? "Mercado Livre autorizado com sucesso. A integração está ativa."
              : "O Mercado Livre retornou, mas a integração ficou em estado de erro."
          );
        }
      } catch {
        // A próxima tentativa continua o acompanhamento sem interromper o fluxo.
      }
    }, 2000);
  }

  async function diagnosticar(integracao) {
    limparFeedback();
    try {
      setProcessando(true);
      const atualizada = await api.diagnosticarMarketplace(integracao.id);
      setIntegracoes((atual) => atual.map((item) => item.id === atualizada.id ? atualizada : item));
      setIntegracaoSelecionada(atualizada);
      setMensagem(
        atualizada.status === "ATIVA"
          ? "Diagnóstico concluído: integração ativa e acessível."
          : `Diagnóstico concluído: ${statusLabel(atualizada.status)}.`
      );
    } catch (e) {
      setErro(e.message);
    } finally {
      setProcessando(false);
    }
  }

  async function alterarAtivacao(integracao) {
    limparFeedback();
    try {
      setProcessando(true);
      const atualizada = await api.alterarAtivacaoMarketplace(integracao.id, integracao.status !== "ATIVA");
      setIntegracoes((atual) => atual.map((item) => item.id === atualizada.id ? atualizada : item));
      setIntegracaoSelecionada(atualizada);
      setMensagem(`Integração ${atualizada.status === "ATIVA" ? "ativada" : "desativada"} com sucesso.`);
    } catch (e) {
      setErro(e.message);
    } finally {
      setProcessando(false);
    }
  }

  async function publicar() {
    if (!integracaoSelecionada || !produtoSelecionado) return;
    limparFeedback();
    try {
      setProcessando(true);
      const publicacao = await api.publicarProdutoMarketplace(
        integracaoSelecionada.id,
        Number(produtoSelecionado)
      );
      setMensagem(
        publicacao.urlPublicacao
          ? `Produto publicado com sucesso. Identificador: ${publicacao.identificadorExterno || "—"}.`
          : `Publicação processada com status ${statusLabel(publicacao.status)}.`
      );
    } catch (e) {
      setErro(e.message);
    } finally {
      setProcessando(false);
    }
  }

  return (
    <main className="conteudo retaguarda">
      <section className="hero hero-retaguarda">
        <div>
          <span className="eyebrow">CANAIS DE VENDA</span>
          <h1>Marketplaces</h1>
          <p>
            Conecte o Siscomercial aos marketplaces, autorize suas lojas e acompanhe a publicação dos produtos.
          </p>
        </div>
        <div className="hero-brilho" aria-hidden="true">◆</div>
      </section>

      <section className="indicadores-retaguarda" aria-label="Indicadores dos marketplaces">
        <IndicadorMarketplace titulo="Integrações" quantidade={indicadores.total} destaque />
        <IndicadorMarketplace titulo="Ativas" quantidade={indicadores.ativas} />
        <IndicadorMarketplace titulo="Aguardando autorização" quantidade={indicadores.configuradas} />
        <IndicadorMarketplace titulo="Com erro" quantidade={indicadores.erros} />
      </section>

      <section className="painel-retaguarda">
        <div className="cabecalho-marketplace">
          <div className="cabecalho-retaguarda">
            <h2>Integrações configuradas</h2>
            <p>Gerencie as conexões das lojas externas e valide o acesso antes de publicar produtos.</p>
          </div>
          <button className="botao-marketplace-principal" onClick={() => { limparFeedback(); setModalNova(true); }}>
            + Conectar Mercado Livre
          </button>
        </div>

        {erro && <p className="estado erro" role="alert">{erro}</p>}
        {mensagem && <p className="estado sucesso-marketplace" role="status">{mensagem}</p>}

        {carregando ? (
          <div className="estado">Carregando integrações...</div>
        ) : integracoes.length === 0 ? (
          <div className="marketplace-vazio">
            <div className="marketplace-vazio-icone">◎</div>
            <h3>Nenhum marketplace conectado</h3>
            <p>Crie a primeira integração e autorize uma conta de teste do Mercado Livre.</p>
            <button className="botao-marketplace-principal" onClick={() => setModalNova(true)}>
              Conectar Mercado Livre
            </button>
          </div>
        ) : (
          <div className="marketplace-lista">
            {integracoes.map((integracao) => (
              <article className={`marketplace-card ${integracaoSelecionada?.id === integracao.id ? "selecionada" : ""}`} key={integracao.id}>
                <div className="marketplace-card-cabecalho">
                  <div className="marketplace-identidade">
                    <div className="marketplace-logo">ML</div>
                    <div>
                      <span className="marketplace-tipo">MERCADO LIVRE</span>
                      <h3>{integracao.lojaProprietaria}</h3>
                      <small>{integracao.identificadorExterno}</small>
                    </div>
                  </div>
                  <span className={statusClass(integracao.status)}>{statusLabel(integracao.status)}</span>
                </div>

                <div className="marketplace-metadados">
                  <span><strong>ID:</strong> {integracao.id}</span>
                  <span><strong>Última sincronização:</strong> {dataHora(integracao.ultimaSincronizacao)}</span>
                  <span><strong>Token expira:</strong> {dataHora(integracao.tokenExpiraEm)}</span>
                </div>

                <div className="marketplace-acoes">
                  {integracao.status !== "ATIVA" && (
                    <button disabled={processando} onClick={() => autorizar(integracao)}>
                      Autorizar Mercado Livre
                    </button>
                  )}
                  {integracao.status === "ATIVA" && (
                    <button disabled={processando} onClick={() => diagnosticar(integracao)}>
                      Testar conexão
                    </button>
                  )}
                  {(integracao.status === "ATIVA" || integracao.status === "INATIVA") && (
                    <button className="botao-secundario" disabled={processando} onClick={() => alterarAtivacao(integracao)}>
                      {integracao.status === "ATIVA" ? "Desativar" : "Ativar"}
                    </button>
                  )}
                  {integracao.status === "ATIVA" && (
                    <button className="link-retaguarda" onClick={() => setIntegracaoSelecionada(integracao)}>
                      Publicar produto
                    </button>
                  )}
                </div>
              </article>
            ))}
          </div>
        )}
      </section>

      {integracaoSelecionada?.status === "ATIVA" && (
        <section className="painel-retaguarda marketplace-publicacao">
          <div className="cabecalho-retaguarda">
            <h2>Publicar produto</h2>
            <p>Selecione um produto cadastrado e envie-o para a integração ativa do Mercado Livre.</p>
          </div>
          <div className="publicacao-grid">
            <div>
              <label className="campo-marketplace-label">Integração</label>
              <div className="campo-marketplace-info">
                <strong>{integracaoSelecionada.lojaProprietaria}</strong>
                <span>Mercado Livre · #{integracaoSelecionada.id}</span>
              </div>
            </div>
            <label className="campo-marketplace-label">
              Produto
              <select value={produtoSelecionado} onChange={(e) => setProdutoSelecionado(e.target.value)}>
                <option value="">Selecione um produto...</option>
                {produtos.map((produto) => (
                  <option value={produto.id} key={produto.id}>
                    {produto.nome} — {produto.codigoInterno || `#${produto.id}`}
                  </option>
                ))}
              </select>
            </label>
            <button className="botao-marketplace-principal" disabled={!produtoSelecionado || processando} onClick={publicar}>
              {processando ? "Processando..." : "Publicar no Mercado Livre"}
            </button>
          </div>
        </section>
      )}

      {modalNova && (
        <div className="checkout-backdrop">
          <section className="modal-retaguarda marketplace-modal" role="dialog" aria-modal="true" aria-labelledby="nova-integracao">
            <button className="checkout-fechar" onClick={() => setModalNova(false)} aria-label="Fechar">×</button>
            <div className="detalhe-cabecalho">
              <span className="eyebrow">NOVA INTEGRAÇÃO</span>
              <h2 id="nova-integracao">Conectar Mercado Livre</h2>
              <p>Cadastre uma identificação local para a loja e, em seguida, autorize a conta de teste.</p>
            </div>
            <form className="marketplace-form" onSubmit={criarIntegracao}>
              <label>
                Loja proprietária
                <input required value={lojaProprietaria} onChange={(e) => setLojaProprietaria(e.target.value)} placeholder="Minha loja" />
              </label>
              <label>
                Identificador externo
                <input required value={identificadorExterno} onChange={(e) => setIdentificadorExterno(e.target.value)} placeholder="TESTE_ML" />
              </label>
              <div className="marketplace-info-box">
                <strong>Próxima etapa</strong>
                <span>Após criar a integração, o Siscomercial abrirá a autorização OAuth do Mercado Livre em uma nova janela.</span>
              </div>
              <div className="marketplace-modal-acoes">
                <button type="button" className="botao-secundario" onClick={() => setModalNova(false)}>Cancelar</button>
                <button type="submit" className="botao-marketplace-principal" disabled={processando}>
                  {processando ? "Criando..." : "Criar integração"}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
    </main>
  );
}

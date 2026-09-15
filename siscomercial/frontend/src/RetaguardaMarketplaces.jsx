import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { api } from "./api";

const statusLabel = (status) =>
  (status || "")
    .replaceAll("_", " ")
    .toLowerCase()
    .replace(/(^|\s)\S/g, (l) => l.toUpperCase());

const dataHora = (valor) =>
  valor
    ? new Intl.DateTimeFormat("pt-BR", {
        dateStyle: "short",
        timeStyle: "short",
      }).format(new Date(valor))
    : "—";

const statusClass = (status) =>
  `status-marketplace status-marketplace-${status || ""}`;

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
  const [ultimaPublicacao, setUltimaPublicacao] = useState(null);
  const [modalNova, setModalNova] = useState(false);
  const [lojaProprietaria, setLojaProprietaria] = useState("Minha loja");
  const [identificadorExterno, setIdentificadorExterno] = useState("");
  const [processando, setProcessando] = useState(false);
  const [integracaoSelecionada, setIntegracaoSelecionada] = useState(null);
  const [atributosObrigatorios, setAtributosObrigatorios] = useState([]);
  const [carregandoAtributos, setCarregandoAtributos] = useState(false);
  const [erroAtributos, setErroAtributos] = useState("");
  const [anuncios, setAnuncios] = useState([]);
  const [carregandoAnuncios, setCarregandoAnuncios] = useState(false);
  const [erroAnuncios, setErroAnuncios] = useState("");
  const [anuncioSelecionado, setAnuncioSelecionado] = useState(null);
  const [secaoMarketplace, setSecaoMarketplace] = useState("conexao");
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

  useEffect(() => {
    const selecionadaExiste = integracaoSelecionada && integracoes.some(
      (item) => item.id === integracaoSelecionada.id,
    );
    if (!selecionadaExiste) {
      const ativa = integracoes.find((item) => item.status === "ATIVA");
      setIntegracaoSelecionada(ativa || null);
    }
  }, [integracoes, integracaoSelecionada]);

  const indicadores = useMemo(
    () => ({
      total: integracoes.length,
      ativas: integracoes.filter((i) => i.status === "ATIVA").length,
      configuradas: integracoes.filter((i) => i.status === "CONFIGURADA")
        .length,
      erros: integracoes.filter((i) => i.status === "ERRO").length,
    }),
    [integracoes],
  );

  useEffect(() => {
    async function carregarAtributosProduto() {
      const produto = produtos.find(
        (item) => String(item.id) === String(produtoSelecionado),
      );
      if (!integracaoSelecionada?.id || !produto?.categoriaMercadoLivreId) {
        setAtributosObrigatorios([]);
        setErroAtributos("");
        return;
      }

      try {
        setCarregandoAtributos(true);
        setErroAtributos("");
        const atributos = await api.listarAtributosCategoriaMercadoLivre(
          integracaoSelecionada.id,
          produto.categoriaMercadoLivreId,
        );
        setAtributosObrigatorios(
          (atributos || []).filter(
            (atributo) =>
              atributo.tags?.required === true ||
              atributo.tags?.new_required === true,
          ),
        );
      } catch (e) {
        setAtributosObrigatorios([]);
        setErroAtributos(e.message);
        setErro(e.message);
      } finally {
        setCarregandoAtributos(false);
      }
    }

    carregarAtributosProduto();
  }, [integracaoSelecionada?.id, produtoSelecionado, produtos]);

  const sincronizarAnuncios = useCallback(async () => {
    if (!integracaoSelecionada?.id || integracaoSelecionada.status !== "ATIVA") {
      setAnuncios([]);
      setErroAnuncios("");
      return;
    }

    try {
      setCarregandoAnuncios(true);
      setErroAnuncios("");
      const lista = await api.sincronizarPublicacoesMarketplace(
        integracaoSelecionada.id,
      );
      setAnuncios(lista || []);
    } catch (e) {
      // Se a sincronização falhar, preservamos a lista local para não ocultar
      // anúncios já conhecidos. O erro fica disponível na tela.
      setErroAnuncios(e.message);
      try {
        const lista = await api.listarPublicacoesMarketplace(
          integracaoSelecionada.id,
        );
        setAnuncios(lista || []);
      } catch {
        // Mantém a última lista conhecida.
      }
    } finally {
      setCarregandoAnuncios(false);
    }
  }, [integracaoSelecionada?.id, integracaoSelecionada?.status]);

  useEffect(() => {
    if (secaoMarketplace === "anuncios") {
      sincronizarAnuncios();
    }
  }, [secaoMarketplace, sincronizarAnuncios]);

  const produtoSelecionadoDados = useMemo(
    () =>
      produtos.find(
        (item) => String(item.id) === String(produtoSelecionado),
      ) || null,
    [produtos, produtoSelecionado],
  );

  const problemasPreflight = useMemo(() => {
    const produto = produtoSelecionadoDados;
    if (!produto) return [];

    const imagens = [produto.imagemPrincipal, ...(produto.imagens || [])].filter(Boolean);
    const preco = produto.emPromocao
      ? produto.precoPromocional
      : produto.precoVenda;
    const problemas = [];

    if (!produto.categoriaMercadoLivreId || !/^MLB\d+$/.test(produto.categoriaMercadoLivreId)) {
      problemas.push("Selecione uma categoria Mercado Livre válida usando o preditor de categorias");
    }
    if (
      !(
        Number(produto.quantidadeEstoque || 0) -
          Number(produto.quantidadeReservada || 0) >
        0
      )
    ) {
      problemas.push("Sem estoque disponível");
    }
    if (!(Number(preco) > 0)) problemas.push("Preço inválido");
    if (!imagens.some((url) => /^https?:\/\//i.test(String(url)))) {
      problemas.push("Sem imagem pública HTTP/HTTPS");
    }

    if (carregandoAtributos) {
      problemas.push("Aguardando validação dos atributos obrigatórios");
    } else if (erroAtributos) {
      problemas.push("Não foi possível validar os atributos obrigatórios da categoria");
    } else {
      const cadastrados = produto.atributosMercadoLivre || [];
      atributosObrigatorios.forEach((atributo) => {
        const cadastrado = cadastrados.find(
          (item) => item.atributoId === atributo.id,
        );
        if (!cadastrado?.valueId && !cadastrado?.valueName?.trim()) {
          problemas.push(`Preencha o atributo obrigatório: ${atributo.name}`);
        }
      });
    }

    return problemas;
  }, [produtoSelecionadoDados, atributosObrigatorios, carregandoAtributos, erroAtributos]);

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
      setMensagem(
        "Integração criada. Agora autorize o acesso à conta do Mercado Livre.",
      );
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
        "width=900,height=760,resizable=yes,scrollbars=yes",
      );
      popupRef.current = popup;

      if (!popup) {
        setErro(
          "O navegador bloqueou a janela de autorização. Permita pop-ups para continuar.",
        );
        return;
      }

      setIntegracaoSelecionada(integracao);
      setMensagem(
        "A autorização foi aberta. Conclua o login e a autorização no Mercado Livre.",
      );
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
          if (popupRef.current && !popupRef.current.closed)
            popupRef.current.close();
          setMensagem(
            atualizada.status === "ATIVA"
              ? "Mercado Livre autorizado com sucesso. A integração está ativa."
              : "O Mercado Livre retornou, mas a integração ficou em estado de erro.",
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
      setIntegracoes((atual) =>
        atual.map((item) => (item.id === atualizada.id ? atualizada : item)),
      );
      setIntegracaoSelecionada(atualizada);
      setMensagem(
        atualizada.status === "ATIVA"
          ? "Diagnóstico concluído: integração ativa e acessível."
          : `Diagnóstico concluído: ${statusLabel(atualizada.status)}.`,
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
      const atualizada = await api.alterarAtivacaoMarketplace(
        integracao.id,
        integracao.status !== "ATIVA",
      );
      setIntegracoes((atual) =>
        atual.map((item) => (item.id === atualizada.id ? atualizada : item)),
      );
      setIntegracaoSelecionada(atualizada);
      setMensagem(
        `Integração ${atualizada.status === "ATIVA" ? "ativada" : "desativada"} com sucesso.`,
      );
    } catch (e) {
      setErro(e.message);
    } finally {
      setProcessando(false);
    }
  }

  async function atualizarStatusAnuncio(anuncio) {
    if (!anuncio?.id) return;
    limparFeedback();
    try {
      setProcessando(true);
      const atualizado = await api.sincronizarPublicacaoMarketplace(anuncio.id);
      setAnuncios((atual) =>
        atual.map((item) => (item.id === atualizado.id ? atualizado : item)),
      );
      setAnuncioSelecionado((atual) =>
        atual?.id === atualizado.id ? atualizado : atual,
      );
      setMensagem(
        `Status do anúncio ${atualizado.identificadorExterno || ""} atualizado: ${statusLabel(atualizado.status)}.`,
      );
    } catch (e) {
      setErro(e.message);
    } finally {
      setProcessando(false);
    }
  }

  async function encerrarAnuncio(anuncio) {
    if (!anuncio?.id) return;
    if (anuncio.status !== "PUBLICADA" && anuncio.status !== "PAUSADA") return;

    const confirmar = window.confirm(
      `Encerrar o anúncio ${anuncio.identificadorExterno || "selecionado"}?\n\nEssa ação é definitiva no Mercado Livre e o anúncio não poderá ser reativado. Para vender novamente, será necessário republicar o produto.`
    );
    if (!confirmar) return;

    limparFeedback();
    try {
      setProcessando(true);
      const atualizado = await api.encerrarPublicacaoMarketplace(anuncio.id);
      setAnuncios((atual) =>
        atual.map((item) => item.id === atualizado.id ? atualizado : item),
      );
      setAnuncioSelecionado(atualizado);
      setMensagem(
        `Anúncio ${atualizado.identificadorExterno || ""} encerrado com sucesso no Mercado Livre.`,
      );
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
        Number(produtoSelecionado),
      );
      setUltimaPublicacao(publicacao);
      setMensagem(
        publicacao.urlPublicacao
          ? `Produto publicado com sucesso. Identificador: ${publicacao.identificadorExterno || "—"}.`
          : `Publicação processada com status ${statusLabel(publicacao.status)}.`,
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
            Conecte o Siscomercial aos marketplaces, autorize suas lojas e
            acompanhe a publicação dos produtos.
          </p>
        </div>
        <div className="hero-brilho" aria-hidden="true">
          ◆
        </div>
      </section>

      <section
        className="indicadores-retaguarda"
        aria-label="Indicadores dos marketplaces"
      >
        <IndicadorMarketplace
          titulo="Integrações"
          quantidade={indicadores.total}
          destaque
        />
        <IndicadorMarketplace titulo="Ativas" quantidade={indicadores.ativas} />
        <IndicadorMarketplace
          titulo="Aguardando autorização"
          quantidade={indicadores.configuradas}
        />
        <IndicadorMarketplace
          titulo="Com erro"
          quantidade={indicadores.erros}
        />
      </section>

      {erro && (
        <p className="estado erro" role="alert">
          {erro}
        </p>
      )}
      {mensagem && (
        <p className="estado sucesso-marketplace" role="status">
          {mensagem}
        </p>
      )}

      <nav className="marketplace-funcionalidades" aria-label="Funcionalidades do marketplace">
        <button
          type="button"
          className={`marketplace-funcionalidade-tab ${secaoMarketplace === "conexao" ? "ativo" : ""}`}
          onClick={() => setSecaoMarketplace("conexao")}
        >
          Testar conexão
        </button>
        <button
          type="button"
          className={`marketplace-funcionalidade-tab ${secaoMarketplace === "publicacao" ? "ativo" : ""}`}
          onClick={() => setSecaoMarketplace("publicacao")}
          disabled={!integracoes.some((item) => item.status === "ATIVA")}
          title={!integracoes.some((item) => item.status === "ATIVA") ? "Ative uma integração para publicar produtos" : "Publicar produto"}
        >
          Publicar produto
        </button>
        <button
          type="button"
          className={`marketplace-funcionalidade-tab ${secaoMarketplace === "anuncios" ? "ativo" : ""}`}
          onClick={() => setSecaoMarketplace("anuncios")}
          disabled={!integracoes.some((item) => item.status === "ATIVA")}
          title={!integracoes.some((item) => item.status === "ATIVA") ? "Ative uma integração para consultar anúncios" : "Anúncios"}
        >
          Anúncios
        </button>
      </nav>

      {secaoMarketplace === "conexao" && (
        <section className="painel-retaguarda">
          <div className="cabecalho-marketplace">
            <div className="cabecalho-retaguarda">
              <h2>Testar conexão</h2>
              <p>
                Gerencie as integrações e valide o acesso das contas do Mercado Livre.
              </p>
            </div>
            <button
              type="button"
              className="botao-primario"
              onClick={() => {
                limparFeedback();
                setModalNova(true);
              }}
            >
              + Conectar Mercado Livre
            </button>
          </div>


          {carregando ? (
            <div className="estado">Carregando integrações...</div>
          ) : integracoes.length === 0 ? (
            <div className="marketplace-vazio">
              <div className="marketplace-vazio-icone">◎</div>
              <h3>Nenhum marketplace conectado</h3>
              <p>
                Crie a primeira integração e autorize uma conta do Mercado Livre.
              </p>
              <button
                type="button"
                className="botao-primario"
                onClick={() => setModalNova(true)}
              >
                Conectar Mercado Livre
              </button>
            </div>
          ) : (
            <div className="marketplace-lista">
              {integracoes.map((integracao) => (
                <article
                  className={`marketplace-card ${integracaoSelecionada?.id === integracao.id ? "selecionada" : ""}`}
                  key={integracao.id}
                >
                  <div className="marketplace-card-cabecalho">
                    <div className="marketplace-identidade">
                      <div className="marketplace-logo">ML</div>
                      <div>
                        <span className="marketplace-tipo">MERCADO LIVRE</span>
                        <h3>{integracao.lojaProprietaria}</h3>
                        <small>{integracao.identificadorExterno}</small>
                      </div>
                    </div>
                    <span className={statusClass(integracao.status)}>
                      {statusLabel(integracao.status)}
                    </span>
                  </div>

                  <div className="marketplace-metadados">
                    <span><strong>ID:</strong> {integracao.id}</span>
                    <span><strong>Última sincronização:</strong> {dataHora(integracao.ultimaSincronizacao)}</span>
                    <span><strong>Token expira:</strong> {dataHora(integracao.tokenExpiraEm)}</span>
                  </div>

                  <div className="marketplace-acoes">
                    {integracao.status !== "ATIVA" && (
                      <button
                        type="button"
                        className="botao-secundario"
                        disabled={processando}
                        onClick={() => autorizar(integracao)}
                      >
                        Autorizar Mercado Livre
                      </button>
                    )}
                    {integracao.status === "ATIVA" && (
                      <button
                        type="button"
                        className="botao-secundario"
                        disabled={processando}
                        onClick={() => diagnosticar(integracao)}
                      >
                        Testar conexão
                      </button>
                    )}
                    {(integracao.status === "ATIVA" || integracao.status === "INATIVA") && (
                      <button
                        type="button"
                        className="botao-secundario"
                        disabled={processando}
                        onClick={() => alterarAtivacao(integracao)}
                      >
                        {integracao.status === "ATIVA" ? "Desativar" : "Ativar"}
                      </button>
                    )}
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
      )}

      {secaoMarketplace === "publicacao" && (
        <section className="painel-retaguarda marketplace-publicacao">
          <div className="cabecalho-marketplace">
            <div className="cabecalho-retaguarda">
              <h2>Publicar produto</h2>
              <p>Selecione a integração e o produto que será enviado ao Mercado Livre.</p>
            </div>
          </div>

          {!integracoes.some((item) => item.status === "ATIVA") ? (
            <div className="marketplace-vazio">
              <h3>Nenhuma integração ativa</h3>
              <p>Ative uma integração na funcionalidade Conexão para publicar produtos.</p>
              <button type="button" className="botao-secundario" onClick={() => setSecaoMarketplace("conexao")}>
                Ir para conexão
              </button>
            </div>
          ) : (
            <>
              <div className="publicacao-grid">
                <label className="campo-marketplace-label">
                  Integração
                  <select
                    value={integracaoSelecionada?.id || ""}
                    onChange={(e) => {
                      const selecionada = integracoes.find((item) => String(item.id) === e.target.value);
                      setIntegracaoSelecionada(selecionada || null);
                      setProdutoSelecionado("");
                      setUltimaPublicacao(null);
                    }}
                  >
                    <option value="">Selecione uma integração...</option>
                    {integracoes.filter((item) => item.status === "ATIVA").map((integracao) => (
                      <option value={integracao.id} key={integracao.id}>
                        {integracao.lojaProprietaria || `Conta ${integracao.id}`}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="campo-marketplace-label">
                  Produto
                  <select
                    value={produtoSelecionado}
                    onChange={(e) => {
                      setProdutoSelecionado(e.target.value);
                      setUltimaPublicacao(null);
                    }}
                    disabled={!integracaoSelecionada}
                  >
                    <option value="">Selecione um produto...</option>
                    {produtos.map((produto) => (
                      <option value={produto.id} key={produto.id}>
                        {produto.nome} — {produto.codigoInterno || `#${produto.id}`}
                      </option>
                    ))}
                  </select>
                </label>
                <button
                  type="button"
                  className="botao-primario"
                  disabled={!integracaoSelecionada || !produtoSelecionado || processando || problemasPreflight.length > 0}
                  onClick={publicar}
                >
                  {processando ? "Processando..." : "Publicar no Mercado Livre"}
                </button>
              </div>

              {produtoSelecionado && (() => {
                const produto = produtos.find((item) => String(item.id) === String(produtoSelecionado));
                if (!produto) return null;
                const imagens = [produto.imagemPrincipal, ...(produto.imagens || [])].filter(Boolean);
                const preco = produto.emPromocao ? produto.precoPromocional : produto.precoVenda;
                const problemas = problemasPreflight;
                return (
                  <div className={`marketplace-preflight ${problemas.length ? "invalida" : "valida"}`}>
                    <div className="marketplace-preflight-cabecalho">
                      <strong>Pré-validação da publicação</strong>
                      <span>{problemas.length ? "Ajustes necessários" : "Dados mínimos preenchidos"}</span>
                    </div>
                    <div className="marketplace-preflight-dados">
                      <span><b>Categoria:</b> {produto.categoriaMercadoLivreNome ? `${produto.categoriaMercadoLivreNome} — ID: ${produto.categoriaMercadoLivreId}` : produto.categoriaMercadoLivreId || "—"}</span>
                      <span><b>Preço:</b> {Number(preco || 0).toLocaleString("pt-BR", { style: "currency", currency: "BRL" })}</span>
                      <span><b>Estoque disponível:</b> {Math.max(0, Number(produto.quantidadeEstoque || 0) - Number(produto.quantidadeReservada || 0))}</span>
                      <span><b>Imagens:</b> {imagens.length}</span>
                      <span><b>Descrição:</b> {produto.descricao ? "cadastrada" : "não cadastrada"}</span>
                      <span><b>Atributos obrigatórios:</b> {carregandoAtributos ? "consultando..." : `${(produto.atributosMercadoLivre || []).filter((item) => atributosObrigatorios.some((atributo) => atributo.id === item.atributoId && (item.valueId || item.valueName?.trim()))).length}/${atributosObrigatorios.length} preenchidos`}</span>
                    </div>
                    {problemas.length > 0 && <ul>{problemas.map((problema) => <li key={problema}>{problema}</li>)}</ul>}
                  </div>
                );
              })()}

              {ultimaPublicacao?.urlPublicacao && (
                <div className="marketplace-publicacao-sucesso">
                  <strong>Publicação criada no Mercado Livre</strong>
                  <a href={ultimaPublicacao.urlPublicacao} target="_blank" rel="noreferrer">
                    Abrir anúncio {ultimaPublicacao.identificadorExterno ? `(${ultimaPublicacao.identificadorExterno})` : ""}
                  </a>
                </div>
              )}
            </>
          )}
        </section>
      )}

      {secaoMarketplace === "anuncios" && (
        <section className="painel-retaguarda marketplace-anuncios">
          <div className="cabecalho-marketplace">
            <div className="cabecalho-retaguarda">
              <h2>Anúncios processados</h2>
              <p>Consulte e gerencie os anúncios já processados nesta conta do Mercado Livre.</p>
            </div>
            <div className="marketplace-anuncios-cabecalho-acoes">
              <button
                type="button"
                className="botao-secundario"
                disabled={processando || carregandoAnuncios || !integracaoSelecionada}
                onClick={sincronizarAnuncios}
              >
                {carregandoAnuncios ? "Sincronizando..." : "Sincronizar status"}
              </button>
              <span className="marketplace-anuncios-contador">
                {anuncios.length} {anuncios.length === 1 ? "anúncio" : "anúncios"}
              </span>
            </div>
          </div>

          {integracoes.filter((item) => item.status === "ATIVA").length > 1 && (
            <div className="marketplace-filtro-integracao">
              <label className="campo-marketplace-label">
                Conta Mercado Livre
                <select
                  value={integracaoSelecionada?.id || ""}
                  onChange={(e) => {
                    const selecionada = integracoes.find((item) => String(item.id) === e.target.value);
                    setIntegracaoSelecionada(selecionada || null);
                  }}
                >
                  {integracoes.filter((item) => item.status === "ATIVA").map((integracao) => (
                    <option value={integracao.id} key={integracao.id}>
                      {integracao.lojaProprietaria || `Conta ${integracao.id}`}
                    </option>
                  ))}
                </select>
              </label>
            </div>
          )}

          {carregandoAnuncios ? (
            <div className="estado">Carregando anúncios...</div>
          ) : erroAnuncios ? (
            <p className="estado erro" role="alert">{erroAnuncios}</p>
          ) : anuncios.length === 0 ? (
            <div className="marketplace-vazio marketplace-vazio-anuncios">
              <div className="marketplace-vazio-icone">↗</div>
              <h3>Nenhum anúncio processado</h3>
              <p>Quando um produto for publicado no Mercado Livre, ele aparecerá aqui para consulta.</p>
            </div>
          ) : (
            <div className="marketplace-anuncios-lista">
              {anuncios.map((anuncio) => (
                <article className="marketplace-anuncio-card" key={anuncio.id}>
                  <div className="marketplace-anuncio-imagem">
                    {anuncio.imagemPrincipal ? <img src={anuncio.imagemPrincipal} alt={`Imagem de ${anuncio.produtoNome || "produto"}`} /> : <span>Sem imagem</span>}
                  </div>
                  <div className="marketplace-anuncio-conteudo">
                    <div className="marketplace-anuncio-cabecalho">
                      <div><span className="marketplace-tipo">MERCADO LIVRE</span><h3>{anuncio.produtoNome || "Produto sem nome"}</h3></div>
                      <span className={statusClass(anuncio.status)}>{statusLabel(anuncio.status)}</span>
                    </div>
                    <div className="marketplace-anuncio-dados">
                      <span><strong>ID:</strong> {anuncio.identificadorExterno || "—"}</span>
                      <span><strong>Código:</strong> {anuncio.produtoCodigoInterno || "—"}</span>
                      <span><strong>Quantidade:</strong> {anuncio.quantidadePublicada ?? 0}</span>
                      <span><strong>Sincronizado:</strong> {dataHora(anuncio.ultimaSincronizacao)}</span>
                    </div>
                    <div className="marketplace-anuncio-acoes">
                      <button type="button" className="botao-secundario" disabled={processando} onClick={() => setAnuncioSelecionado(anuncio)}>Visualizar</button>
                      <button type="button" className="botao-secundario" disabled={processando} onClick={() => atualizarStatusAnuncio(anuncio)}>Atualizar status</button>
                      {anuncio.urlPublicacao && (
                        <a className="botao-secundario" href={anuncio.urlPublicacao} target="_blank" rel="noreferrer">Abrir anúncio ↗</a>
                      )}
                      {(anuncio.status === "PUBLICADA" || anuncio.status === "PAUSADA") && (
                        <button type="button" className="botao-perigo" disabled={processando} onClick={() => encerrarAnuncio(anuncio)}>Encerrar anúncio</button>
                      )}
                    </div>
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
      )}

      {anuncioSelecionado && (
        <div className="checkout-backdrop">
          <section className="modal-retaguarda marketplace-modal marketplace-anuncio-modal" role="dialog" aria-modal="true" aria-labelledby="anuncio-detalhes">
            <button type="button" className="checkout-fechar" onClick={() => setAnuncioSelecionado(null)} aria-label="Fechar">×</button>
            <div className="detalhe-cabecalho">
              <span className="eyebrow">ANÚNCIO PROCESSADO</span>
              <h2 id="anuncio-detalhes">{anuncioSelecionado.produtoNome || "Produto"}</h2>
              <p>ID Mercado Livre: <strong>{anuncioSelecionado.identificadorExterno || "—"}</strong></p>
            </div>
            <div className="marketplace-anuncio-detalhe">
              {anuncioSelecionado.imagemPrincipal && <img src={anuncioSelecionado.imagemPrincipal} alt={`Imagem de ${anuncioSelecionado.produtoNome || "produto"}`} />}
              <div className="marketplace-anuncio-detalhe-dados">
                <span><strong>Status</strong>{statusLabel(anuncioSelecionado.status)}</span>
                <span><strong>Código interno</strong>{anuncioSelecionado.produtoCodigoInterno || "—"}</span>
                <span><strong>Quantidade</strong>{anuncioSelecionado.quantidadePublicada ?? 0}</span>
                <span><strong>Última sincronização</strong>{dataHora(anuncioSelecionado.ultimaSincronizacao)}</span>
              </div>
            </div>
            <div className="marketplace-anuncio-modal-acoes">
              <button type="button" className="botao-secundario" disabled={processando} onClick={() => atualizarStatusAnuncio(anuncioSelecionado)}>
                {processando ? "Sincronizando..." : "Atualizar status"}
              </button>
              {anuncioSelecionado.urlPublicacao && (
                <a className="botao-secundario" href={anuncioSelecionado.urlPublicacao} target="_blank" rel="noreferrer">
                  Abrir anúncio no Mercado Livre ↗
                </a>
              )}
              {(anuncioSelecionado.status === "PUBLICADA" || anuncioSelecionado.status === "PAUSADA") && (
                <button type="button" className="botao-perigo" disabled={processando} onClick={() => encerrarAnuncio(anuncioSelecionado)}>
                  {processando ? "Encerrando..." : "Encerrar anúncio definitivamente"}
                </button>
              )}
            </div>
          </section>
        </div>
      )}

      {modalNova && (
        <div className="checkout-backdrop">
          <section
            className="modal-retaguarda marketplace-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="nova-integracao"
          >
            <button
              className="checkout-fechar"
              onClick={() => setModalNova(false)}
              aria-label="Fechar"
            >
              ×
            </button>
            <div className="detalhe-cabecalho">
              <span className="eyebrow">NOVA INTEGRAÇÃO</span>
              <h2 id="nova-integracao">Conectar Mercado Livre</h2>
              <p>
                Cadastre uma identificação local para a loja e, em seguida,
                autorize a conta de teste.
              </p>
            </div>
            <form className="marketplace-form" onSubmit={criarIntegracao}>
              <label>
                Loja proprietária
                <input
                  required
                  value={lojaProprietaria}
                  onChange={(e) => setLojaProprietaria(e.target.value)}
                  placeholder="Minha loja"
                />
              </label>
              <label>
                Identificador externo
                <input
                  required
                  value={identificadorExterno}
                  onChange={(e) => setIdentificadorExterno(e.target.value)}
                  placeholder="TESTE_ML"
                />
              </label>
              <div className="marketplace-info-box">
                <strong>Próxima etapa</strong>
                <span>
                  Após criar a integração, o Siscomercial abrirá a autorização
                  OAuth do Mercado Livre em uma nova janela.
                </span>
              </div>
              <div className="marketplace-modal-acoes">
                <button
                  type="button"
                  className="botao-secundario"
                  onClick={() => setModalNova(false)}
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  className="botao-marketplace-principal"
                  disabled={processando}
                >
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

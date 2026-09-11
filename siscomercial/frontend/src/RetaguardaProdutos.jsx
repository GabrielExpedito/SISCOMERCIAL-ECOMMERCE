import { useEffect, useMemo, useRef, useState } from "react";
import { api } from "./api";

const vazio = {
  codigoInterno: "",
  nome: "",
  descricao: "",
  precoVenda: "",
  precoPromocional: "",
  categoria: "",
  categoriaMercadoLivreId: "",
  categoriaMercadoLivreNome: "",
  quantidadeEstoque: 0,
  quantidadeReservada: 0,
  status: "ATIVO",
  imagens: [],
  imagemPrincipal: null,
  atributosMercadoLivre: [],
};

const moeda = (valor) =>
  Number(valor || 0).toLocaleString("pt-BR", {
    style: "currency",
    currency: "BRL",
  });

const imagemUrl = (url) => url || "";

function ProdutoForm({ produto, onSalvo, onCancelar }) {
  const [form, setForm] = useState(() => ({
    ...vazio,
    ...(produto || {}),
    precoVenda: produto?.precoVenda ?? "",
    precoPromocional: produto?.precoPromocional ?? "",
    imagens: produto?.imagens || [],
    imagemPrincipal: produto?.imagemPrincipal || null,
    atributosMercadoLivre: produto?.atributosMercadoLivre || [],
  }));
  const [salvando, setSalvando] = useState(false);
  const [erro, setErro] = useState("");
  const [uploadando, setUploadando] = useState(false);
  const [uploadErro, setUploadErro] = useState("");
  const [integracoesMercadoLivre, setIntegracoesMercadoLivre] = useState([]);
  const [integracaoMercadoLivreId, setIntegracaoMercadoLivreId] = useState("");
  const [categorias, setCategorias] = useState([]);
  const [buscandoCategorias, setBuscandoCategorias] = useState(false);
  const [erroCategorias, setErroCategorias] = useState("");
  const [atributosCategoria, setAtributosCategoria] = useState([]);
  const [carregandoAtributos, setCarregandoAtributos] = useState(false);
  const inputImagem = useRef(null);

  useEffect(() => {
    async function carregarIntegracoes() {
      try {
        const lista = await api.listarMarketplaces();
        const ativas = lista.filter(
          (item) => item.marketplace === "MERCADO_LIVRE" && item.status === "ATIVA",
        );
        setIntegracoesMercadoLivre(ativas);
        if (ativas.length === 1) {
          setIntegracaoMercadoLivreId(String(ativas[0].id));
        }
      } catch (e) {
        setErroCategorias(e.message);
      }
    }
    carregarIntegracoes();
  }, []);

  useEffect(() => {
    async function carregarAtributosSalvos() {
      if (!integracaoMercadoLivreId || !form.categoriaMercadoLivreId) return;

      try {
        setCarregandoAtributos(true);
        setErroCategorias("");
        const atributos = await api.listarAtributosCategoriaMercadoLivre(
          integracaoMercadoLivreId,
          form.categoriaMercadoLivreId,
        );
        setAtributosCategoria(atributos || []);
        setForm((atual) => {
          const salvos = atual.atributosMercadoLivre || [];
          const atualizados = (atributos || []).map((atributo) => {
            const existente = salvos.find((item) => item.atributoId === atributo.id);
            return existente || {
              atributoId: atributo.id,
              nome: atributo.name,
              valueId: "",
              valueName: "",
            };
          });
          return { ...atual, atributosMercadoLivre: atualizados };
        });
      } catch (e) {
        setErroCategorias(e.message);
      } finally {
        setCarregandoAtributos(false);
      }
    }

    carregarAtributosSalvos();
  }, [integracaoMercadoLivreId, form.categoriaMercadoLivreId]);

  const editar = (campo, valor) =>
    setForm((atual) => ({ ...atual, [campo]: valor }));

  async function salvar(e) {
    e.preventDefault();
    setErro("");

    if (!form.codigoInterno.trim()) return setErro("Informe o código interno.");
    if (!form.nome.trim()) return setErro("Informe o nome do produto.");
    if (!form.precoVenda || Number(form.precoVenda) <= 0) {
      return setErro("O preço de venda deve ser maior que zero.");
    }

    try {
      setSalvando(true);

      const payload = {
        codigoInterno: form.codigoInterno.trim(),
        nome: form.nome.trim(),
        descricao: form.descricao || "",
        precoVenda: Number(form.precoVenda),
        precoPromocional:
          form.precoPromocional === "" ? null : Number(form.precoPromocional),
        categoria: form.categoria || null,
        categoriaMercadoLivreId: form.categoriaMercadoLivreId || null,
        categoriaMercadoLivreNome: form.categoriaMercadoLivreNome || null,
        quantidadeEstoque: Number(form.quantidadeEstoque || 0),
        quantidadeReservada: Number(form.quantidadeReservada || 0),
        status: form.status || "ATIVO",
        imagens: form.imagens || [],
        imagemPrincipal: form.imagemPrincipal || null,
        atributosMercadoLivre: form.atributosMercadoLivre || [],
      };

      const salvo = produto?.id
        ? await api.atualizarProdutoRetaguarda(produto.id, payload)
        : await api.criarProdutoRetaguarda(payload);

      onSalvo(salvo);
    } catch (e) {
      setErro(e.message);
    } finally {
      setSalvando(false);
    }
  }

  async function buscarCategorias() {
    const integracaoId = integracaoMercadoLivreId || integracoesMercadoLivre[0]?.id;
    const termo = form.nome.trim();
    if (!integracaoId) {
      setErroCategorias("Nenhuma integração ativa do Mercado Livre foi encontrada.");
      return;
    }
    if (!termo) {
      setErroCategorias("Informe o nome do produto antes de pesquisar a categoria.");
      return;
    }

    try {
      setErroCategorias("");
      setCategorias([]);
      setAtributosCategoria([]);
      setBuscandoCategorias(true);
      setIntegracaoMercadoLivreId(String(integracaoId));
      const resultado = await api.preverCategoriasMercadoLivre(integracaoId, termo);
      setCategorias(resultado || []);
    } catch (e) {
      setErroCategorias(e.message);
    } finally {
      setBuscandoCategorias(false);
    }
  }

  async function selecionarCategoria(categoria) {
    setForm((atual) => ({
      ...atual,
      categoriaMercadoLivreId: categoria.categoryId || "",
      categoriaMercadoLivreNome: categoria.categoryName || "",
    }));
    setCategorias([]);
    setAtributosCategoria([]);

    const integracaoId = integracaoMercadoLivreId || integracoesMercadoLivre[0]?.id;
    if (!integracaoId || !categoria.categoryId) return;

    setIntegracaoMercadoLivreId(String(integracaoId));
  }

  function editarAtributo(atributo, valor) {
    setForm((atual) => {
      const lista = [...(atual.atributosMercadoLivre || [])];
      const indice = lista.findIndex((item) => item.atributoId === atributo.id);
      const item = {
        atributoId: atributo.id,
        nome: atributo.name,
        valueId: valor.valueId || "",
        valueName: valor.valueName || "",
      };

      if (indice >= 0) lista[indice] = item;
      else lista.push(item);
      return { ...atual, atributosMercadoLivre: lista };
    });
  }

  function valorAtributo(id) {
    return (form.atributosMercadoLivre || []).find((item) => item.atributoId === id) || {
      atributoId: id,
      valueId: "",
      valueName: "",
    };
  }

  async function enviarImagem(e) {
    const arquivo = e.target.files?.[0];
    e.target.value = "";
    if (!arquivo || !produto?.id) return;

    if (!["image/jpeg", "image/png"].includes(arquivo.type)) {
      setUploadErro("Utilize uma imagem JPG ou PNG.");
      return;
    }

    try {
      setUploadErro("");
      setUploadando(true);
      const resposta = await api.enviarImagemProduto(produto.id, arquivo);

      setForm((atual) => {
        const imagens = [...(atual.imagens || []), resposta.url];
        return {
          ...atual,
          imagens,
          imagemPrincipal:
            atual.imagemPrincipal || resposta.url,
        };
      });
      onSalvo({ ...form, ...produto, imagens: [...(form.imagens || []), resposta.url], imagemPrincipal: form.imagemPrincipal || resposta.url }, { manterEdicao: true });
    } catch (e) {
      setUploadErro(e.message);
    } finally {
      setUploadando(false);
    }
  }

  async function tornarPrincipal(url) {
    try {
      await api.definirImagemPrincipal(produto.id, url);
      setForm((atual) => ({ ...atual, imagemPrincipal: url }));
    } catch (e) {
      setUploadErro(e.message);
    }
  }

  async function removerImagem(url) {
    if (!window.confirm("Remover esta imagem do produto?")) return;

    try {
      await api.removerImagemProduto(produto.id, url);
      const imagens = (form.imagens || []).filter((item) => item !== url);
      setForm((atual) => ({
        ...atual,
        imagens,
        imagemPrincipal:
          atual.imagemPrincipal === url ? imagens[0] || null : atual.imagemPrincipal,
      }));
    } catch (e) {
      setUploadErro(e.message);
    }
  }

  const disponivel =
    Number(form.quantidadeEstoque || 0) -
    Number(form.quantidadeReservada || 0);

  return (
    <section className="produto-editor">
      <div className="produto-editor-cabecalho">
        <div>
          <span className="eyebrow">RETAGUARDA</span>
          <h2>{produto ? "Editar produto" : "Novo produto"}</h2>
          <p>Gerencie os dados comerciais e as imagens usadas no catálogo e marketplaces.</p>
        </div>
        <button className="botao-secundario" type="button" onClick={onCancelar}>
          Voltar
        </button>
      </div>

      {erro && <div className="estado erro">{erro}</div>}

      <form onSubmit={salvar}>
        <div className="produto-editor-grid">
          <div className="produto-editor-campo">
            <label>Código interno</label>
            <input value={form.codigoInterno} onChange={(e) => editar("codigoInterno", e.target.value)} disabled={!!produto} />
          </div>
          <div className="produto-editor-campo">
            <label>Nome</label>
            <input value={form.nome} onChange={(e) => editar("nome", e.target.value)} />
          </div>
          <div className="produto-editor-campo">
            <label>Preço de venda</label>
            <input type="number" min="0.01" step="0.01" value={form.precoVenda} onChange={(e) => editar("precoVenda", e.target.value)} />
          </div>
          <div className="produto-editor-campo">
            <label>Preço promocional</label>
            <input type="number" min="0.01" step="0.01" value={form.precoPromocional ?? ""} onChange={(e) => editar("precoPromocional", e.target.value)} />
          </div>
          <div className="produto-editor-campo produto-categoria-mercado-livre">
            <label>Categoria Mercado Livre</label>
            {integracoesMercadoLivre.length > 1 && (
              <select
                value={integracaoMercadoLivreId}
                onChange={(e) => setIntegracaoMercadoLivreId(e.target.value)}
              >
                <option value="">Selecione a conta Mercado Livre</option>
                {integracoesMercadoLivre.map((integracao) => (
                  <option key={integracao.id} value={integracao.id}>
                    {integracao.lojaProprietaria || `Conta ${integracao.id}`}
                  </option>
                ))}
              </select>
            )}
            <div className="produto-categoria-busca">
              <input
                value={form.nome || ""}
                readOnly
                placeholder="Nome do produto"
              />
              <button
                type="button"
                className="botao-secundario"
                onClick={buscarCategorias}
                disabled={buscandoCategorias}
              >
                {buscandoCategorias ? "Pesquisando..." : "Sugerir categorias"}
              </button>
            </div>

            {form.categoriaMercadoLivreId ? (
              <div className="produto-categoria-selecionada">
                <strong>{form.categoriaMercadoLivreNome || "Categoria selecionada"}</strong>
                <span>ID: {form.categoriaMercadoLivreId}</span>
              </div>
            ) : (
              <small>Nenhuma categoria Mercado Livre selecionada.</small>
            )}

            {erroCategorias && <div className="estado erro">{erroCategorias}</div>}

            {categorias.length > 0 && (
              <div className="produto-categorias-sugestoes">
                <strong>Sugestões do Mercado Livre</strong>
                {categorias.map((categoria) => (
                  <button
                    type="button"
                    key={categoria.categoryId}
                    className="produto-categoria-sugestao"
                    onClick={() => selecionarCategoria(categoria)}
                  >
                    <span>
                      <strong>{categoria.categoryName}</strong>
                      <small>{categoria.categoryId} · {categoria.domainName || ""}</small>
                    </span>
                    <span>Selecionar</span>
                  </button>
                ))}
              </div>
            )}

            {form.categoriaMercadoLivreId && (
              <div className="produto-categoria-atributos">
                <div className="produto-categoria-atributos-cabecalho">
                  <strong>Ficha técnica do Mercado Livre</strong>
                  <small>Preencha os atributos obrigatórios da categoria selecionada.</small>
                </div>

                {carregandoAtributos ? (
                  <small>Consultando atributos...</small>
                ) : atributosCategoria.length ? (
                  <div className="produto-categoria-atributos-form">
                    {atributosCategoria
                      .filter((atributo) =>
                        atributo.tags?.required === true || atributo.tags?.new_required === true,
                      )
                      .map((atributo) => {
                        const atual = valorAtributo(atributo.id);
                        const valores = Array.isArray(atributo.values) ? atributo.values : [];
                        const obrigatorio =
                          atributo.tags?.required === true || atributo.tags?.new_required === true;

                        return (
                          <div className="produto-editor-campo produto-atributo-campo" key={atributo.id}>
                            <label>
                              {atributo.name}
                              {obrigatorio && <span className="produto-atributo-obrigatorio"> *</span>}
                            </label>

                            {valores.length > 0 ? (
                              <select
                                value={atual.valueId || ""}
                                onChange={(e) => {
                                  const opcao = valores.find((item) => item.id === e.target.value);
                                  editarAtributo(atributo, {
                                    valueId: e.target.value,
                                    valueName: opcao?.name || "",
                                  });
                                }}
                              >
                                <option value="">Selecione...</option>
                                {valores.map((valor) => (
                                  <option key={valor.id} value={valor.id}>
                                    {valor.name}
                                  </option>
                                ))}
                              </select>
                            ) : (
                              <input
                                type={atributo.valueType === "number" ? "number" : "text"}
                                value={atual.valueName || ""}
                                onChange={(e) =>
                                  editarAtributo(atributo, { valueName: e.target.value })
                                }
                                placeholder={`Informe ${atributo.name.toLowerCase()}`}
                              />
                            )}
                          </div>
                        );
                      })}
                  </div>
                ) : (
                  <small>Nenhum atributo retornado para esta categoria.</small>
                )}
              </div>
            )}
          </div>
          <div className="produto-editor-campo">
            <label>Status</label>
            <select value={form.status || "ATIVO"} onChange={(e) => editar("status", e.target.value)}>
              <option value="ATIVO">Ativo</option>
              <option value="INATIVO">Inativo</option>
              <option value="SEM_ESTOQUE">Sem estoque</option>
            </select>
          </div>
          <div className="produto-editor-campo">
            <label>Estoque</label>
            <input type="number" min="0" value={form.quantidadeEstoque ?? 0} onChange={(e) => editar("quantidadeEstoque", e.target.value)} />
            <small>Disponível calculado: {Math.max(0, disponivel)}</small>
          </div>
          <div className="produto-editor-campo">
            <label>Quantidade reservada</label>
            <input type="number" min="0" value={form.quantidadeReservada ?? 0} onChange={(e) => editar("quantidadeReservada", e.target.value)} />
          </div>
        </div>

        <div className="produto-editor-campo produto-editor-descricao">
          <label>Descrição</label>
          <textarea rows="6" value={form.descricao || ""} onChange={(e) => editar("descricao", e.target.value)} />
        </div>

        {produto ? (
          <div className="produto-imagens-editor">
            <div className="produto-imagens-editor-cabecalho">
              <div>
                <h3>Imagens do produto</h3>
                <p>Cadastre imagens públicas que poderão ser utilizadas no Mercado Livre.</p>
              </div>
              <button className="botao-primario" type="button" onClick={() => inputImagem.current?.click()} disabled={uploadando}>
                {uploadando ? "Enviando..." : "Adicionar imagem"}
              </button>
              <input ref={inputImagem} type="file" accept="image/jpeg,image/png" onChange={enviarImagem} hidden />
            </div>

            {uploadErro && <div className="estado erro">{uploadErro}</div>}

            {form.imagens?.length ? (
              <div className="produto-imagens-grid">
                {form.imagens.map((url) => (
                  <article className={`produto-imagem-editor-card ${form.imagemPrincipal === url ? "principal" : ""}`} key={url}>
                    <img src={imagemUrl(url)} alt={form.nome} />
                    {form.imagemPrincipal === url && <span className="produto-imagem-principal">Principal</span>}
                    <div>
                      {form.imagemPrincipal !== url && (
                        <button type="button" className="botao-secundario" onClick={() => tornarPrincipal(url)}>
                          Tornar principal
                        </button>
                      )}
                      <button type="button" className="botao-perigo" onClick={() => removerImagem(url)}>
                        Remover
                      </button>
                    </div>
                  </article>
                ))}
              </div>
            ) : (
              <div className="estado vazio">Nenhuma imagem cadastrada.</div>
            )}
          </div>
        ) : (
          <div className="aviso-produto-novo">
            Salve o produto primeiro para poder enviar as imagens. Depois você poderá adicionar quantas imagens precisar.
          </div>
        )}

        <div className="produto-editor-acoes">
          <button type="button" className="botao-secundario" onClick={onCancelar}>Cancelar</button>
          <button type="submit" className="botao-primario" disabled={salvando}>
            {salvando ? "Salvando..." : "Salvar produto"}
          </button>
        </div>
      </form>
    </section>
  );
}

export default function RetaguardaProdutos() {
  const [produtos, setProdutos] = useState([]);
  const [selecionado, setSelecionado] = useState(null);
  const [criando, setCriando] = useState(false);
  const [busca, setBusca] = useState("");
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState("");

  async function carregar() {
    try {
      setCarregando(true);
      setErro("");
      setProdutos(await api.listarProdutosRetaguarda());
    } catch (e) {
      setErro(e.message);
    } finally {
      setCarregando(false);
    }
  }

  useEffect(() => {
    carregar();
  }, []);

  const filtrados = useMemo(() => {
    const termo = busca.trim().toLowerCase();
    if (!termo) return produtos;
    return produtos.filter((p) =>
      [p.codigoInterno, p.nome, p.categoria, p.status]
        .filter(Boolean)
        .some((v) => String(v).toLowerCase().includes(termo)),
    );
  }, [produtos, busca]);

  async function abrirEdicao(produto) {
    try {
      setErro("");
      setSelecionado(await api.buscarProdutoRetaguarda(produto.id));
      setCriando(false);
    } catch (e) {
      setErro(e.message);
    }
  }

  function novoProduto() {
    setSelecionado(null);
    setCriando(true);
  }

  function voltarLista() {
    setSelecionado(null);
    setCriando(false);
    carregar();
  }

  if (criando || selecionado) {
    return (
      <ProdutoForm
        produto={selecionado}
        onSalvo={(salvo, opcoes) => {
          if (opcoes?.manterEdicao) {
            setSelecionado((atual) => ({ ...(atual || {}), ...salvo }));
            return;
          }
          setSelecionado(salvo);
          setCriando(false);
        }}
        onCancelar={voltarLista}
      />
    );
  }

  return (
    <section className="retaguarda-produtos">
      <div className="retaguarda-cabecalho">
        <div>
          <span className="eyebrow">RETAGUARDA</span>
          <h1>Produtos</h1>
          <p>Cadastre, edite e prepare produtos para venda e publicação nos marketplaces.</p>
        </div>
        <button className="botao-primario" onClick={novoProduto}>+ Novo produto</button>
      </div>

      {erro && <div className="estado erro">{erro}</div>}

      <div className="barra-retaguarda">
        <input
          placeholder="Buscar por código, nome, categoria ou status..."
          value={busca}
          onChange={(e) => setBusca(e.target.value)}
        />
        <span>{filtrados.length} produto(s)</span>
      </div>

      {carregando ? (
        <div className="estado">Carregando produtos...</div>
      ) : filtrados.length === 0 ? (
        <div className="estado vazio">Nenhum produto encontrado.</div>
      ) : (
        <div className="tabela-retaguarda-produtos">
          {filtrados.map((produto) => (
            <article className="linha-produto-retaguarda" key={produto.id}>
              <div className="linha-produto-thumb">
                {produto.imagemPrincipal ? <img src={produto.imagemPrincipal} alt="" /> : <span>Sem foto</span>}
              </div>
              <div className="linha-produto-info">
                <strong>{produto.nome}</strong>
                <small>{produto.codigoInterno} · ID {produto.id}</small>
                <span>
                  {produto.categoriaMercadoLivreNome
                    ? `Mercado Livre: ${produto.categoriaMercadoLivreNome} (${produto.categoriaMercadoLivreId})`
                    : produto.categoria || "Categoria não informada"}
                </span>
              </div>
              <div>
                <strong>{moeda(produto.precoVenda)}</strong>
                <small>Estoque disponível: {produto.quantidadeDisponivel ?? Math.max(0, (produto.quantidadeEstoque || 0) - (produto.quantidadeReservada || 0))}</small>
              </div>
              <div>
                <span className={`status-produto status-${produto.status}`}>{produto.status}</span>
                <small>{produto.imagens?.length || 0} imagem(ns)</small>
              </div>
              <button className="botao-secundario" onClick={() => abrirEdicao(produto)}>Editar</button>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}

import React, { useCallback, useEffect, useMemo, useState } from "react";
import { api } from "./api";
import AgenteChat from "./AgenteChat.jsx";
import RetaguardaPedidos from "./RetaguardaPedidos.jsx";
import { useAuth } from "./context/AuthContext.jsx";

function formatarPreco(valor) {
  return Number(valor || 0).toLocaleString("pt-BR", {
    style: "currency",
    currency: "BRL",
  });
}

function imagemProduto(produto) {
  return produto.imagemPrincipal || produto.imagem || null;
}

export default function App() {
  const {
    usuario,
    carregando: carregandoAuth,
    autenticado,
    ehAdmin,
    entrarComGoogle,
    sair,
  } = useAuth();
  const [produtos, setProdutos] = useState([]);
  const [carrinho, setCarrinho] = useState([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState(null);
  const [mostrarAgente, setMostrarAgente] = useState(false);
  const [busca, setBusca] = useState("");
  const [mostrarCheckout, setMostrarCheckout] = useState(false);
  const [enderecoEntrega, setEnderecoEntrega] = useState({
    cep: "",
    logradouro: "",
    numero: "",
    complemento: "",
    bairro: "",
    cidade: "",
    estado: "",
  });
  const [formaPagamento, setFormaPagamento] = useState("PIX");
  const [cotacaoFrete, setCotacaoFrete] = useState(null);
  const [carregandoFrete, setCarregandoFrete] = useState(false);
  const [enviandoPedido, setEnviandoPedido] = useState(false);
  const [erroCheckout, setErroCheckout] = useState(null);
  const [pedidoCriado, setPedidoCriado] = useState(null);
  const [modoRetaguarda, setModoRetaguarda] = useState(false);

  const carregarCatalogo = useCallback(async () => {
    try {
      setErro(null);
      const dados = await api.listarCatalogo();
      setProdutos(dados);
    } catch (e) {
      setErro(e.message);
    } finally {
      setCarregando(false);
    }
  }, []);

  useEffect(() => {
    carregarCatalogo();
  }, [carregarCatalogo]);

  const produtosFiltrados = useMemo(() => {
    const termo = busca.trim().toLowerCase();
    if (!termo) return produtos;
    return produtos.filter((p) =>
      [p.nome, p.codigoInterno, p.descricao, p.categoria]
        .filter(Boolean)
        .some((valor) => String(valor).toLowerCase().includes(termo))
    );
  }, [produtos, busca]);

  function adicionarAoCarrinho(produto) {
    setCarrinho((atual) => {
      const existente = atual.find((i) => i.produto.id === produto.id);
      if (existente) {
        return atual.map((i) =>
          i.produto.id === produto.id
            ? { ...i, quantidade: i.quantidade + 1 }
            : i
        );
      }
      return [...atual, { produto, quantidade: 1 }];
    });
  }

  function removerDoCarrinho(id) {
    setCarrinho((atual) => atual.filter((item) => item.produto.id !== id));
  }

  function abrirCheckout() {
    if (!autenticado) {
      entrarComGoogle();
      return;
    }

    setErroCheckout(null);
    setPedidoCriado(null);
    setMostrarCheckout(true);
  }

  function atualizarEndereco(campo, valor) {
    setEnderecoEntrega((atual) => ({ ...atual, [campo]: valor }));
    if (campo === "cep") setCotacaoFrete(null);
  }

  async function cotarFrete() {
    const cep = enderecoEntrega.cep.replace(/\D/g, "");
    if (cep.length !== 8) {
      setErroCheckout("Informe um CEP com 8 dígitos para calcular o frete.");
      return;
    }

    try {
      setCarregandoFrete(true);
      setErroCheckout(null);
      const cotacao = await api.calcularFrete(cep);
      setCotacaoFrete(cotacao);
    } catch (e) {
      setCotacaoFrete(null);
      setErroCheckout(e.message);
    } finally {
      setCarregandoFrete(false);
    }
  }

  async function finalizarPedido(event) {
    event.preventDefault();
    if (!cotacaoFrete) {
      setErroCheckout("Calcule o frete antes de confirmar o pedido.");
      return;
    }

    try {
      setEnviandoPedido(true);
      setErroCheckout(null);
      const pedido = await api.checkout({
        itens: carrinho.map(({ produto, quantidade }) => ({
          produtoId: produto.id,
          quantidade,
        })),
        enderecoEntrega: {
          ...enderecoEntrega,
          cep: enderecoEntrega.cep.replace(/\D/g, ""),
          estado: enderecoEntrega.estado.trim().toUpperCase(),
        },
        formaPagamento,
      });
      setPedidoCriado(pedido);
      setCarrinho([]);
      carregarCatalogo();
    } catch (e) {
      setErroCheckout(e.message);
    } finally {
      setEnviandoPedido(false);
    }
  }

  const total = carrinho.reduce((soma, item) => {
    const preco = item.produto.emPromocao
      ? item.produto.precoPromocional
      : item.produto.precoVenda;
    return soma + Number(preco || 0) * item.quantidade;
  }, 0);

  return (
    <div className="app-shell">
      <header className="topo">
        <div className="marca">
          <div className="marca-icone">S</div>
          <div>
            <strong>Siscomercial</strong>
            <span>E-commerce inteligente</span>
          </div>
        </div>
        <div className="topo-acoes">
          <div className="status-loja">
            <span />
            Loja online
          </div>

          {ehAdmin && (
            <button
              className={`botao-assistente ${modoRetaguarda ? "ativo" : ""}`}
              onClick={() => setModoRetaguarda((valor) => !valor)}
            >
              {modoRetaguarda ? "Ver loja" : "Retaguarda"}
            </button>
          )}

          {ehAdmin && (
            <button
              className={`botao-assistente ${mostrarAgente ? "ativo" : ""}`}
              onClick={() => setMostrarAgente((v) => !v)}
            >
              <span>✦</span>
              {mostrarAgente ? "Ocultar IA" : "Assistente IA"}
            </button>
          )}

          {!carregandoAuth && !autenticado && (
            <button className="botao-login" onClick={entrarComGoogle}>
              <span className="google-icone">G</span>
              Entrar com Google
            </button>
          )}

          {!carregandoAuth && autenticado && (
            <div className="usuario-menu">
              <div className="usuario-info">
                <div className="usuario-avatar">
                  {(usuario?.nomeRazaoSocial || usuario?.email || "U")
                    .charAt(0)
                    .toUpperCase()}
                </div>

                <div className="usuario-texto">
                  <strong>{usuario?.nomeRazaoSocial || "Usuário"}</strong>

                  <span>
                    {usuario?.perfil === "ADMIN" ? "Administrador" : "Cliente"}
                  </span>
                </div>
              </div>

              <button className="botao-sair" onClick={sair} title="Sair">
                Sair
              </button>
            </div>
          )}
        </div>
      </header>

      {modoRetaguarda && ehAdmin ? (
        <RetaguardaPedidos />
      ) : (
      <main className="conteudo">
        <section className="hero">
          <div>
            <span className="eyebrow">CATÁLOGO SISCOMERCIAL</span>
            <h1>Produtos para o seu negócio.</h1>
            <p>
              Encontre produtos, consulte estoque e use o assistente inteligente
              para administrar sua loja.
            </p>
          </div>
          <div className="hero-brilho" aria-hidden="true">
            ✦
          </div>
        </section>

        <div className="barra-catalogo">
          <div>
            <h2>Produtos em destaque</h2>
            <span>
              {produtos.length} produto{produtos.length === 1 ? "" : "s"}{" "}
              disponível{produtos.length === 1 ? "" : "is"}
            </span>
          </div>
          <div className="busca">
            <span>⌕</span>
            <input
              value={busca}
              onChange={(e) => setBusca(e.target.value)}
              placeholder="Buscar produto..."
            />
          </div>
        </div>

        {carregando && <div className="estado">Carregando catálogo...</div>}
        {erro && (
          <div className="estado erro">Erro ao carregar catálogo: {erro}</div>
        )}

        {!carregando && !erro && produtosFiltrados.length === 0 && (
          <div className="estado vazio">Nenhum produto encontrado.</div>
        )}

        <div className="layout-loja">
          <section className="grade-produtos">
            {produtosFiltrados.map((p) => {
              const imagem = imagemProduto(p);
              return (
                <article className="card-produto" key={p.id}>
                  <div className="produto-imagem">
                    {imagem ? (
                      <img src={imagem} alt={p.nome} />
                    ) : (
                      <span>▧</span>
                    )}
                    {p.emPromocao && <span className="selo">OFERTA</span>}
                  </div>
                  <div className="produto-corpo">
                    {p.categoria && (
                      <span className="categoria">{p.categoria}</span>
                    )}
                    <h3>{p.nome}</h3>
                    <p className="codigo">{p.codigoInterno}</p>
                    {p.descricao && <p className="descricao">{p.descricao}</p>}
                    <div className="preco-area">
                      {p.emPromocao && (
                        <span className="preco-antigo">
                          {formatarPreco(p.precoVenda)}
                        </span>
                      )}
                      <strong>
                        {formatarPreco(
                          p.emPromocao ? p.precoPromocional : p.precoVenda
                        )}
                      </strong>
                    </div>
                    <div className="produto-rodape">
                      <span
                        className={
                          p.quantidadeDisponivel > 0 ? "estoque ok" : "estoque"
                        }
                      >
                        {p.quantidadeDisponivel > 0
                          ? `${p.quantidadeDisponivel} em estoque`
                          : "Sem estoque"}
                      </span>
                      <button
                        disabled={p.quantidadeDisponivel <= 0}
                        onClick={() => adicionarAoCarrinho(p)}
                      >
                        + Carrinho
                      </button>
                    </div>
                  </div>
                </article>
              );
            })}
          </section>

          <aside className="carrinho">
            <div className="carrinho-titulo">
              <div>
                <span className="carrinho-icone">🛒</span>
                <h2>Carrinho</h2>
              </div>
              {carrinho.length > 0 && (
                <span className="contador">{carrinho.length}</span>
              )}
            </div>
            {carrinho.length === 0 ? (
              <div className="carrinho-vazio">
                <span>🛒</span>
                <p>Seu carrinho está vazio.</p>
                <small>Adicione produtos para começar.</small>
              </div>
            ) : (
              <>
                <ul className="lista-carrinho">
                  {carrinho.map((item) => (
                    <li key={item.produto.id}>
                      <div>
                        <strong>{item.produto.nome}</strong>
                        <span>
                          {item.quantidade} ×{" "}
                          {formatarPreco(
                            item.produto.emPromocao
                              ? item.produto.precoPromocional
                              : item.produto.precoVenda
                          )}
                        </span>
                      </div>
                      <button
                        onClick={() => removerDoCarrinho(item.produto.id)}
                        title="Remover"
                      >
                        ×
                      </button>
                    </li>
                  ))}
                </ul>
                <div className="total">
                  <span>Total</span>
                  <strong>{formatarPreco(total)}</strong>
                </div>
                <button className="finalizar" onClick={abrirCheckout}>
                  Finalizar pedido
                </button>
              </>
            )}
          </aside>
        </div>
      </main>
      )}

      {mostrarAgente && <AgenteChat onCatalogoAtualizado={carregarCatalogo} />}

      {mostrarCheckout && (
        <div className="checkout-backdrop" role="presentation">
          <section
            className="checkout-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="checkout-titulo"
          >
            <button
              className="checkout-fechar"
              onClick={() => setMostrarCheckout(false)}
              aria-label="Fechar checkout"
            >
              ×
            </button>

            {pedidoCriado ? (
              <div className="pedido-sucesso">
                <span>✓</span>
                <p className="eyebrow">PEDIDO CRIADO</p>
                <h2>Recebemos seu pedido!</h2>
                <strong>{pedidoCriado.numeroPedido}</strong>
                <p>
                  Total de {formatarPreco(pedidoCriado.total)}. Aguarde a
                  confirmação do pagamento para o pedido seguir para separação.
                </p>
                <button className="finalizar" onClick={() => setMostrarCheckout(false)}>
                  Continuar comprando
                </button>
              </div>
            ) : (
              <form onSubmit={finalizarPedido}>
                <div className="checkout-cabecalho">
                  <p className="eyebrow">CHECKOUT SEGURO</p>
                  <h2 id="checkout-titulo">Finalizar pedido</h2>
                  <p>Confirme a entrega, o frete e a forma de pagamento.</p>
                </div>

                <div className="checkout-grid">
                  <div className="checkout-campos">
                    <h3>Endereço de entrega</h3>
                    <div className="campos-linha cep-linha">
                      <label>
                        CEP
                        <input
                          required
                          inputMode="numeric"
                          maxLength="9"
                          value={enderecoEntrega.cep}
                          onChange={(e) => atualizarEndereco("cep", e.target.value)}
                          onBlur={cotarFrete}
                          placeholder="00000-000"
                        />
                      </label>
                      <button type="button" className="botao-secundario" onClick={cotarFrete} disabled={carregandoFrete}>
                        {carregandoFrete ? "Calculando..." : "Calcular frete"}
                      </button>
                    </div>
                    <label>
                      Logradouro
                      <input required value={enderecoEntrega.logradouro} onChange={(e) => atualizarEndereco("logradouro", e.target.value)} />
                    </label>
                    <div className="campos-linha">
                      <label>
                        Número
                        <input required value={enderecoEntrega.numero} onChange={(e) => atualizarEndereco("numero", e.target.value)} />
                      </label>
                      <label>
                        Complemento
                        <input value={enderecoEntrega.complemento} onChange={(e) => atualizarEndereco("complemento", e.target.value)} />
                      </label>
                    </div>
                    <label>
                      Bairro
                      <input required value={enderecoEntrega.bairro} onChange={(e) => atualizarEndereco("bairro", e.target.value)} />
                    </label>
                    <div className="campos-linha cidade-estado">
                      <label>
                        Cidade
                        <input required value={enderecoEntrega.cidade} onChange={(e) => atualizarEndereco("cidade", e.target.value)} />
                      </label>
                      <label>
                        UF
                        <input required maxLength="2" value={enderecoEntrega.estado} onChange={(e) => atualizarEndereco("estado", e.target.value)} placeholder="SP" />
                      </label>
                    </div>

                    <h3>Forma de pagamento</h3>
                    <div className="pagamentos">
                      {[['PIX', 'PIX'], ['CARTAO_CREDITO', 'Cartão de crédito'], ['CARTAO_DEBITO', 'Cartão de débito']].map(([valor, rotulo]) => (
                        <label className="opcao-pagamento" key={valor}>
                          <input type="radio" name="pagamento" value={valor} checked={formaPagamento === valor} onChange={(e) => setFormaPagamento(e.target.value)} />
                          {rotulo}
                        </label>
                      ))}
                    </div>
                  </div>

                  <aside className="resumo-checkout">
                    <h3>Resumo do pedido</h3>
                    {carrinho.map((item) => (
                      <div className="resumo-item" key={item.produto.id}>
                        <span>{item.quantidade}× {item.produto.nome}</span>
                        <strong>{formatarPreco((item.produto.emPromocao ? item.produto.precoPromocional : item.produto.precoVenda) * item.quantidade)}</strong>
                      </div>
                    ))}
                    <div className="resumo-linha"><span>Produtos</span><strong>{formatarPreco(total)}</strong></div>
                    <div className="resumo-linha"><span>Frete {cotacaoFrete ? `(${cotacaoFrete.modalidade})` : ""}</span><strong>{cotacaoFrete ? formatarPreco(cotacaoFrete.valor) : "—"}</strong></div>
                    {cotacaoFrete && <small>Prazo estimado: {cotacaoFrete.prazoDias} dias úteis.</small>}
                    <div className="resumo-total"><span>Total</span><strong>{formatarPreco(total + Number(cotacaoFrete?.valor || 0))}</strong></div>
                    <button className="finalizar" type="submit" disabled={enviandoPedido}>
                      {enviandoPedido ? "Criando pedido..." : "Confirmar pedido"}
                    </button>
                  </aside>
                </div>
                {erroCheckout && <p className="checkout-erro" role="alert">{erroCheckout}</p>}
              </form>
            )}
          </section>
        </div>
      )}
    </div>
  );
}

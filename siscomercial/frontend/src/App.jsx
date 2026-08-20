import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from './api';
import AgenteChat from './AgenteChat.jsx';

function formatarPreco(valor) {
  return Number(valor || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
}

function imagemProduto(produto) {
  return produto.imagemPrincipal || produto.imagem || null;
}

export default function App() {
  const [produtos, setProdutos] = useState([]);
  const [carrinho, setCarrinho] = useState([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState(null);
  const [mostrarAgente, setMostrarAgente] = useState(false);
  const [busca, setBusca] = useState('');

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
          i.produto.id === produto.id ? { ...i, quantidade: i.quantidade + 1 } : i
        );
      }
      return [...atual, { produto, quantidade: 1 }];
    });
  }

  function removerDoCarrinho(id) {
    setCarrinho((atual) => atual.filter((item) => item.produto.id !== id));
  }

  const total = carrinho.reduce((soma, item) => {
    const preco = item.produto.emPromocao ? item.produto.precoPromocional : item.produto.precoVenda;
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
          <div className="status-loja"><span /> Loja online</div>
          <button className={`botao-assistente ${mostrarAgente ? 'ativo' : ''}`} onClick={() => setMostrarAgente((v) => !v)}>
            <span>✦</span> {mostrarAgente ? 'Ocultar IA' : 'Assistente IA'}
          </button>
        </div>
      </header>

      <main className="conteudo">
        <section className="hero">
          <div>
            <span className="eyebrow">CATÁLOGO SISCOMERCIAL</span>
            <h1>Produtos para o seu negócio.</h1>
            <p>Encontre produtos, consulte estoque e use o assistente inteligente para administrar sua loja.</p>
          </div>
          <div className="hero-brilho" aria-hidden="true">✦</div>
        </section>

        <div className="barra-catalogo">
          <div>
            <h2>Produtos em destaque</h2>
            <span>{produtos.length} produto{produtos.length === 1 ? '' : 's'} disponível{produtos.length === 1 ? '' : 'is'}</span>
          </div>
          <div className="busca">
            <span>⌕</span>
            <input value={busca} onChange={(e) => setBusca(e.target.value)} placeholder="Buscar produto..." />
          </div>
        </div>

        {carregando && <div className="estado">Carregando catálogo...</div>}
        {erro && <div className="estado erro">Erro ao carregar catálogo: {erro}</div>}

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
                    {imagem ? <img src={imagem} alt={p.nome} /> : <span>▧</span>}
                    {p.emPromocao && <span className="selo">OFERTA</span>}
                  </div>
                  <div className="produto-corpo">
                    {p.categoria && <span className="categoria">{p.categoria}</span>}
                    <h3>{p.nome}</h3>
                    <p className="codigo">{p.codigoInterno}</p>
                    {p.descricao && <p className="descricao">{p.descricao}</p>}
                    <div className="preco-area">
                      {p.emPromocao && <span className="preco-antigo">{formatarPreco(p.precoVenda)}</span>}
                      <strong>{formatarPreco(p.emPromocao ? p.precoPromocional : p.precoVenda)}</strong>
                    </div>
                    <div className="produto-rodape">
                      <span className={p.quantidadeDisponivel > 0 ? 'estoque ok' : 'estoque'}>
                        {p.quantidadeDisponivel > 0 ? `${p.quantidadeDisponivel} em estoque` : 'Sem estoque'}
                      </span>
                      <button disabled={p.quantidadeDisponivel <= 0} onClick={() => adicionarAoCarrinho(p)}>
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
              <div><span className="carrinho-icone">🛒</span><h2>Carrinho</h2></div>
              {carrinho.length > 0 && <span className="contador">{carrinho.length}</span>}
            </div>
            {carrinho.length === 0 ? (
              <div className="carrinho-vazio"><span>🛒</span><p>Seu carrinho está vazio.</p><small>Adicione produtos para começar.</small></div>
            ) : (
              <>
                <ul className="lista-carrinho">
                  {carrinho.map((item) => (
                    <li key={item.produto.id}>
                      <div><strong>{item.produto.nome}</strong><span>{item.quantidade} × {formatarPreco(item.produto.emPromocao ? item.produto.precoPromocional : item.produto.precoVenda)}</span></div>
                      <button onClick={() => removerDoCarrinho(item.produto.id)} title="Remover">×</button>
                    </li>
                  ))}
                </ul>
                <div className="total"><span>Total</span><strong>{formatarPreco(total)}</strong></div>
                <button className="finalizar">Finalizar pedido</button>
              </>
            )}
          </aside>
        </div>
      </main>

      {mostrarAgente && <AgenteChat onCatalogoAtualizado={carregarCatalogo} />}
    </div>
  );
}

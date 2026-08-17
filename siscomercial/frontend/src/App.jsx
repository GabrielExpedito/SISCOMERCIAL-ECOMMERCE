import React, { useEffect, useState } from 'react';
import { api } from './api';
import AgenteChat from './AgenteChat.jsx';

/**
 * Prototipo de front-end do e-commerce Siscomercial (RF002/RF003) com o
 * painel do agente de IA (RF009) embutido. Consome exclusivamente a REST
 * API do backend Spring Boot - nenhuma regra de negocio vive aqui.
 */
export default function App() {
  const [produtos, setProdutos] = useState([]);
  const [carrinho, setCarrinho] = useState([]); // [{produto, quantidade}]
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState(null);
  const [mostrarAgente, setMostrarAgente] = useState(false);

  useEffect(() => {
    api.listarCatalogo()
      .then(setProdutos)
      .catch((e) => setErro(e.message))
      .finally(() => setCarregando(false));
  }, []);

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

  const total = carrinho.reduce((soma, item) => {
    const preco = item.produto.emPromocao ? item.produto.precoPromocional : item.produto.precoVenda;
    return soma + preco * item.quantidade;
  }, 0);

  return (
    <div className="app">
      <header className="topo">
        <h1>Siscomercial</h1>
        <button className="botao-carrinho" onClick={() => setMostrarAgente((v) => !v)}>
          {mostrarAgente ? 'Fechar assistente' : '🤖 Assistente IA'}
        </button>
      </header>

      <main>
        <section className="catalogo">
          <h2>Catálogo</h2>
          {carregando && <p>Carregando produtos...</p>}
          {erro && <p className="erro">Erro ao carregar catálogo: {erro}</p>}
          <div className="grade-produtos">
            {produtos.map((p) => (
              <div className="card-produto" key={p.id}>
                <h3>{p.nome}</h3>
                <p className="descricao">{p.descricao}</p>
                <p className="preco">
                  {p.emPromocao ? (
                    <>
                      <span className="preco-antigo">R$ {p.precoVenda?.toFixed(2)}</span>{' '}
                      <span className="preco-promocional">R$ {p.precoPromocional?.toFixed(2)}</span>
                    </>
                  ) : (
                    <>R$ {p.precoVenda?.toFixed(2)}</>
                  )}
                </p>
                <p className="disponibilidade">
                  {p.quantidadeDisponivel > 0 ? `${p.quantidadeDisponivel} em estoque` : 'Sem estoque'}
                </p>
                <button
                  disabled={p.quantidadeDisponivel <= 0}
                  onClick={() => adicionarAoCarrinho(p)}
                >
                  Adicionar ao carrinho
                </button>
              </div>
            ))}
          </div>
        </section>

        <aside className="carrinho">
          <h2>Carrinho</h2>
          {carrinho.length === 0 && <p>Seu carrinho está vazio.</p>}
          <ul>
            {carrinho.map((item) => (
              <li key={item.produto.id}>
                {item.produto.nome} × {item.quantidade}
              </li>
            ))}
          </ul>
          {carrinho.length > 0 && <p className="total">Total: R$ {total.toFixed(2)}</p>}
        </aside>
      </main>

      {mostrarAgente && <AgenteChat />}
    </div>
  );
}

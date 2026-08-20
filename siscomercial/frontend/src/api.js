const BASE_URL = '/api';

async function request(path, options = {}) {
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options,
  });

  if (!res.ok) {
    const erro = await res.json().catch(() => ({ erro: res.statusText }));
    throw new Error(erro.erro || 'Erro na requisicao');
  }

  return res.status === 204 ? null : res.json();
}

export const api = {
  listarCatalogo: () => request('/catalogo/produtos'),
  pesquisarProdutos: (q) => request(`/catalogo/produtos/pesquisar?q=${encodeURIComponent(q)}`),
  calcularFrete: (cep) => request(`/catalogo/frete?cep=${encodeURIComponent(cep)}`),
  checkout: (payload) => request('/pedidos/checkout', { method: 'POST', body: JSON.stringify(payload) }),
  buscarPedido: (numero) => request(`/pedidos/${numero}`),
  confirmarPagamento: (id) => request(`/pedidos/${id}/confirmar-pagamento`, { method: 'POST' }),
  chatAgente: (sessionId, mensagem) =>
    request('/ia/chat', { method: 'POST', body: JSON.stringify({ sessionId, mensagem }) }),
};

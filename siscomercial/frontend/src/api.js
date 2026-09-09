const BASE_URL = "/api";

async function request(path, options = {}) {
  const res = await fetch(`${BASE_URL}${path}`, {
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {}),
    },
    ...options,
  });

  if (!res.ok) {
    const erro = await res.json().catch(() => ({
      erro: res.statusText,
    }));

    throw new Error(erro.erro || "Erro na requisicao");
  }

  return res.status === 204 ? null : res.json();
}

export const api = {
  listarCatalogo: () => request("/catalogo/produtos"),

  pesquisarProdutos: (q) =>
    request(`/catalogo/produtos/pesquisar?q=${encodeURIComponent(q)}`),

  calcularFrete: (cep) =>
    request(`/catalogo/frete?cep=${encodeURIComponent(cep)}`),

  checkout: (payload) =>
    request("/pedidos/checkout", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  buscarPedido: (numero) => request(`/pedidos/${numero}`),

  confirmarPagamento: (id) =>
    request(`/pedidos/${id}/confirmar-pagamento`, {
      method: "POST",
    }),

  chatAgente: (sessionId, mensagem) =>
    request("/ia/chat", {
      method: "POST",
      body: JSON.stringify({
        sessionId,
        mensagem,
      }),
    }),

  buscarUsuarioAtual: () => request("/auth/me"),

  listarPedidosRetaguarda: (filtros = {}) => {
    const parametros = new URLSearchParams();
    Object.entries(filtros).forEach(([chave, valor]) => {
      if (valor !== undefined && valor !== null && valor !== "") {
        const dataInicio = chave === "dataInicio" && /^\d{4}-\d{2}-\d{2}$/.test(valor);
        const dataFim = chave === "dataFim" && /^\d{4}-\d{2}-\d{2}$/.test(valor);
        parametros.set(chave, dataInicio ? `${valor}T00:00:00` : dataFim ? `${valor}T23:59:59` : valor);
      }
    });
    const query = parametros.toString();
    return request(`/retaguarda/pedidos${query ? `?${query}` : ""}`);
  },

  buscarPedidoRetaguarda: (id) => request(`/retaguarda/pedidos/${id}`),

  listarHistoricoPedidoRetaguarda: (id) =>
    request(`/retaguarda/pedidos/${id}/historico`),

  alterarStatusPedidoRetaguarda: (id, status) => request(`/retaguarda/pedidos/${id}/status`, {
    method: "POST", body: JSON.stringify({ status }),
  }),

  faturarPedidoRetaguarda: (id) => request(`/retaguarda/pedidos/${id}/faturar`, { method: "POST" }),

  enviarPedidoRetaguarda: (id, codigoRastreamento) => request(`/retaguarda/pedidos/${id}/enviar`, {
    method: "POST", body: JSON.stringify({ codigoRastreamento }),
  }),

  cancelarPedidoRetaguarda: (id, motivo) => request(`/retaguarda/pedidos/${id}/cancelar`, {
    method: "POST", body: JSON.stringify({ motivo }),
  }),
};

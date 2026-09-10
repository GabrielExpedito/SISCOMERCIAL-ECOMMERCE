const BASE_URL = "/api";

async function request(path, options = {}) {
  const headers = new Headers(options.headers || {});
  const isFormData = options.body instanceof FormData;

  if (!isFormData && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }

  const res = await fetch(`${BASE_URL}${path}`, {
    credentials: "include",
    ...options,
    headers,
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

  listarMarketplaces: () => request("/retaguarda/marketplaces"),

  criarIntegracaoMercadoLivre: (payload) =>
    request("/retaguarda/marketplaces/mercado-livre", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  iniciarAutorizacaoMercadoLivre: (id) =>
    request(`/retaguarda/marketplaces/${id}/mercado-livre/autorizacao`, {
      method: "POST",
    }),

  alterarAtivacaoMarketplace: (id, ativa) =>
    request(`/retaguarda/marketplaces/${id}/ativacao`, {
      method: "POST",
      body: JSON.stringify({ ativa }),
    }),

  diagnosticarMarketplace: (id) =>
    request(`/retaguarda/marketplaces/${id}/diagnostico`, {
      method: "POST",
    }),

  listarProdutosRetaguarda: () => request("/retaguarda/produtos"),

  buscarProdutoRetaguarda: (id) =>
    request(`/retaguarda/produtos/${id}`),

  criarProdutoRetaguarda: (payload) =>
    request("/retaguarda/produtos", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  atualizarProdutoRetaguarda: (id, payload, usuario = "admin") =>
    request(`/retaguarda/produtos/${id}?usuario=${encodeURIComponent(usuario)}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    }),

  inativarProdutoRetaguarda: (id) =>
    request(`/retaguarda/produtos/${id}/inativar`, { method: "POST" }),

  reativarProdutoRetaguarda: (id) =>
    request(`/retaguarda/produtos/${id}/reativar`, { method: "POST" }),

  enviarImagemProduto: (produtoId, arquivo) => {
    const formData = new FormData();
    formData.append("file", arquivo);

    return request(`/retaguarda/produtos/${produtoId}/imagens`, {
      method: "POST",
      body: formData,
    });
  },

  definirImagemPrincipal: (produtoId, url) =>
    request(`/retaguarda/produtos/${produtoId}/imagens/principal`, {
      method: "PUT",
      body: JSON.stringify({ url }),
    }),

  removerImagemProduto: (produtoId, url) =>
    request(
      `/retaguarda/produtos/${produtoId}/imagens?url=${encodeURIComponent(url)}`,
      { method: "DELETE" },
    ),

  publicarProdutoMarketplace: (integracaoId, produtoId) =>
    request("/retaguarda/marketplaces/publicacoes", {
      method: "POST",
      body: JSON.stringify({ integracaoId, produtoId }),
    }),

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

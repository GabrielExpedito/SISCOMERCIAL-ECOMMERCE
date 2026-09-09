import { useCallback, useEffect, useMemo, useState } from "react";
import { api } from "./api";

const STATUS = [
  "AGUARDANDO_PAGAMENTO", "PAGAMENTO_APROVADO", "EM_SEPARACAO",
  "FATURADO", "ENVIADO", "ENTREGUE", "CANCELADO", "PAGAMENTO_RECUSADO",
  "EXPIRADO", "DEVOLVIDO",
];

const rotuloStatus = (status) => (status || "").replaceAll("_", " ");
const moeda = (valor) => Number(valor || 0).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
const dataHora = (valor) => valor ? new Intl.DateTimeFormat("pt-BR", { dateStyle: "short", timeStyle: "short" }).format(new Date(valor)) : "—";

function Indicador({ titulo, quantidade, destaque }) {
  return <article className={`indicador-retaguarda ${destaque ? "destaque" : ""}`}><span>{titulo}</span><strong>{quantidade}</strong></article>;
}

export default function RetaguardaPedidos() {
  const [filtros, setFiltros] = useState({ numeroPedido: "", cliente: "", status: "", dataInicio: "", dataFim: "" });
  const [pagina, setPagina] = useState(0);
  const [resultado, setResultado] = useState({ content: [], totalElements: 0, totalPages: 0 });
  const [pedido, setPedido] = useState(null);
  const [historico, setHistorico] = useState([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState("");

  const carregarPedidos = useCallback(async () => {
    try {
      setCarregando(true); setErro("");
      setResultado(await api.listarPedidosRetaguarda({ ...filtros, page: pagina, size: 20 }));
    } catch (e) { setErro(e.message); } finally { setCarregando(false); }
  }, [filtros, pagina]);

  useEffect(() => { carregarPedidos(); }, [carregarPedidos]);

  const indicadores = useMemo(() => {
    const pedidos = resultado.content || [];
    return {
      total: resultado.totalElements || 0,
      aguardando: pedidos.filter((item) => item.status === "AGUARDANDO_PAGAMENTO").length,
      operacao: pedidos.filter((item) => ["PAGAMENTO_APROVADO", "EM_SEPARACAO", "FATURADO"].includes(item.status)).length,
      enviados: pedidos.filter((item) => item.status === "ENVIADO").length,
    };
  }, [resultado]);

  async function abrirPedido(id) {
    try {
      setErro("");
      const [detalhe, eventos] = await Promise.all([api.buscarPedidoRetaguarda(id), api.listarHistoricoPedidoRetaguarda(id)]);
      setPedido(detalhe); setHistorico(eventos);
    } catch (e) { setErro(e.message); }
  }

  function atualizarFiltro(campo, valor) { setPagina(0); setFiltros((atual) => ({ ...atual, [campo]: valor })); }
  function limparFiltros() { setPagina(0); setFiltros({ numeroPedido: "", cliente: "", status: "", dataInicio: "", dataFim: "" }); }

  async function executarAcao(acao) {
    if (!pedido) return;
    try {
      setErro("");
      let atualizado;
      if (acao === "separar") atualizado = await api.alterarStatusPedidoRetaguarda(pedido.id, "EM_SEPARACAO");
      if (acao === "faturar") atualizado = await api.faturarPedidoRetaguarda(pedido.id);
      if (acao === "enviar") {
        const codigo = window.prompt("Informe o código de rastreamento:");
        if (!codigo) return;
        atualizado = await api.enviarPedidoRetaguarda(pedido.id, codigo);
      }
      if (acao === "cancelar") {
        const motivo = window.prompt("Informe o motivo do cancelamento (opcional):") || "";
        if (!window.confirm("Confirma o cancelamento deste pedido?")) return;
        atualizado = await api.cancelarPedidoRetaguarda(pedido.id, motivo);
      }
      setPedido(atualizado);
      setHistorico(await api.listarHistoricoPedidoRetaguarda(pedido.id));
      carregarPedidos();
    } catch (e) { setErro(e.message); }
  }

  return <main className="conteudo retaguarda">
    <section className="hero hero-retaguarda"><div><span className="eyebrow">OPERAÇÃO ADMINISTRATIVA</span><h1>Retaguarda de pedidos</h1><p>Acompanhe a fila operacional e consulte os detalhes auditáveis de cada venda.</p></div></section>
    <section className="indicadores-retaguarda" aria-label="Indicadores dos pedidos carregados">
      <Indicador titulo="Pedidos encontrados" quantidade={indicadores.total} destaque />
      <Indicador titulo="Aguardando pagamento" quantidade={indicadores.aguardando} />
      <Indicador titulo="Em operação" quantidade={indicadores.operacao} />
      <Indicador titulo="Enviados" quantidade={indicadores.enviados} />
    </section>
    <section className="painel-retaguarda">
      <div className="cabecalho-retaguarda"><div><h2>Fila de pedidos</h2><p>Use os filtros para localizar vendas do e-commerce e, futuramente, dos marketplaces.</p></div></div>
      <div className="filtros-retaguarda">
        <input value={filtros.numeroPedido} onChange={(e) => atualizarFiltro("numeroPedido", e.target.value)} placeholder="Número do pedido" aria-label="Número do pedido" />
        <input value={filtros.cliente} onChange={(e) => atualizarFiltro("cliente", e.target.value)} placeholder="Cliente ou comprador" aria-label="Cliente ou comprador" />
        <select value={filtros.status} onChange={(e) => atualizarFiltro("status", e.target.value)} aria-label="Status"><option value="">Todos os status</option>{STATUS.map((status) => <option value={status} key={status}>{rotuloStatus(status)}</option>)}</select>
        <label>De<input type="date" value={filtros.dataInicio} onChange={(e) => atualizarFiltro("dataInicio", e.target.value)} /></label>
        <label>Até<input type="date" value={filtros.dataFim} onChange={(e) => atualizarFiltro("dataFim", e.target.value)} /></label>
        <button className="botao-secundario" onClick={limparFiltros}>Limpar</button>
      </div>
      {erro && <p className="estado erro" role="alert">{erro}</p>}
      {carregando ? <div className="estado">Carregando pedidos...</div> : <div className="tabela-responsiva"><table><thead><tr><th>Pedido</th><th>Cliente</th><th>Data</th><th>Status</th><th>Itens</th><th>Total</th><th></th></tr></thead><tbody>
        {resultado.content?.map((item) => <tr key={item.id}><td><strong>{item.numeroPedido}</strong><small>{item.modalidadeFrete || "Frete não informado"}</small></td><td>{item.clienteNome || "Comprador não identificado"}<small>{item.clienteEmail}</small></td><td>{dataHora(item.dataHora)}</td><td><span className={`status-pedido status-${item.status}`}>{rotuloStatus(item.status)}</span></td><td>{item.quantidadeItens}</td><td><strong>{moeda(item.total)}</strong></td><td><button className="link-retaguarda" onClick={() => abrirPedido(item.id)}>Ver detalhe</button></td></tr>)}
        {!resultado.content?.length && <tr><td colSpan="7" className="sem-resultados">Nenhum pedido corresponde aos filtros.</td></tr>}
      </tbody></table></div>}
      {resultado.totalPages > 1 && <div className="paginacao"><button className="botao-secundario" disabled={pagina === 0} onClick={() => setPagina((v) => v - 1)}>Anterior</button><span>Página {pagina + 1} de {resultado.totalPages}</span><button className="botao-secundario" disabled={pagina + 1 >= resultado.totalPages} onClick={() => setPagina((v) => v + 1)}>Próxima</button></div>}
    </section>
    {pedido && <div className="checkout-backdrop"><section className="modal-retaguarda" role="dialog" aria-modal="true" aria-labelledby="detalhe-pedido"><button className="checkout-fechar" onClick={() => setPedido(null)} aria-label="Fechar detalhes">×</button><div className="detalhe-cabecalho"><span className="eyebrow">DETALHE DO PEDIDO</span><h2 id="detalhe-pedido">{pedido.numeroPedido}</h2><span className={`status-pedido status-${pedido.status}`}>{rotuloStatus(pedido.status)}</span></div><div className="detalhe-grid"><section><h3>Cliente</h3><p><strong>{pedido.cliente?.nomeRazaoSocial || "—"}</strong><br />{pedido.cliente?.email || "—"}<br />{pedido.cliente?.telefone || ""}</p><h3>Entrega</h3><p>{pedido.enderecoEntrega?.logradouro}, {pedido.enderecoEntrega?.numero}<br />{pedido.enderecoEntrega?.bairro}<br />{pedido.enderecoEntrega?.cidade}/{pedido.enderecoEntrega?.estado} — {pedido.enderecoEntrega?.cep}<br />{pedido.codigoRastreamento && <>Rastreamento: <strong>{pedido.codigoRastreamento}</strong></>}</p><h3>Pagamento</h3><p>{rotuloStatus(pedido.pagamento?.formaPagamento)}<br />{rotuloStatus(pedido.pagamento?.status)}</p></section><section><h3>Itens</h3><ul className="itens-detalhe">{pedido.itens?.map((item) => <li key={item.id}><span>{item.quantidade}× {item.produtoNome || item.descricaoMomentoCompra}</span><strong>{moeda(item.subtotal)}</strong></li>)}</ul><div className="totais-detalhe"><span>Produtos <strong>{moeda(pedido.subtotal)}</strong></span><span>Descontos <strong>{moeda(pedido.desconto)}</strong></span><span>Frete <strong>{moeda(pedido.frete)}</strong></span><span>Total <strong>{moeda(pedido.total)}</strong></span></div><div className="acoes-retaguarda">{pedido.status === "PAGAMENTO_APROVADO" && <button onClick={() => executarAcao("separar")}>Iniciar separação</button>}{pedido.status === "EM_SEPARACAO" && <button onClick={() => executarAcao("faturar")}>Faturar</button>}{pedido.status === "FATURADO" && <button onClick={() => executarAcao("enviar")}>Registrar envio</button>}{!["CANCELADO", "ENTREGUE"].includes(pedido.status) && <button className="botao-secundario" onClick={() => executarAcao("cancelar")}>Cancelar pedido</button>}</div><h3>Histórico</h3><ol className="historico-pedido">{historico.map((evento) => <li key={evento.id}><strong>{rotuloStatus(evento.statusAnterior)} → {rotuloStatus(evento.novoStatus)}</strong><span>{dataHora(evento.dataHora)} · {rotuloStatus(evento.origem)}{evento.responsavel ? ` · ${evento.responsavel}` : ""}</span></li>)}{!historico.length && <li>Sem alterações de status registradas.</li>}</ol></section></div></section></div>}
  </main>;
}

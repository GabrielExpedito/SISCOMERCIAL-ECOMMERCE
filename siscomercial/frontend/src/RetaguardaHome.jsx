const MODULOS = [
  {
    id: "pedidos",
    icone: "▣",
    titulo: "Pedidos",
    descricao: "Acompanhe os pedidos realizados pelos clientes e conduza o fluxo operacional.",
    detalhe: "Acompanhar pedidos",
  },
  {
    id: "produtos",
    icone: "▦",
    titulo: "Produtos",
    descricao: "Cadastre, edite, organize imagens e prepare os produtos para venda.",
    detalhe: "Gerenciar catálogo",
  },
  {
    id: "marketplaces",
    icone: "↗",
    titulo: "Marketplaces",
    descricao: "Gerencie integrações, autorizações e publicações nos canais externos.",
    detalhe: "Integrações e publicações",
  },
];

export default function RetaguardaHome({ onSelecionarModulo }) {
  return (
    <main className="conteudo retaguarda retaguarda-home">
      <section className="hero hero-retaguarda">
        <div>
          <span className="eyebrow">RETAGUARDA</span>
          <h1>Administração do Siscomercial</h1>
          <p>
            Acesse os módulos administrativos para acompanhar a operação,
            gerenciar produtos e controlar os canais de venda.
          </p>
        </div>
        <div className="hero-brilho" aria-hidden="true">◆</div>
      </section>

      <section className="retaguarda-home-cabecalho">
        <div>
          <span className="eyebrow">MÓDULOS</span>
          <h2>O que você deseja gerenciar?</h2>
          <p>Selecione um módulo para acessar suas funcionalidades.</p>
        </div>
      </section>

      <section className="retaguarda-modulos" aria-label="Módulos da retaguarda">
        {MODULOS.map((modulo) => (
          <button
            type="button"
            className="retaguarda-modulo-card"
            key={modulo.id}
            onClick={() => onSelecionarModulo(modulo.id)}
          >
            <span className="retaguarda-modulo-icone" aria-hidden="true">
              {modulo.icone}
            </span>

            <span className="retaguarda-modulo-conteudo">
              <span className="retaguarda-modulo-titulo">{modulo.titulo}</span>
              <span className="retaguarda-modulo-descricao">{modulo.descricao}</span>
              <span className="retaguarda-modulo-detalhe">{modulo.detalhe}</span>
            </span>

            <span className="retaguarda-modulo-seta" aria-hidden="true">→</span>
          </button>
        ))}
      </section>
    </main>
  );
}

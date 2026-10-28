import React from "react";
import "./legal-page.css";

const SITE_URL = "https://siscomercial-app.onrender.com";
const EMAIL_CONTATO = "gabrielexpedi18@gmail.com";

const termos = [
  {
    titulo: "1. Identificação do responsável",
    paragrafos: [
      "O SisComercial é uma plataforma de software em desenvolvimento, destinada à gestão de operações de comércio eletrônico e à integração com marketplaces.",
      "Responsável pela operação nesta fase inicial: Gabriel Expedito da Silva, pessoa física.",
      `Site oficial: ${SITE_URL}.`,
      `E-mail de contato: ${EMAIL_CONTATO}.`,
      "Endereço para contato: Rua Roberto Scarpa, 421, Jardim Caieira, Limeira – SP.",
    ],
  },
  {
    titulo: "2. Aceitação dos Termos",
    paragrafos: [
      "Ao criar uma conta ou utilizar os serviços disponibilizados pelo SisComercial, o usuário declara que leu e compreendeu estes Termos e concorda com as condições aplicáveis à utilização da plataforma.",
      "Quando necessário, o aceite será solicitado expressamente antes da utilização de funcionalidades ou da contratação do serviço. Caso não concorde com estes Termos, o usuário deverá deixar de utilizar as funcionalidades abrangidas por eles.",
    ],
  },
  {
    titulo: "3. Descrição da plataforma",
    paragrafos: [
      "O SisComercial tem como objetivo oferecer ferramentas para apoiar a gestão de operações de comércio eletrônico, incluindo, conforme as funcionalidades disponibilizadas em cada versão:",
    ],
    itens: [
      "Cadastro e gerenciamento de produtos;",
      "Administração de pedidos e estoque;",
      "Gerenciamento de anúncios e publicações;",
      "Integração com marketplaces compatíveis;",
      "Consulta de informações operacionais;",
      "Automação de tarefas relacionadas à operação comercial.",
    ],
    fim: "A plataforma está em desenvolvimento e poderá receber atualizações, novas funcionalidades e melhorias. A disponibilidade de cada recurso dependerá da versão do produto e das integrações habilitadas.",
  },
  {
    titulo: "4. Modelo comercial e contratação",
    paragrafos: [
      "O modelo comercial previsto para o SisComercial é baseado em assinaturas mensais.",
      "Os valores, planos, limites de utilização, funcionalidades incluídas e condições de pagamento serão definidos e divulgados antes do início da comercialização. Nenhuma mensalidade deverá ser cobrada sem que as condições aplicáveis à contratação tenham sido apresentadas ao usuário e aceitas por ele.",
      "Até a definição e divulgação dos planos comerciais, este documento não estabelece preço, prazo mínimo de permanência ou compromisso de contratação.",
      "As condições definitivas de cobrança, renovação, cancelamento, reembolso e eventual período de teste serão apresentadas antes da contratação, conforme aplicável à oferta disponibilizada.",
    ],
  },
  {
    titulo: "5. Cadastro e responsabilidades do usuário",
    paragrafos: [
      "O usuário deverá fornecer informações corretas e atualizadas e manter a confidencialidade de suas credenciais de acesso. Também será responsável por:",
    ],
    itens: [
      "Utilizar a plataforma de acordo com a legislação aplicável;",
      "Garantir que possui autorização para conectar contas e sistemas externos;",
      "Conferir informações de produtos, preços, estoque e pedidos;",
      "Manter suas credenciais protegidas e restringir o acesso à conta a pessoas autorizadas;",
      "Comunicar suspeitas de acesso indevido;",
      "Garantir que possui os direitos necessários sobre os dados e conteúdos que disponibilizar na plataforma.",
    ],
    fim: "É proibido utilizar o SisComercial para atividades ilícitas, fraudes, violações de direitos de terceiros ou tentativas de acesso não autorizado a sistemas e informações.",
  },
  {
    titulo: "6. Integrações com marketplaces",
    paragrafos: [
      "O SisComercial poderá oferecer integração com marketplaces, incluindo Magalu, Mercado Livre e Shopee, conforme a disponibilidade e a habilitação dos respectivos serviços.",
      "A conexão poderá depender de cadastro, autorização do titular da conta, credenciais válidas, permissões específicas e disponibilidade das APIs externas. O usuário será responsável por autorizar as conexões e operações solicitadas e por garantir que possui os direitos necessários para realizar as atividades comerciais correspondentes.",
      "A integração técnica não garante a aprovação de anúncios, a realização de vendas, a disponibilidade ininterrupta dos serviços externos ou a aceitação de operações pelo marketplace.",
      "O SisComercial não deverá ser interpretado como representante oficial, proprietário ou parceiro comercial de um marketplace apenas em razão da existência de uma integração técnica. Cada marketplace poderá estabelecer seus próprios termos, políticas, requisitos de cadastro e regras de utilização.",
    ],
  },
  {
    titulo: "7. Credenciais e autorizações",
    paragrafos: [
      "Quando o usuário conectar uma conta externa, o SisComercial tratará os dados técnicos necessários à integração conforme as permissões concedidas e a Política de Privacidade.",
      "O usuário poderá solicitar a desconexão da conta, observados os recursos disponibilizados pela plataforma e pelo marketplace correspondente. Os procedimentos definitivos de revogação de autorização, desconexão e exclusão de credenciais deverão estar documentados e disponíveis antes do lançamento comercial das funcionalidades correspondentes.",
    ],
  },
  {
    titulo: "8. Disponibilidade e manutenção",
    paragrafos: [
      "A plataforma poderá passar por atualizações, manutenções ou indisponibilidades temporárias, inclusive em razão de falhas de infraestrutura ou de serviços de terceiros.",
      "Quando viável, mudanças relevantes e interrupções programadas serão comunicadas pelos canais disponíveis. Os níveis de disponibilidade, prazos de suporte e eventuais compromissos de serviço serão definidos nas condições comerciais aplicáveis, caso sejam oferecidos.",
    ],
  },
  {
    titulo: "9. Responsabilidades e limitações",
    paragrafos: [
      "O SisComercial é uma ferramenta de apoio operacional. O usuário permanece responsável por suas decisões comerciais, pela conferência dos dados e pelo cumprimento das obrigações legais relacionadas à sua atividade.",
      "O responsável pela plataforma deverá adotar medidas adequadas de segurança e observar a legislação aplicável. Não se garante disponibilidade contínua de serviços externos, estabilidade permanente de APIs de terceiros ou aprovação de operações pelos marketplaces.",
      "Nada nestes Termos exclui direitos ou responsabilidades que não possam ser afastados pela legislação aplicável.",
    ],
  },
  {
    titulo: "10. Propriedade intelectual",
    paragrafos: [
      "Os componentes, a interface, o código e os materiais próprios do SisComercial são protegidos pela legislação aplicável, respeitados os direitos de terceiros e as licenças de software utilizadas.",
      "O usuário não poderá copiar, distribuir, explorar comercialmente ou modificar componentes da plataforma sem autorização, exceto quando permitido pela legislação ou pelas respectivas licenças. Os dados, marcas, imagens e conteúdos fornecidos pelo usuário permanecem sujeitos aos direitos de seus respectivos titulares.",
    ],
  },
  {
    titulo: "11. Suspensão, cancelamento e encerramento",
    paragrafos: [
      "O acesso poderá ser suspenso ou encerrado diante de uso ilícito, violação destes Termos, risco à segurança, determinação legal ou outra justificativa legítima, observadas a legislação aplicável e as condições contratuais.",
      `O usuário poderá solicitar o encerramento da conta pelo e-mail ${EMAIL_CONTATO}, enquanto não houver canal específico disponibilizado na plataforma.`,
      "As condições comerciais definitivas deverão especificar os procedimentos de cancelamento de assinatura, encerramento da conta, exportação de dados e tratamento das informações remanescentes.",
    ],
  },
  {
    titulo: "12. Proteção de dados pessoais",
    paragrafos: [
      "O tratamento de dados pessoais será realizado conforme a legislação aplicável, incluindo a Lei Geral de Proteção de Dados Pessoais (LGPD), e a Política de Privacidade do SisComercial.",
      "A Política de Privacidade apresenta informações sobre dados tratados, finalidades, compartilhamentos, retenção e meios disponíveis para o exercício de direitos.",
    ],
  },
  {
    titulo: "13. Alterações dos Termos",
    paragrafos: [
      "Estes Termos poderão ser atualizados em razão de mudanças no produto, nas integrações, no modelo comercial ou na legislação aplicável.",
      "A versão vigente deverá permanecer acessível, com indicação da data da última atualização. Quando necessário, os usuários serão comunicados sobre alterações relevantes e será solicitado novo aceite, se aplicável.",
    ],
  },
  {
    titulo: "14. Legislação e contato",
    paragrafos: [
      "Estes Termos serão interpretados conforme a legislação brasileira, respeitadas as normas obrigatórias aplicáveis à relação estabelecida entre as partes.",
      "Responsável: Gabriel Expedito da Silva.",
      `E-mail: ${EMAIL_CONTATO}.`,
      "Endereço: Rua Roberto Scarpa, 421, Jardim Caieira, Limeira – SP.",
      "A definição de foro e de mecanismos específicos de resolução de conflitos deverá respeitar as regras legais aplicáveis.",
    ],
  },
];

const privacidade = [
  {
    titulo: "1. Quem é responsável pelo SisComercial",
    paragrafos: [
      "Nesta fase inicial, o responsável informado pela operação do SisComercial é Gabriel Expedito da Silva, pessoa física.",
      `Site: ${SITE_URL}.`,
      `Contato sobre privacidade e dados pessoais: ${EMAIL_CONTATO}.`,
      "Endereço para contato: Rua Roberto Scarpa, 421, Jardim Caieira, Limeira – SP.",
      "O papel de controlador ou operador de dados pode variar conforme a finalidade do tratamento e a relação com o cliente. Essa definição deverá ser confirmada conforme os fluxos reais de dados e os contratos aplicáveis.",
    ],
  },
  {
    titulo: "2. Abrangência desta Política",
    paragrafos: [
      "Esta Política descreve, em termos gerais, como o SisComercial poderá tratar dados pessoais durante o cadastro, o acesso à plataforma, a gestão de produtos e pedidos, o suporte e a utilização de integrações com serviços externos.",
      "O texto deve ser atualizado para refletir os recursos efetivamente disponibilizados, os fornecedores utilizados e os fluxos reais de dados antes do lançamento comercial.",
    ],
  },
  {
    titulo: "3. Dados que podem ser tratados",
    paragrafos: [
      "Conforme os recursos utilizados, o SisComercial poderá tratar as seguintes categorias de dados:",
    ],
    subtitulos: [
      ["Dados de cadastro e conta", "Nome, e-mail, identificadores de conta, perfil de acesso e informações fornecidas durante o cadastro ou suporte."],
      ["Dados operacionais", "Informações de produtos, pedidos, estoque, anúncios, preços, entregas e registros relacionados à operação comercial."],
      ["Dados de integração", "Identificadores de contas conectadas, tokens de autorização, credenciais técnicas e informações retornadas por marketplaces, na medida necessária ao funcionamento da integração."],
      ["Dados técnicos e de uso", "Registros de acesso, data e hora de solicitações, informações técnicas do navegador e registros de erro ou auditoria, quando coletados pelo sistema ou pela infraestrutura."],
      ["Comunicações", "Conteúdo das mensagens enviadas ao suporte ou por outros canais de contato disponibilizados."],
    ],
    fim: "A lista efetiva depende dos campos, funcionalidades, integrações e ferramentas de monitoramento realmente habilitados no ambiente.",
  },
  {
    titulo: "4. Finalidades do tratamento",
    paragrafos: [
      "Os dados poderão ser tratados, conforme aplicável, para:",
    ],
    itens: [
      "Criar e administrar contas e autenticar usuários;",
      "Disponibilizar funcionalidades de catálogo, pedidos, estoque e gestão de anúncios;",
      "Conectar e sincronizar informações com marketplaces autorizados pelo usuário;",
      "Executar solicitações do usuário e prestar suporte;",
      "Proteger a plataforma, prevenir fraudes e investigar falhas ou incidentes;",
      "Cumprir obrigações legais e regulatórias;",
      "Administrar planos, assinaturas, pagamentos e cobranças, quando essas funcionalidades forem disponibilizadas.",
    ],
  },
  {
    titulo: "5. Bases legais",
    paragrafos: [
      "O tratamento será fundamentado na hipótese legal aplicável a cada finalidade, nos termos da LGPD. Conforme o contexto, poderão ser utilizadas bases como execução de contrato ou procedimentos preliminares, cumprimento de obrigação legal, exercício regular de direitos, legítimo interesse após avaliação pertinente ou consentimento, quando necessário.",
      "A base legal efetiva deverá ser definida de acordo com cada operação de tratamento; esta Política não significa que todas as bases mencionadas sejam utilizadas para todos os dados.",
    ],
  },
  {
    titulo: "6. Integrações e compartilhamento",
    paragrafos: [
      "Para executar funcionalidades solicitadas, os dados estritamente necessários poderão ser transmitidos a marketplaces e serviços externos conectados pelo usuário, de acordo com as autorizações concedidas e as regras desses provedores.",
      "Também poderá haver tratamento por fornecedores de hospedagem, infraestrutura, autenticação, armazenamento, monitoramento, comunicação ou pagamentos, conforme os serviços efetivamente contratados.",
      "A relação completa de fornecedores, categorias de dados compartilhados e finalidades deverá ser atualizada antes da publicação definitiva desta Política. O SisComercial não deve compartilhar dados para finalidades incompatíveis com as informadas ou sem fundamento legal adequado.",
    ],
  },
  {
    titulo: "7. Credenciais de marketplaces",
    paragrafos: [
      "A conexão com marketplaces pode exigir tokens, autorizações ou outros dados técnicos. Esses dados devem ser utilizados somente para executar as operações autorizadas e protegidos por medidas de segurança adequadas.",
      "O usuário deve verificar as permissões solicitadas antes de autorizar uma integração. A revogação do acesso poderá ser realizada pelos recursos disponibilizados no SisComercial ou no serviço externo, conforme a implementação de cada integração.",
      "Os procedimentos técnicos de armazenamento, revogação e exclusão de tokens devem ser confirmados conforme a implementação real do sistema.",
    ],
  },
  {
    titulo: "8. Cookies e tecnologias semelhantes",
    paragrafos: [
      "O site e a aplicação poderão utilizar armazenamento local, cookies ou tecnologias semelhantes necessários à autenticação, segurança, preferências e funcionamento da sessão.",
      "O inventário de cookies e tecnologias efetivamente utilizados, inclusive eventual uso de ferramentas analíticas ou publicitárias, deverá ser verificado no ambiente publicado e descrito nesta seção antes da publicação definitiva.",
    ],
  },
  {
    titulo: "9. Armazenamento, segurança e retenção",
    paragrafos: [
      "O responsável deverá adotar medidas técnicas e organizacionais proporcionais aos riscos para proteger os dados contra acessos não autorizados, perda, alteração ou divulgação indevida.",
      "Nenhum sistema é capaz de garantir segurança absoluta. Em caso de incidente com dados pessoais, serão avaliadas as medidas de contenção e as comunicações exigidas pela legislação aplicável.",
      "Os prazos de retenção variam conforme a finalidade, as obrigações legais, a necessidade de defesa de direitos e o funcionamento do serviço. Os prazos específicos e os procedimentos de exclusão deverão ser definidos e documentados antes da comercialização.",
    ],
  },
  {
    titulo: "10. Transferência internacional",
    paragrafos: [
      "Se fornecedores ou infraestrutura utilizados pelo SisComercial armazenarem ou processarem dados fora do Brasil, a transferência internacional deverá observar os requisitos da LGPD e da regulamentação aplicável.",
      "Os países envolvidos, fornecedores e mecanismos jurídicos aplicáveis deverão ser confirmados com base na infraestrutura efetivamente utilizada.",
    ],
  },
  {
    titulo: "11. Direitos dos titulares",
    paragrafos: [
      "Nos termos da LGPD e conforme aplicável, o titular poderá solicitar confirmação da existência de tratamento, acesso aos dados, correção de informações incompletas ou desatualizadas, anonimização, bloqueio ou eliminação nas hipóteses legais, portabilidade quando aplicável, informações sobre compartilhamento e revisão de decisões automatizadas, além de outros direitos previstos em lei.",
      `As solicitações podem ser encaminhadas para ${EMAIL_CONTATO}. Para proteger os dados, poderá ser necessário confirmar a identidade do solicitante. O atendimento observará os limites, prazos e exceções previstos na legislação.`,
    ],
  },
  {
    titulo: "12. Dados de terceiros inseridos pelo cliente",
    paragrafos: [
      "O cliente que inserir dados pessoais de compradores, funcionários ou outras pessoas deverá garantir que possui fundamento legal para fazê-lo e que prestou as informações necessárias aos titulares, quando exigido.",
      "Quando o SisComercial tratar esses dados em nome do cliente, as responsabilidades de cada parte deverão ser estabelecidas conforme os papéis efetivamente exercidos e os instrumentos contratuais aplicáveis.",
    ],
  },
  {
    titulo: "13. Dados de crianças e adolescentes",
    paragrafos: [
      "A plataforma não é direcionada intencionalmente a crianças. Caso seja identificado tratamento de dados de crianças ou adolescentes, ele deverá observar os requisitos específicos da legislação aplicável e o melhor interesse do titular.",
    ],
  },
  {
    titulo: "14. Alterações desta Política",
    paragrafos: [
      "Esta Política poderá ser alterada para refletir mudanças na plataforma, nos fornecedores, nas integrações ou na legislação. A versão vigente deverá permanecer acessível, com a data de atualização.",
    ],
  },
  {
    titulo: "15. Contato",
    paragrafos: [
      "Responsável informado nesta fase: Gabriel Expedito da Silva.",
      `E-mail para assuntos de privacidade: ${EMAIL_CONTATO}.`,
      "Endereço: Rua Roberto Scarpa, 421, Jardim Caieira, Limeira – SP.",
      "A necessidade de designação formal de encarregado pelo tratamento de dados pessoais deverá ser avaliada conforme a legislação e o contexto efetivo da operação.",
    ],
  },
];

function BlocoSecao({ secao }) {
  return (
    <section className="legal-secao">
      <h2>{secao.titulo}</h2>
      {secao.paragrafos?.map((paragrafo, indice) => (
        <p key={indice}>{paragrafo}</p>
      ))}
      {secao.itens && (
        <ul>
          {secao.itens.map((item) => <li key={item}>{item}</li>)}
        </ul>
      )}
      {secao.subtitulos && secao.subtitulos.map(([titulo, texto]) => (
        <div className="legal-subsecao" key={titulo}>
          <h3>{titulo}</h3>
          <p>{texto}</p>
        </div>
      ))}
      {secao.fim && <p>{secao.fim}</p>}
    </section>
  );
}

export default function LegalPage({ tipo }) {
  const ehTermos = tipo === "termos";
  const secoes = ehTermos ? termos : privacidade;
  const titulo = ehTermos ? "Termos de Uso" : "Política de Privacidade";
  const descricao = ehTermos
    ? "Condições gerais para utilização da plataforma SisComercial."
    : "Como o SisComercial trata e protege dados pessoais durante o uso da plataforma.";

  return (
    <div className="legal-pagina">
      <header className="legal-topo">
        <a className="legal-marca" href="/" aria-label="SisComercial - página inicial">
          <span className="legal-marca-icone">S</span>
          <span>
            <strong>SisComercial</strong>
            <small>E-commerce inteligente</small>
          </span>
        </a>
        <a className="legal-voltar" href="/">Voltar à loja <span aria-hidden="true">↗</span></a>
      </header>

      <main className="legal-conteudo">
        <div className="legal-caminho"><a href="/">Início</a><span>/</span>{titulo}</div>
        <section className="legal-hero">
          <span className="legal-etiqueta">SISCOMERCIAL · DOCUMENTO INSTITUCIONAL</span>
          <h1>{titulo}</h1>
          <p>{descricao}</p>
          <div className="legal-metadados">
            <span><strong>Versão:</strong> 0.2 — rascunho para revisão</span>
            <span><strong>Data:</strong> 28 de outubro de 2026</span>
          </div>
        </section>

        <div className="legal-aviso">
          <strong>Documento em revisão</strong>
          <p>Este texto é uma versão inicial e deve ser conferido em relação aos fluxos reais de dados, fornecedores, controles de segurança e condições comerciais antes de ser tratado como versão definitiva.</p>
        </div>

        <article className="legal-documento">
          {secoes.map((secao) => <BlocoSecao key={secao.titulo} secao={secao} />)}
        </article>

        <section className="legal-contato">
          <div>
            <span className="legal-etiqueta">PRECISA DE AJUDA?</span>
            <h2>Entre em contato</h2>
            <p>Para dúvidas sobre este documento ou solicitações relacionadas à privacidade, fale com o responsável pelo SisComercial.</p>
          </div>
          <a href={`mailto:${EMAIL_CONTATO}`}>{EMAIL_CONTATO}</a>
        </section>
      </main>

      <footer className="legal-rodape">
        <span>© {new Date().getFullYear()} SisComercial</span>
        <nav aria-label="Documentos legais">
          <a href="/termos-de-uso">Termos de Uso</a>
          <a href="/politica-de-privacidade">Política de Privacidade</a>
          <a href={`mailto:${EMAIL_CONTATO}`}>Contato</a>
        </nav>
      </footer>
    </div>
  );
}

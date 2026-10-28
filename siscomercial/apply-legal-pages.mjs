import fs from "node:fs";
import path from "node:path";

const frontendSrc = path.resolve("frontend/src");
const appPath = path.join(frontendSrc, "App.jsx");
const cssPath = path.join(frontendSrc, "index.css");

function readRequired(file) {
  if (!fs.existsSync(file)) {
    throw new Error(`Arquivo não encontrado: ${file}. Execute este script na pasta siscomercial do projeto.`);
  }
  return fs.readFileSync(file, "utf8");
}

let app = readRequired(appPath);
let css = readRequired(cssPath);

const importOriginal = 'import { useAuth } from "./context/AuthContext.jsx";';
const importNovo = `${importOriginal}\nimport LegalPage from "./LegalPage.jsx";`;
if (!app.includes('import LegalPage from "./LegalPage.jsx";')) {
  if (!app.includes(importOriginal)) throw new Error("Não encontrei o import esperado em App.jsx; nenhum arquivo foi alterado.");
  app = app.replace(importOriginal, importNovo);
}

if (app.includes("export default function App() {")) {
  app = app.replace("export default function App() {", "function LojaApp() {");
}

const finalOriginal = `      )}
    </div>
  );
}
`;
const finalNovo = `      )}

      <footer className="rodape-legal">
        <span>© {new Date().getFullYear()} SisComercial</span>
        <nav aria-label="Links institucionais">
          <a href="/termos-de-uso">Termos de Uso</a>
          <a href="/politica-de-privacidade">Política de Privacidade</a>
          <a href="mailto:gabrielexpedi18@gmail.com">Contato</a>
        </nav>
      </footer>
    </div>
  );
}

export default function App() {
  const caminho = window.location.pathname.replace(/\/+$/, "") || "/";
  if (caminho === "/termos-de-uso") {
    return <LegalPage tipo="termos" />;
  }
  if (caminho === "/politica-de-privacidade") {
    return <LegalPage tipo="privacidade" />;
  }
  return <LojaApp />;
}
`;

if (!app.includes('return <LegalPage tipo="termos" />;')) {
  if (!app.endsWith(finalOriginal)) {
    throw new Error("O final de App.jsx não corresponde à versão esperada; nenhum arquivo foi alterado.");
  }
  app = app.slice(0, -finalOriginal.length) + finalNovo;
}

const footerCss = `
/* Links institucionais do rodapé da loja */
.rodape-legal {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18px;
  padding: 22px 5vw;
  border-top: 1px solid #e8ebf2;
  background: #fff;
  color: #8992a5;
  font-size: 11px;
}
.rodape-legal nav {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
}
.rodape-legal a {
  color: #656e82;
  text-decoration: none;
}
.rodape-legal a:hover { color: #554cc9; text-decoration: underline; }
@media (max-width: 620px) {
  .rodape-legal { align-items: flex-start; flex-direction: column; padding: 20px 18px; }
  .rodape-legal nav { gap: 12px 16px; }
}
`;
if (!css.includes(".rodape-legal")) css += `\n${footerCss}\n`;

// Só grava depois de validar os dois arquivos e todos os pontos de alteração.
fs.writeFileSync(appPath, app, "utf8");
fs.writeFileSync(cssPath, css, "utf8");
console.log("Páginas legais integradas ao frontend.");
console.log("Arquivos alterados: frontend/src/App.jsx e frontend/src/index.css");
console.log("Arquivos novos esperados: frontend/src/LegalPage.jsx e frontend/src/legal-page.css");

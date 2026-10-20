# Backend — configuração local do banco

O datasource requer três variáveis de ambiente. A aplicação falha no startup se alguma estiver ausente ou vazia:

- `DB_URL`: URL JDBC PostgreSQL, por exemplo `jdbc:postgresql://localhost:5432/siscomercial`.
- `DB_USERNAME`: usuário do banco local/de ambiente.
- `DB_PASSWORD`: senha obtida de um cofre local ou definida somente na sessão do terminal.

No PowerShell, informe os valores locais sem gravá-los em arquivos versionados:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/siscomercial'
$env:DB_USERNAME = Read-Host 'DB_USERNAME'
$env:DB_PASSWORD = Read-Host 'DB_PASSWORD'
.\mvnw.cmd spring-boot:run
```

No macOS/Linux, exporte `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` na sessão ou injete-os por um gerenciador de segredos e execute `./mvnw spring-boot:run`. Não coloque credenciais em argumentos de linha de comando, scripts versionados ou logs.

Em produção, configure as mesmas variáveis no serviço de secrets da plataforma de implantação. `DB_URL` não tem fallback para localhost. Os testes automatizados usam H2 em memória e não necessitam das credenciais PostgreSQL. A configuração OAuth Google existente ainda pode exigir suas próprias variáveis para executar o fluxo de login.

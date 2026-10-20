# Diff de revisão — SEC-001

Este arquivo reúne as alterações intencionais desta tarefa. É um diff para avaliação, não para aplicação automática: o valor removido do datasource foi substituído por `[REDACTED]`. Artefatos `backend/target` foram omitidos por serem saídas geradas e já estarem modificados antes desta execução.

```diff
diff --git a/backend/src/main/resources/application.yml b/backend/src/main/resources/application.yml
--- a/backend/src/main/resources/application.yml
+++ b/backend/src/main/resources/application.yml
@@ -13,8 +13,8 @@
   datasource:
-    url: jdbc:postgresql://localhost:5432/siscomercial
-    username: postgres
-    password: [REDACTED]
+    url: ${DB_URL}
+    username: ${DB_USERNAME}
+    password: ${DB_PASSWORD}
     driver-class-name: org.postgresql.Driver
```


```diff
diff --git a/.gitignore b/.gitignore
new file mode 100644
--- /dev/null
+++ b/.gitignore
@@ -0,0 +1,13 @@
+# Local environment files and credentials
+.env
+.env.*
+!.env.example
+backend/.env
+backend/.env.*
+!backend/.env.example
+
+# Local Spring configuration files that may contain credentials
+**/application-secrets*.yml
+**/application-secrets*.yaml
+**/application-local-secrets*.yml
+**/application-local-secrets*.yaml
```

```diff
diff --git a/backend/README.md b/backend/README.md
new file mode 100644
--- /dev/null
+++ b/backend/README.md
@@ -0,0 +1,20 @@
+# Backend — configuração local do banco
+
+O datasource requer três variáveis de ambiente. A aplicação falha no startup se alguma estiver ausente ou vazia:
+
+- `DB_URL`: URL JDBC PostgreSQL, por exemplo `jdbc:postgresql://localhost:5432/siscomercial`.
+- `DB_USERNAME`: usuário do banco local/de ambiente.
+- `DB_PASSWORD`: senha obtida de um cofre local ou definida somente na sessão do terminal.
+
+No PowerShell, informe os valores locais sem gravá-los em arquivos versionados:
+
+```powershell
+$env:DB_URL = 'jdbc:postgresql://localhost:5432/siscomercial'
+$env:DB_USERNAME = Read-Host 'DB_USERNAME'
+$env:DB_PASSWORD = Read-Host 'DB_PASSWORD'
+.\mvnw.cmd spring-boot:run
+```
+
+No macOS/Linux, exporte `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` na sessão ou injete-os por um gerenciador de segredos e execute `./mvnw spring-boot:run`. Não coloque credenciais em argumentos de linha de comando, scripts versionados ou logs.
+
+Em produção, configure as mesmas variáveis no serviço de secrets da plataforma de implantação. `DB_URL` não tem fallback para localhost. Os testes automatizados usam H2 em memória e não necessitam das credenciais PostgreSQL. A configuração OAuth Google existente ainda pode exigir suas próprias variáveis para executar o fluxo de login.
```

```diff
diff --git a/backend/src/main/java/com/siscomercial/ecommerce/config/DatabaseCredentialsValidator.java b/backend/src/main/java/com/siscomercial/ecommerce/config/DatabaseCredentialsValidator.java
new file mode 100644
--- /dev/null
+++ b/backend/src/main/java/com/siscomercial/ecommerce/config/DatabaseCredentialsValidator.java
@@ -0,0 +1,38 @@
+package com.siscomercial.ecommerce.config;
+
+import org.springframework.beans.factory.InitializingBean;
+import org.springframework.beans.factory.annotation.Value;
+import org.springframework.stereotype.Component;
+
+/** Fails startup when datasource connection settings are missing or blank. */
+@Component
+public class DatabaseCredentialsValidator implements InitializingBean {
+    private final String url;
+    private final String username;
+    private final String password;
+
+    public DatabaseCredentialsValidator(
+            @Value("${spring.datasource.url:}") String url,
+            @Value("${spring.datasource.username:}") String username,
+            @Value("${spring.datasource.password:}") String password) {
+        this.url = url;
+        this.username = username;
+        this.password = password;
+    }
+
+    @Override
+    public void afterPropertiesSet() {
+        validate(url, username, password);
+    }
+
+    static void validate(String url, String username, String password) {
+        if (isBlank(url) || isBlank(username) || isBlank(password)) {
+            throw new IllegalStateException(
+                    "Database configuration is incomplete: DB_URL, DB_USERNAME and DB_PASSWORD are required.");
+        }
+    }
+
+    private static boolean isBlank(String value) {
+        return value == null || value.isBlank();
+    }
+}
```

```diff
diff --git a/backend/src/test/java/com/siscomercial/ecommerce/config/DatabaseCredentialsValidatorTest.java b/backend/src/test/java/com/siscomercial/ecommerce/config/DatabaseCredentialsValidatorTest.java
new file mode 100644
--- /dev/null
+++ b/backend/src/test/java/com/siscomercial/ecommerce/config/DatabaseCredentialsValidatorTest.java
@@ -0,0 +1,42 @@
+package com.siscomercial.ecommerce.config;
+
+import org.junit.jupiter.api.BeforeEach;
+import org.junit.jupiter.api.Test;
+import org.springframework.boot.test.context.runner.ApplicationContextRunner;
+
+import static org.junit.jupiter.api.Assertions.assertNotNull;
+import static org.junit.jupiter.api.Assertions.assertNull;
+
+class DatabaseCredentialsValidatorTest {
+    private ApplicationContextRunner contextRunner;
+
+    @BeforeEach
+    void configurarContexto() {
+        contextRunner = new ApplicationContextRunner()
+                .withUserConfiguration(DatabaseCredentialsValidator.class);
+    }
+
+    @Test
+    void aceitaConfiguracaoCompleta() {
+        contextRunner.withPropertyValues(
+                "spring.datasource.url=jdbc:postgresql://localhost:5432/siscomercial",
+                "spring.datasource.username=usuario-local",
+                "spring.datasource.password=senha-local")
+                .run(context -> assertNull(context.getStartupFailure()));
+    }
+
+    @Test
+    void rejeitaConfiguracaoAusenteOuVaziaSemExporValores() {
+        contextRunner.run(context -> assertNotNull(context.getStartupFailure()));
+        contextRunner.withPropertyValues(
+                "spring.datasource.url=jdbc:postgresql://localhost/db",
+                "spring.datasource.username= ",
+                "spring.datasource.password=senha-local")
+                .run(context -> assertNotNull(context.getStartupFailure()));
+        contextRunner.withPropertyValues(
+                "spring.datasource.url=jdbc:postgresql://localhost/db",
+                "spring.datasource.username=usuario-local",
+                "spring.datasource.password=")
+                .run(context -> assertNotNull(context.getStartupFailure()));
+    }
+}
```

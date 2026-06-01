# Hugginn Mobile QR Code

App Android de **credencial de acesso corporativo via QR Code dinâmico**. Após autenticação biométrica, exibe um QR com token assinado (renovado a cada 10 s) que o leitor de portaria (**Heimdall**) escaneia e valida.

```
Odin Admin → Hugginn Mobile QR → [QR dinâmico] → Heimdall → Backend REST
```

Parte do ecossistema de controle de acesso SRBR (3 apps + módulo compartilhado).

---

## Documentação do ecossistema

| Documento | Conteúdo |
|---|---|
| [INTEGRATION_GUIDE.md](../INTEGRATION_GUIDE.md) | Contrato de comunicação: token, Base64Url, nonce, HMAC, vetores de teste |
| [BUILD_CICD.md](../BUILD_CICD.md) | Build, injeção de chaves HMAC, CI/CD, certificate pinning |
| [ARCHITECTURE.md](../ARCHITECTURE.md) | Arquitetura, módulo `:core-credential`, design system de Slots |

> O guia local [CLAUDE.md](CLAUDE.md) contém restrições de build, mas a seção de formato de token/nonce está **desatualizada** — prevalecem os docs do ecossistema acima.

---

## Papel deste repositório

| Aspecto | Valor |
|---|---|
| Stack | Kotlin + Jetpack Compose + MVVM + Hilt |
| minSdk / target | 26 / 34 |
| `applicationId` | `com.srbr.huginn.qr` |
| Canal de saída | QR Code dinâmico (ZXing), renovado a cada 10 s |
| Gerador de token | `QrTokenGenerator` |
| Slot visual / motion | `QrCodeComposable` / `RISE_UP` |
| Módulo compartilhado | `com.srbr.huginn:credential:0.3.0-SNAPSHOT` (mavenLocal) |
| Validação de cadastro | 100% offline (sem rede) |

---

## Build rápido

**1.** Publique o módulo compartilhado (no repo `huginn-core-credential`):

```bash
./gradlew :core-credential:publishToMavenLocal
```

**2.** Configure as chaves em `local.properties` (nunca commitar):

```properties
HUGINN_QR_HMAC_KEY=...
HUGINN_TOKEN_HMAC_KEY=...
```

> Precedência `-P → local.properties → env var`. Sem a chave, **release aborta**; debug usa `DEV_ONLY_FALLBACK_NEVER_RELEASE`. Detalhes em [BUILD_CICD.md §3](../BUILD_CICD.md#3-injeção-de-chaves-hmac).

**3.** Compile e teste:

```bash
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew testDebugUnitTest
```

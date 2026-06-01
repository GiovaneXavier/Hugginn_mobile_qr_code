# CLAUDE.md — Huginn Mobile QR Code

Este arquivo orienta o Claude Code ao trabalhar neste repositório.

## Comandos de Build e Teste

```bash
# Build
./gradlew assembleDebug          # APK de debug
./gradlew assembleRelease        # APK de release (com ProGuard)
./gradlew build                  # Todos os variants

# Testes
./gradlew test                   # Todos os unit tests
./gradlew testDebugUnitTest      # Só variant debug
./gradlew connectedAndroidTest   # Testes de instrumentação (requer emulador/device)

# Lint
./gradlew lint
```

## Configuração de Segredos

Antes de compilar, configure as chaves HMAC em `local.properties` (nunca commitar este arquivo):

```properties
HUGINN_QR_HMAC_KEY=sua_chave_aqui
HUGINN_TOKEN_HMAC_KEY=sua_chave_token_aqui
```

Sem a chave, build de **release aborta** com `GradleException`. Build de debug usa
`DEV_ONLY_FALLBACK_NEVER_RELEASE` (string explicitamente inválida para produção).
Não existem mais fallbacks de chave de produção hardcoded — ver `BUILD_CICD.md §3.3`.
Use `local.properties.example` como referência.

## Arquitetura

**Stack**: Kotlin + Jetpack Compose + MVVM + Hilt
**SDK mínimo**: 26
**Propósito**: App de credencial de acesso corporativo via **QR code dinâmico**.

### Fluxo principal

1. **Onboarding**: Usuário escaneia QR de registro emitido pelo Odin (sistema admin) → valida assinatura HMAC → salva credencial criptografada
2. **Card**: Usuário autentica com biometria → app exibe QR dinâmico (token de 30 s, renovado a cada 10 s) → Heimdall escaneia o QR

### Estrutura de pacotes

> **Modularização (Fase 1 concluída):** domínio, storage, onboarding e UI compartilhada
> foram extraídos para `com.srbr.huginn:credential:0.3.0-SNAPSHOT` (repo `huginn-core-credential`).
> Cada app contém apenas o que é específico ao seu canal de saída.

```
# Neste repo (app-específico):
core/security/
  QrTokenGenerator   Gera tokens QR: usa TokenPayload + HmacUtils do :core-credential

feature/card/
  CardViewModel      herda BaseCardViewModel; onUnlocked() → gera e exibe qrToken
  CardScreen         usa CardScreenScaffold com slot QrCodeComposable / RISE_UP

ui/components/
  QrCodeComposable   Gera Bitmap com IntArray + setPixels() (sem loop de setPixel por pixel)

di/AppModule.kt    → injeta QR_HMAC_KEY e TOKEN_HMAC_KEY via @Named para o :core-credential
MainActivity.kt    → BiometricPrompt e navegação
NavGraph.kt        → Compose Navigation

# Em :core-credential (huginn-core-credential):
  HuginnCard, QRValidator, DeviceIdentity, HmacUtils, Base64UrlCodec, NonceGenerator,
  TokenPayload, CardStorage, CardRepository, OnboardingViewModel, OnboardingScreen,
  HuginnCardComposable, BaseCardViewModel, CardScreenScaffold, ui/theme/
```

## Decisões Técnicas Importantes

| Decisão | Motivo |
|---|---|
| `java.util.Base64` URL-safe sem padding via `Base64UrlCodec` (`:core-credential`) | Compatível com JVM pura em testes; `android.util.Base64` descontinuado neste uso |
| `MessageDigest.isEqual()` para HMAC | Comparação em tempo constante; previne timing attacks |
| `SecureRandom` para nonces | `kotlin.random.Random` não é criptograficamente seguro |
| `DeviceIdentity` cached com `by lazy` | Evita SHA-256 + Settings read a cada chamada |
| `FLAG_SECURE` no Window | Impede screenshots e gravações de tela |
| `repository.hasCard()` em `Dispatchers.IO` via `LaunchedEffect` | `EncryptedSharedPreferences` não deve bloquear a main thread |
| `QrCodeComposable` usa `IntArray` + `setPixels()` | Substitui 262.144 chamadas JNI individuais de `setPixel()` |
| `hasCard()` = `contains(KEY) && loadCard() != null` | Evita falso-positivo se a chave existe mas a deserialização falha |
| `deleteCard()` remove também `KEY_NONCES` | Evita vazamento de nonces de credenciais anteriores |
| Validação offline | Toda validação de QR é local — não há chamadas de rede |
| Segredos via BuildConfig | `QR_HMAC_KEY` e `TOKEN_HMAC_KEY` injetados em compile-time via `buildConfigField` |

## Formato do Token QR

```
deviceId|employeeId|systemId|timestamp_unix|nonce.hmac-sha256-base64url
```
- `timestamp` em segundos (Unix)
- `nonce` = `NonceGenerator.generate()` → **64 bits / 11 chars Base64url** (via `Base64UrlCodec.encode(8 bytes)`)
- `hmac` = HMAC-SHA256 do payload antes do `.`, Base64url sem padding (43 chars) — via `HmacUtils.sign()`
- Chave: `BuildConfig.TOKEN_HMAC_KEY` (injetada pelo Hilt; nunca hardcoded)
- Token expirado/renovado a cada 10 s; UI auto-bloqueia após 30 s
- Fonte canônica: [INTEGRATION_GUIDE.md §2](../INTEGRATION_GUIDE.md#2-primitivas-criptográficas)

## Fluxo de Estado (Onboarding)

`OnboardingStep` é uma sealed class: `Welcome → Scanning → Validating → Error | Success`

## Fluxo de Estado (Card)

```
locked
  ↓ onBiometricSuccess()
unlocked (countdown 30 s, QR renovado a cada 10 s)
  ↓ onExpire() | onAppBackground() | countdown chega a 0
locked
```

## Testes

Organização por pacote (espelha a estrutura de produção):

```
com.srbr.huginn.core.security/
  QRValidatorTest         — payload, adulteração, HMAC, formato Base64url, vetor cross-platform
  QrTokenGeneratorTest    — formato, unicidade, timestamp, renovação

com.srbr.huginn.core.storage/
  CardStorageTest         — CRUD, FIFO de nonces (máx. 50) — usa Robolectric

com.srbr.huginn.feature.card/
  CardViewModelTest       — estados, countdown, refresh 10 s, auto-lock

com.srbr.huginn.feature.onboarding/
  OnboardingViewModelTest — fluxo completo, nonce reutilizado, permissão/câmera negada
```

**Ferramentas**: JUnit 4 + MockK + Turbine + `StandardTestDispatcher` + Robolectric (para CardStorage)

### Vetor de teste cross-platform (QRValidatorTest)

```bash
node -e "const c=require('crypto'); \
  console.log(c.createHmac('sha256','TEST_SECRET_KEY') \
  .update('1|REG|1700000000|1700003600|fixed-nonce|EMP001|Test User|SYS001') \
  .digest('base64').replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,''))"
# Resultado: eSJdyv9cvTX3kst9JbzhJJoiD_P60Svb2UhaXPgVbBE
```

## Diferença em relação ao Hugginn_mobile (NFC)

| Aspecto | Este app (QR) | Hugginn_mobile (NFC) |
|---|---|---|
| Canal de saída | QR code dinâmico na tela | NFC HCE (`HuginnHCEService`) |
| Token gerado por | `QrTokenGenerator` | `NfcTokenGenerator` |
| Renovação do token | A cada 10 s enquanto desbloqueado | Cada leitura NFC gera novo token |
| Animação | `QrCodeComposable` | `NfcRippleComposable` |
| CardViewModel unlock | Gera e exibe `qrToken` | Autoriza `HuginnHCEService` |

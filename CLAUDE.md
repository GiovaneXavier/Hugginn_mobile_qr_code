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

Sem essas propriedades, o build usa os valores padrão de desenvolvimento
(`SRBR_HUGINN_ODIN_SECRET_2024` e `SRBR_HEIMDALL_TOKEN_SECRET_2024`).
Use `local.properties.example` como referência.

## Arquitetura

**Stack**: Kotlin + Jetpack Compose + MVVM + Hilt
**SDK mínimo**: 26
**Propósito**: App de credencial de acesso corporativo via **QR code dinâmico**.

### Fluxo principal

1. **Onboarding**: Usuário escaneia QR de registro emitido pelo Odin (sistema admin) → valida assinatura HMAC → salva credencial criptografada
2. **Card**: Usuário autentica com biometria → app exibe QR dinâmico (token de 30 s, renovado a cada 10 s) → Heimdall escaneia o QR

### Estrutura de pacotes

```
core/security/     → Criptografia e modelos de domínio
  HuginnCard         Data class da credencial
  QRValidator        Valida QRs de registro (HMAC-SHA256, expiração, nonce)
  QrTokenGenerator   Gera tokens de autenticação: deviceId|empId|sysId|ts|nonce.sig
  DeviceIdentity     SHA-256(salt + ANDROID_ID), exibido como "SRBR-XXXX-YYYY"

core/storage/      → Persistência criptografada
  CardStorage        EncryptedSharedPreferences (AES-256-GCM) + rastreamento de nonces
  CardRepository     Fachada sobre CardStorage (facilita mock nos testes)

feature/onboarding/→ Fluxo de cadastro
  OnboardingViewModel  sealed class OnboardingStep: Welcome → Scanning → Validating → Error | Success
  OnboardingScreen     Câmera ML Kit, callbacks de permissão/erro da câmera

feature/card/      → Exibição da credencial
  CardViewModel      locked → (biometria) → unlocked 30 s + token renovado a cada 10 s + auto-lock
  CardScreen         DisposableEffect observa ON_STOP → onAppBackground()

ui/components/     → Composables reutilizáveis
  HuginnCardComposable  Card 3D com flip
  QrCodeComposable      Gera Bitmap com IntArray + setPixels() (sem loop de setPixel por pixel)

di/AppModule.kt    → Hilt: provê QRValidator, QrTokenGenerator, CardRepository, DeviceIdentity
MainActivity.kt    → Orquestra câmera (ML Kit), BiometricPrompt e navegação
NavGraph.kt        → Compose Navigation; passa callbacks de câmera/biometria
```

## Decisões Técnicas Importantes

| Decisão | Motivo |
|---|---|
| `Base64.URL_SAFE or NO_WRAP or NO_PADDING` | JS strip padding; ambos os lados devem produzir 43 chars sem `=` |
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
- `nonce` = `SecureRandom().nextLong() and 0xFFFFFFL` (6 hex digits)
- `hmac` = HMAC-SHA256 do payload antes do `.`, Base64url sem padding (43 chars)
- Chave: `BuildConfig.TOKEN_HMAC_KEY`
- Token expirado/renovado a cada 10 s; UI auto-bloqueia após 30 s

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

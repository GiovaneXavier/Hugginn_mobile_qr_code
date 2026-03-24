# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

Sem essas propriedades, o build usa os valores padrão de desenvolvimento (`SRBR_HUGINN_ODIN_SECRET_2024` e `SRBR_HEIMDALL_TOKEN_SECRET_2024`). Use `local.properties.example` como referência.

## Arquitetura

**Stack**: Kotlin + Jetpack Compose + MVVM + Hilt

**Propósito**: App de credencial de acesso corporativo. Fluxo principal:
1. **Onboarding**: Usuário escaneia QR de registro emitido pelo Odin (sistema de onboarding) → valida assinatura HMAC → salva credencial criptografada
2. **Card**: Usuário autentica com biometria → app exibe QR dinâmico de 30s para o Heimdall (sistema de controle de acesso) escanear

### Camadas

```
core/security/     → Criptografia e modelos de domínio (sem dependências Android pesadas)
core/storage/      → Persistência criptografada (EncryptedSharedPreferences)
feature/onboarding/→ Fluxo de cadastro (ViewModel + Screen)
feature/card/      → Exibição da credencial (ViewModel + Screen)
ui/components/     → Composables reutilizáveis (HuginnCard com flip 3D, QrCode)
di/                → Módulo Hilt (AppModule)
```

### Responsabilidades-chave

- **`QRValidator`**: Valida payload JSON dos QRs de registro (versão, expiração, HMAC-SHA256 com comparação em tempo constante para prevenir timing attacks)
- **`QrTokenGenerator`**: Gera tokens de autenticação dinâmicos — formato `deviceId|employeeId|systemId|timestamp|nonce.signature` — mesmo formato que o NFC HCE do servidor
- **`DeviceIdentity`**: ID único de device via `SHA-256(salt + ANDROID_ID)`, exibido como "SRBR-XXXX-YYYY"
- **`CardStorage`**: EncryptedSharedPreferences (AES-256-GCM) + rastreamento de nonces (máx. 50, previne replay de QR)
- **`CardRepository`**: Fachada sobre `CardStorage`, facilita mock nos testes
- **`MainActivity`**: Orquestra permissão de câmera, ML Kit scanning e BiometricPrompt; delega lógica aos ViewModels via callbacks

### Fluxo de estado (Onboarding)

`OnboardingStep` é uma sealed class: `Welcome → Scanning → Validating → Error | Success`

### Fluxo de estado (Card)

CardViewModel gerencia: locked → (biometria) → unlocked com countdown de 30s + refresh de QR a cada 10s → auto-lock ao expirar ou ir para background

## Testes

Os testes unitários cobrem os caminhos críticos:
- **`QRValidatorTest`**: Validação de payload, detecção de adulteração, verificação HMAC
- **`CardViewModelTest`**: Máquina de estados com Turbine (StateFlow), MockK para dependências, controle de tempo com `StandardTestDispatcher`
- **`OnboardingViewModelTest`**: Fluxo completo de onboarding, reutilização de nonce, estados de erro

**Ferramentas**: JUnit 4 + MockK + Turbine + `ExperimentalCoroutinesApi`

## Decisões Técnicas Importantes

- **Validação offline**: Toda validação de QR é local — não há chamadas de rede
- **Segredos via BuildConfig**: `QR_HMAC_KEY` e `TOKEN_HMAC_KEY` são injetados em compile-time via `buildConfigField` lendo `local.properties`
- **Nonces**: QRs de registro têm nonce único; nonces usados são persistidos para prevenir reutilização
- **Câmera + Biometria**: Gerenciadas na `MainActivity` (não nos ViewModels) porque requerem `ActivityResultLauncher` e `FragmentManager`
- **SDK mínimo 26**: Necessário para `EncryptedSharedPreferences` e `BiometricPrompt`

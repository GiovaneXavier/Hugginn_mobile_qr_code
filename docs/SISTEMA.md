# Huginn Mobile QR Code — Documentação do Sistema

## Sumário

1. [Visão Geral](#visão-geral)
2. [Contexto de Negócio](#contexto-de-negócio)
3. [Arquitetura](#arquitetura)
4. [Estrutura de Pacotes](#estrutura-de-pacotes)
5. [Camadas e Componentes](#camadas-e-componentes)
   - [Core / Security](#core--security)
   - [Core / Storage](#core--storage)
   - [Feature / Onboarding](#feature--onboarding)
   - [Feature / Card](#feature--card)
   - [UI / Components](#ui--components)
   - [UI / Theme](#ui--theme)
   - [Injeção de Dependências](#injeção-de-dependências)
   - [Entry Points](#entry-points)
6. [Fluxos Principais](#fluxos-principais)
   - [Cadastro (Onboarding)](#cadastro-onboarding)
   - [Acesso com Credencial (Card)](#acesso-com-credencial-card)
7. [Segurança](#segurança)
8. [Testes](#testes)
9. [Build e Configuração](#build-e-configuração)
10. [Dependências](#dependências)
11. [Integração com Sistemas Externos](#integração-com-sistemas-externos)

---

## Visão Geral

O **Huginn Mobile QR Code** é um aplicativo Android de credencial de acesso corporativo. Ele permite que colaboradores se cadastrem escaneando um QR de registro emitido pelo sistema Odin e, posteriormente, se autentiquem via biometria para exibir um QR dinâmico que o sistema Heimdall escaneia para liberar o acesso.

**Stack principal:** Kotlin · Jetpack Compose · MVVM · Hilt · CameraX · ML Kit · ZXing · EncryptedSharedPreferences

---

## Contexto de Negócio

| Ator | Papel |
|---|---|
| **Odin** | Sistema de onboarding corporativo. Emite QR de registro assinado com HMAC-SHA256 |
| **Heimdall** | Sistema de controle de acesso. Escaneia o QR dinâmico gerado pelo app para liberar passagem |
| **Huginn App** | Credencial mobile do colaborador: valida o QR do Odin, armazena os dados criptografados e gera tokens dinâmicos para o Heimdall |

O app opera **completamente offline** — toda validação é local, sem chamadas de rede.

---

## Arquitetura

```
┌─────────────────────────────────────────────────────────┐
│                      MainActivity                        │
│         (câmera · biometria · navegação)                 │
└───────────────────────┬─────────────────────────────────┘
                        │ callbacks
          ┌─────────────┴─────────────┐
          │                           │
  ┌───────▼────────┐         ┌────────▼───────┐
  │  Onboarding    │         │     Card        │
  │  ViewModel +   │         │  ViewModel +    │
  │  Screen        │         │  Screen         │
  └───────┬────────┘         └────────┬────────┘
          │                           │
  ┌───────▼───────────────────────────▼────────┐
  │               Core / Security              │
  │  QRValidator · QrTokenGenerator            │
  │  DeviceIdentity · HuginnCard               │
  └───────────────────┬────────────────────────┘
                      │
  ┌───────────────────▼────────────────────────┐
  │               Core / Storage               │
  │       CardRepository → CardStorage         │
  │       (EncryptedSharedPreferences)         │
  └────────────────────────────────────────────┘
```

**Padrão:** MVVM com estado unidirecional (UDF). Os ViewModels expõem `StateFlow<UiState>` e recebem eventos via métodos públicos. A `MainActivity` é responsável por câmera e biometria (dependências de `ActivityResultLauncher` / `FragmentManager`) e delega os callbacks aos ViewModels.

---

## Estrutura de Pacotes

```
com.srbr.huginn/
├── core/
│   ├── security/
│   │   ├── QRValidator.kt          # Validação de QR de cadastro
│   │   ├── QrTokenGenerator.kt     # Geração de token dinâmico
│   │   ├── DeviceIdentity.kt       # ID único de dispositivo
│   │   └── HuginnCard.kt           # Modelo de dados da credencial
│   └── storage/
│       ├── CardStorage.kt          # Persistência criptografada
│       └── CardRepository.kt       # Fachada para testes
├── di/
│   └── AppModule.kt                # Módulo Hilt
├── feature/
│   ├── onboarding/
│   │   ├── OnboardingViewModel.kt  # Máquina de estados de cadastro
│   │   └── OnboardingScreen.kt     # UI de cadastro
│   └── card/
│       ├── CardViewModel.kt        # Máquina de estados da credencial
│       └── CardScreen.kt           # UI da credencial
├── ui/
│   ├── components/
│   │   ├── HuginnCardComposable.kt # Card com flip 3D
│   │   └── QrCodeComposable.kt     # Renderização do QR dinâmico
│   └── theme/
│       └── Theme.kt                # Tema Material 3 escuro
├── HuginnApp.kt                    # Application (@HiltAndroidApp)
├── MainActivity.kt                 # Activity principal
└── NavGraph.kt                     # Navegação Compose
```

---

## Camadas e Componentes

### Core / Security

#### `HuginnCard`
Modelo de domínio puro (sem dependências Android). Representa a credencial do colaborador extraída do QR de registro.

| Campo | Tipo | Descrição |
|---|---|---|
| `employeeId` | String | ID do colaborador |
| `employeeName` | String | Nome completo |
| `employeeArea` | String | Área/departamento |
| `employeeRole` | String | Cargo |
| `systemId` | String | ID do sistema de acesso |
| `systemName` | String | Nome do sistema |
| `cardColor` | String | Cor do card (hex) |
| `registeredAt` | Long | Timestamp de cadastro |
| `nonce` | String | Nonce único do QR de registro |

Provê serialização `toJson()` / `fromJson()` via `JSONObject`.

---

#### `QRValidator`
Valida o payload JSON dos QRs de registro emitidos pelo Odin.

**Verificações realizadas (em ordem):**
1. Parse JSON válido
2. Campo `version` = `"v1"`
3. Campo `mode` = `"REG"`
4. Campo `expiresAt` > `System.currentTimeMillis()`
5. Assinatura HMAC-SHA256 em comparação de tempo constante (previne timing attacks)

**Entrada:** String com conteúdo do QR
**Saída:** `QRValidator.Result` — `Success(HuginnCard)` ou `Failure(reason: String)`

A chave HMAC é injetada via Hilt com `@Named("qrHmacKey")`.

---

#### `QrTokenGenerator`
Gera tokens de autenticação dinâmicos para o Heimdall.

**Formato do token:**
```
deviceId|employeeId|systemId|timestamp|nonce.signature
```

- `timestamp`: Unix millis no momento da geração
- `nonce`: 6 dígitos aleatórios (previne replay)
- `signature`: HMAC-SHA256 do payload antes do ponto

A chave HMAC é injetada via Hilt com `@Named("tokenHmacKey")`.

---

#### `DeviceIdentity`
Gera e formata o identificador único do dispositivo.

```
ID Raw   : SHA-256("SRBR_HUGINN_2024" + ANDROID_ID) → 16 chars hex
Exibição : "SRBR-XXXX-YYYY"  (ex: SRBR-A1B2-C3D4)
```

Singleton injetado pelo Hilt, recebe `Context`.

---

### Core / Storage

#### `CardStorage`
Persistência criptografada via `EncryptedSharedPreferences`.

- **Algoritmo:** AES-256-GCM (`MasterKey`)
- **Chaves:** `AES256_SIV` (chaves) e `AES256_GCM` (valores)
- **Dados armazenados:**
  - `KEY_CARD`: JSON serializado de `HuginnCard`
  - `KEY_NONCES`: lista de nonces usados (máximo 50, FIFO)
- **Anti-replay:** nonces de QRs já processados são persistidos; ao atingir 50, o mais antigo é descartado

| Método | Descrição |
|---|---|
| `saveCard(card)` | Persiste credencial |
| `loadCard()` | Lê credencial ou null |
| `hasCard()` | Verifica se há credencial |
| `deleteCard()` | Remove credencial e nonces |
| `isNonceUsed(nonce)` | Verifica reutilização |
| `markNonceUsed(nonce)` | Registra nonce como usado |

#### `CardRepository`
Fachada sobre `CardStorage`. Delega todas as operações sem lógica adicional. Facilita mock nos testes unitários.

---

### Feature / Onboarding

#### `OnboardingViewModel`

Máquina de estados para o fluxo de cadastro:

```
Welcome ──[startScan]──► Scanning ──[QR detectado]──► Validating
                              ▲                            │
                              └──[cancelar]            ┌──┴──────────┐
                                                       ▼             ▼
                                                    Success        Error
                                                                     │
                                                             [tryAgain]──► Welcome
```

**`OnboardingUiState`:**
- `step: OnboardingStep` — estado atual
- `validatingStatus: String` — mensagem de progresso
- `validatingSub: String` — sub-mensagem

**Fluxo de `onQRDetected(content)`:**
1. Transita para `Validating`
2. Valida com `QRValidator` (em `Dispatchers.Default`)
3. Verifica nonce (não reutilizado)
4. Persiste card via `CardRepository`
5. Marca nonce como usado
6. Transita para `Success`

---

#### `OnboardingScreen`
UI Compose com `AnimatedContent` para transições entre etapas.

| Etapa | Componentes |
|---|---|
| `Welcome` | Logo, texto explicativo, botão "Escanear QR de Cadastro" |
| `Scanning` | Câmera ativa (gerenciada pela MainActivity), botão cancelar |
| `Validating` | Indicador de progresso, mensagens de status |
| `Error` | Mensagem de erro, botão "Tentar Novamente" |
| `Success` | Preview do card, redirecionamento automático para Card |

---

### Feature / Card

#### `CardViewModel`

Máquina de estados para exibição e uso da credencial:

```
locked ──[biometria OK]──► unlocked (30s)
                               │
                    ┌──────────┴──────────┐
                    ▼                     ▼
              (a cada 10s)          (ao expirar /
           refresh do QR           ir ao background)
                                         │
                                         ▼
                                       locked
```

**`CardUiState`:**

| Campo | Tipo | Descrição |
|---|---|---|
| `card` | HuginnCard? | Dados da credencial |
| `displayId` | String | ID formatado "SRBR-XXXX-YYYY" |
| `isUnlocked` | Boolean | Estado de desbloqueio |
| `countdown` | Int | Segundos restantes (0–30) |
| `countdownPct` | Float | Progresso 0.0–1.0 |
| `hasCard` | Boolean | Se há credencial cadastrada |
| `qrToken` | String | Token atual para QR |

**Comportamentos:**
- `onBiometricSuccess()`: gera QR, inicia countdown de 30s, inicia refresh a cada 10s
- `onAppBackground()`: auto-lock imediato se desbloqueado
- `onExpire()`: limpa estado, volta para locked

---

#### `CardScreen`
UI Compose com:
- **Card 3D** (`HuginnCardComposable`) com flip animado
- **QR Code** (`QrCodeComposable`) com fade suave a cada refresh
- **Barra de progresso** do countdown (30s)
- **Botão de biometria** (visível apenas quando locked)
- **Label** "SRBR · HEIMDALL" no topo

---

### UI / Components

#### `HuginnCardComposable`
Card com animação de flip 3D (500ms tween, `rotationY` via `graphicsLayer`).

| Face | Conteúdo |
|---|---|
| **Locked** | Gradiente escuro, ícone de cadeado, ID mascarado, "Autentique para desbloquear" |
| **Unlocked** | Gradiente colorido (cor da credencial), nome do colaborador, chip EMV decorativo, ID do device, badge "ATIVO" |

#### `QrCodeComposable`
Renderiza o token como QR Code usando ZXing.

- Tamanho padrão: 220dp (configurável)
- Bitmap: 512×512px (`QRCodeWriter`)
- Cores: módulos brancos sobre fundo `#16213E`
- Transições: `fadeIn` (300ms) / `fadeOut` (200ms) a cada refresh

---

### UI / Theme

**Paleta de cores (`Theme.kt`):**

| Token | Valor | Uso |
|---|---|---|
| `SamsungBlue` | `#1428A0` | Primária |
| `DarkBackground` | `#0D0D1A` | Fundo principal |
| `DarkSurface` | `#16213E` | Superfícies de card |
| `MutedText` | `#9198B0` | Texto secundário |
| `SuccessGreen` | `#00C882` | Confirmações |
| `ErrorRed` | `#FF3B5C` | Erros |

Tema escuro Material 3, aplicado via `HuginnTheme()`.

---

### Injeção de Dependências

**`AppModule` (Hilt `@Module @InstallIn(SingletonComponent)`):**

| Binding | Tipo | Fonte |
|---|---|---|
| `@Named("qrHmacKey")` | String | `BuildConfig.QR_HMAC_KEY` |
| `@Named("tokenHmacKey")` | String | `BuildConfig.TOKEN_HMAC_KEY` |
| `DeviceIdentity` | Singleton | Hilt |
| `CardStorage` | Singleton | Hilt |
| `CardRepository` | Singleton | Hilt |
| `QRValidator` | Singleton | Hilt |
| `QrTokenGenerator` | Singleton | Hilt |

---

### Entry Points

#### `HuginnApp`
`Application` com `@HiltAndroidApp`. Inicializa o grafo de DI.

#### `MainActivity`
Activity principal (portrait, single-top).

**Responsabilidades:**
- Roteamento inicial: `hasCard()` → ONBOARDING ou CARD
- Permissão de câmera via `ActivityResultContracts.RequestPermission`
- Scanning de QR via CameraX + ML Kit (`ImageAnalysis`, single-thread, keeps-only-latest)
- `BiometricPrompt` com `BIOMETRIC_STRONG | DEVICE_CREDENTIAL`
- Fornece callbacks `onRequestCamera` e `onRequestBiometric` ao `NavGraph`

#### `NavGraph`
`NavHost` Compose com duas rotas:

| Rota | Composable | Transição |
|---|---|---|
| `ONBOARDING` | `OnboardingScreen` | → CARD (pop inclusive) após sucesso |
| `CARD` | `CardScreen` | — |

---

## Fluxos Principais

### Cadastro (Onboarding)

```
Usuário                App                    Odin (prévio)
  │                     │                          │
  │  Abre o app         │                          │
  │────────────────────►│  Nenhum card → Onboarding│
  │                     │                          │
  │  Toca "Escanear"    │                          │
  │────────────────────►│  Ativa câmera            │
  │                     │                          │
  │  Aponta para QR     │◄─── QR com payload ──────┤
  │                     │     assinado (Odin)       │
  │                     │                          │
  │                     │  1. Valida JSON           │
  │                     │  2. Verifica versão/modo  │
  │                     │  3. Verifica expiração    │
  │                     │  4. Verifica HMAC         │
  │                     │  5. Verifica nonce        │
  │                     │  6. Salva criptografado   │
  │                     │  7. Registra nonce        │
  │                     │                          │
  │  Card exibido       │◄──────────────────────── │
  │◄────────────────────│                          │
```

### Acesso com Credencial (Card)

```
Usuário                App                    Heimdall
  │                     │                        │
  │  Toca "Desbloquear" │                        │
  │────────────────────►│  BiometricPrompt        │
  │  Autentica          │                        │
  │────────────────────►│                        │
  │                     │  Gera token HMAC        │
  │                     │  Exibe QR (30s)         │
  │◄────────────────────│                        │
  │                     │  ← a cada 10s →         │
  │                     │  Novo token + novo QR   │
  │                     │                        │
  │  Aproxima do leitor │                        │
  │────────────────────►│◄────scan QR────────────┤
  │                     │                        │
  │  (30s esgotados)    │                        │
  │                     │  Auto-lock              │
  │◄────────────────────│                        │
```

---

## Segurança

| Mecanismo | Implementação | Proteção |
|---|---|---|
| **Validação HMAC** | SHA-256 com comparação em tempo constante | Impede QR falsificados |
| **Nonces únicos** | Persistência de até 50 nonces | Impede replay de QR de cadastro |
| **Token dinâmico** | Timestamp + nonce aleatório + HMAC | Impede replay de QR de acesso |
| **Storage criptografado** | AES-256-GCM (EncryptedSharedPreferences) | Dados protegidos em repouso |
| **Biometria** | BiometricPrompt (BIOMETRIC_STRONG ou device credential) | Gating de acesso ao QR |
| **Auto-lock** | 30s expiration + app background detection | Exposição mínima do QR |
| **ID do device** | SHA-256(salt + ANDROID_ID) | Rastreabilidade sem PII direta |
| **Sem backup** | `allowBackup=false` + data extraction rules | Sem vazamento por backup nuvem |
| **Segredos em BuildConfig** | Chaves HMAC injetadas em compile-time | Segredos fora do código-fonte |
| **Sem rede** | Validação 100% offline | Elimina vetor de ataque de rede |

---

## Testes

### Cobertura

| Arquivo de Teste | Alvo | Casos |
|---|---|---|
| `QRValidatorTest` | `QRValidator` | JSON inválido, versão errada, modo errado, expirado, adulterado, chave errada, HMAC correto |
| `CardViewModelTest` | `CardViewModel` | Estado inicial, biometria OK, expiração, background, countdown, auto-lock, refresh de QR |
| `OnboardingViewModelTest` | `OnboardingViewModel` | Transições de estado, QR válido, QR inválido, nonce reutilizado, retry |

### Ferramentas

| Biblioteca | Versão | Uso |
|---|---|---|
| JUnit 4 | 4.13.2 | Framework base |
| MockK | 1.13.9 | Mocking de dependências |
| Turbine | 1.0.0 | Asserções de `StateFlow` |
| `kotlinx-coroutines-test` | 1.7.3 | `StandardTestDispatcher`, controle de tempo |
| `ExperimentalCoroutinesApi` | — | `advanceTimeBy`, `runTest` |

### Como executar

```bash
./gradlew testDebugUnitTest          # Unit tests do variant debug
./gradlew test                       # Todos os unit tests
./gradlew connectedAndroidTest       # Instrumentação (requer emulador/device)
```

---

## Build e Configuração

### Requisitos

| Item | Valor |
|---|---|
| `compileSdk` | 34 |
| `minSdk` | 26 (EncryptedSharedPreferences + BiometricPrompt) |
| `targetSdk` | 34 |
| Kotlin | 1.9.23 |
| Java / JVM target | 17 |
| Compose Compiler | 1.5.8 |

### Configuração de Segredos

Crie o arquivo `local.properties` (nunca commitar) com base em `local.properties.example`:

```properties
sdk.dir=C\:\\Users\\<usuario>\\AppData\\Local\\Android\\Sdk
HUGINN_QR_HMAC_KEY=<chave_hmac_odin>
HUGINN_TOKEN_HMAC_KEY=<chave_hmac_heimdall>
```

Se omitidos, o build usa as chaves de desenvolvimento padrão.

### Comandos de Build

```bash
./gradlew assembleDebug     # APK de debug
./gradlew assembleRelease   # APK de release (ProGuard + resource shrinking)
./gradlew build             # Todos os variants
./gradlew lint              # Análise estática
```

### Release

O build de release aplica ProGuard (minificação de código) e resource shrinking. Regras de ProGuard são definidas em `app/proguard-rules.pro`.

---

## Dependências

### Produção

| Grupo | Biblioteca | Versão |
|---|---|---|
| **AndroidX Core** | core-ktx | 1.12.0 |
| **AndroidX AppCompat** | appcompat | 1.6.1 |
| **Lifecycle** | lifecycle-runtime-ktx, viewmodel-ktx, viewmodel-compose | 2.7.0 |
| **Compose BOM** | compose-bom | 2024.02.00 |
| **Compose** | ui, material3, animation, foundation | (BOM) |
| **Activity** | activity-compose | 1.8.2 |
| **Navigation** | navigation-compose | 2.7.6 |
| **Hilt** | hilt-android, hilt-navigation-compose | 2.50 / 1.1.0 |
| **Biometria** | biometric | 1.1.0 |
| **Segurança** | security-crypto | 1.1.0-alpha06 |
| **QR (geração)** | zxing:core | 3.5.3 |
| **CameraX** | camera-core, camera2, lifecycle, view | 1.3.1 |
| **ML Kit** | barcode-scanning | 17.2.0 |
| **Coroutines** | kotlinx-coroutines-android | 1.7.3 |
| **Splash Screen** | core-splashscreen | 1.0.1 |

### Testes

| Biblioteca | Versão |
|---|---|
| junit | 4.13.2 |
| kotlinx-coroutines-test | 1.7.3 |
| mockk | 1.13.9 |
| turbine | 1.0.0 |
| androidx.test:core | 1.5.0 |
| espresso-core | 3.5.1 |

---

## Integração com Sistemas Externos

### Odin (Sistema de Onboarding)

O Odin emite QR Codes de registro com o seguinte payload JSON:

```json
{
  "version": "v1",
  "mode": "REG",
  "employeeId": "...",
  "employeeName": "...",
  "employeeArea": "...",
  "employeeRole": "...",
  "systemId": "...",
  "systemName": "...",
  "cardColor": "#RRGGBB",
  "expiresAt": 1700000000000,
  "nonce": "abc123",
  "hmac": "<sha256-hex>"
}
```

A assinatura HMAC cobre todos os campos exceto `hmac`. O app valida o payload localmente com a chave `HUGINN_QR_HMAC_KEY`.

### Heimdall (Sistema de Controle de Acesso)

O Heimdall escaneia o QR dinâmico do app. O token segue o mesmo formato gerado pelo NFC HCE do servidor:

```
<deviceId>|<employeeId>|<systemId>|<timestamp>|<nonce>.<hmacSignature>
```

O Heimdall valida a assinatura HMAC com a chave `HUGINN_TOKEN_HMAC_KEY` e verifica `timestamp` para rejeitar tokens expirados.

---

*Documentação gerada em 2026-03-23. Para atualizações, consulte o CLAUDE.md do projeto.*

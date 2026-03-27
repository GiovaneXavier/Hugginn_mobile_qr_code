# Roadmap — Huginn Mobile QR Code

## P0 — Crítico (bloqueia uso em produção)

### Gestão de chaves HMAC
As chaves `QR_HMAC_KEY` e `TOKEN_HMAC_KEY` têm fallback hardcoded para dev. Em produção:
- Definir processo de rotação de chave (invalida todos os QRs ativos, requer redistribuição para apps)
- Documentar procedimento de emergência para revogação imediata

### Testes instrumentados de câmera
Os unit tests cobrem a lógica, mas o fluxo físico (câmera ML Kit lendo QR real) nunca é exercido por um test runner. Adicionar ao menos 1 teste instrumentado de fumaça.

---

## P1 — Alta prioridade (segurança e robustez)

### Splash screen com `core-splashscreen`
`installSplashScreen()` deve ser chamado antes de `setContent`. Atualmente o `LaunchedEffect` de Q32 deixa a tela preta por alguns frames enquanto `repository.hasCard()` roda em IO. Integrar `androidx.core:core-splashscreen` e manter o splash ativo até `startDestination != null`.

### Limite de tentativas biométricas
`BiometricPrompt.onAuthenticationFailed()` está vazio. Adicionar contador com bloqueio temporário após N falhas consecutivas.

---

## P2 — Médio prazo (qualidade e experiência)

### Acessibilidade do QR code
`QrCodeComposable` não tem `contentDescription`. Leitores de tela não conseguem descrever o QR. Adicionar descrição semântica com as informações da credencial (nome, sistema, validade).

### Persistência de estado de erro em rotação de tela
`OnboardingStep.Error` é recriado ao rotacionar a tela. Usar `SavedStateHandle` no `OnboardingViewModel` para persistir a mensagem de erro.

### Monitoramento e observabilidade
Adicionar logging estruturado: erros de câmera, tentativas de validação de QR rejeitadas, renovações de token.

---

## P3 — Futuro / nice-to-have

### Multi-credencial
`CardStorage` suporta apenas 1 `HuginnCard`. Para funcionários com acesso a múltiplos sistemas, implementar lista de credenciais com alternância de ativo.

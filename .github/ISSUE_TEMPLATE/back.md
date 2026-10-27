---
name: Código nativo (Java)
about: Criar ou ajustar uma classe nativa do aplicativo (ex.: Config, PdfHandler, CameraEngine)
title: "Ajustar "
labels: nativo
---

## Tipo

- [ ] Bug
- [ ] Tela (UI)
- [X] Código nativo (Java)
- [ ] Integração (WebView/DMS)
- [ ] Outros
- [ ] Documentação

## Classe: [Nome da Classe]

[Descrição breve do que a classe deve fazer]

### Métodos / responsabilidades

-

### Convenções do projeto

- Arquivo: `app/src/main/java/com/dms/webview/[Nome].java`
- Pacote: `com.dms.webview`
- Java 17, SDK mínimo API 24, SDK alvo API 34
- Configurações persistidas via `SharedPreferences` (ver `Config.java`)
- Arquivos gerados ficam em `Webview/` (logs) ou `Webview/tmp/` (PDFs)
- Permissões em runtime quando necessário (ex.: `CAMERA`)

### Checklist

- [ ] Arquivo `[Nome].java` criado no pacote `com.dms.webview`
- [ ] Configurações lidas/gravadas via `Config` (sem `SharedPreferences` solto)
- [ ] Permissões em runtime tratadas (se aplicável)
- [ ] Erros tratados sem derrubar o app
- [ ] `./gradlew assembleDebug` sem erro

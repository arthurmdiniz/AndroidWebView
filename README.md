# Webview Nativo — Android WebView

Aplicativo Android com WebView nativo para acesso ao DMS.

## Arquitetura

- **Linguagem:** Java 17
- **SDK mínimo:** API 24 (Android 7.0)
- **SDK alvo:** API 34
- **Plugin:** Android Gradle Plugin 8.2.2
- **Engine:** Android WebView (nativa do sistema)
- **Configuração:** SharedPreferences

## Estrutura do Projeto

```
AndroidWebView/
├── app/
│   ├── src/main/
│   │   ├── java/com/dms/webview/
│   │   │   ├── MainActivity.java        # Activity principal com abas
│   │   │   ├── WebViewTab.java          # Wrapper de WebView com suporte a abas
│   │   │   ├── SettingsActivity.java    # Configurações (URL, network log)
│   │   │   ├── Config.java             # SharedPreferences (URL, network logging)
│   │   │   ├── PdfHandler.java         # Download e impressão de PDFs (Webview/tmp/)
│   │   │   ├── NetworkLogger.java      # Log de requisições de rede (Webview/)
│   │   │   ├── CrashLogger.java        # Captura de exceções não tratadas (Webview/)
│   │   │   └── CameraActivity.java     # Câmera in-app com foco no toque (upload de arquivo)
│   │   ├── assets/
│   │   │   └── help.html                # Página interna de ajuda (1ª config + botão "?")
│   │   ├── res/
│   │   │   ├── layout/
│   │   │   │   ├── activity_main.xml    # Toolbar, tabs, container WebView
│   │   │   │   └── activity_settings.xml
│   │   │   ├── values/
│   │   │   │   ├── themes.xml           # Tema roxo (#411E5A)
│   │   │   │   ├── strings.xml
│   │   │   │   └── colors.xml
│   │   │   ├── menu/main_menu.xml      # Nova aba, Sites, Configurações
│   │   │   ├── drawable/               # Ícones (focus_ring.xml — indicador de foco)
│   │   │   ├── mipmap/                 # Launcher icons
│   │   │   └── xml/network_security_config.xml
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
├── gradle.properties
├── local.properties
├── gradlew
└── gradlew.bat
```

## Funcionalidades

- **Página de ajuda** — na 1ª abertura (sem URL configurada) mostra `assets/help.html`
  com o passo a passo de configuração; botão **"?"** na barra superior reabre a qualquer
  momento (inclui data de compilação via `BuildConfig.BUILD_TIME`)
- **Múltiplas abas** — abas dinâmicas com scroll horizontal
- **Menu de Sites** — navegação rápida entre os módulos do DMS
- **Popup em aba** — `window.open()` redirecionado para nova aba
- **Ctrl+click / middle-click** — abre links em nova aba via JavaScript Interface
- **Blob PDF** — blob URLs de PDF são detectadas e abertas no visualizador de PDF do Android
- **Cache limpo** — limpa cookie, DOM storage e banco de dados SQL na inicialização
- **Permissão de câmera** — solicita permissão runtime `CAMERA` ao acessar `getUserMedia()`
- **Câmera in-app** — o `<input type="file">` abre a `CameraActivity` nativa para tirar a foto
- **Foco no toque** — toque no preview define áreas de foco e de medição (`Camera.Area`),
  anel indicador verde/vermelho, volta ao foco contínuo após ~2,5s; se houver foco pendente,
  a captura espera o resultado do autofoco
- **PDF handler** — download, visualização e impressão via `PdfRenderer`, salvos em `Webview/tmp/`
- **Network Logger** — intercepta XHR/fetch via script injetado, salva em `Webview/network_<date>.log`
- **Crash Logger** — captura exceções não tratadas e salva em `Webview/crash_<timestamp>.log`
- **Configurações** — URL inicial configurável e toggle de network log
- **Tema personalizado** — roxo (#411E5A)
- **Deep links** — abre URLs HTTP/HTTPS diretamente (intent filter)

## Compilar

```bash
cd AndroidWebView
./gradlew assembleDebug
```

O APK será copiado automaticamente para `publish/Webview-Nativo.apk`.

Para release:
```bash
./gradlew assembleRelease
```

## Requisitos

- Android Studio ou JDK 17+
- Android SDK 34

## Sites

Os sites disponíveis no menu são definidos no array `SITE_NAMES` em `MainActivity.java:37`.

## Documentação

- https://developer.android.com/reference/android/webkit/WebView
- https://developer.android.com/guide/topics/connectivity/httpurlconnection

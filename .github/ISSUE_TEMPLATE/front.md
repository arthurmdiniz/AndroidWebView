---
name: Tela (UI)
about: Criar ou ajustar uma tela ou elemento visual do aplicativo
title: "Ajustar tela de "
labels: ui
---

## Tipo

- [ ] Bug
- [X] Tela (UI)
- [ ] Código nativo (Java)
- [ ] Integração (WebView/DMS)
- [ ] Outros
- [ ] Documentação

## Tela: [Nome da Tela]

[Descrição breve do que a tela deve fazer]

### Requisitos

-

### Convenções do projeto

- Layout: `app/src/main/res/layout/[nome]_activity.xml` (ex.: `activity_settings.xml`)
- Textos em `res/values/strings.xml`, nunca hardcoded
- Cores em `res/values/colors.xml` (tema roxo `#411E5A`)
- Activity em `app/src/main/java/com/dms/webview/`
- Declarar a Activity em `AndroidManifest.xml`

### Checklist

- [ ] Layout criado/atualizado em `res/layout/`
- [ ] Textos extraídos para `strings.xml`
- [ ] Activity declarada no `AndroidManifest.xml` (se nova)
- [ ] Funciona em tela pequena e em orientação retrato/paisagem
- [ ] `./gradlew assembleDebug` sem erro

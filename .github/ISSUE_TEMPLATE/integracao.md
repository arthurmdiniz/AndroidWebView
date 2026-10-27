---
name: Integração
about: Conectar o WebView, o JavaScript Interface ou um fluxo nativo ao DMS
title: "Integrar "
labels: integracao
---

## Tipo

- [ ] Bug
- [ ] Tela (UI)
- [ ] Código nativo (Java)
- [X] Integração (WebView/DMS)
- [ ] Outros
- [ ] Documentação

## Integração: [Descrição]

[Descreva o fluxo entre o app nativo e o site do DMS]

### Componentes envolvidos

- Activity/classe nativa:
- Página ou módulo do DMS:
- Interface JavaScript (se houver):

### Requisitos

- [ ] Carregar a URL configurada em `Config`
- [ ] Abrir links em nova aba quando necessário
- [ ] Tratar downloads/PDFs via `PdfHandler`
- [ ] Tratar `<input type="file">` via câmera in-app (se aplicável)

### Checklist

- [ ] Fluxo testado no WebView com a URL configurada
- [ ] Cookies e cache não afetam o resultado (cache limpo na inicialização)
- [ ] Requisições registradas pelo `NetworkLogger` quando ativo
- [ ] Permissões e `network_security_config.xml` revisados (se aplicável)
- [ ] `./gradlew assembleDebug` sem erro

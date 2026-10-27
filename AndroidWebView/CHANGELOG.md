# Changelog

## 23/10/2026

### Funcionalidades
- Configurações: opção "Qualidade da foto" (Baixa, Média, Alta; padrão: Alta) para definir a compressão JPEG
- CameraActivity: captura em maior resolução suportada pelo sensor
- CameraActivity: botão "VOLTAR" no canto superior esquerdo da tela de captura
- Câmera: motor moderno (Camera2, `Camera2Activity`) selecionável em Configurações, com validação do nível de hardware e recomendação automática (`CameraEngine`)
- Configurações: exibe o nível de hardware detectado e desabilita o motor moderno quando o aparelho não suporta

## 16/10/2026

### Funcionalidades
- Configurações: opção "Botão de captura na lateral direita" para posicionar o botão CAPTURAR na lateral da tela da câmera (padrão: desmarcado, mantém o botão central inferior)

## 28/07/2026

### Correções
- NetworkLogger: implementada rotação de arquivos de log ao atingir 50MB (cria _1.log, _2.log, etc.)
- CameraActivity: adicionado tratamento de erro (try-catch) no takePicture() para evitar crash em tablets
- CameraActivity: adicionada verificação camera != null no onResume()

### Funcionalidades
- Menu "Versão": exibe versão e data/hora da compilação (formato DDMMYYYY.HHmm)
- Build timestamp automático via BuildConfig (timezone forçado America/Sao_Paulo)
- Habilitado buildFeatures.buildConfig no build.gradle

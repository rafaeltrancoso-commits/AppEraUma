# Geração assíncrona de histórias

O aplicativo novo usa `POST /api/families/{familyId}/story-generations`. A resposta é imediata e contém uma história persistida com um destes estados: `PENDENTE`, `PROCESSANDO_TEXTO`, `PROCESSANDO_IMAGENS`, `CONCLUIDA`, `CONCLUIDA_COM_FALHAS` ou `ERRO`.

O corpo aceita `characterIds` na ordem de protagonismo (de 1 a 3 IDs), `otherCharacters` como texto livre e `idempotencyKey` para impedir duplicação por reenvio. O endpoint legado `POST /api/families/{familyId}/stories/generate` continua síncrono para compatibilidade com versões publicadas do aplicativo.

## Consulta e recuperação

- `GET /api/stories/{storyId}` retorna o estado do texto, personagens e estados individuais das imagens.
- `POST /api/stories/{storyId}/retry` refaz uma geração de texto com estado `ERRO`.
- `POST /api/story-images/{imageId}/retry` refaz somente uma imagem com falha.

Os estados ficam no PostgreSQL. Executores locais fazem o trabalho sem manter transação aberta durante chamadas externas; heartbeats impedem a retomada de um trabalho ainda ativo e um agendador recupera trabalhos realmente interrompidos há mais de cinco minutos. As migrations correspondentes são `V017__add_async_story_characters_and_push.sql` e a correção incremental `V018__harden_async_story_processing.sql`.

Limites de tentativas:

- `APP_STORY_MAX_ATTEMPTS`, padrão `3`.
- `APP_STORY_IMAGE_MAX_ATTEMPTS`, padrão `3`.

## Push de conclusão

- `POST /api/push-tokens` registra ou atualiza o Expo push token autenticado do aparelho.
- `DELETE /api/push-tokens/{deviceId}` desativa o aparelho atual.
- A notificação informa apenas que a história está pronta e envia `storyId` nos dados; ao tocar, o app busca a história autenticada.

Para entrega real, configure as credenciais APNs e FCM no projeto EAS e use development build/TestFlight ou build de produção em aparelho físico. O Expo Go não é a validação final deste fluxo. Tokens rejeitados como `DeviceNotRegistered` são desativados pelo backend.

## Builds EAS

```powershell
cd mobile
npx eas build --platform android --profile testing
npx eas build --platform ios --profile testflight
```

O áudio usa `expo-speech` com a sessão de áudio de `expo-audio` configurada para reproduzir no modo silencioso do iOS. A confirmação final deve ser feita em iPhone físico via TestFlight, alternando a chave lateral para silencioso antes de iniciar a narração.

## Limitações conhecidas

- Consistência visual usa fichas textuais canônicas; ainda não envia foto de referência nem fixa seed entre imagens.
- Entrega de push e áudio no modo silencioso dependem de credenciais EAS/APNs/FCM e precisam de validação em aparelho físico.
- O worker é persistente no banco e recuperável, mas roda dentro da aplicação Spring; não há um broker externo dedicado.
- O backend trata erros imediatos dos push tickets. Uma evolução futura deve persistir o ID do ticket e consultar o push receipt posteriormente para detectar erros tardios de APNs/FCM.

# EraUma

**EraUma â€” Momentos que viram histÃ³rias**

AplicaÃ§Ã£o infantil/familiar. As Fases 1 e 2 entregam cadastro, autenticaÃ§Ã£o, famÃ­lias, crianÃ§as e Momentos. A Fase 3 adiciona **HistÃ³rias e Biblioteca**. A Fase 4 permite geraÃ§Ã£o real de histÃ³rias via OpenAI API, mantendo fallback mock.

Fluxo principal validado:

`Abrir app â†’ Criar conta â†’ Criar famÃ­lia â†’ Cadastrar crianÃ§a â†’ Home â†’ Momentos â†’ Criar HistÃ³ria â†’ Biblioteca`

NÃ£o hÃ¡ integraÃ§Ã£o com n8n, geraÃ§Ã£o de imagens, narraÃ§Ã£o, pagamentos, notificaÃ§Ãµes ou compartilhamento social nesta fase.

## Stack

- Backend: Java 21, Spring Boot, Maven Wrapper, Spring Web, Spring Security, Bean Validation, Spring Data JPA, Flyway e PostgreSQL.
- Mobile: React Native, Expo e TypeScript.
- Infra local: Docker Compose com PostgreSQL.
- Storage de fotos: filesystem local via abstraÃ§Ã£o `FileStorageService`.
- GeraÃ§Ã£o de histÃ³rias: contrato `StoryGenerator` com providers `MockStoryGenerator` e `OpenAIStoryGenerator`.

## Estrutura

```text
AppEraUma/
â”œâ”€â”€ backend/
â”œâ”€â”€ mobile/
â”œâ”€â”€ storage/
â”œâ”€â”€ docker-compose.yml
â”œâ”€â”€ .env.example
â”œâ”€â”€ .gitignore
â””â”€â”€ README.md
```

## VariÃ¡veis de ambiente

Copie `.env.example` para `.env` na raiz e ajuste:

```text
POSTGRES_DB=erauma
POSTGRES_USER=erauma
POSTGRES_PASSWORD=senha-local
POSTGRES_PORT=5433
JWT_SECRET=secret-local-longo-com-pelo-menos-32-caracteres
RESEND_API_KEY=
APP_EMAIL_FROM=EraUma <noreply@erauma.app.br>
APP_PASSWORD_RESET_URL=https://erauma.app.br/reset-password
APP_EMAIL_TIMEOUT_SECONDS=10
EXPO_PUBLIC_API_URL=http://localhost:8080/api
APP_STORAGE_ROOT=storage
MOMENT_MAX_PHOTOS=10
MOMENT_MAX_PHOTO_SIZE_MB=10
```

NÃ£o versionar `.env` nem arquivos em `storage/`.

## PostgreSQL

```bash
docker compose --env-file .env up -d postgres
```

## Iniciar ambiente local

Use o script da raiz para configurar Java, Docker, PostgreSQL e backend em uma unica execucao:

```powershell
cd "D:\Developer RR Sistemas\AppEraUma"
.\start-local.ps1
```

Requisitos:

- JDK 21 ou superior. No ambiente local atual o script procura o JDK 22 em `C:\Program Files\Java\jdk-22`.
- Docker Desktop instalado. O script adiciona `C:\Program Files\Docker\Docker\resources\bin` ao `PATH` apenas para a sessao atual.
- Arquivo `.env` na raiz do projeto.
- PostgreSQL publicado em `localhost:5433`.
- Backend publicado em `localhost:8080`.

Se `.env` nao existir, crie a partir do exemplo e ajuste os valores locais:

```powershell
Copy-Item .env.example .env
```

Nao envie `.env`, senhas, JWT, chaves OpenAI ou qualquer segredo ao Git. O backend usa o perfil `local` e carrega as credenciais do banco pelo script. Depois que ele iniciar, confirme o health em:

```text
http://localhost:8080/actuator/health
```

## Executar testes

```powershell
cd "D:\Developer RR Sistemas\AppEraUma"
.\test-local.ps1
```

O script configura Java e Docker da mesma forma que o ambiente local, sobe o PostgreSQL sem apagar volume e executa `backend\mvnw.cmd clean test`. Ele nao inicia o mobile, nao publica build e retorna codigo diferente de zero se os testes falharem.

## Backend

```bash
cd backend
./mvnw test
./mvnw spring-boot:run
```

No Windows:

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Health check:

```bash
curl http://localhost:8080/actuator/health
```

## Mobile

```bash
cd mobile
npm install
npm run typecheck
npm run lint
npx expo start
```

Configure `EXPO_PUBLIC_API_URL` conforme o ambiente:

- Android Emulator: `http://10.0.2.2:8080/api`.
- iOS Simulator: `http://localhost:8080/api`.
- Dispositivo fÃ­sico: `http://IP_DA_MAQUINA:8080/api`.

PersistÃªncia de sessÃ£o:

- Android/iOS continuam usando `expo-secure-store`.
- Expo Web usa `localStorage` apenas para Beta/desenvolvimento local, com acesso defensivo quando o navegador nÃ£o disponibiliza storage.

## Gerando APK Beta Android

O APK executado em um celular fÃ­sico nÃ£o consegue acessar o backend do computador usando `localhost`. Para Beta/local, use o IP do computador na mesma rede.

1. Suba o PostgreSQL e o backend:

```powershell
docker compose --env-file .env up -d postgres
cd backend
.\mvnw.cmd spring-boot:run
```

2. Descubra o IP local do computador:

```powershell
ipconfig
```

Use o IPv4 da interface conectada Ã  mesma rede do Android. Exemplo temporÃ¡rio para este ambiente: `192.168.0.6`.

3. Configure a API antes de testar ou gerar o build:

```powershell
cd mobile
$env:EXPO_PUBLIC_API_URL="http://IP_DO_PC:8080/api"
```

Para Web local, mantenha `EXPO_PUBLIC_API_URL=http://localhost:8080/api`. Para Android fÃ­sico, use `http://IP_DO_PC:8080/api`.

4. Teste o backend pelo navegador do celular:

```text
http://IP_DO_PC:8080/actuator/health
```

5. Autentique no Expo/EAS, se ainda nÃ£o estiver autenticado:

```powershell
npx eas-cli login
```

6. Gere o APK Beta instalÃ¡vel diretamente:

```powershell
npx eas-cli build --platform android --profile preview
```

O profile `preview` em `mobile/eas.json` usa `distribution: internal` e `android.buildType: apk`, portanto o artefato esperado Ã© APK, nÃ£o AAB.

7. Ao fim do build, baixe o APK pelo link exibido pelo EAS.

8. Instale o APK no Android e permita instalaÃ§Ã£o de fonte externa se o sistema solicitar.

ObservaÃ§Ãµes:

- BETA LOCAL em 19/08/2026: notebook `192.168.0.6`, API `http://192.168.0.6:8080/api` e health `http://192.168.0.6:8080/actuator/health`.
- Essa configuraÃ§Ã£o Ã© temporÃ¡ria para instalaÃ§Ã£o direta em Android fÃ­sico na mesma rede.
- O backend local usa HTTP; o Android Beta estÃ¡ configurado com `usesCleartextTraffic` para permitir testes locais.
- ProduÃ§Ã£o futura deve usar HTTPS e pode remover a liberaÃ§Ã£o de HTTP claro.
- O app solicita apenas permissÃµes de leitura de imagens para seleÃ§Ã£o/upload de fotos de Momentos; nÃ£o solicita cÃ¢mera.

## Migrations

- `V001__create_app_user.sql`
- `V002__create_family.sql`
- `V003__create_family_member.sql`
- `V004__create_child_profile.sql`
- `V005__create_moment.sql`
- `V006__create_moment_child.sql`
- `V007__create_moment_participant.sql`
- `V008__create_moment_photo.sql`
- `V009__create_story.sql`
- `V010__create_story_chapter.sql`

## Endpoints principais

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/families`
- `GET /api/families/me`
- `POST /api/families/{familyId}/children`
- `GET /api/families/{familyId}/children`
- `GET /api/children/{childId}`
- `PUT /api/children/{childId}`
- `GET /actuator/health`

Todas as rotas `/api/**`, exceto cadastro e login, exigem JWT Bearer.

## Recuperacao de senha por e-mail

O fluxo de recuperacao usa:

- `POST /api/auth/forgot-password`
- `POST /api/auth/reset-password`
- `PasswordResetToken`
- `EmailService`

Nos perfis `local` e `test`, o projeto usa `LoggingEmailService`. No perfil `prod`, o envio real usa a API do Resend por `ResendEmailService`.

Variaveis obrigatorias em producao:

```text
RESEND_API_KEY=
APP_EMAIL_FROM=EraUma <noreply@erauma.app.br>
APP_PASSWORD_RESET_URL=https://erauma.app.br/reset-password
APP_EMAIL_TIMEOUT_SECONDS=10
```

Nao coloque `RESEND_API_KEY` no codigo, YAML, app mobile, `eas.json` ou README com valor real. Configure a variavel diretamente no Railway.

Configuracao do dominio no Resend:

1. Adicione o dominio `erauma.app.br` no painel do Resend.
2. Copie manualmente os registros DNS exibidos pelo Resend para o provedor DNS do dominio. Normalmente incluem SPF/TXT, DKIM/CNAME ou TXT e, quando solicitado, DMARC/TXT.
3. Aguarde o Resend marcar o dominio como verificado.
4. Configure `APP_EMAIL_FROM` com um remetente do dominio verificado, por exemplo `EraUma <noreply@erauma.app.br>`.
5. Configure `APP_PASSWORD_RESET_URL` para a pagina/tela que recebe o parametro `token`, por exemplo `https://erauma.app.br/reset-password`.

Procedimento de teste:

1. Em ambiente de homologacao/producao, configure as variaveis no Railway.
2. Confirme `GET https://api.erauma.app.br/api/actuator/health`.
3. Solicite recuperacao para um e-mail cadastrado.
4. Confirme que o e-mail chega sem expor token em logs.
5. Abra o link recebido e redefina a senha.
6. Confirme que o token nao pode ser reutilizado.

## Momentos

Um Momento pertence Ã  famÃ­lia e pode envolver nenhuma, uma ou vÃ¡rias crianÃ§as, alÃ©m de participantes livres sem conta no aplicativo.

- `POST /api/families/{familyId}/moments`
- `GET /api/families/{familyId}/moments?page=0&size=20`
- `GET /api/families/{familyId}/moments?childId={uuid}`
- `GET /api/families/{familyId}/moments?favorite=true`
- `GET /api/moments/{momentId}`
- `PUT /api/moments/{momentId}`
- `PATCH /api/moments/{momentId}/favorite`
- `DELETE /api/moments/{momentId}`
- `POST /api/moments/{momentId}/photos`
- `GET /api/moment-photos/{photoId}/content`
- `DELETE /api/moment-photos/{photoId}`

Upload de foto via `curl`:

```bash
curl -H "Authorization: Bearer TOKEN" \
  -F "files=@foto.png;type=image/png" \
  http://localhost:8080/api/moments/MOMENT_ID/photos
```

## Fotos

- O PostgreSQL guarda somente metadados e `storage_key`.
- Arquivos ficam por padrÃ£o em `../storage/moments`.
- Tipos aceitos: `image/jpeg`, `image/png`, `image/webp`.
- Limites padrÃ£o: `10 MB` por foto e `10` fotos por momento.
- O nome original nunca Ã© usado como caminho fÃ­sico.
- Fotos nÃ£o possuem URL pÃºblica; o endpoint de conteÃºdo valida autenticaÃ§Ã£o e pertencimento Ã  famÃ­lia.

## HistÃ³rias

HistÃ³rias pertencem Ã  famÃ­lia, sÃ£o vinculadas a uma crianÃ§a e podem ter um Momento de origem. A geraÃ§Ã£o usa `StoryGenerator`, com provider configurÃ¡vel entre `mock` e `openai`.

Enums:

- `StoryStyle`: `ADVENTURE`, `FUNNY`, `EDUCATIONAL`, `FANTASY`, `BEDTIME`.
- `StoryLength`: `SHORT`, `MEDIUM`, `LONG`.
- `GenerationType`: `MOCK`, `AI`.

Endpoints:

- `POST /api/families/{familyId}/stories/generate`
- `GET /api/families/{familyId}/stories?page=0&size=20`
- `GET /api/families/{familyId}/stories?childId={uuid}`
- `GET /api/families/{familyId}/stories?favorite=true`
- `GET /api/families/{familyId}/stories?style=ADVENTURE`
- `GET /api/stories/{storyId}`
- `PATCH /api/stories/{storyId}/favorite`
- `PUT /api/stories/{storyId}`
- `DELETE /api/stories/{storyId}`

Exemplo de geraÃ§Ã£o:

```json
{
  "childId": "UUID",
  "sourceMomentId": null,
  "theme": "Medo do escuro",
  "place": "Floresta",
  "favoriteAnimal": "Dinossauro",
  "style": "ADVENTURE",
  "length": "MEDIUM"
}
```

## Biblioteca

- Lista apenas histÃ³rias `active = true`.
- Ordena por `created_at DESC`.
- Usa paginaÃ§Ã£o `page` e `size`, com limite mÃ¡ximo de `50`.
- Permite filtros por crianÃ§a, favorito e estilo.
- Favoritar usa estado explÃ­cito, nÃ£o toggle.
- ExclusÃ£o Ã© lÃ³gica.

## IA

Provider atual:

```text
APP_STORY_GENERATOR=mock
```

ConfiguraÃ§Ã£o para geraÃ§Ã£o real:

```text
APP_STORY_GENERATOR=openai
APP_STORY_AI_FALLBACK_ENABLED=true
APP_STORY_DAILY_LIMIT=10
OPENAI_API_KEY=
OPENAI_MODEL=gpt-4.1-mini
OPENAI_TIMEOUT_SECONDS=20
APP_STORY_IMAGE_GENERATION_ENABLED=true
APP_STORY_MAX_IMAGES=3
APP_STORY_ILLUSTRATED_DAILY_LIMIT=20
OPENAI_IMAGE_MODEL=gpt-image-2
OPENAI_IMAGE_SIZE=1024x1024
OPENAI_IMAGE_QUALITY=medium
OPENAI_IMAGE_TIMEOUT_SECONDS=60
```

SeguranÃ§a:

- A chave `OPENAI_API_KEY` deve existir somente no backend e nunca no mobile, APK, `eas.json` ou documentaÃ§Ã£o com valor real.
- O backend envia Ã  OpenAI apenas dados mÃ­nimos necessÃ¡rios: primeiro nome, idade calculada, animal favorito, tema, lugar, estilo, tamanho e contexto textual do Momento.
- IDs internos, JWT, email, fotos, caminhos de storage e metadados tÃ©cnicos nÃ£o sÃ£o enviados Ã  OpenAI.
- Campos digitados pelo usuÃ¡rio sÃ£o tratados como dados, nÃ£o como instruÃ§Ãµes confiÃ¡veis.

SeguranÃ§a para menores:

- O EraUma Ã© voltado a famÃ­lias e crianÃ§as; a geraÃ§Ã£o usa prompt seguro, validaÃ§Ã£o de saÃ­da estruturada, limites por usuÃ¡rio e supervisÃ£o do responsÃ¡vel.
- Temas sensÃ­veis devem ser adaptados para uma versÃ£o infantil segura quando possÃ­vel.
- NÃ£o depender exclusivamente do modelo para seguranÃ§a; manter validaÃ§Ãµes, logs mÃ­nimos e limites de consumo.

Imagens IA:

- Modo `TEXT_ONLY` mant??m o comportamento atual e n??o chama gera????o de imagem.
- Modo `ILLUSTRATED` gera texto primeiro, persiste a hist??ria e tenta criar 1 capa + 2 cenas.
- Arquivos ficam fora do banco em `storage/stories/{storyId}`; a API retorna apenas `/api/story-images/{imageId}/content`.
- O endpoint de imagem ?? autenticado e valida pertencimento ?? fam??lia antes do download.
- N??o h?? foto real, face reference, likeness ou prompt em log nesta fase.

Uploads:

- O limite multipart do Spring Boot aceita arquivos de ate `10MB` e requests de ate `100MB`.
- As regras funcionais do EraUma continuam em ate `10` fotos por Momento e ate `10MB` por foto.

Custos:

- Uso da OpenAI API Ã© cobrado conforme modelo e consumo.
- O backend registra `input_tokens` e `output_tokens` quando a API retorna usage, sem salvar prompt integral ou histÃ³ria completa no log de IA.

Timeout e retry:

- Chamadas mobile comuns usam timeout curto de `10s`; geraÃ§Ã£o de histÃ³ria usa `90s`.
- Cada tentativa OpenAI usa `OPENAI_TIMEOUT_SECONDS`, com padrÃ£o `20s`.
- O backend faz no mÃ¡ximo `3` tentativas apenas para timeout/conexÃ£o, HTTP `429` e HTTP `5xx`.
- HTTP `400`, `401` e `403` nÃ£o fazem retry.
- Backoff atual: `500ms` antes da segunda tentativa e `1000ms` antes da terceira; pior caso aproximado com padrÃ£o atual: `61,5s`.

Teste real manual, somente com `OPENAI_API_KEY` configurada:

```powershell
$env:APP_STORY_GENERATOR="openai"
$env:OPENAI_API_KEY="sua-chave-local"
$env:OPENAI_MODEL="gpt-4.1-mini"
.\mvnw.cmd spring-boot:run
```

Gere apenas uma histÃ³ria curta para validar tÃ­tulo, resumo, capÃ­tulos, persistÃªncia, biblioteca e `generationType=AI`.

## SeguranÃ§a

- UsuÃ¡rios sÃ³ acessam famÃ­lias das quais sÃ£o membros.
- CrianÃ§as, Momentos, fotos e HistÃ³rias validam pertencimento Ã  famÃ­lia.
- Recursos de outra famÃ­lia retornam negaÃ§Ã£o coerente, preferencialmente `404`, para nÃ£o revelar existÃªncia.
- A API nÃ£o retorna senha, `password_hash`, token JWT em logs, caminho fÃ­sico de arquivo ou stack trace em respostas.

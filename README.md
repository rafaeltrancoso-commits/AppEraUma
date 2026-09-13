# EraUma

**EraUma — Momentos que viram histórias**

Aplicação infantil/familiar. As Fases 1 e 2 entregam cadastro, autenticação, famílias, crianças e Momentos. A Fase 3 adiciona **Histórias e Biblioteca**. A Fase 4 permite geração real de histórias via OpenAI API, mantendo fallback mock.

Fluxo principal validado:

`Abrir app → Criar conta → Criar família → Cadastrar criança → Home → Momentos → Criar História → Biblioteca`

Não há integração com n8n, geração de imagens, narração, pagamentos, notificações ou compartilhamento social nesta fase.

## Stack

- Backend: Java 21, Spring Boot, Maven Wrapper, Spring Web, Spring Security, Bean Validation, Spring Data JPA, Flyway e PostgreSQL.
- Mobile: React Native, Expo e TypeScript.
- Infra local: Docker Compose com PostgreSQL.
- Storage de fotos: filesystem local via abstração `FileStorageService`.
- Geração de histórias: contrato `StoryGenerator` com providers `MockStoryGenerator` e `OpenAIStoryGenerator`.

## Estrutura

```text
AppEraUma/
├── backend/
├── mobile/
├── storage/
├── docker-compose.yml
├── .env.example
├── .gitignore
└── README.md
```

## Variáveis de ambiente

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

Não versionar `.env` nem arquivos em `storage/`.

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
- Dispositivo físico: `http://IP_DA_MAQUINA:8080/api`.

Persistência de sessão:

- Android/iOS continuam usando `expo-secure-store`.
- Expo Web usa `localStorage` apenas para Beta/desenvolvimento local, com acesso defensivo quando o navegador não disponibiliza storage.

## Gerando APK Beta Android

O APK executado em um celular físico não consegue acessar o backend do computador usando `localhost`. Para Beta/local, use o IP do computador na mesma rede.

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

Use o IPv4 da interface conectada à mesma rede do Android. Exemplo temporário para este ambiente: `192.168.0.6`.

3. Configure a API antes de testar ou gerar o build:

```powershell
cd mobile
$env:EXPO_PUBLIC_API_URL="http://IP_DO_PC:8080/api"
```

Para Web local, mantenha `EXPO_PUBLIC_API_URL=http://localhost:8080/api`. Para Android físico, use `http://IP_DO_PC:8080/api`.

4. Teste o backend pelo navegador do celular:

```text
http://IP_DO_PC:8080/actuator/health
```

5. Autentique no Expo/EAS, se ainda não estiver autenticado:

```powershell
npx eas-cli login
```

6. Gere o APK Beta instalável diretamente:

```powershell
npx eas-cli build --platform android --profile preview
```

O profile `preview` em `mobile/eas.json` usa `distribution: internal` e `android.buildType: apk`, portanto o artefato esperado é APK, não AAB.

7. Ao fim do build, baixe o APK pelo link exibido pelo EAS.

8. Instale o APK no Android e permita instalação de fonte externa se o sistema solicitar.

Observações:

- BETA LOCAL em 19/08/2026: notebook `192.168.0.6`, API `http://192.168.0.6:8080/api` e health `http://192.168.0.6:8080/actuator/health`.
- Essa configuração é temporária para instalação direta em Android físico na mesma rede.
- O backend local usa HTTP; o Android Beta está configurado com `usesCleartextTraffic` para permitir testes locais.
- Produção futura deve usar HTTPS e pode remover a liberação de HTTP claro.
- O app solicita apenas permissões de leitura de imagens para seleção/upload de fotos de Momentos; não solicita câmera.

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

Um Momento pertence à família e pode envolver nenhuma, uma ou várias crianças, além de participantes livres sem conta no aplicativo.

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
- Arquivos ficam por padrão em `../storage/moments`.
- Tipos aceitos: `image/jpeg`, `image/png`, `image/webp`.
- Limites padrão: `10 MB` por foto e `10` fotos por momento.
- O nome original nunca é usado como caminho físico.
- Fotos não possuem URL pública; o endpoint de conteúdo valida autenticação e pertencimento à família.

## Histórias

Histórias pertencem à família, são vinculadas a uma criança e podem ter um Momento de origem. A geração usa `StoryGenerator`, com provider configurável entre `mock` e `openai`.

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

Exemplo de geração:

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

- Lista apenas histórias `active = true`.
- Ordena por `created_at DESC`.
- Usa paginação `page` e `size`, com limite máximo de `50`.
- Permite filtros por criança, favorito e estilo.
- Favoritar usa estado explícito, não toggle.
- Exclusão é lógica.

## IA

Provider atual:

```text
APP_STORY_GENERATOR=mock
```

Configuração para geração real:

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

Segurança:

- A chave `OPENAI_API_KEY` deve existir somente no backend e nunca no mobile, APK, `eas.json` ou documentação com valor real.
- O backend envia à OpenAI apenas dados mínimos necessários: primeiro nome, idade calculada, animal favorito, tema, lugar, estilo, tamanho e contexto textual do Momento.
- IDs internos, JWT, email, fotos, caminhos de storage e metadados técnicos não são enviados à OpenAI.
- Campos digitados pelo usuário são tratados como dados, não como instruções confiáveis.

Segurança para menores:

- O EraUma é voltado a famílias e crianças; a geração usa prompt seguro, validação de saída estruturada, limites por usuário e supervisão do responsável.
- Temas sensíveis devem ser adaptados para uma versão infantil segura quando possível.
- Não depender exclusivamente do modelo para segurança; manter validações, logs mínimos e limites de consumo.

Imagens IA:

- Modo `TEXT_ONLY` mantém o comportamento atual e não chama geração de imagem.
- Modo `ILLUSTRATED` gera texto primeiro, persiste a história e tenta criar 1 capa + 2 cenas.
- Arquivos ficam fora do banco em `storage/stories/{storyId}`; a API retorna apenas `/api/story-images/{imageId}/content`.
- O endpoint de imagem é autenticado e valida pertencimento à família antes do download.
- Não há foto real, face reference, likeness ou prompt em log nesta fase.

Uploads:

- O limite multipart do Spring Boot aceita arquivos de ate `10MB` e requests de ate `100MB`.
- As regras funcionais do EraUma continuam em ate `10` fotos por Momento e ate `10MB` por foto.

Custos:

- Uso da OpenAI API é cobrado conforme modelo e consumo.
- O backend registra `input_tokens` e `output_tokens` quando a API retorna usage, sem salvar prompt integral ou história completa no log de IA.

Timeout e retry:

- Chamadas mobile comuns usam timeout curto de `10s`; geração de história usa `90s`.
- Cada tentativa OpenAI usa `OPENAI_TIMEOUT_SECONDS`, com padrão `20s`.
- O backend faz no máximo `3` tentativas apenas para timeout/conexão, HTTP `429` e HTTP `5xx`.
- HTTP `400`, `401` e `403` não fazem retry.
- Backoff atual: `500ms` antes da segunda tentativa e `1000ms` antes da terceira; pior caso aproximado com padrão atual: `61,5s`.

Teste real manual, somente com `OPENAI_API_KEY` configurada:

```powershell
$env:APP_STORY_GENERATOR="openai"
$env:OPENAI_API_KEY="sua-chave-local"
$env:OPENAI_MODEL="gpt-4.1-mini"
.\mvnw.cmd spring-boot:run
```

Gere apenas uma história curta para validar título, resumo, capítulos, persistência, biblioteca e `generationType=AI`.

## Segurança

- Usuários só acessam famílias das quais são membros.
- Crianças, Momentos, fotos e Histórias validam pertencimento à família.
- Recursos de outra família retornam negação coerente, preferencialmente `404`, para não revelar existência.
- A API não retorna senha, `password_hash`, token JWT em logs, caminho físico de arquivo ou stack trace em respostas.

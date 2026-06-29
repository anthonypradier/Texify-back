# Texify Backend — Project Context

## Tech Stack

| Layer           | Choice                  |
|-----------------|-------------------------|
| Language        | Java 25                 |
| Framework       | Spring Boot 4.0.0       |
| Security        | Spring Security 7 + JWT (HMAC-SHA256) |
| JSON            | Jackson 3 (`tools.jackson`) |
| Database        | MySQL 8, port 3306      |
| ORM             | Spring Data JPA / Hibernate |
| Build           | Maven                   |
| Logs            | SLF4J via `@Slf4j` (Lombok) |

---

## Running Locally

1. Copy `src/main/resources/application-local.yml.example` (if it exists) or create
   `src/main/resources/application-local.yml` — it is git-ignored.
   Fill in your local credentials (DB, OAuth2, SMTP, JWT secret, etc.).
2. Start the app with the `local` profile:
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
   ```
   Or in your IDE, set the active profile to `local` in the run configuration.
3. MySQL must be running on port **3306** with a `texify_db` database
   (`createDatabaseIfNotExist=true` is set in the JDBC URL as a fallback).
4. Run tests — no MySQL or SMTP required (H2 in-memory, mail mocked):
   ```bash
   ./mvnw test
   ```
5. Lombok requires `annotationProcessorPaths` in `maven-compiler-plugin` (configured in `pom.xml`) — mandatory for Java 25.

---

## Authentication

The API is **stateless** — no HTTP sessions. Authentication uses JWT.

- After login or registration, the server returns a signed JWT.
- Attach it to every protected request:
  ```
  Authorization: Bearer <token>
  ```
- Token TTL: **24 hours** (override via `JWT_EXPIRATION` env var).
- Logout revokes the token server-side (in-memory blacklist) **and** the client
  must discard its local copy.

### OAuth2 (Google & GitHub)

Spring Security handles the OAuth2 redirect flow. Two endpoints are public in `SecurityConfig`:

| Endpoint | Role |
|---|---|
| `GET /api/auth/oauth2/authorize/{provider}` | Initiates the redirect to Google or GitHub |
| `GET /login/oauth2/code/{provider}` | Callback URL — Spring processes the provider response |

Flow:
1. Frontend redirects the user to `/api/auth/oauth2/authorize/google` (or `github`).
2. Spring redirects to the provider. The pending request is stored in a short-lived cookie (`oauth2_auth_request`, 3 min TTL) by `HttpCookieOAuth2AuthorizationRequestRepository` — keeps the API stateless.
3. Provider redirects back to `/login/oauth2/code/{provider}`.
4. `OAuth2AuthenticationSuccessHandler` calls `OAuth2UserService.processOAuth2User()` — finds or creates the account (always `enabled=true`, no email verification needed), then generates a JWT and redirects to `{app.frontend-url}/oauth2/callback?token=<jwt>`.
5. On failure, `OAuth2AuthenticationFailureHandler` redirects to `{app.frontend-url}/login?error=oauth2_failed`.

**Env vars required:**
```
GOOGLE_CLIENT_ID=...
GOOGLE_CLIENT_SECRET=...
GITHUB_CLIENT_ID=...
GITHUB_CLIENT_SECRET=...
APP_FRONTEND_URL=http://localhost:5173
```

`User.authProvider` (enum `AuthProvider`: `LOCAL`, `GOOGLE`, `GITHUB`) tracks which provider created the account.

---

## API Reference

### Base URL
```
http://localhost:9000/api
```

---

### `POST /api/auth/register`
Public. Creates a new account. The account starts **disabled** until the user verifies their email.

**Body:**
```json
{
  "email":     "alice@example.com",
  "password":  "secret123",
  "firstName": "Alice",
  "lastName":  "Smith"
}
```

| Status | Meaning |
|--------|---------|
| `201 Created`    | Account created — verification email sent — returns `MessageResponse` |
| `400 Bad Request`| Validation failure                                                     |
| `409 Conflict`   | Email already in use                                                   |

**`MessageResponse`:**
```json
{ "message": "Verification email sent to alice@example.com. Please check your inbox." }
```

---

### `GET /api/auth/verify?token=<uuid>`
Public. Activates the account linked to the verification token.

| Status | Meaning |
|--------|---------|
| `200 OK`          | Account activated — returns `MessageResponse` |
| `400 Bad Request` | Token unknown or expired                      |

---

### `POST /api/auth/resend-verification`
Public. Re-sends a verification email. Always returns 200 to avoid leaking account existence.

**Body:**
```json
{ "email": "alice@example.com" }
```

| Status | Meaning |
|--------|---------|
| `200 OK` | Generic confirmation — returns `MessageResponse` |

---

### `POST /api/auth/forgot-password`
Public. Sends a password reset email. Always returns 200 to avoid leaking account existence.

**Body:**
```json
{ "email": "alice@example.com" }
```

| Status | Meaning |
|--------|---------|
| `200 OK`          | Generic confirmation — returns `MessageResponse` |
| `400 Bad Request` | Validation failure (invalid email format)        |

---

### `POST /api/auth/reset-password`
Public. Resets the user's password using a valid reset token. Callable from any context (login page, account settings, etc.).

**Body:**
```json
{
  "token":       "<uuid-from-email>",
  "newPassword": "newsecret123"
}
```

| Status | Meaning |
|--------|---------|
| `200 OK`          | Password updated — returns `MessageResponse`      |
| `400 Bad Request` | Token invalid / expired / already used, or password too short |

---

### `POST /api/auth/login`
Public. Authenticates an existing **verified** user.

**Body:**
```json
{
  "email":    "alice@example.com",
  "password": "secret123"
}
```

| Status | Meaning |
|--------|---------|
| `200 OK`          | Login successful — returns `AuthResponse`      |
| `400 Bad Request` | Validation failure                             |
| `401 Unauthorized`| Invalid email or password                      |
| `403 Forbidden`   | Account not yet verified (email not confirmed) |

---

### `POST /api/auth/logout`
Protected. Revokes the current JWT.

**Header:** `Authorization: Bearer <token>`

| Status | Meaning |
|--------|---------|
| `204 No Content`  | Token revoked — client must discard it |
| `401 Unauthorized`| Missing or invalid token               |

---

### `GET /api/auth/me`
Protected. Returns the authenticated user's profile.

**Header:** `Authorization: Bearer <token>`

| Status | Meaning |
|--------|---------|
| `200 OK`          | Returns `UserResponse` |
| `401 Unauthorized`| Missing or invalid token |

**`UserResponse`:**
```json
{
  "id":        1,
  "email":     "alice@example.com",
  "firstName": "Alice",
  "lastName":  "Smith",
  "role":      "ROLE_USER"
}
```

---

## Error Format

All errors return a consistent JSON body:
```json
{
  "status":    400,
  "error":     "Validation Error",
  "message":   "Password must be at least 8 characters",
  "timestamp": "2026-04-19T10:30:00"
}
```

---

## User Roles

| Value       | Description                     |
|-------------|---------------------------------|
| `ROLE_USER` | Default role for new accounts   |
| `ROLE_ADMIN`| Reserved for admin endpoints    |

---

## Architecture Notes

- `User` entity is a plain JPA object — it does **not** implement `UserDetails`.
- `UserDetailsServiceImpl` converts a `User` entity into a transient Spring
  Security `UserDetails` on each authentication request.
- The JWT principal (stored in the security context) is a Spring Security
  value object; the full `User` entity is re-loaded from the DB when needed
  (e.g. `GET /me`).
- JWT blacklist is in-memory (`ConcurrentHashSet`). Replace with Redis for
  multi-instance deployments or persistence across restarts.
- `jjwt-jackson` (0.12.x) still uses Jackson 2 internally — this works because
  Spring Boot 4 maintains Jackson 2 dependency management for transitive deps.

---

## Project Structure

```
src/
├── main/
│   ├── java/com/texify/backend/
│   │   ├── config/          # SecurityConfig, TemplateSeeder, StorageConfig
│   │   ├── controller/      # AuthController, DocumentController, LabelController,
│   │   │                    #   TemplateController, StorageController,
│   │   │                    #   WaitlistController, HealthController
│   │   ├── dto/             # RegisterRequest, LoginRequest, AuthResponse,
│   │   │                    #   UserResponse, ErrorResponse, MessageResponse,
│   │   │                    #   ResendVerificationRequest, ForgotPasswordRequest,
│   │   │                    #   ResetPasswordRequest,
│   │   │                    #   CreateDocumentRequest, UpdateDocumentRequest, DocumentResponse,
│   │   │                    #   CreateLabelRequest, UpdateLabelRequest, LabelResponse,
│   │   │                    #   TemplateResponse
│   │   ├── entity/          # User, Role, AuthProvider, VerificationToken, TokenType,
│   │   │                    #   Document, Label, Template, PreviewStatus, WaitlistEntry
│   │   ├── exception/       # GlobalExceptionHandler, EmailAlreadyExistsException,
│   │   │                    #   InvalidVerificationTokenException,
│   │   │                    #   DocumentNotFoundException, LabelNotFoundException,
│   │   │                    #   TemplateNotFoundException, InvalidFileException, StorageException
│   │   ├── repository/      # UserRepository, VerificationTokenRepository,
│   │   │                    #   DocumentRepository, LabelRepository, TemplateRepository,
│   │   │                    #   WaitlistRepository
│   │   ├── security/        # JwtService, JwtAuthenticationFilter, PublicModeGuardFilter,
│   │   │                    #   OAuth2AuthenticationSuccessHandler,
│   │   │                    #   OAuth2AuthenticationFailureHandler,
│   │   │                    #   HttpCookieOAuth2AuthorizationRequestRepository
│   │   └── service/         # AuthService, UserDetailsServiceImpl, EmailService,
│   │                        #   OAuth2UserService, DocumentService, DocumentPreviewService,
│   │                        #   LabelService, TemplateService, TemplatePreviewService,
│   │                        #   StorageService, WaitlistService
│   └── resources/
│       └── application.yml
└── test/
    ├── java/com/texify/backend/
    │   ├── controller/      # AuthControllerTest  (@SpringBootTest + @AutoConfigureMockMvc)
    │   └── service/         # AuthServiceTest     (Mockito pur, pas de contexte Spring)
    └── resources/
        └── application-test.yml
```

### Notes sur les tests (Spring Boot 4)

| Ancien (Spring Boot 3) | Nouveau (Spring Boot 4) |
|---|---|
| `@MockBean` (`o.s.boot.test.mock.mockito`) | `@MockitoBean` (`o.s.test.context.bean.override.mockito`) |
| `@AutoConfigureMockMvc` (`o.s.boot.test.autoconfigure.web.servlet`) | `@AutoConfigureMockMvc` (`o.s.boot.webmvc.test.autoconfigure`) |
| `com.fasterxml.jackson.databind.ObjectMapper` | `tools.jackson.databind.ObjectMapper` |
| dépendance `spring-boot-starter-test` seule | + `spring-boot-starter-webmvc-test` (scope test) |

---

## Email Verification Flow

Tokens are stored in a dedicated `verification_tokens` table (not in `users`), which allows multiple token types (EMAIL_VERIFICATION, PASSWORD_RESET) without polluting the main table.

1. `POST /register` → account created with `enabled=false`. A `VerificationToken` (UUID, TTL 24 h) is inserted into `verification_tokens`. Email sent.
2. User clicks the link → frontend calls `GET /api/auth/verify?token=<uuid>`.
3. Backend checks: token known + `used_at IS NULL` + not expired → `used_at = now()`, `users.enabled = true`.
4. User can now call `POST /login`.

---

## Password Reset Flow

1. `POST /forgot-password` → any existing `PASSWORD_RESET` token for the user is deleted. A new `VerificationToken` (UUID, TTL **1 hour**) is inserted. Email sent. Always returns 200.
2. User clicks the link → frontend calls `POST /reset-password` with the token and new password.
3. Backend checks: token known + type == `PASSWORD_RESET` + `used_at IS NULL` + not expired → `used_at = now()`, password re-hashed and saved.
4. User can now log in with the new password.

The `resetPassword` service method is context-agnostic — it can be called from the login page, account settings, or any future surface.

**Schema `verification_tokens` :**
```sql
id         BIGINT PK AUTO_INCREMENT
user_id    BIGINT FK → users.id ON DELETE CASCADE
token      VARCHAR(255) UNIQUE NOT NULL
type       ENUM('EMAIL_VERIFICATION','PASSWORD_RESET') NOT NULL
expires_at DATETIME NOT NULL
used_at    DATETIME NULL   -- null = pas encore consommé
```

**MailHog local setup:**
```bash
docker run -d -p 1025:1025 -p 8025:8025 mailhog/mailhog
```
UI available at `http://localhost:8025`. Default `application.yml` already points to `localhost:1025`.

**Env vars for production SMTP:**
```
MAIL_HOST=smtp.example.com
MAIL_PORT=587
MAIL_USERNAME=...
MAIL_PASSWORD=...
```
Set `mail.smtp.auth=true` and `mail.smtp.starttls.enable=true` via properties or override in `application.yml`.

---

## Documents & Labels (Projects Tab)

### Entities

**`Document`** — table `documents`

| Colonne | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | auto-increment |
| `title` | VARCHAR NOT NULL | |
| `blocks` | JSON | défaut `"[]"` — contenu de l'éditeur bloc |
| `owner_id` | BIGINT FK → users.id | |
| `is_public` | BOOLEAN NOT NULL | défaut `false` |
| `compile_pdf_path` | VARCHAR NULL | chemin du PDF compilé |
| `word_count` | INT NOT NULL | défaut 0 |
| `created_at` | DATETIME NOT NULL | immutable |
| `updated_at` | DATETIME | refreshed on update |
| `deleted_at` | DATETIME NULL | soft-delete — non null = supprimé |
| `pinned` | BOOLEAN NOT NULL | défaut `false` |
| `icon` | VARCHAR NULL | |
| `color` | VARCHAR NULL | |
| `equation_count` | INT NOT NULL | défaut 0 — statistiques |
| `figure_count` | INT NOT NULL | défaut 0 |
| `plot_count` | INT NOT NULL | défaut 0 |
| `code_count` | INT NOT NULL | défaut 0 |
| `compilation_count` | INT NOT NULL | défaut 0 |
| `preview_image_path` | VARCHAR(500) NULL | PNG page 1 du dernier PDF compilé — `null` = jamais compilé |
| `preview_generated_at` | DATETIME NULL | date de génération de la preview courante |
| `preview_status` | VARCHAR(15) NOT NULL | enum `PreviewStatus`, défaut `PENDING` |

**`Label`** — table `labels`

| Colonne | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | auto-increment |
| `name` | VARCHAR NOT NULL | |
| `color` | VARCHAR NULL | |
| `user_id` | BIGINT FK → users.id | |
| `created_at` | DATETIME NOT NULL | immutable |

**Table de jointure `document_labels`** : `label_id` + `document_id` (Label est le côté propriétaire de la relation ManyToMany).

### Relation ManyToMany

- `Label.documents` (`@ManyToMany` avec `@JoinTable`) — côté propriétaire.
- `Document.labels` (`@ManyToMany(mappedBy = "documents")`) — côté inverse.
- Pour ajouter/retirer un label, toujours passer par le côté `Label` et sauvegarder le label.

### Boolean `isPublic` — convention Lombok/Jackson

Le champ `private boolean isPublic` dans l'entité `Document` génère via Lombok :
- getter `isPublic()`, setter `setPublic(boolean)` (Lombok retire le préfixe `is`).

Dans les DTOs de requête, on utilise `private Boolean isPublic` (**wrapper**) pour que Lombok génère `getIsPublic()` / `setIsPublic(Boolean)`, ce que Jackson mappe correctement sur la clé JSON `"isPublic"`.

### API Documents (Protected)

| Méthode | Endpoint | Status | Description |
|---|---|---|---|
| `POST` | `/api/documents` | 201 | Créer un document |
| `GET` | `/api/documents` | 200 | Lister ses documents (hors soft-deleted) |
| `GET` | `/api/documents/{id}` | 200 | Récupérer un document |
| `PATCH` | `/api/documents/{id}` | 200 | Modifier (partiel) |
| `DELETE` | `/api/documents/{id}` | 204 | Soft-delete |
| `POST` | `/api/documents/{id}/labels/{labelId}` | 200 | Attacher un label |
| `DELETE` | `/api/documents/{id}/labels/{labelId}` | 200 | Détacher un label |
| `GET` | `/api/documents/{id}/preview/status` | 200 | Statut de preview (polling front) — renvoie un `DocumentResponse` |

**`DocumentResponse`** (record) :
```json
{
  "id": 1, "title": "Mon doc", "blocks": "[]",
  "isPublic": false, "compilePdfPath": null,
  "wordCount": 0, "createdAt": "...", "updatedAt": "...",
  "pinned": false, "icon": null, "color": null,
  "equationCount": 0, "figureCount": 0, "plotCount": 0,
  "codeCount": 0, "compilationCount": 0,
  "previewImageUrl": null, "previewStatus": "PENDING",
  "labels": [{ "id": 1, "name": "urgent", "color": "#ff0000" }]
}
```

| Status | Meaning |
|---|---|
| `404 Not Found` | Document introuvable ou n'appartient pas à l'utilisateur |

### API Labels (Protected)

| Méthode | Endpoint | Status | Description |
|---|---|---|---|
| `POST` | `/api/labels` | 201 | Créer un label |
| `GET` | `/api/labels` | 200 | Lister ses labels |
| `PATCH` | `/api/labels/{id}` | 200 | Modifier nom/couleur |
| `DELETE` | `/api/labels/{id}` | 204 | Supprimer un label |

| Status | Meaning |
|---|---|
| `404 Not Found` | Label introuvable ou n'appartient pas à l'utilisateur |

### Règles métier

- Un utilisateur ne voit et ne modifie que **ses propres** documents et labels.
- Les documents soft-deletés (`deleted_at IS NOT NULL`) sont exclus de toutes les requêtes.
- Les compteurs (`wordCount`, `equationCount`, etc.) sont mis à jour par le client via `PATCH` — aucune compilation n'est effectuée côté serveur.
- Un label ne peut être attaché à un document que s'il appartient **au même utilisateur**.

---

## Templates (Templates Tab)

L'onglet « Templates » présente des cartes (mêmes cards que les documents). Cliquer sur une carte ouvre l'éditeur sur un **PDF de preview par défaut** (`previewPdfPath`). L'utilisateur peut alors créer un nouveau document à partir du template (bouton « New document with template ») ou revenir en arrière. La création est aussi accessible via les « trois points » de la carte.

**MVP : uniquement des templates « système »** — seedés au démarrage, sans créateur (`created_by = NULL`, `is_system = true`). La création de templates par l'utilisateur viendra plus tard.

### Entité

**`Template`** — table `templates`

| Colonne | Type | Notes |
|---|---|---|
| `id` | BIGINT PK | auto-increment |
| `title` | VARCHAR NOT NULL | |
| `description` | VARCHAR NULL | texte de la carte |
| `blocks` | JSON | défaut `"[]"` — contenu éditeur (usage futur) |
| `preview_pdf_path` | VARCHAR NULL | PDF par défaut affiché en preview dans l'éditeur |
| `icon` | VARCHAR NULL | |
| `color` | VARCHAR NULL | |
| `category` | VARCHAR NULL | regroupement libre (ex. « Academic ») |
| `is_system` | BOOLEAN NOT NULL | défaut `false` — `true` = template intégré |
| `created_by` | BIGINT FK → users.id NULL | `NULL` = template système |
| `created_at` | DATETIME NOT NULL | immutable |
| `updated_at` | DATETIME | refreshed on update |

Un template est **accessible** à un utilisateur s'il est `is_system = true` **ou** que `created_by` est l'utilisateur courant. Un template inaccessible renvoie `404` (pas de fuite d'existence).

### Seeding

`TemplateSeeder` (`CommandLineRunner`) insère 4 templates système au démarrage si aucun n'existe (idempotent). En dev (`ddl-auto: create-drop`) le seeding s'exécute à chaque boot ; en prod le garde-fou évite les doublons. Templates par défaut : *Academic Paper*, *Resume / CV*, *Lab Report*, *Beamer Presentation*.

### API Templates (Protected)

| Méthode | Endpoint | Status | Description |
|---|---|---|---|
| `GET` | `/api/templates` | 200 | Lister les templates accessibles (système + ceux de l'user) |
| `GET` | `/api/templates/{id}` | 200 | Récupérer un template (preview dans l'éditeur) |
| `POST` | `/api/templates/{id}/use` | 201 | Créer un document à partir du template — renvoie `DocumentResponse` |

**`TemplateResponse`** (record) :
```json
{
  "id": 1,
  "title": "Academic Paper",
  "description": "A clean two-column article layout for research papers.",
  "blocks": "[]",
  "previewPdfPath": "/templates/previews/academic-paper.pdf",
  "icon": "📄",
  "color": "#4F46E5",
  "category": "Academic",
  "isSystem": true,
  "createdAt": "..."
}
```

| Status | Meaning |
|---|---|
| `404 Not Found` | Template introuvable ou non accessible à l'utilisateur |

### Preview PDF des templates — réglages Spring Security temporaires ⚠️

> **TEMPORAIRE** — à durcir plus tard, voir *Planned Features*.

Les PDF de preview sont des **fichiers statiques** (`src/main/resources/static/templates/previews/*.pdf`), servis directement par Spring Boot à l'URL `/templates/previews/<fichier>.pdf` (= valeur de `previewPdfPath`). Ce ne sont **pas** des endpoints de contrôleur.

Deux ajustements dans `SecurityConfig` pour que le front puisse les afficher :

1. **`X-Frame-Options` désactivé** — `headers(h -> h.frameOptions(f -> f.disable()))`. Le défaut Spring Security est `DENY`, qui empêchait le front d'afficher le PDF dans un `<iframe>`/`<embed>`. `disable()` supprime complètement l'en-tête (solution permissive, retenue en accord avec l'agent front).
2. **`/templates/previews/**` rendu public** — ajouté au `permitAll()`. Sinon `anyRequest().authenticated()` exigeait un JWT pour télécharger les PDF statiques (le front ne pouvait pas les charger dans une balise sans en-tête `Authorization`).

À nettoyer à terme : remplacer `frameOptions.disable()` par une CSP `frame-ancestors` ciblée sur l'origine du front plutôt qu'une désactivation totale.

### Règles métier

- `POST /{id}/use` crée un **document neuf et vierge** pour l'utilisateur courant : `title = "<template> - copy"`, `blocks = "[]"`. **Aucune autre donnée** du template (preview, icon, color, blocks) n'est copiée — la liaison au template est volontairement absente du MVP.
- Le document créé est un `Document` standard : il apparaît ensuite dans `GET /api/documents` et suit tout le cycle de vie habituel.
- L'éditeur n'est pas encore fonctionnel : la preview se limite au PDF par défaut (`previewPdfPath`).

### Piège JPQL — INNER JOIN implicite sur `createdBy` (templates système)

`TemplateRepository.findAccessibleBy` doit utiliser un **LEFT JOIN explicite** :
```java
@Query("SELECT t FROM Template t LEFT JOIN t.createdBy u WHERE t.isSystem = true OR u.email = :email")
```
Naviguer une association dans le `WHERE` (`t.createdBy.email`) génère un **INNER JOIN implicite** : comme les templates système ont `created_by = NULL`, ils étaient **tous éliminés** avant l'évaluation du `WHERE` → liste vide côté API (« no templates available » au front) alors que l'endpoint répond bien `200`. Le `LEFT JOIN` préserve les lignes à `createdBy` null.

---

## Système de preview des templates & stockage de fichiers

### Principe
Les previews sont **générées une fois** (à terme : compilation LaTeX → PNG page 1 + PDF), stockées sur le système de fichiers, puis **invalidées** quand les blocs du template changent. Pattern : générer → mettre en cache → invalider sur changement.

> ⚠️ Le compilateur LaTeX (tectonic) **n'est pas encore branché**. Toute l'infrastructure est en place ; `TemplatePreviewService.generatePreviewAsync` est un **stub**. En attendant, les previews des templates système sont fournies par les PDF statiques seedés et/ou un upload manuel admin.

### Champs ajoutés à l'entité `Template`

| Colonne | Type | Notes |
|---|---|---|
| `preview_image_path` | VARCHAR(500) NULL | chemin relatif du PNG (page 1) — `null` = placeholder front |
| `preview_pdf_path` | VARCHAR NULL | déjà existant — chemin du PDF de preview |
| `preview_generated_at` | DATETIME NULL | date de génération de la preview courante |
| `blocks_updated_at` | DATETIME NOT NULL | màj à chaque changement des blocs — sert à détecter l'obsolescence |
| `preview_status` | VARCHAR(15) NOT NULL | enum `PreviewStatus`, défaut `PENDING` |

**Pas de migration Flyway** (le projet n'en a pas) : les colonnes sont créées par `ddl-auto`. Le `TemplateSeeder` initialise les 4 templates système avec `previewStatus = READY` + `previewGeneratedAt = now` (ils ont déjà un PDF statique).

### Enum `PreviewStatus` (`entity/PreviewStatus.java`)
`PENDING` (jamais générée) · `GENERATING` (job async en cours, spinner front) · `READY` (à jour) · `OUTDATED` (blocs modifiés depuis) · `ERROR` (échec génération).

Obsolescence : `blocksUpdatedAt > previewGeneratedAt` → preview à régénérer (`TemplatePreviewService.isPreviewUpToDate`).

### Stockage de fichiers (`StorageConfig` + `StorageService`)
Propriétés `texify.storage.*` (defaults adaptés au port **9000**) :
```yaml
texify.storage:
  local-root-path: ${STORAGE_ROOT:./storage}     # racine locale (gitignored)
  type:            ${STORAGE_TYPE:LOCAL}          # LOCAL | S3 (S3 = futur)
  base-url:        ${STORAGE_BASE_URL:http://localhost:9000/storage}
```
Arborescence créée au démarrage (`@PostConstruct`) :
```
storage/
├── templates/previews/{templateId}/preview.png
├── templates/pdfs/{templateId}/template.pdf
└── documents/{pdfs,images}/...
```
- Chemins stockés en base **relatifs** (jamais d'URL absolue). L'URL publique est construite par `StorageService.buildPublicUrl` au moment de la réponse.
- **Convention `buildPublicUrl`** : un chemin commençant par `/` est renvoyé tel quel (asset statique seedé, ex. `/templates/previews/academic-paper.pdf`, servi depuis `src/main/resources/static`) ; sinon il est préfixé par `base-url` → `/storage/...`.
- **Sécurité path traversal** : `StorageService` vérifie que le chemin résolu reste sous la racine, sinon `SecurityException`.

### `StorageController` — `GET /storage/**` (public, cache 24h)
Sert les fichiers du stockage local en dev (en prod : Nginx/CDN, désactiver le controller). Rendu **public** via `permitAll` sur `/storage/**` dans `SecurityConfig`. ⚠️ Pour le MVP **tout** `/storage/**` est public ; à durcir quand les PDF de documents (privés) y seront stockés.

### Upload manuel des previews (ADMIN, phase dev sans compilateur)
| Méthode | Endpoint | Rôle | Description |
|---|---|---|---|
| `POST` | `/api/templates/{id}/preview/image` | ADMIN | upload PNG/JPG/WebP (≤ 5 Mo), `multipart` champ `file` → `previewStatus = READY` |
| `POST` | `/api/templates/{id}/preview/pdf` | ADMIN | upload PDF (≤ 20 Mo), `multipart` champ `file` |
| `GET` | `/api/templates/{id}/preview/status` | USER | renvoie le `TemplateResponse` (contient `previewStatus`, URLs) — pour le polling front |

`@PreAuthorize("hasRole('ADMIN')")` (méthode security déjà activée). Multipart configuré dans `application.yml` (`spring.servlet.multipart`, max 20/25 Mo).

### DTO `TemplateResponse` — champs ajoutés
`previewImageUrl` et `previewPdfUrl` (URLs complètes construites par `StorageService`, nullable) + `previewStatus`. `previewPdfPath` (chemin brut) est conservé pour rétro-compat. Le mapping vit dans `TemplateService.toResponse` (rendu **public** et réutilisé par `TemplatePreviewService` — pas de dépendance circulaire car `TemplateService` ne dépend pas de `TemplatePreviewService`).

### Génération auto (TODO — brancher tectonic)
`TemplatePreviewService.generatePreviewAsync` est le stub à implémenter. Flux prévu : blocks JSON → assemblage `.tex` → compilation tectonic → extraction PNG page 1 (PDFBox) → stockage PNG + PDF → `previewStatus = READY`. Points d'intégration déjà prêts : `invalidatePreview` (à appeler sur modif des blocs) et `deletePreviewFiles` (à appeler avant suppression). `@EnableAsync` / `@EnableScheduling` activés sur la classe main. Dépendance à ajouter le moment venu : `org.apache.pdfbox:pdfbox`.

### Exceptions ajoutées
`InvalidFileException` → 400 (fichier vide / mauvais type / trop gros) ; `StorageException` → 500 (erreur I/O stockage). Gérées dans `GlobalExceptionHandler`.

---

## Preview des documents

Extension du système de preview (storage + `PreviewStatus`) aux documents. `StorageService`, `PreviewStatus` et `TemplatePreviewService` sont **réutilisés tels quels** (aucune modif).

### Principe
La preview d'un document est générée **après chaque compilation réussie** (pas à chaque sauvegarde) : elle reflète l'état **compilé**, pas le brouillon en cours. À la modification des blocs, la preview existante passe `OUTDATED` mais reste affichée (badge « à recompiler » côté front) — **pas** de régénération auto : c'est l'utilisateur qui décide quand compiler.

### Champs ajoutés à l'entité `Document`
`previewImagePath` (PNG page 1, relatif), `previewGeneratedAt`, `previewStatus` (enum `PreviewStatus`, défaut `PENDING`). Colonnes créées par `ddl-auto` (pas de Flyway). `DocumentResponse` expose `previewImageUrl` (URL construite par `StorageService`, nullable) + `previewStatus`.

### `DocumentPreviewService` (`service/`)
- `onCompilationSuccess(documentId, pdfPath)` — **hook d'intégration** appelé par le futur pipeline de compilation : passe le statut à `GENERATING` puis déclenche la génération.
- `generatePreviewAsync(documentId, pdfPath)` — **stub** `@Async` (remet `PENDING` en attendant). Flux prévu : `StorageService.load(pdf)` → PDFBox `renderImageWithDPI(0, 150)` → `ImageIO` PNG → `StorageService.store(...)` → statut `READY`. Dépendance à ajouter : `org.apache.pdfbox:pdfbox:3.0.x`.
- `invalidatePreview(documentId)` — appelé par `DocumentService.update` quand `blocks` change ; `READY → OUTDATED` uniquement.
- `deletePreviewFiles(document)` — à appeler avant une suppression **définitive** (le soft-delete actuel conserve les fichiers).

> ⚠️ Le pipeline de compilation (entité `CompilationLog`, endpoint `compile`) **n'existe pas encore** — hors scope de cette feature. Le seul point de contact prévu est `onCompilationSuccess`, que le compilateur appellera en cas de succès.

### Stockage
`documents/previews/{documentId}/preview.png` — écrasé à chaque compilation réussie (pas d'accumulation). Servi via `/storage/**`.

### Statuts (`PreviewStatus`, partagé avec les templates)
`PENDING` (jamais compilé) · `OUTDATED` (blocs modifiés depuis — badge « à recompiler ») · `GENERATING` (extraction PNG en cours, polling front) · `READY` (à jour) · `ERROR` (extraction échouée).

### Endpoint de polling
`GET /api/documents/{id}/preview/status` → `DocumentResponse` (contient `previewStatus` + `previewImageUrl`). Réservé au propriétaire (via `findById`).

---

## Public (Landing) Mode — feature flag `app.public-mode`

Permet de déployer en prod une **landing page publique** (capture d'emails / waitlist) pendant que le MVP est encore en développement, pour démarrer le référencement Google tôt. Le code du MVP reste **intact** ; on bascule via configuration.

### Principe
- Propriété `app.public-mode` (défaut **`false`**), surchargée par la variable d'env **`APP_PUBLIC_MODE`**.
- `false` → application complète, comportement MVP inchangé.
- `true` → **seuls** les endpoints whitelistés répondent ; **tout le reste renvoie `404`** (choisi plutôt que 403 pour ne pas révéler l'existence de l'API MVP).

### Le verrou est réel, pas cosmétique
`PublicModeGuardFilter` (`OncePerRequestFilter`, enregistré en tête de la chaîne Spring Security) bloque côté serveur **avant** d'atteindre le moindre contrôleur — impossible de contourner depuis le front. Quand `public-mode=false`, le filtre est un **no-op** complet.

> Choix d'archi : un filtre centralisé plutôt que des `@ConditionalOnProperty` sur chaque contrôleur — une seule source de vérité, couvre tout (y compris ce qu'on oublierait d'annoter), et réversible instantanément par le flag.

### Whitelist en mode public
- `POST /api/waitlist` — capture d'email
- `GET /api/health` — health check
- requêtes `OPTIONS` (préflight CORS)

Ces routes sont aussi `permitAll` dans `SecurityConfig` (publiques dans les **deux** modes).

### Endpoint waitlist
`POST /api/waitlist` — body `{ "email": "alice@example.com" }`. Email validé (`@Email`), normalisé (trim + minuscules), stocké dans `waitlist_entries` (colonne `email` **UNIQUE**).

| Status | Meaning |
|---|---|
| `200 OK` | Email enregistré **ou** déjà présent — message générique (idempotent, ne fuite pas l'existence) |
| `400 Bad Request` | Email manquant ou format invalide |

Doublon géré proprement : `existsByEmail` + capture de `DataIntegrityViolationException` (race condition concurrente) → même résultat idempotent.

**Schéma `waitlist_entries`** (créé par `ddl-auto`, pas de Flyway) :
```sql
id         BIGINT PK AUTO_INCREMENT
email      VARCHAR(255) UNIQUE NOT NULL
created_at DATETIME NOT NULL
```

### Activer / désactiver
```bash
# Prod — mode vitrine (landing seule)
APP_PUBLIC_MODE=true

# Jour du lancement — réactive tout le MVP, AUCUN autre changement ni migration
APP_PUBLIC_MODE=false   # ou supprimer la variable (défaut false)
```
Un simple redémarrage avec la variable suffit ; rien d'autre à toucher.

> Note prod : la `CorsConfigurationSource` autorise `http://localhost:5173`. Pour la landing déployée, ajouter l'origine du domaine public aux origins autorisés (hors périmètre de ce flag).

---

## Planned Features (not yet implemented)

- Création / édition de templates par l'utilisateur (templates non-système)

- Compilateur LaTeX (tectonic) : implémenter `TemplatePreviewService.generatePreviewAsync` (blocks → `.tex` → tectonic → PDF → PNG via PDFBox) et appeler `invalidatePreview` sur modif des blocs / `deletePreviewFiles` à la suppression.

- Pipeline de compilation des documents (entité `CompilationLog`, endpoint `POST /api/documents/{id}/compile`) qui appellera `DocumentPreviewService.onCompilationSuccess` ; implémenter `DocumentPreviewService.generatePreviewAsync` (extraction PNG page 1 via PDFBox).

- Durcir la sécurité des previews PDF : remplacer `frameOptions.disable()` par une CSP `frame-ancestors` ciblée et resserrer l'accès public à `/templates/previews/**` et `/storage/**` (séparer previews publiques vs fichiers privés de documents).

- Migrer le stockage local vers S3 / Cloudflare R2 (`StorageConfig.type = S3`).

- Stripe payment integration

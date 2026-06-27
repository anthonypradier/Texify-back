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
│   │   ├── config/          # SecurityConfig, TemplateSeeder
│   │   ├── controller/      # AuthController, DocumentController, LabelController,
│   │   │                    #   TemplateController
│   │   ├── dto/             # RegisterRequest, LoginRequest, AuthResponse,
│   │   │                    #   UserResponse, ErrorResponse, MessageResponse,
│   │   │                    #   ResendVerificationRequest, ForgotPasswordRequest,
│   │   │                    #   ResetPasswordRequest,
│   │   │                    #   CreateDocumentRequest, UpdateDocumentRequest, DocumentResponse,
│   │   │                    #   CreateLabelRequest, UpdateLabelRequest, LabelResponse,
│   │   │                    #   TemplateResponse
│   │   ├── entity/          # User, Role, AuthProvider, VerificationToken, TokenType,
│   │   │                    #   Document, Label, Template
│   │   ├── exception/       # GlobalExceptionHandler, EmailAlreadyExistsException,
│   │   │                    #   InvalidVerificationTokenException,
│   │   │                    #   DocumentNotFoundException, LabelNotFoundException,
│   │   │                    #   TemplateNotFoundException
│   │   ├── repository/      # UserRepository, VerificationTokenRepository,
│   │   │                    #   DocumentRepository, LabelRepository, TemplateRepository
│   │   ├── security/        # JwtService, JwtAuthenticationFilter,
│   │   │                    #   OAuth2AuthenticationSuccessHandler,
│   │   │                    #   OAuth2AuthenticationFailureHandler,
│   │   │                    #   HttpCookieOAuth2AuthorizationRequestRepository
│   │   └── service/         # AuthService, UserDetailsServiceImpl, EmailService,
│   │                        #   OAuth2UserService, DocumentService, LabelService,
│   │                        #   TemplateService
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

**`DocumentResponse`** (record) :
```json
{
  "id": 1, "title": "Mon doc", "blocks": "[]",
  "isPublic": false, "compilePdfPath": null,
  "wordCount": 0, "createdAt": "...", "updatedAt": "...",
  "pinned": false, "icon": null, "color": null,
  "equationCount": 0, "figureCount": 0, "plotCount": 0,
  "codeCount": 0, "compilationCount": 0,
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

## Planned Features (not yet implemented)

- Création / édition de templates par l'utilisateur (templates non-système)

- Stripe payment integration

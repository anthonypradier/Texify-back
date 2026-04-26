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

1. Copy `.env` and fill in your values (it is git-ignored).
2. Export the variables before starting the app:
   ```bash
   export $(grep -v '^#' .env | xargs)
   ./mvnw spring-boot:run
   ```
   Or configure your IDE to load `.env` as environment variables for the run configuration.
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
│   │   ├── config/          # SecurityConfig
│   │   ├── controller/      # AuthController
│   │   ├── dto/             # RegisterRequest, LoginRequest, AuthResponse,
│   │   │                    #   UserResponse, ErrorResponse, MessageResponse,
│   │   │                    #   ResendVerificationRequest, ForgotPasswordRequest,
│   │   │                    #   ResetPasswordRequest
│   │   ├── entity/          # User, Role, VerificationToken, TokenType
│   │   ├── exception/       # GlobalExceptionHandler, EmailAlreadyExistsException,
│   │   │                    #   InvalidVerificationTokenException
│   │   ├── repository/      # UserRepository, VerificationTokenRepository
│   │   ├── security/        # JwtService, JwtAuthenticationFilter
│   │   └── service/         # AuthService, UserDetailsServiceImpl, EmailService
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

## Planned Features (not yet implemented)

- Stripe payment integration
- Document CRUD (core SaaS feature)

# Texify Backend — Conventions & Standards

## Tech Stack

| Component         | Version / Choice                                                   |
|-------------------|--------------------------------------------------------------------|
| Java              | 25                                                                 |
| Spring Boot       | 4.0.0                                                              |
| Spring Security   | 7 (bundled with Boot 4)                                            |
| Spring Data JPA   | via Boot 4                                                         |
| Hibernate         | via Boot 4 — dialect `MySQLDialect`                                |
| JWT               | JJWT 0.12.6 (HMAC-SHA256)                                         |
| Jackson           | 3 (`tools.jackson`) — imports differ from Jackson 2, watch out     |
| MySQL             | 8, port 3306, database `texify_db`                                 |
| Build             | Maven Wrapper (`./mvnw`)                                           |
| Lombok            | latest stable (via Boot 4 BOM)                                     |
| Tests             | JUnit 5 + Mockito + H2 (in-memory)                                 |
| Mail (dev)        | MailHog on `localhost:1025`                                        |

---

## Architecture — Package Layout

Root package: `com.texify.backend`

```
config/          # Spring configuration beans (SecurityConfig, etc.)
                 # No business logic here — wiring only.

controller/      # @RestController: receives the HTTP request, delegates to the service,
                 # returns a ResponseEntity. No business logic, no direct repo access.

dto/             # Transfer objects (Request / Response). Java records or Lombok classes.
                 # JPA entities must never be exposed directly in an HTTP response.

entity/          # JPA entities (@Entity). Pure domain/persistence objects.
                 # Must not implement UserDetails (layer separation is intentional).

exception/       # Custom business exceptions + GlobalExceptionHandler (@RestControllerAdvice).
                 # Every new business exception must be added here and handled in the handler.

repository/      # Spring Data JPA interfaces (implicit @Repository).
                 # Method naming follows Spring conventions: findByX, existsByX, deleteByX.

security/        # Everything JWT-related: JwtService, JwtAuthenticationFilter.
                 # In-memory blacklist (ConcurrentHashSet) — replace with Redis for
                 # multi-instance deployments or persistence across restarts.

service/         # Business logic. @Service + @Transactional where needed.
                 # This is where repository calls are orchestrated.
                 # UserDetailsServiceImpl lives here (bridges entity → Spring Security UserDetails).
```

**Strict flow rule:** `Controller → Service → Repository`. Never skip a layer.

---

## Java Code Conventions

### Naming
- Classes: `PascalCase`
- Methods and variables: `camelCase`
- Constants: `UPPER_SNAKE_CASE`
- SQL tables / columns: `snake_case` (mapped via `@Column(name = "...")`)
- Packages: all lowercase, no underscores

### Lombok
Apply Lombok consistently on entities and services:

| Need                          | Annotation(s)                                                  |
|-------------------------------|----------------------------------------------------------------|
| JPA entity                    | `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor` |
| Service / injected component  | `@RequiredArgsConstructor` (constructor injection)             |
| Logging                       | `@Slf4j` — use `log.debug/info/warn/error`                     |
| Immutable DTO                 | Java record or `@Value` (Lombok)                               |

### Dependency Injection
Always via constructor (`@RequiredArgsConstructor`). Never use `@Autowired` on a field.

### Validation
Bean Validation (`jakarta.validation`) on request DTOs. Always annotate the controller parameter with `@Valid`.

### Transactions
`@Transactional` on service methods that write to the database (or read multiple entities in a single unit of work). Never put `@Transactional` on controllers.

### JPA Entities
- `@PrePersist` to initialize `createdAt` and `updatedAt`.
- `@PreUpdate` to refresh `updatedAt`.
- Enums stored in the database: `@Enumerated(EnumType.STRING)` (readability and forward compatibility).
- Never expose a JPA entity directly in an HTTP response — always go through a DTO.

### Error Handling
- Every business error case = a custom exception in `exception/`.
- `GlobalExceptionHandler` is the sole exit point toward the HTTP client.
- Uniform error response format: `{ status, error, message, timestamp }`.
- Never expose a stack trace or internal detail to the client.
- For sensitive cases (e.g. login), return a deliberately vague generic message.

### Logging
- `log.debug`: endpoint entry (non-sensitive data only).
- `log.info`: significant business actions (account creation, successful login, token revoked).
- `log.warn`: expected errors / suspicious attempts (bad credentials, invalid token).
- `log.error`: unexpected exceptions (with stack trace via `log.error("msg", ex)`).
- Never log passwords, full JWT tokens, or sensitive personal data.

### DTOs
- Suffix `Request` for incoming payloads, `Response` for outgoing ones.
- Java records are preferred for responses (immutability).
- Lombok classes with `@Data` or `@Value` are acceptable for requests.

### Configuration
- All sensitive values go through environment variables (`${VAR:default}` in `application.yml`).
- Never hardcode secrets in source code (JWT secret, passwords, etc.).

---

## Best Practices to Apply Consistently

- Respect layer separation without exception.
- Write a test (service or controller) for every new feature.
- Service tests use plain Mockito (no Spring context). Controller tests use `@SpringBootTest` + `@AutoConfigureMockMvc`.
- Use H2 (profile `test`) for tests — never MySQL in tests.
- Apply `@Transactional` on methods that perform multiple database operations in a single request.
- Always return `ResponseEntity<T>` from controllers with the correct HTTP status code.
- Validate inputs at the boundary (controller) — do not re-validate in the service.

---

## Things You Must NEVER Do

- **Do not implement `UserDetails` in the `User` entity** — the separation is intentional.
- **Do not expose JPA entities** directly in HTTP responses.
- **Do not put business logic in controllers** — they delegate, nothing more.
- **Do not access repositories from a controller** — always go through the service.
- **Do not hardcode secrets** (JWT secret, SMTP credentials, etc.).
- **Do not use `@Autowired` on fields** — constructor injection only.
- **Do not throw generic exceptions** (`RuntimeException`, `Exception`) for business cases — create a dedicated exception.
- **Do not break the uniform error format** (`ErrorResponse`) — every new exception must go through `GlobalExceptionHandler`.
- **Do not log sensitive data** (passwords, JWT tokens, personal information).
- **Do not use `ddl-auto: create-drop` in production** — `application.yml` is configured this way for dev only.
- **Do not use `@MockBean`** (Spring Boot 3) — use `@MockitoBean` (Spring Boot 4).
- **Do not import `com.fasterxml.jackson`** directly — use `tools.jackson` (Jackson 3).
- **Do not use `@AutoConfigureMockMvc` from `o.s.boot.test.autoconfigure`** — import from `o.s.boot.webmvc.test.autoconfigure` (Spring Boot 4).

---

## Useful Commands

```bash
# Start the application (with env vars loaded from .env)
export $(grep -v '^#' .env | xargs) && ./mvnw spring-boot:run

# Run all tests (H2 in-memory, no MySQL or SMTP required)
./mvnw test

# Run a specific test class
./mvnw test -Dtest=AuthServiceTest
./mvnw test -Dtest=AuthControllerTest

# Build without running tests
./mvnw package -DskipTests

# Clean the build output
./mvnw clean

# Clean and rebuild
./mvnw clean package

# Start MailHog (local dev mail server)
docker run -d -p 1025:1025 -p 8025:8025 mailhog/mailhog
# UI available at http://localhost:8025

# Inspect the full dependency tree
./mvnw dependency:tree

# Check for available dependency upgrades
./mvnw versions:display-dependency-updates
```

---

## Spring Boot 4 Gotchas (migration traps)

| Spring Boot 3                      | Spring Boot 4                                     |
|------------------------------------|---------------------------------------------------|
| `@MockBean`                        | `@MockitoBean`                                    |
| `@AutoConfigureMockMvc` (boot)     | Same name, different import (`webmvc.test`)       |
| `com.fasterxml.jackson`            | `tools.jackson`                                   |
| `spring-boot-starter-test` alone   | + `spring-boot-starter-webmvc-test` (test scope)  |

`jjwt-jackson` (0.12.x) uses Jackson 2 internally — this is expected and handled by Boot 4.

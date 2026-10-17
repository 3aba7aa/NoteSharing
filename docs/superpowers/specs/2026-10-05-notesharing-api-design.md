# NoteSharing API Design Specification

**Date**: 2026-10-05
**Status**: Approved
**Project**: NoteSharing (Spring Boot 4.1.1, Java 27)

---

## 1. Architecture Overview

### Pattern
Layered architecture: **Controller → Service → Repository**

### Package Structure
```
org.example.notesharing
├── config          # Security, OpenAPI, Database, WebMvc configuration
├── controller      # REST endpoints (/api/v1/...)
├── service         # Business logic, transaction boundaries
├── repository      # Spring Data JPA interfaces
├── dto             # Request/Response DTOs, mappers
├── exception       # Global exception handling, custom exceptions
├── security        # JWT filter, auth entry point, user details
└── util            # Mappers, validators, constants
```

---

## 2. Domain APIs

### Auth (`/api/v1/auth`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/register` | User registration |
| POST | `/login` | Login, returns access + refresh tokens |
| POST | `/refresh` | Refresh access token |
| POST | `/2fa/enable` | Enable 2FA, returns QR code |
| POST | `/2fa/verify` | Verify 2FA code |
| POST | `/logout` | Invalidate refresh token |

### Users (`/api/v1/users`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/me` | Current user profile |
| PUT | `/me` | Update current user profile |
| GET | `/{id}` | Get user by ID |
| GET | `/search?q=` | Search users by username/email |

### Notes (`/api/v1/notes`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `` | Create note |
| GET | `` | List notes (with filters: type, visibility, date range) |
| GET | `/{id}` | Get note by ID |
| PUT | `/{id}` | Update note |
| DELETE | `/{id}` | Delete note |
| POST | `/{id}/share` | Share note with user (custom) |
| DELETE | `/{id}/share/{userId}` | Remove share (custom) |

### Sharing (`/api/v1/notes/{id}/shares`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `` | List note shares |
| PUT | `/{userId}` | Update share permission (VIEW/EDIT) |

### Social (`/api/v1/users`, `/api/v1/friend-requests`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/users/{id}/friend-request` | Send friend request |
| PUT | `/friend-requests/{id}/accept` | Accept friend request |
| DELETE | `/friend-requests/{id}/reject` | Reject friend request |
| DELETE | `/users/{id}/block` | Block user |
| GET | `/users/me/friends` | List friends |
| GET | `/users/me/blocked` | List blocked users |

### Notifications (`/api/v1/notifications`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `` | List notifications (filter: unread) |
| PUT | `/{id}/read` | Mark as read |
| PUT | `/read-all` | Mark all as read |

### Settings (`/api/v1/users/me/settings`)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `` | Get user settings |
| PUT | `` | Update user settings |

---

## 3. Security

### Authentication
- **Password Encoding**: BCrypt (strength 12)
- **JWT**: HS256 algorithm
  - Access Token: 15 minutes
  - Refresh Token: 7 days
- **Token Delivery**: HttpOnly Secure cookies + Authorization: Bearer header support
- **2FA**: TOTP (RFC 6238), QR code at enable, verification at login

### Authorization
- Public: `/api/v1/auth/**`, `/api/v1/users/search`, `/api/v1/users/{id}` (limited)
- Secured: All other endpoints require valid JWT
- Method-level: `@PreAuthorize` on delete, share, block, permission changes

### CORS
- Configured for frontend origin(s), credentials allowed

---

## 4. Data Configuration

### Profiles
| Profile | Database | DDL Mode | Use Case |
|---------|----------|----------|----------|
| `dev` | H2 (file-based) | `create-drop` | Local development |
| `prod` | PostgreSQL | `validate` | Production |
| `test` | Testcontainers (PostgreSQL) | `create-drop` | Integration tests |

### Flyway Migrations
- Location: `src/main/resources/db/migration`
- Naming: `V{version}__{description}.sql`
- Baseline on migrate: true

### Connection Pool
- HikariCP (Spring Boot default)
- Tuned for production in `application-prod.properties`

---

## 5. DTO Strategy

### Request DTOs
- Bean Validation annotations: `@NotBlank`, `@Size`, `@Email`, `@NotNull`, `@Valid`
- Custom validators for enums, date ranges
- No circular references

### Response DTOs
- Flat structure, no bidirectional relationships
- `@JsonInclude(JsonInclude.Include.NON_NULL)`
- Consistent wrapper: `ApiResponse<T>`; list endpoints return `ApiResponse<List<T>>`
  (pagination removed 2026-10-17, see 2026-10-17-remove-pagination-design.md)

### Mappers
- MapStruct (compile-time) for entity ↔ DTO conversion
- Custom mappers for complex transformations

---

## 6. Error Handling

### Global Exception Handler
- `@ControllerAdvice` returning `ProblemDetail` (RFC 7807)
- Structured error responses with: `type`, `title`, `status`, `detail`, `instance`, `timestamp`

### Custom Exceptions
| Exception | HTTP Status | Use Case |
|-----------|-------------|----------|
| `ResourceNotFoundException` | 404 | Entity not found |
| `UnauthorizedException` | 401 | Invalid/missing auth |
| `ForbiddenException` | 403 | Insufficient permissions |
| `ValidationException` | 400 | Business rule violation |
| `ConflictException` | 409 | Duplicate resource |
| `BadRequestException` | 400 | Malformed request |

### Validation
- `@Valid` on `@RequestBody` parameters
- `MethodArgumentNotValidException` → field errors in ProblemDetail

---

## 7. OpenAPI Documentation

### Library
- Springdoc OpenAPI Starter WebMVC UI

### Configuration
- Grouped by domain (Auth, Users, Notes, Sharing, Social, Notifications, Settings)
- JWT Bearer security scheme
- Example values for all DTOs
- Server URLs for dev/prod

### Endpoints
- Swagger UI: `/swagger-ui.html`
- OpenAPI JSON: `/v3/api-docs`

---

## 8. Testing Strategy

### Unit Tests
- Service layer with `@MockBean` repositories
- Mapper tests
- Validator tests
- Target: 80%+ coverage on service layer

### Integration Tests
- `@SpringBootTest` + Testcontainers (PostgreSQL)
- Repository tests (query methods, custom queries)
- Controller tests (MockMvc, full request/response cycle)
- Security tests (authenticated, unauthorized, forbidden scenarios)

### Test Annotations
- `@WithMockUser` for simple role tests
- Custom `@WithJwtUser` for JWT-based tests
- `@Sql` for test data setup

---

## 9. Dependencies to Add

```xml
<!-- Spring Boot Starters (already present) -->
<dependency> <!-- spring-boot-starter-web --> </dependency>
<dependency> <!-- spring-boot-starter-data-jpa --> </dependency>
<dependency> <!-- spring-boot-starter-security --> </dependency>
<dependency> <!-- spring-boot-starter-test --> </dependency>
<dependency> <!-- lombok --> </dependency>

<!-- New Dependencies -->
<dependency> <!-- spring-boot-starter-validation --> </dependency>
<dependency> <!-- spring-boot-starter-mail (for future) --> </dependency>

<!-- Database -->
<dependency> <!-- com.h2database:h2 (dev profile) --> </dependency>
<dependency> <!-- org.postgresql:postgresql (prod/test) --> </dependency>
<dependency> <!-- org.flywaydb:flyway-core --> </dependency>

<!-- Security / JWT -->
<dependency> <!-- io.jsonwebtoken:jjwt-api --> </dependency>
<dependency> <!-- io.jsonwebtoken:jjwt-impl --> </dependency>
<dependency> <!-- io.jsonwebtoken:jjwt-jackson --> </dependency>

<!-- 2FA -->
<dependency> <!-- com.google.zxing:core (QR codes) --> </dependency>
<dependency> <!-- com.google.zxing:javase --> </dependency>

<!-- MapStruct -->
<dependency> <!-- org.mapstruct:mapstruct --> </dependency>
<dependency> <!-- org.mapstruct:mapstruct-processor (annotationProcessor) --> </dependency>

<!-- OpenAPI -->
<dependency> <!-- org.springdoc:springdoc-openapi-starter-webmvc-ui --> </dependency>

<!-- Testcontainers -->
<dependency> <!-- org.testcontainers:junit-jupiter (test) --> </dependency>
<dependency> <!-- org.testcontainers:postgresql (test) --> </dependency>
```

---

## 10. Implementation Phases

### Phase 1: Foundation (Week 1)
1. Database configuration (profiles, Flyway, H2/PostgreSQL)
2. Security config (BCrypt, JWT filter, authentication manager)
3. Global exception handling + ProblemDetail
4. Base DTOs, mappers, ApiResponse wrappers
5. OpenAPI configuration

### Phase 2: Auth & User APIs (Week 1-2)
1. AuthController, AuthService, UserRepository
2. Register, Login, Refresh, Logout
3. 2FA enable/verify (TOTP + QR)
4. UserController (me, profile, search)

### Phase 3: Notes & Sharing APIs (Week 2)
1. NoteRepository, NoteService, NoteController
2. CRUD + filtering/pagination
3. NoteSharingRepository, SharingService
4. Share/Unshare endpoints, permission updates

### Phase 4: Social & Notifications APIs (Week 2-3)
1. UserRelationshipRepository, SocialService
2. Friend requests, accept/reject, block
3. NotificationRepository, NotificationService
3. List, mark read, mark all read

### Phase 5: Settings & Polish (Week 3)
1. UserSettingsController, SettingsService
2. OpenAPI examples, validation refinement
3. Integration tests (Testcontainers)
4. Documentation review

---

## 11. Success Criteria

- [ ] All endpoints return correct HTTP status codes
- [ ] JWT auth works (login, refresh, protected access)
- [ ] 2FA flow works end-to-end
- [ ] Notes CRUD + sharing with permissions
- [ ] Friend requests + blocking works
- [ ] Notifications generated and readable
- [ ] Settings persist and apply
- [ ] Swagger UI accessible and accurate
- [ ] Integration tests pass on PostgreSQL
- [ ] Dev profile runs on H2 without external DB

---

## 12. Out of Scope (Future Iterations)

- Email verification on registration
- Password reset flow
- Note versioning/history
- Rich text note content
- File attachments
- Real-time notifications (WebSocket)
- Admin panel
- Rate limiting
- Audit logging
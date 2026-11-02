# NoteSharing Phase 1 — Foundation, Auth & Users Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up a working, tested REST API for registration, login, token refresh, logout, 2FA-enable scaffolding, and user profile — on a schema that actually persists correctly on both H2 and PostgreSQL.

**Architecture:** Layered Controller → Service → Repository. Stateless JWT auth with BCrypt passwords; refresh tokens are server-side rows so logout and revocation are real. Entities are corrected first, because none of the existing mappings can boot against PostgreSQL.

**Tech Stack:** Spring Boot 4.1.1, Java 27, Spring Web MVC, Spring Data JPA, Spring Security, Lombok, jjwt, ZXing, Flyway, H2 (dev) / PostgreSQL (prod), JUnit 5 + Mockito + Testcontainers.

**Spec:** `docs/superpowers/specs/2026-10-05-notesharing-api-design.md`

---

## Scope

This plan covers **Phase 1 only** (spec §10): foundation, auth, users. Phases 2–5 (notes, sharing, social, notifications, settings) are separate plans, each building on this one's repositories, `ApiResponse`/`ProblemDetail` conventions, and security chain.

---

## Spec Conflicts & Decisions

These were found by reading the existing entities against the approved spec. Each needs a call; the plan proceeds on the default stated, and a reviewer rejecting any line means changing that task.

1. **`TwoFactor` entity cannot hold TOTP.** It has `key`/`time`/`used` — one-time-password fields. Spec §3 mandates TOTP (RFC 6238). **Decision:** repurpose `TwoFactor` to `{id, user, secret, enabledAt}`; drop `time` and `used`. A TOTP secret needs no `used` flag and no per-code row.
2. **No refresh-token storage exists.** Spec §3 requires logout to invalidate; a stateless JWT cannot do that. **Decision:** add a new `RefreshToken` entity `{id, user, tokenId (UUID), expiresAt, revoked}`. New entity, additive only.
3. **Table `User` is a reserved word** in both H2 and PostgreSQL, so the current `@Table(name = "User")` cannot work on the target prod database. **Decision:** rename to `users`; other tables get explicit snake_case names.
4. **`imageData` uses `columnDefinition = "BLOB"`** — MySQL-only syntax; fails on PostgreSQL and H2. **Decision:** drop `columnDefinition`, use `@JdbcTypeCode(SqlTypes.VARBINARY)`.
5. **`NoteSharing` and `TwoFactor` have no `@GeneratedValue`** — ids must be hand-assigned, so inserts fail. **Decision:** add `GenerationType.IDENTITY`.
6. **`Note.ownerId` duplicates `Note.user`.** Two sources of truth for ownership. **Decision:** delete `ownerId`; ownership is `Note.user`.
7. **Enums missing `@Enumerated(EnumType.STRING)`** on `UserSettings.theme`, `UserRelationship.type`, `NoteSharing.permission` — they persist as ordinals, so reordering an enum silently corrupts data. **Decision:** annotate all three.
8. **`UserSettings` mapping is contradictory** — `UserSettings` declares `@MapsId @JoinColumn(name="UserID")` while `User.userSettings` is `@OneToOne` with no `mappedBy`, so JPA expects an owning side on *both*. **Decision:** make `UserSettings` the owning side (it already is) and `User.userSettings` `@OneToOne(mappedBy = "user")`.
9. **`@Data` on entities with collections** gives infinite-recursion JSON and unstable `equals`/`hashCode` across a persisted/loaded pair. **Decision:** replace `@Data` on entities with `@Getter @Setter`, and restrict `@ToString`/`@EqualsAndHashCode` to `id` via `onlyExplicitlyIncluded = true`.
10. **MapStruct omitted.** Spec §5 offered MapStruct *or* manual mappers. Adding a second annotation processor next to Lombok on Java 27 + Boot 4 is build risk with no benefit at this size. **Decision:** hand-written static mappers in `util/mapper`. Revisit if DTO count grows past ~25.
11. **`@MockBean` (spec §8) is deprecated** in modern Spring Boot in favour of `@MockitoBean`. **Decision:** use `@MockitoBean`; if compilation fails, fall back to `@MockBean` and note it.
12. **Testcontainers needs Docker.** **Decision:** gate PostgreSQL integration tests behind a `postgres-it` Maven profile so `mvn test` stays green without Docker; CI runs `-Ppostgres-it`.

---

## Global Constraints

- Spring Boot `4.1.1`, Java `27` — do not change. If either fails to resolve, stop and report rather than downgrading.
- All endpoints live under `/api/v1`.
- BCrypt cost factor **12**, exactly.
- Access token TTL **15 minutes**; refresh token TTL **7 days**.
- JWT signing algorithm **HS256**; secret supplied via `app.jwt.secret` property, never hardcoded in Java.
- Passwords are stored only as BCrypt hashes and must never appear in any response DTO.
- Error responses use Spring's `ProblemDetail` (RFC 7807), never ad-hoc map bodies.
- All enum persistence uses `@Enumerated(EnumType.STRING)`.
- Table names are explicit, lowercase snake_case, plural.
- Existing enum constant names (`RelationshipType.BLOCKED12` etc.) are left alone — social redesign belongs to Phase 4.
- Lombok on entities: `@Getter @Setter`, never `@Data`.
- Every task ends with a green `mvnw test` before its commit.

## Review Focus

The inputs most likely to break this software, none of which the spec spells out:

1. **Email/username differing only by case** (`Bob@x.com` vs `bob@x.com`) must be one account, not two.
2. **A refresh token replayed after logout** must be rejected, not honoured.
3. **Any response body** — registration, login, profile, search — must contain no password or hash field.
4. **`byte[] imageData` and the `users` table** must round-trip on PostgreSQL, not just H2.
5. **A disabled `UserSettings` row or a second `UserSettings` for one user** must not be creatable — one settings row per user, always.

---

## File Structure

```
src/main/java/org/example/notesharing/
├── entity/         User, UserSettings, Note, NoteSharing, UserRelationship,
│                   Notification, TwoFactor, RefreshToken        (corrected)
├── enums/          unchanged                                    (7 enums)
├── repository/     UserRepository, UserSettingsRepository, RefreshTokenRepository
├── security/       JwtService, TokenType, JwtAuthFilter, JwtPrincipal,
│                   JwtAuthenticationEntryPoint,
│                   RestAccessDeniedHandler
├── service/        AuthService, UserService, TwoFactorService
├── controller/     AuthController, UserController
├── dto/
│   ├── auth/       RegisterRequestDto, LoginRequestDto, AuthResponseDto,
│   │               TokenPairDto, RefreshRequestDto, TwoFactorEnrollmentDto
│   ├── user/       UserResponseDto, UserSummaryDto, UpdateUserRequestDto
│   └── common/     ApiResponse, PageResponse
├── mapper/         UserMapper, AuthMapper
├── exception/      ResourceNotFoundException, ConflictException,
│                   UnauthorizedException, ForbiddenException, BusinessRuleException,
│                   GlobalExceptionHandler
└── config/         SecurityConfig, OpenApiConfig, JacksonConfig
src/main/resources/
├── application.properties
├── application-dev.properties
├── application-prod.properties
└── db/migration/V1__baseline_schema.sql
src/test/java/org/example/notesharing/...
```

---

## Task 1: Baseline Build & Toolchain Verification

Establishes that the project compiles and boots today, and surfaces Boot 4 API differences before any code depends on them.

**Files:**
- Modify: `pom.xml`
- Test: `src/test/java/org/example/notesharing/NoteSharingApplicationTests.java` (existing)

**Interfaces:**
- Consumes: nothing
- Produces: a compiling build and a recorded dependency baseline for Tasks 2–10.

- [ ] **Step 1: Run the baseline build and record what actually happens**

Run: `./mvnw -q clean test`
Expected: either PASS, or a concrete error. If it fails on entity mappings (very likely — see the `@GeneratedValue` and bidirectional issues), record the error text in the commit message and continue; Task 2 is what fixes it. Do **not** attempt a fix here.

- [ ] **Step 2: Add the dependencies Phase 1 needs**

Add to `pom.xml` `<dependencies>`: `spring-boot-starter-validation`, `com.h2database:h2` (runtime), `org.postgresql:postgresql` (runtime), `org.flywaydb:flyway-core`, `io.jsonwebtoken:jjwt-api`, `io.jsonwebtoken:jjwt-impl` (runtime), `io.jsonwebtoken:jjwt-jackson` (runtime), `com.google.zxing:core`, `com.google.zxing:javase`, `org.springdoc:springdoc-openapi-starter-webmvc-ui`.

Add to `<build><plugins>`: `org.apache.maven.plugins:maven-compiler-plugin` with
`<annotationProcessorPaths>` containing `org.projectlombok:lombok` and
`<compilerArgs><arg>-Amapstruct.defaultComponentModel=spring</arg></compilerArgs>` is **not** needed (no MapStruct) — keep only the Lombok processor path so Lombok resolves under Java 27.

Then add a `postgres-it` profile to `<profiles>` that sets `spring.profiles.active=test` and binds `failsafe` to `*IT.java`. Leave `maven-surefire-plugin` excluding `**/*IT.java` in the base build.

Pin the Spring Boot version `4.1.1` on every new Boot-managed dependency and omit `<version>` where Boot's BOM covers it (validation, H2, PostgreSQL, Flyway, Testcontainers) so the parent stays authoritative.

- [ ] **Step 3: Verify dependency resolution and the build still compiles**

Run: `./mvnw -q clean compile`
Expected: BUILD SUCCESS. If Springdoc or jjwt fails to resolve against Boot 4.1.1, stop, report the unresolved artifact and version, and remove it — do not substitute a different major version silently.

- [ ] **Step 4: Commit**

```bash
git add pom.xml
git commit -m "build: add validation, jwt, zxing, flyway, testcontainers deps"
```

---

## Task 2: Correct the Entity Mappings

Nothing downstream works until the schema is valid on PostgreSQL. This task also lands the Flyway baseline so `ddl-auto=validate` has something to validate against.

**Files:**
- Modify: `src/main/java/org/example/notesharing/entity/User.java`
- Modify: `src/main/java/org/example/notesharing/entity/UserSettings.java`
- Modify: `src/main/java/org/example/notesharing/entity/Note.java`
- Modify: `src/main/java/org/example/notesharing/entity/NoteSharing.java`
- Modify: `src/main/java/org/example/notesharing/entity/UserRelationship.java`
- Modify: `src/main/java/org/example/notesharing/entity/TwoFactor.java`
- Create: `src/main/java/org/example/notesharing/entity/RefreshToken.java`
- Create: `src/main/resources/db/migration/V1__baseline_schema.sql`
- Test: `src/test/java/org/example/notesharing/repository/EntityMappingIT.java`

**Interfaces:**
- Consumes: nothing
- Produces:
  - `User` with `Long id`, `String username`, `String hashedPassword`, `String email`, `LocalDate bod`, `LocalDateTime createdAt`, `Gender gender`, `byte[] imageData`, and associations `userSettings` (`@OneToOne(mappedBy="user")`), `notes`, `notesSharing`, `twoFactors`, `userRelationships`. **No `notifications` on `User`** (dropped — see below).
  - `UserSettings` with `Long userId`, `User user`, `Theme theme`, `Boolean twoFactorEnabled`, `Boolean notificationEnabled`.
  - `TwoFactor` with `Long id`, `User user`, `String secret`, `LocalDateTime enabledAt` (replaces `key`/`time`/`used`).
  - `RefreshToken` with `Long id`, `User user`, `UUID tokenId`, `LocalDateTime expiresAt`, `Boolean revoked`.
  - `Note` with `Long id`, `String title`, `String content`, `LocalDateTime createdAt`, `modifiedAt`, `startDate`, `endDate`, `NoteType type`, `NoteVisibility visibility`, `Integer notifyThreshold`, `User user`. **No `ownerId`.**
  - `NoteSharing` with `Long id`, `User user`, `Note note`, `NotePermission permission`, unique on `(user, note)`.

- [ ] **Step 1: Write the failing integration test**

Create `src/test/java/org/example/notesharing/repository/EntityMappingIT.java` (suffix `IT` so it is excluded from the default build and runs under `-Ppostgres-it`):

```java
@Test
void userRoundTripsWithImageData() {
    User u = new User();
    u.setUsername("bob");
    u.setHashedPassword("x");
    u.setEmail("bob@x.com");
    u.setGender(Gender.NONE);
    u.setCreatedAt(LocalDateTime.now());
    u.setImageData(new byte[]{1, 2, 3, 4});
    User saved = userRepository.saveAndFlush(u);

    User found = userRepository.findById(saved.getId()).orElseThrow();
    assertThat(found.getImageData()).containsExactly(1, 2, 3, 4);
    assertThat(found.getEmail()).isEqualTo("bob@x.com");
}

@Test
void noteSharingGeneratesItsOwnId() {
    NoteSharing ns = new NoteSharing();
    ns.setUser(user);
    ns.setNote(note);
    ns.setPermission(NotePermission.VIEW);
    assertThat(noteSharingRepository.saveAndFlush(ns).getId()).isNotNull();
}

@Test
void enumsPersistByNameNotOrdinal() {
    UserSettings s = new UserSettings();
    s.setUser(user);
    s.setTheme(Theme.DARK);
    userSettingsRepository.saveAndFlush(s);
    assertThat(userSettingsRepository.findById(user.getId()).orElseThrow().getTheme())
        .isEqualTo(Theme.DARK);
}

@Test
void userHasAtMostOneSettingsRow() {
    UserSettings first = new UserSettings();
    first.setUser(user);
    userSettingsRepository.saveAndFlush(first);
    UserSettings second = new UserSettings();
    second.setUser(user);
    assertThatThrownBy(() -> userSettingsRepository.saveAndFlush(second))
        .isInstanceOf(DataIntegrityViolationException.class);
}
```

Annotate the class `@SpringBootTest`, `@ActiveProfiles("test")`, `@Testcontainers`, `@DynamicPropertySource` wiring `PostgreSQLContainer<?>` (`postgres:16-alpine`) into `spring.datasource.*`.

- [ ] **Step 2: Run it and confirm it fails**

Run: `./mvnw -Ppostgres-it verify -Dtest=EntityMappingIT`
Expected: FAIL — reserved-word table `User`, or `columnDefinition = "BLOB"` rejected by PostgreSQL, or `null id` on `NoteSharing`.

- [ ] **Step 3: Rewrite the entities**

For every entity: `@Getter @Setter`, `@NoArgsConstructor`, explicit `@Table(name = "<snake_case_plural>")`, `@EqualsAndHashCode(onlyExplicitlyIncluded = true)` and `@ToString(onlyExplicitlyIncluded = true)` with `@EqualsAndHashCode.Include` / `@ToString.Include` on `id` only, and `@JsonIgnore` on every association and collection field.

Specifics:
- `User`: `@Table(name = "users")`. Rename `Username`→`username`, `HashedPassword`→`hashedPassword`, `Email`→`email`, `BOD`→`bod`, `CreatedAt`→`createdAt`. `email` gets `@Column(nullable = false, unique = true)`. `imageData` gets `@JdbcTypeCode(SqlTypes.VARBINARY)` and **no** `columnDefinition`. `userSettings` becomes `@OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)`. Drop the `notifications` collection — `Notification.notified` already owns that side; keeping both unidirectional mappings creates two join tables for one relationship.
- `UserSettings`: `@Table(name = "user_settings")`; add `@Enumerated(EnumType.STRING)` to `theme`.
- `Note`: drop `ownerId`; keep `@Enumerated(EnumType.STRING)` on `type` and `visibility`; `@ManyToOne(optional = false) @JoinColumn(name = "user_id")`; drop the `notifications` collection for the same reason as above.
- `NoteSharing`: `@Table(name = "note_sharing")`, `@GeneratedValue(strategy = IDENTITY)`, `@Enumerated(EnumType.STRING)` on `permission`, `@ManyToOne(optional = false)` on both associations, `@Table(uniqueConstraints = @UniqueConstraint(name = "uk_note_sharing_user_note", columnNames = {"user_id", "note_id"}))`.
- `UserRelationship`: `@Table(name = "user_relationship")`, `@Enumerated(EnumType.STRING)` on `type`, `@UniqueConstraint(name = "uk_user_relationship_pair", columnNames = {"first_user_id", "second_user_id"})`.
- `Notification`: keep as-is apart from `@JsonIgnore`, `@Getter/@Setter`, and the include-only-`id` equality/toString.
- `TwoFactor`: `@Table(name = "two_factor")`, `@GeneratedValue(strategy = IDENTITY)`, `@ManyToOne(optional = false)`, fields become `secret` and `enabledAt`.
- `RefreshToken` (new): `@Entity @Table(name = "refresh_token") @Getter @Setter @NoArgsConstructor`, `@Id @GeneratedValue(strategy = IDENTITY) Long id`, `@ManyToOne(optional = false) User user`, `@Column(nullable = false, unique = true) UUID tokenId`, `@Column(nullable = false) LocalDateTime expiresAt`, `@Column(nullable = false) Boolean revoked`, plus `@UniqueConstraint(name = "uk_refresh_token_token_id", columnNames = {"token_id"})`.

- [ ] **Step 4: Write the matching Flyway baseline**

Create `V1__baseline_schema.sql` with hand-written DDL matching the mapping exactly — column names snake_case, `bytea` for `imageData`, `varchar(255)` for enums-as-string, the two unique constraints, `timestamptz` for `LocalDateTime` columns. Since `ddl-auto=validate` compares Hibernate's expected schema to this file, every column and constraint must exist here.

- [ ] **Step 5: Run the test and confirm it passes**

Run: `./mvnw -Ppostgres-it verify -Dtest=EntityMappingIT`
Expected: PASS on PostgreSQL. If Docker is unavailable, note it and run `./mvnw test` to confirm nothing else regressed.

- [ ] **Step 6: Commit**

```bash
git add -A src/main/java/org/example/notesharing/entity src/main/resources/db/migration src/test
git commit -m "fix: correct JPA mappings for PostgreSQL portability"
```

---

## Task 3: Application Profiles & Configuration

**Files:**
- Modify: `src/main/resources/application.properties`
- Create: `src/main/resources/application-dev.properties`
- Create: `src/main/resources/application-prod.properties`
- Create: `src/test/resources/application-test.properties`
- Test: `src/test/java/org/example/notesharing/config/ProfileConfigTest.java`

**Interfaces:**
- Consumes: entities from Task 2
- Produces: properties `app.jwt.secret`, `app.jwt.access-token-ttl`, `app.jwt.refresh-token-ttl`, `app.jwt.issuer`, `app.cors.allowed-origins` — read by Tasks 5 and 9. Profile `dev` runs on H2; `test` uses Testcontainers-injected datasource.

- [ ] **Step 1: Write the failing test**

```java
@SpringBootTest
@ActiveProfiles("dev")
class ProfileConfigTest {
    @Autowired Environment env;
    @Test void devUsesH2AndCreateDrop() {
        assertThat(env.getProperty("spring.datasource.url")).startsWith("jdbc:h2:mem:");
        assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("create-drop");
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./mvnw test -Dtest=ProfileConfigTest`
Expected: FAIL — no datasource configured.

- [ ] **Step 3: Write the property files**

`application.properties`: `spring.application.name=NoteSharing`, `spring.jpa.open-in-view=false`, `spring.jackson.serialization.fail-on-empty-beans=false`, `app.jwt.access-token-ttl=PT15M`, `app.jwt.refresh-token-ttl=P7D`, `app.jwt.issuer=notesharing`. Leave `app.jwt.secret` **unset** here.

`application-dev.properties`: `spring.datasource.url=jdbc:h2:mem:notesharing;DB_CLOSE_DELAY=-1`, driver `org.h2.Driver`, `spring.jpa.hibernate.ddl-auto=create-drop`, `spring.h2.console.enabled=true`, `app.jwt.secret=` a ≥32-byte dev-only value.

`application-prod.properties`: `spring.datasource.url=${DB_URL}`, `spring.datasource.username=${DB_USERNAME}`, `spring.datasource.password=${DB_PASSWORD}`, `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect`, `spring.flyway.enabled=true`, `app.jwt.secret=${JWT_SECRET}`, `app.cors.allowed-origins=${CORS_ORIGINS}`.

`src/test/resources/application-test.properties`: `spring.jpa.hibernate.ddl-auto=validate`, `spring.flyway.enabled=true`, `app.jwt.secret=` a test-only value.

Add a startup guard in `config/JwtProperties` — if `app.jwt.secret` is blank or under 32 bytes while the `prod` profile is active, throw `IllegalStateException` at bean creation. Test that guard in this task too.

- [ ] **Step 4: Run the test and verify it passes**

Run: `./mvnw test -Dtest=ProfileConfigTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/resources src/test/resources src/main/java/org/example/notesharing/config src/test
git commit -m "feat: add dev/prod/test profiles and jwt properties"
```

---

## Task 4: Common Response Types & Global Exception Handling

**Files:**
- Create: `src/main/java/org/example/notesharing/dto/common/ApiResponse.java`
- Create: `src/main/java/org/example/notesharing/dto/common/PageResponse.java`
- Create: `src/main/java/org/example/notesharing/exception/{ResourceNotFoundException,ConflictException,UnauthorizedException,ForbiddenException,BusinessRuleException}.java`
- Create: `src/main/java/org/example/notesharing/exception/GlobalExceptionHandler.java`
- Test: `src/test/java/org/example/notesharing/exception/GlobalExceptionHandlerTest.java`

**Interfaces:**
- Consumes: nothing
- Produces:
  - `record ApiResponse<T>(T data, String message, Instant timestamp)`
  - `record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages)` with static factory `static <T> PageResponse<T> from(Page<T> page)` that copies `getContent()`, `getNumber()`, `getSize()`, `getTotalElements()` and `getTotalPages()`.
  - Exceptions extending `RuntimeException` with an `HttpStatus status` field.
  - `GlobalExceptionHandler` with `@ExceptionHandler` methods for each custom exception, `MethodArgumentNotValidException`, `HttpMessageNotReadableException`, and `Exception`.

- [ ] **Step 1: Write the failing tests**

Use `@WebMvcTest` with a stub controller, or simpler: `new GlobalExceptionHandler()` called directly. Direct instantiation is faster and has no Spring context:

```java
@Test void notFoundBecomes404ProblemDetail() {
    ProblemDetail pd = handler.handleNotFound(new ResourceNotFoundException("Note", 7L));
    assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(pd.getTitle()).isEqualTo("Note not found");
    assertThat(pd.getDetail()).contains("7");
}

@Test void validationErrorsListFieldNames() {
    var errors = new BeanPropertyBindingResult(new RegisterRequestDto(), "dto");
    errors.rejectValue("email", "invalid", "must be well-formed");
    MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
        MethodParameter.from(RegisterRequestDto.class), errors);
    ProblemDetail pd = handler.handleValidation(ex);
    assertThat(pd.getProperties()).containsKey("errors");
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./mvnw test -Dtest=GlobalExceptionHandlerTest`
Expected: FAIL — classes do not exist.

- [ ] **Step 3: Implement**

Each custom exception carries a `HttpStatus` and a message template. `GlobalExceptionHandler` is `@RestControllerAdvice` returning `ProblemDetail` (Spring 6+ native type) and sets `pd.setInstance(URI.create("/api/v1" + request.getRequestURI()))` where a `HttpServletRequest` is available. Titles are fixed strings (`"Note not found"`, `"Validation failed"`, `"Unauthorized"`, `"Forbidden"`, `"Conflict"`, `"Internal server error"`); the 500 handler logs the exception and returns a `ProblemDetail` with no internal detail, so stack traces never reach the client.

- [ ] **Step 4: Run to verify they pass**

Run: `./mvnw test -Dtest=GlobalExceptionHandlerTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/example/notesharing/dto/common src/main/java/org/example/notesharing/exception src/test
git commit -m "feat: add ApiResponse, PageResponse and RFC7807 error handling"
```

---

## Task 5: Repositories

**Files:**
- Create: `src/main/java/org/example/notesharing/repository/{UserRepository,UserSettingsRepository,RefreshTokenRepository}.java`
- Test: `src/test/java/org/example/notesharing/repository/UserRepositoryIT.java`

**Interfaces:**
- Consumes: entities (Task 2), profiles (Task 3)
- Produces:
  - `UserRepository extends JpaRepository<User, Long>`: `Optional<User> findByEmailIgnoreCase(String email)`, `boolean existsByEmailIgnoreCase(String email)`, `Optional<User> findByUsernameIgnoreCase(String username)`, `boolean existsByUsernameIgnoreCase(String username)`, `@Query("select u from User u where lower(u.username) like lower(concat('%', :q, '%')) or lower(u.email) like lower(concat('%', :q, '%'))") Page<User> search(@Param("q") String q, Pageable pageable)`
  - `UserSettingsRepository extends JpaRepository<UserSettings, Long>`
  - `RefreshTokenRepository extends JpaRepository<RefreshToken, Long>`: `Optional<RefreshToken> findByTokenId(UUID tokenId)`, `@Modifying @Query("update RefreshToken r set r.revoked = true where r.user.id = :userId and r.revoked = false") int revokeAllForUser(@Param("userId") Long userId)`

- [ ] **Step 1: Write the failing test**

```java
@Test void lookupIsCaseInsensitiveOnEmail() {
    userRepository.saveAndFlush(user("Bob@Example.com"));
    assertThat(userRepository.findByEmailIgnoreCase("bob@example.COM")).isPresent();
}

@Test void searchMatchesUsernameOrEmailCaseInsensitively() {
    userRepository.saveAndFlush(user("Alice@x.com"));
    var page = userRepository.search("ALICE", PageRequest.of(0, 10));
    assertThat(page.getTotalElements()).isEqualTo(1);
}

@Test void revokeAllForUserOnlyTouchesThatUsersTokens() {
    refreshTokenRepository.saveAndFlush(token(userA));
    refreshTokenRepository.saveAndFlush(token(userB));
    assertThat(refreshTokenRepository.revokeAllForUser(userA.getId())).isEqualTo(1);
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw -Ppostgres-it verify -Dtest=UserRepositoryIT`
Expected: FAIL — repository interfaces missing.

- [ ] **Step 3: Implement the three interfaces**

Method declarations only, derived-query names exactly as above so Spring Data generates the SQL.

- [ ] **Step 4: Run to verify it passes**

Run: `./mvnw -Ppostgres-it verify -Dtest=UserRepositoryIT`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/example/notesharing/repository src/test
git commit -m "feat: add User, UserSettings and RefreshToken repositories"
```

---

## Task 6: Security Foundation (BCrypt + JWT)

**Files:**
- Create: `src/main/java/org/example/notesharing/security/{TokenType,JwtService,JwtPrincipal,JwtAuthFilter,JwtAuthenticationEntryPoint,RestAccessDeniedHandler}.java`
- Create: `src/main/java/org/example/notesharing/config/SecurityConfig.java`
- Test: `src/test/java/org/example/notesharing/security/JwtServiceTest.java`
- Test: `src/test/java/org/example/notesharing/security/JwtAuthFilterTest.java`

**Interfaces:**
- Consumes: `JwtProperties` (Task 3), `UserRepository` (Task 5)
- Produces:
  - `enum TokenType { ACCESS, REFRESH }`
  - `record JwtPrincipal(Long userId, String username)` carrying the authenticated user id.
  - `JwtService`: `String generateAccessToken(Long userId, String username)`, `String generateRefreshToken(Long userId, UUID tokenId)`, `String generateAccessTokenWithTtl(Long userId, String username, Duration ttl)` (test seam only), `Optional<ParsedToken> parse(String token)`, `Optional<ParsedToken> parseAs(String token, TokenType required)`, `long getExpiresInSeconds(TokenType type)`, where `record ParsedToken(Long userId, UUID tokenId, TokenType type, String username)`.
  - `JwtAuthFilter extends OncePerRequestFilter` — populates the SecurityContext from a valid `Authorization: Bearer` header, setting a `JwtPrincipal`.
  - `SecurityConfig` exposing `SecurityFilterChain` and `PasswordEncoder` (BCrypt cost 12). Authentication is performed by `AuthService` calling `passwordEncoder.matches` directly, so no `UserDetailsService` or `AuthenticationManager` is needed.

- [ ] **Step 1: Write the failing `JwtServiceTest`**

```java
@Test void accessTokenCarriesUserIdAndType() {
    String token = jwtService.generateAccessToken(42L, "bob");
    JwtService.ParsedToken parsed = jwtService.parse(token).orElseThrow();
    assertThat(parsed.userId()).isEqualTo(42L);
    assertThat(parsed.type()).isEqualTo(TokenType.ACCESS);
}

@Test void refreshTokenIsNotAcceptedAsAccessToken() {
    String token = jwtService.generateRefreshToken(42L, UUID.randomUUID());
    assertThat(jwtService.parseAs(token, TokenType.ACCESS)).isEmpty();
}

@Test void tamperedTokenIsRejected() {
    String token = jwtService.generateAccessToken(42L, "bob");
    String tampered = token.substring(0, token.length() - 2) + "xy";
    assertThat(jwtService.parse(tampered)).isEmpty();
}

@Test void expiredTokenIsRejected() {
    String token = jwtService.generateAccessTokenWithTtl(42L, "bob", Duration.ofMillis(-1));
    assertThat(jwtService.parse(token)).isEmpty();
}
```

`@ExtendWith(MockitoExtension.class)` with `JwtProperties` set directly — no Spring context.

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw test -Dtest=JwtServiceTest`
Expected: FAIL — `JwtService` not found.

- [ ] **Step 3: Implement `JwtService`**

jjwt 0.12+ API: `Jwts.builder().subject(...).claim("typ", type.name()).claim("jti", tokenId).issuer(issuer).issuedAt(now).expiration(exp).signWith(key).compact()`, and `Jwts.parser().verifyWith(key).requireIssuer(issuer).build().parseSignedClaims(token)`. Reject any token whose `typ` claim does not equal the requested `TokenType` — that is what stops a refresh token being replayed as a bearer credential. Catch `JwtException`/`IllegalArgumentException` and return `Optional.empty()`.

- [ ] **Step 4: Write the failing `JwtAuthFilterTest`**

```java
@Test void validBearerTokenPopulatesSecurityContext() {
    String token = jwtService.generateAccessToken(7L, "bob");
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("Authorization", "Bearer " + token);
    filter.doFilter(req, new MockHttpServletResponse(), (r, s) -> {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(((JwtPrincipal) auth.getPrincipal()).userId()).isEqualTo(7L);
    });
}

@Test void malformedHeaderLeavesContextEmptyAndContinuesChain() {
    MockHttpServletRequest req = new MockHttpServletRequest();
    req.addHeader("Authorization", "Bearer not-a-jwt");
    filter.doFilter(req, new MockHttpServletResponse(), (r, s) ->
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());
}
```

- [ ] **Step 5: Run to verify it fails**

Run: `./mvnw test -Dtest=JwtAuthFilterTest`
Expected: FAIL.

- [ ] **Step 6: Implement the filter, entry point, and `SecurityConfig`**

`SecurityConfig`: `SessionCreationPolicy.STATELESS`, `csrf(...).disable()`, permit `/api/v1/auth/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/h2-console/**`; everything else `authenticated()`. `JwtAuthenticationEntryPoint` writes a 401 `ProblemDetail`; `RestAccessDeniedHandler` writes a 403. `httpBasic` and `formLogin` disabled.

- [ ] **Step 7: Run to verify they pass**

Run: `./mvnw test -Dtest=JwtServiceTest+JwtAuthFilterTest`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/org/example/notesharing/security src/main/java/org/example/notesharing/config src/test
git commit -m "feat: add BCrypt + JWT security foundation"
```

---

## Task 7: Registration

**Files:**
- Create: `src/main/java/org/example/notesharing/dto/auth/RegisterRequestDto.java`
- Create: `src/main/java/org/example/notesharing/dto/user/UserResponseDto.java`
- Create: `src/main/java/org/example/notesharing/mapper/UserMapper.java`
- Create: `src/main/java/org/example/notesharing/service/AuthService.java`
- Create: `src/main/java/org/example/notesharing/controller/AuthController.java`
- Test: `src/test/java/org/example/notesharing/service/AuthServiceTest.java`
- Test: `src/test/java/org/example/notesharing/controller/AuthControllerTest.java`

**Interfaces:**
- Consumes: `UserRepository`, `UserSettingsRepository`, `JwtService`, `PasswordEncoder`, `ApiResponse`, `GlobalExceptionHandler`
- Produces:
  - `record RegisterRequestDto(@NotBlank @Size(min=3, max=50) String username, @NotBlank @Email String email, @NotBlank @Size(min=8, max=72) String password, LocalDate bod, Gender gender)`
  - `record UserResponseDto(Long id, String username, String email, LocalDate bod, Gender gender, LocalDateTime createdAt)` — **no password/hash/imageData fields**
  - `UserMapper.toResponse(User)`, `UserMapper.toEntity(RegisterRequestDto)`
  - `AuthService.register(RegisterRequestDto) -> TokenPairDto`
  - `POST /api/v1/auth/register` → `201 Created`, body `ApiResponse<TokenPairDto>`

- [ ] **Step 1: Write the failing service test**

```java
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @MockitoBean UserRepository users;
    @MockitoBean UserSettingsRepository settings;
    @MockitoBean RefreshTokenRepository tokens;
    @Mock PasswordEncoder encoder;

    @Test void duplicateEmailIsRejectedCaseInsensitively() {
        given(users.existsByEmailIgnoreCase("bob@x.com")).willReturn(true);
        assertThatThrownBy(() -> service.register(dto("Bob@X.com", "bob")))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("Email");
    }

    @Test void passwordIsHashedNotStored() {
        given(users.existsByEmailIgnoreCase(anyString())).willReturn(false);
        given(users.save(any())).willAnswer(inv -> inv.getArgument(0));
        service.register(dto("bob@x.com", "bob"));
        ArgumentCaptor<User> cap = ArgumentCaptor.forClass(User.class);
        verify(users).save(cap.capture());
        verify(encoder).encode("plain-password");
        assertThat(cap.getValue().getHashedPassword()).isNotEqualTo("plain-password");
    }

    @Test void settingsRowIsCreatedAlongsideUser() {
        given(users.save(any())).willAnswer(inv -> { User u = inv.getArgument(0); u.setId(1L); return u; });
        service.register(dto("bob@x.com", "bob"));
        verify(settings).save(argThat(s -> s.getTheme() == Theme.SYSTEM));
    }

    @Test void responseDtoHasNoPasswordField() {
        assertThat(UserResponseDto.class.getRecordComponents())
            .extracting(RecordComponent::getName)
            .doesNotContain("password", "hashedPassword");
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw test -Dtest=AuthServiceTest`
Expected: FAIL.

- [ ] **Step 3: Implement DTOs, mapper, service, controller**

`AuthService.register`: check `existsByEmailIgnoreCase` then `existsByUsernameIgnoreCase` (throw `ConflictException` naming the offending field), build the entity, `passwordEncoder.encode`, `user.setCreatedAt(LocalDateTime.now())`, save, save a `UserSettings` with `theme = Theme.SYSTEM`, `twoFactorEnabled = false`, `notificationEnabled = true`, persist a `RefreshToken` row, and return access + refresh tokens via `JwtService`.

`AuthController.register` is annotated `@Valid @RequestBody`, returns `ResponseEntity.status(CREATED).body(new ApiResponse<>(tokens, "Registered", Instant.now()))`.

- [ ] **Step 4: Run to verify it passes**

Run: `./mvnw test -Dtest=AuthServiceTest`
Expected: PASS.

- [ ] **Step 5: Write the failing controller test**

`@WebMvcTest(AuthController.class)` with `@MockitoBean AuthService` and `@Import(SecurityConfig.class, GlobalExceptionHandler.class)`:

```java
@Test void registerReturns201WithTokens() throws Exception {
    given(authService.register(any())).willReturn(pair);
    mockMvc.perform(post("/api/v1/auth/register")
            .contentType(APPLICATION_JSON)
            .content("""
                {"username":"bob","email":"bob@x.com","password":"plain-password",
                 "bod":"1990-01-01","gender":"NONE"}"""))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());
}

@Test void registerRejectsShortPassword() throws Exception {
    mockMvc.perform(post("/api/v1/auth/register")
            .contentType(APPLICATION_JSON)
            .content("""
                {"username":"bob","email":"bob@x.com","password":"short"}"""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("password"));
}

@Test void registerNeverEchoesPassword() throws Exception {
    // assert the serialized response body does not contain "plain-password"
}
```

- [ ] **Step 6: Run to verify it fails, then implement until green**

Run: `./mvnw test -Dtest=AuthControllerTest`
Expected: FAIL, then PASS after adjusting the controller's response shape.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/example/notesharing/dto src/main/java/org/example/notesharing/mapper src/main/java/org/example/notesharing/service src/main/java/org/example/notesharing/controller src/test
git commit -m "feat: add user registration endpoint"
```

---

## Task 8: Login, Refresh, Logout

**Files:**
- Create: `src/main/java/org/example/notesharing/dto/auth/{LoginRequestDto,TokenPairDto,RefreshRequestDto}.java`
- Create: `src/main/java/org/example/notesharing/mapper/AuthMapper.java`
- Modify: `src/main/java/org/example/notesharing/service/AuthService.java`
- Modify: `src/main/java/org/example/notesharing/controller/AuthController.java`
- Test: `src/test/java/org/example/notesharing/service/AuthServiceLoginTest.java`

**Interfaces:**
- Consumes: Tasks 5, 6, 7
- Produces:
  - `record LoginRequestDto(@NotBlank @Email String email, @NotBlank String password)` — login is by email, not username.
  - `record RefreshRequestDto(@NotBlank String refreshToken)`
  - `record TokenPairDto(String accessToken, String refreshToken, long expiresInSeconds)`
  - `AuthService.login(LoginRequestDto)`, `AuthService.refresh(String)`, `AuthService.logout(Long userId, String)`
  - `POST /api/v1/auth/login` → 200, `POST /api/v1/auth/refresh` → 200, `POST /api/v1/auth/logout` → 204

- [ ] **Step 1: Write the failing tests**

```java
@Test void loginWithWrongPasswordIsUnauthorized() {
    given(users.findByEmailIgnoreCase("bob@x.com")).willReturn(Optional.of(user));
    given(encoder.matches("wrong", user.getHashedPassword())).willReturn(false);
    assertThatThrownBy(() -> service.login(new LoginRequestDto("bob@x.com", "wrong")))
        .isInstanceOf(UnauthorizedException.class);
}

@Test void loginWithUnknownEmailIsUnauthorizedNotNotFound() {
    given(users.findByEmailIgnoreCase("ghost@x.com")).willReturn(Optional.empty());
    assertThatThrownBy(() -> service.login(new LoginRequestDto("ghost@x.com", "x")))
        .isInstanceOf(UnauthorizedException.class);
}

@Test void refreshRotatesTheTokenAndRevokesTheOld() {
    var old = persistRefreshToken(user);
    var pair = service.refresh(jwtService.generateRefreshToken(user.getId(), old.getTokenId()));
    assertThat(pair.refreshToken()).isNotEqualTo(old.getTokenId().toString());
    assertThat(refreshTokenRepository.findByTokenId(old.getTokenId()).orElseThrow().getRevoked())
        .isTrue();
}

@Test void refreshWithRevokedTokenIsUnauthorized() {
    var old = persistRefreshToken(user);
    old.setRevoked(true);
    assertThatThrownBy(() ->
        service.refresh(jwtService.generateRefreshToken(user.getId(), old.getTokenId())))
        .isInstanceOf(UnauthorizedException.class);
}

@Test void logoutRevokesEveryTokenForTheUser() {
    service.logout(user.getId(), "any-token");
    verify(tokens).revokeAllForUser(user.getId());
}
```

`refreshWithRevokedTokenIsUnauthorized` is the replay case from Review Focus item 2.

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw test -Dtest=AuthServiceLoginTest`
Expected: FAIL.

- [ ] **Step 3: Implement**

`login`: look up by `findByEmailIgnoreCase`; on empty **or** `encoder.matches` false throw `UnauthorizedException("Invalid email or password")` — the same message both ways, so the endpoint does not confirm which emails exist.

`refresh`: parse the token as `TokenType.REFRESH`; on invalid/expired throw `UnauthorizedException`. Look up `RefreshToken` by `tokenId`; reject if absent, `revoked`, or `expiresAt` before now. Then revoke the presented row, issue a fresh `RefreshToken` row and token pair (rotation).

`logout`: `revokeAllForUser(userId)` — idempotent, returns 204 with an empty body.

- [ ] **Step 4: Run to verify it passes**

Run: `./mvnw test -Dtest=AuthServiceLoginTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/example/notesharing/dto/auth src/main/java/org/example/notesharing/mapper src/main/java/org/example/notesharing/service src/main/java/org/example/notesharing/controller src/test
git commit -m "feat: add login, refresh rotation and logout endpoints"
```

---

## Task 9: 2FA Enrolment (TOTP)

**Files:**
- Create: `src/main/java/org/example/notesharing/dto/auth/TwoFactorEnrollmentDto.java`
- Create: `src/main/java/org/example/notesharing/repository/TwoFactorRepository.java`
- Create: `src/main/java/org/example/notesharing/service/TwoFactorService.java`
- Modify: `src/main/java/org/example/notesharing/controller/AuthController.java`
- Test: `src/test/java/org/example/notesharing/service/TwoFactorServiceTest.java`

**Interfaces:**
- Consumes: `TwoFactor` (Task 2), `JwtPrincipal` (Task 6), `UserSettingsRepository` (Task 5)
- Produces:
  - `TwoFactorRepository extends JpaRepository<TwoFactor, Long>`: `Optional<TwoFactor> findByUserId(Long userId)`
  - `record TwoFactorEnrollmentDto(String secret, String qrCodeDataUri, boolean enabled)`
  - `TwoFactorService.enrol(Long userId) -> TwoFactorEnrollmentDto`, `verify(Long userId, String code) -> boolean`
  - `POST /api/v1/auth/2fa/enable` → 200, `POST /api/v1/auth/2fa/verify` → 204

- [ ] **Step 1: Write the failing tests**

```java
@Test void enrolGeneratesSecretAndQrPng() {
    var dto = service.enrol(user.getId());
    assertThat(dto.secret()).hasSizeGreaterThanOrEqualTo(16);
    assertThat(dto.qrCodeDataUri()).startsWith("data:image/png;base64,");
    assertThat(twoFactorRepository.findByUserId(user.getId())).isPresent();
}

@Test void verifyAcceptsCurrentTotpAndFlipsTheFlag() {
    service.enrol(user.getId());
    var secret = twoFactorRepository.findByUserId(user.getId()).orElseThrow().getSecret();
    assertThat(service.verify(user.getId(), currentCode(secret))).isTrue();
    assertThat(userSettingsRepository.findById(user.getId()).orElseThrow().getTwoFactorEnabled())
        .isTrue();
}

@Test void verifyRejectsWrongCode() {
    service.enrol(user.getId());
    assertThat(service.verify(user.getId(), "000000")).isFalse();
}
```

`currentCode(secret)` generates a valid code with the ZXing `TOTP` class at the current 30-second step.

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw test -Dtest=TwoFactorServiceTest`
Expected: FAIL.

- [ ] **Step 3: Implement**

`enrol`: generate a 20-byte `SecureRandom` secret, Base32-encode it, upsert the `TwoFactor` row with `enabledAt = now()`, and render the `otpauth://totp/NoteSharing:{email}?secret={secret}&issuer=NoteSharing` URI to a PNG via ZXing into a `data:image/png;base64,` URI. Do **not** set `twoFactorEnabled` yet — it flips only after a successful `verify`.

`verify`: validate the six-digit code against the stored secret with a one-step-window tolerance, and on success set `UserSettings.twoFactorEnabled = true`.

Note for Phase 2's login flow: `login` must branch to a 2FA challenge when `twoFactorEnabled` is true. That branch is **not** built here — record it as the first item of the Phase 2 plan.

- [ ] **Step 4: Run to verify it passes**

Run: `./mvnw test -Dtest=TwoFactorServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/example/notesharing/dto/auth src/main/java/org/example/notesharing/repository/TwoFactorRepository.java src/main/java/org/example/notesharing/service/TwoFactorService.java src/main/java/org/example/notesharing/controller/AuthController.java src/test
git commit -m "feat: add TOTP two-factor enrolment"
```

---

## Task 10: User Profile & Search

**Files:**
- Create: `src/main/java/org/example/notesharing/dto/user/{UserSummaryDto,UpdateUserRequestDto}.java`
- Create: `src/main/java/org/example/notesharing/service/UserService.java`
- Create: `src/main/java/org/example/notesharing/controller/UserController.java`
- Create: `src/main/java/org/example/notesharing/controller/GlobalUserAdvice.java`
- Test: `src/test/java/org/example/notesharing/controller/UserControllerTest.java`

**Interfaces:**
- Consumes: `UserRepository` (Task 5), `JwtPrincipal` (Task 6)
- Produces:
  - `record UserSummaryDto(Long id, String username, Gender gender)`
  - `record UpdateUserRequestDto(@Size(min=3, max=50) String username, LocalDate bod, Gender gender, MultipartFile imageData)` — image optional; omit to leave unchanged.
  - `UserService.getById(Long)`, `update(Long, UpdateUserRequestDto)`, `search(String, Pageable)`
  - `GET /api/v1/users/me` → 200 `UserResponseDto`
  - `PUT /api/v1/users/me` → 200 `UserResponseDto`
  - `GET /api/v1/users/{id}` → 200 `UserSummaryDto`
  - `GET /api/v1/users/search?q=&page=&size=` → 200 `PageResponse<UserSummaryDto>`

- [ ] **Step 1: Write the failing controller tests**

`@WebMvcTest(UserController.class)` with `@MockitoBean UserService` and a helper that sets a `JwtPrincipal` into the SecurityContext:

```java
@Test void meReturnsProfileWithoutHash() throws Exception {
    mockMvc.perform(get("/api/v1/users/me").with(jwt(1L, "bob")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.username").value("bob"))
        .andExpect(jsonPath("$.data.hashedPassword").doesNotExist());
}

@Test void meWithoutTokenIs401() throws Exception {
    mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
}

@Test void unknownUserIs404ProblemDetail() throws Exception {
    given(service.getById(99L)).willThrow(new ResourceNotFoundException("User", 99L));
    mockMvc.perform(get("/api/v1/users/99"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.title").value("User not found"));
}

@Test void searchReturnsPagedSummaries() throws Exception {
    mockMvc.perform(get("/api/v1/users/search?q=bob&page=0&size=10"))
        .andExpect(jsonPath("$.totalElements").value(1));
}

@Test void updateRejectsInvalidGender() throws Exception {
    mockMvc.perform(put("/api/v1/users/me").with(jwt(1L, "bob"))
            .contentType(APPLICATION_JSON).content("{\"gender\":\"ALIEN\"}"))
        .andExpect(status().isBadRequest());
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./mvnw test -Dtest=UserControllerTest`
Expected: FAIL.

- [ ] **Step 3: Implement**

`GlobalUserAdvice` is a `@ControllerAdvice` with a single `@ModelAttribute("currentUserId") Long currentUserId()` reading the `JwtPrincipal` from the SecurityContext and returning `null` when absent — controllers take `currentUserId` as a parameter, so they never touch the SecurityContext directly.

`UserController.search` clamps `size` to `max 100` and rejects a blank `q` with `BadRequestException`.

- [ ] **Step 4: Run to verify it passes**

Run: `./mvnw test -Dtest=UserControllerTest`
Expected: PASS.

- [ ] **Step 5: OpenAPI configuration**

Create `config/OpenApiConfig.java` exposing a `GroupedOpenApi` per domain (auth, users) with `SecurityRequirement` on the JWT bearer scheme; add `@Tag`/`@Operation` annotations to both controllers. Verify `/v3/api-docs` renders by loading it in `UserControllerTest` with `MockMvc` and asserting a non-empty `paths` object.

- [ ] **Step 6: Run the whole suite**

Run: `./mvnw clean test`
Expected: BUILD SUCCESS, all tests green.

- [ ] **Step 7: Verify against PostgreSQL**

Run: `./mvnw -Ppostgres-it clean verify`
Expected: PASS, including `EntityMappingIT` and `UserRepositoryIT`. If Docker is unavailable, record that and re-run `./mvnw clean test` as the gate.

- [ ] **Step 8: Commit**

```bash
git add -A src
git commit -m "feat: add user profile and search endpoints with OpenAPI docs"
```

---

## Definition of Done

- [ ] `./mvnw clean test` is green with no Docker required
- [ ] `./mvnw -Ppostgres-it clean verify` is green on PostgreSQL 16
- [ ] `POST /api/v1/auth/register` returns 201 and persists a `users`, `user_settings` and `refresh_token` row
- [ ] `POST /api/v1/auth/login` returns 200 with an access/refresh pair; wrong password and unknown email both return the same 401 body
- [ ] A revoked refresh token cannot be exchanged
- [ ] `POST /api/v1/auth/logout` revokes all of that user's refresh tokens
- [ ] No response body anywhere contains a password or hash
- [ ] `/swagger-ui.html` and `/v3/api-docs` render the auth and users endpoints
- [ ] All errors are RFC 7807 `ProblemDetail`

## Follow-up Plans

Phase 2 (notes + sharing) starts by adding the 2FA challenge branch to `AuthService.login`. Phase 4 (social) must decide whether `RelationshipType`'s direction-encoded constants (`BLOCKED12`, `FRIENDREQ21`) are kept or replaced by a directional relationship plus a status column.

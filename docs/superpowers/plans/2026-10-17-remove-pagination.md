# Remove Pagination Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove pagination from all nine list endpoints so each returns its full matching set as a plain array inside `ApiResponse`.

**Architecture:** Approach A. Repositories return `List` instead of `Page`, and ordering moves out of `Pageable` into explicit `order by` clauses in the queries. Services return `List<Dto>`; controllers return `ApiResponse<List<Dto>>`. `PageResponse` and `SafePageable` are deleted once no caller remains.

**Tech Stack:** Java 27, Spring Boot 4, Spring Data JPA, Hibernate, H2 (dev), PostgreSQL 16 (integration tests), Maven.

**Spec:** `docs/superpowers/specs/2026-10-17-remove-pagination-design.md` — the plan argues from the spec; executors read both.

## Global Constraints

- `JAVA_HOME` is **not** persisted machine-wide. Every build must set it first: `$env:JAVA_HOME = "C:\Program Files\Java\jdk-27"`.
- Kill the running app before any `mvn clean`; it holds `target\run.log`. `Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force`.
- Dev run command: `.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=dev'`. Dev is in-memory H2 with `create-drop`, so **every restart is a clean slate** — assertion scripts must register their own users and may not rely on prior state.
- Verification gate: `Select-String -Path target\run.log -Pattern "\sERROR\s"` and `Unhandled exception` must both return **0** matches.
- JSON nulls are omitted project-wide (`spring.jackson.default-property-inclusion=non_null`). A `null` list therefore serialises as an absent key, not `[]`. Services must return a real `List`, never null.
- Ordering, exactly as pinned by the spec (section 4):
  - notes → `order by n.createdAt desc, n.id desc`
  - notifications → `order by n.issueDate desc, n.id desc`
  - user search → `order by u.username asc, u.id asc`
  - relationships → `order by r.createdAt desc, r.id desc`
- No `Pageable`, `Page`, `PageResponse`, or `SafePageable` may remain in any file after Task 5.
- 12 source files change. Tests: none committed today reference pagination; do not add unit tests for this refactor — verify over real HTTP as the project already does.

## Review Focus

These are the failure modes the spec implies but that no committed test exercises. Each has a test pinned to the task that owns the code.

1. **A converted query loses its `order by`.** Removing `Pageable` without adding ordering returns database order, which looks plausible and is not. Most likely on the two relationship methods, which change from derived-finder naming to `@Query`. → Task 4 asserts a seeded out-of-order list comes back ordered.
2. **A filter parameter is dropped during the signature rewrite.** Rewriting `findOwned` to drop `Pageable` can also drop `type`/`visibility`/`from`/`to`, which would make the endpoint return everything. → Tasks 1 and 3 assert filters still narrow results.
3. **Empty result serialises as a missing key.** With `non_null`, a `null` list vanishes from the response. → Tasks 1–4 each assert `data` is `[]` for a user with no rows.
4. **`GET /users/search` regresses to 500.** Making `q` optional is the point of the fix, but if the blank check is removed the endpoint returns an empty list instead of 400. → Task 2 asserts both missing and blank `q` return 400.
5. **Premature deletion.** `PageResponse` and `SafePageable` are used by all four controllers; deleting them before every controller is converted breaks the build. → Task 5 owns deletion and re-verifies all nine endpoints.

---

### Task 1: Notifications list

**Files:**
- Modify: `src/main/java/org/example/notesharing/repository/NotificationRepository.java:21-30`
- Modify: `src/main/java/org/example/notesharing/service/NotificationService.java:36-39`
- Modify: `src/main/java/org/example/notesharing/controller/NotificationController.java:40-45,75-77`

**Interfaces:**
- Consumes: `Notification` entity (`issueDate`, `read`, `type`, `notified`), `NotificationResponseDto`, `ApiResponse.of(...)`.
- Produces:
  - `NotificationRepository.findForUser(Long userId, Boolean read, NotificationType type) → List<Notification>`
  - `NotificationService.list(Long userId, Boolean read, NotificationType type) → List<NotificationResponseDto>`
  - `NotificationController.list(...)` returns `ApiResponse<List<NotificationResponseDto>>`

- [ ] **Step 1: Write the failing HTTP assertion**

Start the dev app, then run an assertion script that registers three users A/B/C where B and C each send A a friend request (which raises a notification on accept, and a request notification immediately). Assert:

```js
// GET /api/v1/notifications  (auth = A's access token)
assert(Array.isArray(body.data), "data must be an array")
assert(!("content" in body.data), "PageResponse.content must be gone")
assert(!("totalElements" in body.data), "totalElements must be gone")
assert(!("page" in body.data), "page must be gone")
// ordering: newest issueDate first
const dates = body.data.map(n => n.issueDate)
assert(JSON.stringify(dates) === JSON.stringify([...dates].sort().reverse()),
       "notifications must be newest-first")
```

- [ ] **Step 2: Run the assertion to verify it fails**

Expected: FAIL on `data must be an array` — `data` is currently `{content,page,size,...}`.

- [ ] **Step 3: Implement the repository**

In `NotificationRepository.findForUser`, change the return type to `List<Notification>`, delete the `Pageable pageable` parameter and the `Page`/`Pageable` imports, and append `order by n.issueDate desc, n.id desc` to the `@Query`.

- [ ] **Step 4: Implement the service**

In `NotificationService.list`, change the return type to `List<NotificationResponseDto>`, drop the `Pageable` parameter, and return `notifications.findForUser(...).stream().map(NotificationService::toResponse).toList()`.

- [ ] **Step 5: Implement the controller**

In `NotificationController.list`, change the return type to `ApiResponse<List<NotificationResponseDto>>`, delete the `@PageableDefault` `Pageable` parameter, delete the `bounded(...)` helper and its `SafePageable` import, and wrap the service result with `ApiResponse.of(...)`.

- [ ] **Step 6: Run the assertion to verify it passes**

Expected: PASS, including the ordering assertion.

- [ ] **Step 7: Assert empty and filtered results**

With a user who has no notifications, assert `Array.isArray(body.data) && body.data.length === 0`. With `?read=true` after marking one read, assert only the unread one is returned — proving the `read` filter survived the rewrite.

- [ ] **Step 8: Run the regression suite and the log gate**

Run: `.\mvnw.cmd test`
Expected: 13/13 pass. Then confirm `ERROR` = 0 and `Unhandled exception` = 0 in `target\run.log`.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/org/example/notesharing/repository/NotificationRepository.java \
        src/main/java/org/example/notesharing/service/NotificationService.java \
        src/main/java/org/example/notesharing/controller/NotificationController.java
git commit -m "feat: notifications list returns a plain array, newest first"
```

---

### Task 2: User search, and the missing-`q` 500

**Files:**
- Modify: `src/main/java/org/example/notesharing/repository/UserRepository.java:32-34`
- Modify: `src/main/java/org/example/notesharing/service/UserService.java:44-52`
- Modify: `src/main/java/org/example/notesharing/controller/UserController.java:76-88` (and remove `MAX_PAGE_SIZE`)

**Interfaces:**
- Consumes: `User` entity, `UserSummaryDto(Long id, String username)`, `BusinessRuleException` → 400.
- Produces:
  - `UserRepository.search(String q) → List<User>`
  - `UserService.search(String query) → List<UserSummaryDto>`
  - `UserController.search(...)` returns `ApiResponse<List<UserSummaryDto>>`

- [ ] **Step 1: Write the failing HTTP assertion**

Register `alice`, `bob`, `carol`. Assert:

```js
let r = await fetch(base + "/users/search?q=a", { headers: auth(alice) })
assert(Array.isArray((await r.json()).data), "data must be an array")
assert(!("content" in (await r.json()).data), "content must be gone")

r = await fetch(base + "/users/search", { headers: auth(alice) })
assert(r.status === 400, "missing q must be 400, not " + r.status)

r = await fetch(base + "/users/search?q=%20%20", { headers: auth(alice) })
assert(r.status === 400, "blank q must be 400")
```

- [ ] **Step 2: Run the assertion to verify it fails**

Expected: FAIL — `data` is a `PageResponse`, and missing `q` returns **500** (`MissingServletRequestParameterException`, unhandled).

- [ ] **Step 3: Implement the repository**

In `UserRepository.search`, change the return type to `List<User>`, delete the `Pageable` parameter and imports, and append `order by u.username asc, u.id asc` to the `@Query`.

- [ ] **Step 4: Implement the service**

In `UserService.search`, change the return type to `List<UserSummaryDto>` and delete the `Pageable` parameter; keep the existing blank/`null` guard that throws `BusinessRuleException`; return the mapped list.

- [ ] **Step 5: Implement the controller**

In `UserController.search`, change the return type to `ApiResponse<List<UserSummaryDto>>`; change `@RequestParam(name = "q")` to `@RequestParam(name = "q", required = false)`; delete the `page` and `size` parameters, the clamping arithmetic, `MAX_PAGE_SIZE`, and the `PageRequest`/`Sort` imports.

- [ ] **Step 6: Run the assertion to verify it passes**

Expected: PASS — array shape, and both missing and blank `q` return 400.

- [ ] **Step 7: Assert ordering and matching**

Seed `alice`, `bob`, `carol`. `?q=a` must return usernames in `["alice","bob","carol"]` order (`username` ascending). Searching a full email must still match that account, and the result must still not expose `email`.

- [ ] **Step 8: Run the regression suite and the log gate**

Run: `.\mvnw.cmd test`
Expected: 13/13 pass. `ERROR` = 0, `Unhandled exception` = 0.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/org/example/notesharing/repository/UserRepository.java \
        src/main/java/org/example/notesharing/service/UserService.java \
        src/main/java/org/example/notesharing/controller/UserController.java
git commit -m "feat: user search returns a plain array; missing q is 400 not 500"
```

---

### Task 3: Notes lists

**Files:**
- Modify: `src/main/java/org/example/notesharing/repository/NoteRepository.java:25-75`
- Modify: `src/main/java/org/example/notesharing/service/NoteService.java:69-90`
- Modify: `src/main/java/org/example/notesharing/controller/NoteController.java:52-95,171-173`

**Interfaces:**
- Consumes: `Note` entity, `NoteSummaryDto`, `NoteType`, `NoteVisibility`, `permissionFor(Note, Long)` used by `toSummary`.
- Produces:
  - `NoteRepository.findOwned(Long ownerId, NoteType type, NoteVisibility visibility, LocalDateTime from, LocalDateTime to) → List<Note>`
  - `NoteRepository.findSharedWithMe(Long userId, NoteType type, LocalDateTime from, LocalDateTime to) → List<Note>`
  - `NoteRepository.findPublicByOthers(Long userId, NoteVisibility visibility, NoteType type, LocalDateTime from, LocalDateTime to) → List<Note>`
  - `NoteService.listOwned(...)`, `listSharedWithMe(...)`, `listPublic(...)` — same parameters, each returning `List<NoteSummaryDto>`

- [ ] **Step 1: Write the failing HTTP assertion**

Register two users, have one share a note with the other. Assert for `GET /notes/mine`, `GET /notes/shared`, and `GET /notes/public`:

```js
for (const path of ["/notes/mine", "/notes/shared", "/notes/public"]) {
  const r = await fetch(base + path, { headers: auth(alice) })
  const body = await r.json()
  assert(Array.isArray(body.data), path + ": data must be an array")
  assert(!("content" in body.data), path + ": content must be gone")
  const created = body.data.map(n => n.createdAt)
  assert(JSON.stringify(created) === JSON.stringify([...created].sort().reverse()),
         path + ": must be newest-first")
}
```

- [ ] **Step 2: Run the assertion to verify it fails**

Expected: FAIL on the first loop iteration — `data` is a `PageResponse`.

- [ ] **Step 3: Implement the repository**

In `NoteRepository`, change all three methods to return `List<Note>`, delete their `Pageable` parameters and the `Page`/`Pageable` imports, and append `order by n.createdAt desc, n.id desc` to each `@Query`. Leave the null-guarded filter predicates untouched.

- [ ] **Step 4: Implement the service**

In `NoteService`, change `listOwned`, `listSharedWithMe`, and `listPublic` to return `List<NoteSummaryDto>` and to drop their `Pageable` parameters, mapping the repository result with the existing `toSummary` logic.

- [ ] **Step 5: Implement the controller**

In `NoteController`, change the three list endpoints to return `ApiResponse<List<NoteSummaryDto>>`, delete the `@PageableDefault Pageable` parameters, delete the `bounded(...)` helper and its `SafePageable` import, and keep the `type`, `visibility`, `from`, `to` parameters exactly as they are.

- [ ] **Step 6: Run the assertion to verify it passes**

Expected: PASS for all three endpoints, including ordering.

- [ ] **Step 7: Assert filters still narrow**

On `GET /notes/mine`, create one `NOTE` and one `CHECKLIST`. `?type=NOTE` must return only the first. `?from=`/`?to=` must exclude a note created outside the window. This is the check that catches a filter silently dropped alongside `Pageable`.

- [ ] **Step 8: Assert per-view isolation and the content omission**

`GET /notes/shared` from the owner's account must not include their own notes; `GET /notes/public` must exclude the caller's own notes. Every item in every list must omit `content`.

- [ ] **Step 9: Run the regression suite and the log gate**

Run: `.\mvnw.cmd test`
Expected: 13/13 pass. `ERROR` = 0, `Unhandled exception` = 0.

- [ ] **Step 10: Commit**

```bash
git add src/main/java/org/example/notesharing/repository/NoteRepository.java \
        src/main/java/org/example/notesharing/service/NoteService.java \
        src/main/java/org/example/notesharing/controller/NoteController.java
git commit -m "feat: note lists return plain arrays, newest first"
```

---

### Task 4: Social lists

**Files:**
- Modify: `src/main/java/org/example/notesharing/repository/UserRelationshipRepository.java:22-31`
- Modify: `src/main/java/org/example/notesharing/service/SocialService.java:228-254,271-274`
- Modify: `src/main/java/org/example/notesharing/controller/SocialController.java:35,40-73,95-97`

**Interfaces:**
- Consumes: `UserRelationship` entity (`createdAt`, `firstUser`, `secondUser`, `type`), `RelationshipResponseDto`, `SocialService.Direction` (`INCOMING`/`OUTGOING`), `toResponse(UserRelationship, Direction)`.
- Produces:
  - `UserRelationshipRepository.findByFirstUserIdAndType(Long firstUserId, RelationshipType type) → List<UserRelationship>`
  - `UserRelationshipRepository.findBySecondUserIdAndType(Long secondUserId, RelationshipType type) → List<UserRelationship>`
  - `SocialService.listFriends(Long userId)`, `listBlocked(Long userId)`, `listOutgoingRequests(Long userId)` → `List<RelationshipResponseDto>` (all three use the `firstUser` repository method)
  - `SocialService.listIncomingRequests(Long userId) → List<RelationshipResponseDto>` (uses the `secondUser` method)

- [ ] **Step 1: Write the failing HTTP assertion**

Register four users. A befriends B (auto-friend on mutual request); A sends C a request; D blocks A. Then as A assert:

```js
for (const path of ["/users/" + aId + "/friends",
                    "/users/" + aId + "/blocked",
                    "/friend-requests/incoming",
                    "/friend-requests/outgoing"]) {
  const body = await fetch(base + path, { headers: auth(a) }).then(r => r.json())
  assert(Array.isArray(body.data), path + ": data must be an array")
  assert(!("content" in body.data), path + ": content must be gone")
}
```

- [ ] **Step 2: Run the assertion to verify it fails**

Expected: FAIL on the first loop iteration.

- [ ] **Step 3: Implement the repository**

In `UserRelationshipRepository`, **delete** the two non-paged `List` overloads at lines 22 and 28 (dead code — all four call sites use the `Page` overloads). Convert the two `Page` methods to `@Query` methods returning `List<UserRelationship>`, dropping the `Pageable` parameter, with `order by r.createdAt desc, r.id desc`. Delete the `Page`/`Pageable` imports.

- [ ] **Step 4: Implement the service**

In `SocialService`, change the four list methods to return `List<RelationshipResponseDto>` and drop their `Pageable` parameters, mapping with the existing `toResponse(row, direction)`. Delete the `page(...)` helper at lines 271-274 and the now-unused `PageResponse`/`Page` imports.

- [ ] **Step 5: Implement the controller**

In `SocialController`, change the four endpoints to return `ApiResponse<List<RelationshipResponseDto>>`, delete the `DEFAULT_SORT` constant, the `@PageableDefault` `Pageable` parameters, the `bounded(...)` helper, and the `SafePageable`/`Pageable`/`Sort`/`PageableDefault` imports.

- [ ] **Step 6: Run the assertion to verify it passes**

Expected: PASS for all four endpoints.

- [ ] **Step 7: Assert ordering, direction, and empty**

Seed three outgoing requests at different moments and assert they arrive newest-first. Assert `/friend-requests/incoming` shows D's request to A with `Direction.INCOMING` semantics while `/outgoing` shows A's request to C — proving the two repository methods were not swapped. Assert `data.length === 0` for a user with no relationships.

- [ ] **Step 8: Run the regression suite and the log gate**

Run: `.\mvnw.cmd test`
Expected: 13/13 pass. `ERROR` = 0, `Unhandled exception` = 0.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/org/example/notesharing/repository/UserRelationshipRepository.java \
        src/main/java/org/example/notesharing/service/SocialService.java \
        src/main/java/org/example/notesharing/controller/SocialController.java
git commit -m "feat: social lists return plain arrays, newest first"
```

---

### Task 5: Delete the pagination types and update the spec

**Files:**
- Delete: `src/main/java/org/example/notesharing/dto/common/PageResponse.java`
- Delete: `src/main/java/org/example/notesharing/web/SafePageable.java`
- Modify: `docs/superpowers/specs/2026-10-05-notesharing-api-design.md:141`

**Interfaces:**
- Consumes: nothing — Tasks 1-4 removed every caller.
- Produces: a codebase with no `Pageable`, `Page`, `PageResponse`, or `SafePageable` anywhere.

- [ ] **Step 1: Prove nothing still references them**

Run: `Get-ChildItem -Recurse -Filter *.java src | Select-String -Pattern "PageResponse|SafePageable|Pageable|Page<"`
Expected: no matches in `src/main`. (Matches in `target\` may be ignored; delete `target` first if it is noisy.)

- [ ] **Step 2: Delete both files**

```bash
git rm src/main/java/org/example/notesharing/dto/common/PageResponse.java \
       src/main/java/org/example/notesharing/web/SafePageable.java
```

- [ ] **Step 3: Update the original design spec**

In `docs/superpowers/specs/2026-10-05-notesharing-api-design.md` line 141, replace:

```
- Consistent wrapper: `ApiResponse<T>` for single, `PageResponse<T>` for paginated
```

with:

```
- Consistent wrapper: `ApiResponse<T>`; list endpoints return `ApiResponse<List<T>>`
  (pagination removed 2026-10-17, see 2026-10-17-remove-pagination-design.md)
```

- [ ] **Step 4: Full clean build and integration tests**

Run: `.\mvnw.cmd clean verify`
Expected: compile succeeds, surefire 13/13, postgres-it 7/7 against real PostgreSQL 16 — proving the six new `order by` clauses are valid against the Flyway schema, not only Hibernate's `create-drop` model.

- [ ] **Step 5: End-to-end re-verification of all nine endpoints**

Restart the dev app and re-run the assertions from Tasks 1-4 in a single session, adding for every endpoint:

```js
assert(Array.isArray(body.data))
assert(!("content" in body.data) && !("totalElements" in body.data) && !("page" in body.data))
```

Also confirm a legacy client sending `?page=2&size=20` to any list endpoint still gets 200 with the full list.

- [ ] **Step 6: The log gate**

Run: `Select-String -Path target\run.log -Pattern "\sERROR\s"`, then `... -Pattern "Unhandled exception"`
Expected: both zero matches.

- [ ] **Step 7: Commit**

```bash
git add docs/superpowers/specs/2026-10-05-notesharing-api-design.md
git commit -m "chore: remove PageResponse and SafePageable; spec reflects flat lists"
```

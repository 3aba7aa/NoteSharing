# Design: Remove Pagination from List Endpoints

**Date:** 2026-10-17
**Status:** Approved, pending spec review
**Branch:** `phase1-api`
**Supersedes:** the paginated list contract in `2026-10-05-notesharing-api-design.md`

---

## 1. Context

All nine list endpoints currently accept `?page=&size=&sort=` and return a
`PageResponse<T>` envelope. The goal is to remove pagination entirely: each list
endpoint returns its full matching set as a plain array inside the project's
standard `ApiResponse` wrapper.

This is a deliberate simplification for a development-stage API driven from
Postman. It trades the safety of bounded result sets for a smaller, flatter
contract.

## 2. Decisions

| Question | Decision |
|---|---|
| Scope | All nine list endpoints |
| Response shape | Keep `ApiResponse<T>`; `data` becomes the array |
| Client-chosen sorting | Removed. Fixed order per endpoint, no `?sort=` param |
| Result cap | None. Every endpoint returns its full matching set |
| Implementation approach | **A** — repositories return `List`, ordering moves into SQL |

**Why approach A over B.** The rejected alternative kept `Pageable` in
repositories and sliced with `PageRequest.of(0, Integer.MAX_VALUE)` in the
service layer. That would have changed ~4 files instead of ~12, but every list
call would still execute a `select count(*)` nobody needs, `Page`/`Pageable`
would remain in the signatures, and sorting would still flow through
`@PageableDefault`. Pagination would be gone from the contract while remaining
in the implementation. Approach A makes it impossible to reintroduce by
accident: there is no `Pageable` left to reach for.

Thirteen files change in total: 12 source files (4 repositories, 4 services,
4 controllers) and this design's counterpart in the original spec.

## 3. Contract

### Before

```jsonc
GET /api/v1/notifications?page=0&size=20&sort=issueDate,desc
{
  "data": {
    "content": [ { "...": "..." } ],
    "page": 0, "size": 20, "totalElements": 41,
    "totalPages": 3, "first": true, "last": false
  },
  "message": "Notifications",
  "timestamp": "2026-10-17T12:00:00Z"
}
```

### After

```jsonc
GET /api/v1/notifications
{
  "data": [ { "...": "..." } ],
  "message": "Notifications",
  "timestamp": "2026-10-17T12:00:00Z"
}
```

### Endpoints

| Endpoint | `data` becomes | Fixed order |
|---|---|---|
| `GET /users/search` | `List<UserSummaryDto>` | `username` ASC |
| `GET /notes/mine` | `List<NoteSummaryDto>` | `createdAt` DESC |
| `GET /notes/shared` | `List<NoteSummaryDto>` | `createdAt` DESC |
| `GET /notes/public` | `List<NoteSummaryDto>` | `createdAt` DESC |
| `GET /users/{id}/friends` | `List<RelationshipResponseDto>` | `createdAt` DESC |
| `GET /users/{id}/blocked` | `List<RelationshipResponseDto>` | `createdAt` DESC |
| `GET /friend-requests/incoming` | `List<RelationshipResponseDto>` | `createdAt` DESC |
| `GET /friend-requests/outgoing` | `List<RelationshipResponseDto>` | `createdAt` DESC |
| `GET /notifications` | `List<NotificationResponseDto>` | `issueDate` DESC |

### Parameters

**Removed:** `page`, `size`, `sort`

**Kept:** `q` (search), `type` and `visibility` (notes lists), `read` and `type`
(notifications), `from` and `to` (note creation window). These are content
filters, not pagination, and are unaffected.

### Compatibility

A client that still sends `?page=2&size=20` receives a normal 200 and those
parameters are silently ignored — Spring ignores unmatched query parameters.
The prior clamping behaviour (`size=99999` → 100, `page=-5` → 0) is gone with
the parameters themselves.

## 4. Ordering

**The critical detail.** No query in the repository layer currently contains an
`order by` clause, and no derived finder has `OrderBy` in its name. All ordering
today is injected by `Pageable` via `@PageableDefault` in the controllers and
`PageRequest.of(...)` in `UserService`.

Removing `Pageable` without replacing that ordering would not simplify the API —
it would silently change result order to whatever the database returns first.
Javadoc such as *"Notifications addressed to one user, newest first"* describes
behaviour the query itself does not implement; the controller was providing it.

Every list query therefore gains an explicit `order by`.

### Tiebreakers

`createdAt` and `issueDate` are populated with `LocalDateTime.now()`
(`NoteService:63`, `NotificationService:86`, `SocialService:81/201/267`). Rows
created within the same clock tick can tie, and a tied `order by` leaves their
relative position undefined — a client could see a list reorder between calls.
Each query therefore appends the primary key as a deterministic tiebreaker.

| Query | Order clause |
|---|---|
| `NoteRepository.findOwned` | `order by n.createdAt desc, n.id desc` |
| `NoteRepository.findSharedWithMe` | `order by n.createdAt desc, n.id desc` |
| `NoteRepository.findPublicByOthers` | `order by n.createdAt desc, n.id desc` |
| `NotificationRepository.findForUser` | `order by n.issueDate desc, n.id desc` |
| `UserRepository.search` | `order by u.username asc, u.id asc` |
| `UserRelationshipRepository` (both directions) | `order by r.createdAt desc, r.id desc` |

The relationship queries move from derived finder naming to explicit `@Query`,
so the repository layer has one style and every list ordering is visible in the
same place.

## 5. Changes by layer

### Repositories (4 files, 7 query methods)

| Repository | Change |
|---|---|
| `NoteRepository` | 3 methods: `Page<Note>` → `List<Note>`, drop `Pageable`, add order by |
| `NotificationRepository` | `findForUser`: `Page` → `List`, drop `Pageable`, add order by |
| `UserRepository` | `search`: `Page` → `List`, drop `Pageable`, add order by |
| `UserRelationshipRepository` | Delete 2 non-paged `List` overloads (dead code); convert 2 `Page` overloads to `@Query` returning `List` with order by |

The two non-paged `List` overloads at `UserRelationshipRepository:22` and `:28`
are never called — all four call sites in `SocialService` use the `Page`
overloads. They are deleted rather than reused.

### Services (4 files)

| Service | Change |
|---|---|
| `UserService.search` | Returns `List<UserSummaryDto>`; drops `Pageable` |
| `NoteService` | `listOwned`, `listSharedWithMe`, `listPublic` return `List<NoteSummaryDto>` |
| `SocialService` | 4 list methods return `List<RelationshipResponseDto>`; `page(...)` helper deleted |
| `NotificationService.list` | Returns `List<NotificationResponseDto>` |

### Controllers (4 files)

| Controller | Change |
|---|---|
| `UserController.search` | Drops `page`, `size`, `MAX_PAGE_SIZE`, `PageRequest`, `Sort` |
| `NoteController` | Drops `@PageableDefault`, `Pageable`, `bounded()` (3 endpoints) |
| `SocialController` | Drops `DEFAULT_SORT`, `@PageableDefault`, `Pageable`, `bounded()` (4 endpoints) |
| `NotificationController` | Drops `@PageableDefault`, `Pageable`, `bounded()` |

### Deleted

| File / symbol | Reason |
|---|---|
| `dto/common/PageResponse.java` | No remaining producer or consumer |
| `web/SafePageable.java` | Sort allow-lists were only reachable through `Pageable` |
| `UserRelationshipRepository` non-paged `List` overloads | Dead code |
| `UserController.MAX_PAGE_SIZE` | Only used by the removed clamping |
| `SocialService.page(...)` helper | Only wrapped `Page` |
| `bounded()` in 3 controllers | Only invoked `SafePageable.of` |

## 6. In-scope bug fix

`GET /users/search` with no `q` parameter currently returns **500**.
`MissingServletRequestParameterException` is not handled by
`GlobalExceptionHandler`, so it falls through to the catch-all. `q` becomes
optional at the binding layer (`required = false`), allowing the request to
reach the existing blank check in `UserService.search`, which throws
`BusinessRuleException` → **400**.

Missing and blank `q` now produce the same 400. This is included because the
method is being rewritten regardless, and a 500 is an unhandled exception in
`target/run.log`, which fails the project's verification gate.

## 7. Errors and edge cases

| Case | Behaviour |
|---|---|
| No matches | `data: []` — an empty array, not an error |
| Malformed filter enum, e.g. `?type=BOGUS` | 400 via `MethodArgumentTypeMismatchException` (unchanged) |
| Unknown query parameter, e.g. `?page=2` | Ignored, 200 |
| `?q=` blank | 400 (unchanged) |
| `?q` absent | 400 — previously 500 |
| Very large result set | Returned in full; no cap |

## 8. Verification

1. `mvnw clean compile` passes.
2. Surefire green (13 tests). `OpenApiDocumentTests` asserts security scheme and
   public-endpoint opt-out only, and makes no reference to paging parameters.
3. `-Ppostgres-it verify` green (7 tests) — proves the new `order by` clauses
   run against the Flyway schema on real PostgreSQL 16, not only Hibernate's
   `create-drop` model.
4. Dev app started; HTTP assertions against all nine endpoints confirming:
   - `data` is a JSON array with no `content`, `page`, `totalElements`, or
     `totalPages` keys
   - content filters still filter
   - ordering holds when rows are seeded out of order
   - `GET /users/search` with no `q` returns 400, not 500
5. `grep target/run.log` for `\sERROR\s` and `Unhandled exception` — both zero.

**No committed test references pagination.** All five test files were checked.

## 9. Out of scope

- Adding a result cap or cursor-based paging for production readiness
- `bod` / `gender` being readable but writable by no endpoint
- `LINK_ONLY` share link tokens
- Clearing note fields to `null` via `PUT`
- Database schema changes — none are required; ordering is query-level only

## 10. Files touched

**Deleted (2):** `dto/common/PageResponse.java`, `web/SafePageable.java`

**Modified (13):** 12 source files — `NoteRepository`, `NotificationRepository`,
`UserRepository`, `UserRelationshipRepository`, `UserService`, `NoteService`,
`SocialService`, `NotificationService`, `UserController`, `NoteController`,
`SocialController`, `NotificationController` — plus the original design spec
`2026-10-05-notesharing-api-design.md` (line 141, the `PageResponse` wrapper
note).

**Tests:** none require changes.

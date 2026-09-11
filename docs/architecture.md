# AI Finance Tracker — Architecture Documentation

> **Status:** Working draft  
> **Last updated:** 2026-09-11  
> This document records accepted decisions and how the system is built. Detail diagrams live under [`diagrams/`](diagrams/).

## Contents

1. [Product vision](#1-product-vision)
2. [Problem and users](#2-problem-and-users)
3. [MVP scope](#3-mvp-scope)
4. [System shape](#4-system-shape) — stack, deployment, packages, frontend, API
5. [Flow A — Authentication](#5-flow-a--authentication)
6. [Flow B — Document processing](#6-flow-b--document-processing)
7. [Flow C — Expense exploration](#7-flow-c--expense-exploration)
8. [Data model](#8-data-model)
9. [Open decisions](#9-open-decisions)
10. [Principles](#10-principles)
11. [Change log](#11-change-log)

---

## 1. Product vision

AI Finance Tracker is a web app for personal expense tracking. A user uploads a receipt or invoice, the system proposes the main financial fields, the user reviews and corrects them, and the confirmed expense is stored for lists, filters, and a dashboard.

The product reduces manual data entry while keeping the user in control of the final saved data.

```mermaid
flowchart LR
    A[Upload receipt or invoice] --> B[Extract text and structured data]
    B --> C[User review and correction]
    C --> D[Approve and save expense]
    D --> E[View, filter and analyse expenses]
```

---

## 2. Problem and users

Receipts and invoices are unstructured (paper, photos, PDFs). Manual entry is slow. The MVP turns a document into a verified structured expense:

```text
Document → OCR text → proposed fields → user verification → saved expense
```

It is not accounting software. Receipts are evidence for personal expenses, not full accounting objects.

**Primary MVP user:** an individual who wants to track personal spending (students, young professionals, people who already photograph receipts).

**Later possibility:** freelancers organising business expenses. Tax, VAT, teams, and multi-role workflows are outside the MVP.

---

## 3. MVP scope

### Included

- User registration, login, and logout.
- One application role: `USER`.
- Upload of supported image and PDF files.
- Secure association of every document and expense with its owner.
- OCR text extraction.
- Extraction of merchant, date, total amount, currency, and category.
- Review and correction before final saving.
- Manual entry when automatic extraction is incomplete or fails.
- Storage of the original document and the confirmed expense data.
- Expense list, details, edit, and delete operations.
- Filters by period, category, and merchant.
- A basic dashboard with aggregated expense information.
- Basic automated tests and Docker-based local setup.

### Explicitly excluded

- Administrator profile and admin panel.
- Line-item extraction from receipts.
- Accounting and tax calculations.
- Bank integrations.
- Mobile application.
- Teams, organisations, and multiple user roles.
- Microservices and Kubernetes.
- A custom AI model trained from scratch.
- Natural-language querying in the first release.
- Guaranteed perfect recognition of every document format.

---

## 4. System shape

### 4.1 Technology stack

| Layer | Choice |
| ----- | ------ |
| Backend | Java 21+, Spring Boot 3, REST JSON |
| Auth | JWT bearer (Spring Security), BCrypt password hashes |
| Validation | Jakarta Bean Validation on request DTOs |
| Persistence | Spring Data JPA; schema owned by SQL in `db/migrations/` (`ddl-auto=none`) |
| Database | PostgreSQL 16 |
| Frontend | Vite + plain JavaScript (hash routing + `fetch`) |
| Files | Local disk under `UPLOAD_DIR` (Docker volume in Compose) |
| Local run | Docker Compose for Postgres + backend; frontend on the host |

### 4.2 Deployment (local)

```mermaid
flowchart LR
    Browser["Browser<br/>localhost:5173"]
    Vite["Vite dev server<br/>proxy /auth /documents /expenses …"]
    API["Spring Boot<br/>localhost:8080"]
    PG["PostgreSQL<br/>localhost:5432"]
    Disk["Upload volume<br/>UPLOAD_DIR"]

    Browser --> Vite
    Vite -->|"same-origin proxy"| API
    Browser -.->|"optional direct / CORS"| API
    API --> PG
    API --> Disk
```

- **Compose** (`docker-compose.yml`): `postgres` + `backend`. Migrations mount into `docker-entrypoint-initdb.d` (run only when the Postgres volume is first created).
- **Frontend** is not in Compose: `cd frontend && npm run dev`.
- Env values come from `.env` (see `.env.example`): DB credentials, `JWT_SECRET`, `UPLOAD_DIR`, JDBC URL.

How requests move:

1. The SPA calls paths like `/documents` (relative).
2. Vite proxies them to the backend, so the browser avoids CORS during normal local use.
3. `SecurityConfig` + `WebConfig` still allow `http://localhost:5173` if the API is called directly.
4. Protected handlers read the user from the JWT via `CurrentUser` (never from a body `userId`).

### 4.3 Backend packages and responsibilities

Root package: `com.financetracker`.

```mermaid
flowchart TB
    subgraph api ["HTTP boundary"]
        AuthC[auth.AuthController]
        DocC[document.DocumentController]
        ExpC[expense.ExpenseController]
        CatC[category.CategoryController]
        HealthC[health.HealthController]
    end

    subgraph security ["Security"]
        Filter[JwtAuthFilter]
        Jwt[JwtService]
        CU[CurrentUser]
    end

    subgraph services ["Application services"]
        AuthS[AuthService]
        DocS[DocumentService]
        ExpS[ExpenseService]
        Mock[MockExtractionService]
        Files[FileStorageService]
    end

    subgraph data ["Persistence"]
        UserR[UserRepository]
        DocR[DocumentRepository]
        ExtR[DocumentExtractionRepository]
        ExpR[ExpenseRepository]
        CatR[CategoryRepository]
        PG[(PostgreSQL)]
    end

    Filter --> Jwt
    AuthC --> AuthS --> UserR
    DocC --> CU
    DocC --> DocS
    DocC --> ExpS
    ExpC --> CU
    ExpC --> ExpS
    DocS --> Mock
    DocS --> Files
    DocS --> DocR
    DocS --> ExtR
    ExpS --> DocR
    ExpS --> ExpR
    ExpS --> CatR
    CatC --> CatR
    UserR --> PG
    DocR --> PG
    ExtR --> PG
    ExpR --> PG
    CatR --> PG
    Files --> Disk[(UPLOAD_DIR)]
```

| Package | Main types | Responsibility |
| ------- | ---------- | ---------------- |
| `auth` | `AuthController`, `AuthService`, request/response DTOs | Register, login, `/me` |
| `security` | `SecurityConfig`, `JwtAuthFilter`, `JwtService`, `CurrentUser`, `UserPrincipal` | Stateless JWT API; ownership identity |
| `user` | `User`, `UserRepository` | Account row (`email`, `password_hash`) |
| `document` | `DocumentController`, `DocumentService`, `FileStorageService`, `MockExtractionService`, entities/DTOs | Upload, mock process, review GET, file stream, pending delete; thin approve HTTP entry |
| `expense` | `ExpenseController`, `ExpenseService`, `Expense`, `ExpenseRepository`, approve + read DTOs | Atomic approve; owner-scoped list/detail reads |
| `category` | `CategoryController`, entity/repo | List active categories |
| `health` | `HealthController` | Liveness + DB check |
| `config` | `WebConfig` | MVC CORS for the Vite origin |

**Typical collaboration (protected document/expense call):** browser → `JwtAuthFilter` → controller → `CurrentUser` → `DocumentService` / `ExpenseService` → repositories / `FileStorageService` → JSON or file bytes.

### 4.4 Frontend structure

| File / area | Role |
| ----------- | ---- |
| `main.js` | Entry; maps hash routes to page renderers |
| `router.js` | Hash router (`#/…`) |
| `api.js` | `api()` (JSON + Bearer); `apiBlob()` for file preview |
| `auth.js` | JWT in `localStorage` (`ft_token`); login helpers |
| `pages/home.js` | Account, categories sample, health; logged-in link to Expenses |
| `pages/login.js` / `register.js` | Auth forms |
| `pages/upload.js` | Multipart upload → navigate to review |
| `pages/review.js` | Preview + editable proposed fields; Approve (→ expenses list) / retry / continue-manual / delete |
| `pages/expenses.js` | Owner expense list (`GET /expenses`); empty state + link to upload |
| `pages/expense-detail.js` | One expense (`GET /expenses/{id}`) + document preview/link; no edit/delete |
| `vite.config.js` | Dev server `:5173` + API proxy (`/auth`, `/documents`, `/expenses`, …) |

| Hash route | Page |
| ---------- | ---- |
| `#/` | Home |
| `#/login` | Login |
| `#/register` | Register |
| `#/upload` | Upload (logged-in) |
| `#/review/:id` | Review (logged-in) |
| `#/expenses` | Expense list (logged-in) |
| `#/expenses/:id` | Expense details (logged-in) |

Unknown hashes fall through to home.

### 4.5 HTTP API overview

| Method | Path | Auth | Success |
| ------ | ---- | ---- | ------- |
| `GET` | `/health` | Public | `200` status + DB |
| `POST` | `/auth/register` | Public | `201` `{ token }` |
| `POST` | `/auth/login` | Public | `200` `{ token }` |
| `GET` | `/auth/me` | JWT | `{ id, email }` |
| `GET` | `/categories` | JWT | Active categories `{ id, name, slug }` |
| `POST` | `/documents` | JWT | `201` review DTO (after mock processing) |
| `GET` | `/documents/{id}` | JWT | Review DTO |
| `GET` | `/documents/{id}/file` | JWT | File bytes (inline) |
| `POST` | `/documents/{id}/process` | JWT | Review DTO (retry mock) |
| `POST` | `/documents/{id}/continue-manual` | JWT | Review DTO (empty extraction) |
| `POST` | `/documents/{id}/approve` | JWT | `201` `ExpenseResponse` (atomic expense + `SAVED`) |
| `DELETE` | `/documents/{id}` | JWT | `204` (pending only; `SAVED` → `409`) |
| `GET` | `/expenses` | JWT | `200` `ExpenseViewResponse[]` (owner only; `expense_date DESC`, then `id DESC`) |
| `GET` | `/expenses/{id}` | JWT | `200` `ExpenseViewResponse` (missing/foreign → `404`) |

Review DTO fields: `id`, `status`, `originalFilename`, `mimeType`, `fileSizeBytes`, `createdAt`, `fileUrl`, nullable `extraction` (`rawOcrText`, proposed merchant/date/amount/currency/categoryId`). Never exposes `storage_path`.

**Approve** (`POST /documents/{id}/approve`): body = confirmed form fields (`expenseDate`, `totalAmount`, `currency`, `categoryId`, optional `merchant`). Only from `REVIEW_REQUIRED` (`409` otherwise; second approve included). Missing/foreign document → `404`. Invalid amount/currency/category → `400`. One `@Transactional` insert into `expenses` + `documents.status = SAVED`; failure rolls back and leaves `REVIEW_REQUIRED`. Extraction row is not updated (proposals stay as history). Response: `id`, `documentId`, `categoryId`, `merchant`, `expenseDate`, `totalAmount`, `currency`, `createdAt`. UI Approve is shown only for `REVIEW_REQUIRED`; on success navigates to `#/expenses`.

**Expense read** (`GET /expenses`, `GET /expenses/{id}`): ownership via join `expenses.document_id → documents` and `documents.user_id = currentUser` (same 404 policy as documents). `ExpenseViewResponse` = approve fields **plus** `categoryName`, `documentFileUrl` (`/documents/{documentId}/file`), `originalFilename`. Approve’s `ExpenseResponse` shape is unchanged. Filters, edit, delete, and dashboard remain later steps.

---

## 5. Flow A — Authentication

The user registers or logs in with email and password, receives a JWT, and then accesses only their own resources. There is no server-side session table.

MVP auth only: register / login / logout (client clears the token). No admin, social login, 2FA, email confirmation, or password reset.

### Contract

- Passwords stored only as BCrypt (`users.password_hash`).
- Email is trimmed and lowercased before store/lookup.
- Register password length: 8–100 characters.
- JWT (HS256): claims `sub` (user id), `email`, `iat`, `exp`; lifetime 24 hours (`app.jwt.expiration-ms`). The signing key is derived with SHA-256 of `JWT_SECRET` so short local secrets still work.
- Frontend stores the token and sends `Authorization: Bearer …`.
- Logout is client-only; the backend does not revoke tokens.
- Ownership always comes from the JWT (`CurrentUser`). There is no `users.role` column; the filter assigns a fixed `ROLE_USER`.
- Missing user and wrong password both return the same `401` message (`Invalid email or password`).

### Diagrams

| Topic | Source |
| ----- | ------ |
| Overview (register vs login) | [diagrams/authentication-flow.mmd](diagrams/authentication-flow.mmd) |
| Register | [diagrams/register-flow.mmd](diagrams/register-flow.mmd) |
| Login | [diagrams/login-flow.mmd](diagrams/login-flow.mmd) |
| Protected request | [diagrams/jwt-protected-request.mmd](diagrams/jwt-protected-request.mmd) |

```mermaid
sequenceDiagram
    participant Browser
    participant JwtFilter as JwtAuthFilter
    participant JwtService
    participant SecurityRules as SecurityConfig
    participant Controller
    participant CurrentUser

    Browser->>JwtFilter: Request with Bearer JWT
    JwtFilter->>JwtService: Validate token
    alt Token is valid
        JwtService-->>JwtFilter: User id and email
        JwtFilter->>SecurityRules: Continue authenticated
        SecurityRules->>Controller: Allowed
        Controller->>CurrentUser: Read principal
        Controller-->>Browser: Protected response
    else Token missing or invalid
        JwtFilter->>SecurityRules: Continue anonymous
        SecurityRules-->>Browser: 401 Unauthorized
    end
```

---

## 6. Flow B — Document processing

### 6.1 Goal

Turn an uploaded receipt/invoice into a **user-approved** expense without silently trusting OCR or AI output.

### 6.2 Happy path (product)

1. User selects a file; frontend checks type/size for usability.
2. Backend authenticates and re-validates the file (trust boundary).
3. File is stored; `documents` row created; processing produces proposed fields.
4. Status becomes `REVIEW_REQUIRED`; UI shows file + editable form.
5. User corrects values and **approves**.
6. Backend validates again, creates `expenses`, sets document `SAVED`.
7. Expense appears in list / filters / dashboard.

Full activity diagram (including failures and manual continue): [diagrams/document-processing-flow.mmd](diagrams/document-processing-flow.mmd) (SVG: [document-processing-flow.svg](diagrams/document-processing-flow.svg)).

### 6.3 Upload, mock processing, review, and approve (current contract)

Processing runs **synchronously** inside `POST /documents` after the row is saved. The `201` body already has the post-processing status. Real OCR libraries are not used yet — `MockExtractionService` fills deterministic sample fields (merchant `Demo Cafe`, amount `12.50`, `EUR`, today’s date; category left null on purpose).

**Order:** validate → write file → insert `UPLOADED` → `PROCESSING` → mock extract → `REVIEW_REQUIRED` or `PROCESSING_FAILED`. If processing fails after file + row exist, prefer a recoverable `PROCESSING_FAILED` document over a disk orphan without a row. If the DB insert fails after a disk write, delete the orphan file.

**Approve** (`POST /documents/{id}/approve`, JWT): `DocumentController` delegates to `ExpenseService.approve`. Body carries confirmed fields (not a re-read of extraction). Only `REVIEW_REQUIRED` is allowed. In one DB transaction the service inserts `expenses` and sets `documents.status = SAVED`. Any failure rolls back — no orphan expense and status stays `REVIEW_REQUIRED`. `document_extractions` is left unchanged. Unique `expenses.document_id` is a safety net against double approve. Frontend shows Approve only when status is `REVIEW_REQUIRED`; success navigates to `#/expenses`.

**Rules:**

- MIME: `image/jpeg`, `image/png`, `application/pdf`; max **5 MB**.
- Disk path: `{UPLOAD_DIR}/{userId}/{uuid}{ext}` (client filename is not used for the path).
- Filename containing `fail` (case-insensitive) → `PROCESSING_FAILED`, no usable extraction.
- `POST …/process` — retry from `UPLOADED` or `PROCESSING_FAILED` only (`409` otherwise).
- `POST …/continue-manual` — from `PROCESSING_FAILED` only; empty extraction row + `REVIEW_REQUIRED`.
- `POST …/approve` — from `REVIEW_REQUIRED` only; atomic expense insert + `SAVED` (`409` if wrong status; `400` if validation/category fails).
- `DELETE` — hard-delete pending document (cascade extraction) + disk file; not allowed for `SAVED` (`409`).
- Frontend `#/review/:id` loads review DTO + categories and previews via authenticated blob URL.

### 6.4 Status model

| Status | Meaning |
| ------ | ------- |
| `UPLOADED` | File stored; processing not finished |
| `PROCESSING` | Extraction running |
| `REVIEW_REQUIRED` | Ready for review (full, partial, or empty manual form) |
| `PROCESSING_FAILED` | Auto processing failed; retry, continue manually, or delete |
| `SAVED` | User approved; an `expenses` row exists |

`DELETED` is not a status — pending delete is a hard delete of the row and file.

When a saved expense is hard-deleted later (Flow C), the document returns to `REVIEW_REQUIRED` and the file is kept.

State diagram: [diagrams/document-status-model.mmd](diagrams/document-status-model.mmd).

```mermaid
stateDiagram-v2
    [*] --> UPLOADED: valid upload stored
    UPLOADED --> PROCESSING: processing starts
    PROCESSING --> REVIEW_REQUIRED: full or partial extraction
    PROCESSING --> PROCESSING_FAILED: cannot produce a result
    PROCESSING_FAILED --> PROCESSING: retry
    PROCESSING_FAILED --> REVIEW_REQUIRED: continue manually
    PROCESSING_FAILED --> [*]: hard delete
    REVIEW_REQUIRED --> SAVED: user approves
    REVIEW_REQUIRED --> [*]: hard delete
    SAVED --> REVIEW_REQUIRED: expense hard-deleted
```

### 6.5 Important failure / alternative paths

| Situation | Behaviour |
| --------- | --------- |
| Invalid file (client or server) | Reject; no document |
| Storage write fails | Clean up; retryable error |
| Partial extraction | Still go to review; user fills gaps |
| Total processing failure | `PROCESSING_FAILED` → retry, continue-manual, or delete |
| User leaves review | Stays `REVIEW_REQUIRED`; not in statistics |
| Delete pending | Remove DB row + file; no expense |
| Approve validation / DB save fails | Stay on review; no partial expense |

### 6.6 Functional rules

1. Extraction never creates a final expense by itself — explicit approval is required.
2. Backend repeats important validations (frontend checks are UX only).
3. A user sees only their own documents and expenses.
4. Partial extraction is useful; complete failure must still allow manual entry.
5. Final expense save is atomic; pending/failed documents stay out of dashboard maths.
6. MVP uses hard delete only (no `deleted_at`).
7. Original file and confirmed expense stay linked after approve.

### 6.7 Required fields before approval

- `expense_date` present  
- `total_amount` > 0  
- `currency` in `{EUR, USD, GBP}`  
- `category_id` → active category  
- Owner via `documents.user_id`  

`merchant` may be null.

---

## 7. Flow C — Expense exploration

After approve, the user can open a saved-expense **list** and **details**. Ownership is always enforced through `documents.user_id`. Dashboard aggregates, filters, edit, and delete are still later steps; when delete lands, removing an expense will return its document to `REVIEW_REQUIRED` and keep the file.

### Current contract (Step 12)

| Capability | Behaviour |
| ---------- | --------- |
| List | `GET /expenses` → `ExpenseViewResponse[]` ordered by `expense_date DESC`, then `id DESC` |
| Details | `GET /expenses/{id}` → one `ExpenseViewResponse`; missing or other user’s id → `404` |
| Auth | No token → `401` (`anyRequest().authenticated()`; no `SecurityConfig` change) |
| UI | `#/expenses` list (empty state + upload link); `#/expenses/:id` fields + document preview via `documentFileUrl` / `apiBlob`; home “Expenses” link when logged in |
| After approve | Review navigates to `#/expenses` so the new row is visible immediately |

`ExpenseController` + `ExpenseService.list` / `getById` join expenses to documents by owner. Category name is resolved via `CategoryRepository` (still shown if the category later becomes inactive).

Full product activity diagram (incl. future filters/edit/delete/dashboard): [diagrams/expense-exploration-flow.mmd](diagrams/expense-exploration-flow.mmd).

```mermaid
sequenceDiagram
    participant UI as ExpensesPages
    participant API as ExpenseController
    participant Svc as ExpenseService
    participant DB as Postgres

    UI->>API: GET /expenses JWT
    API->>Svc: list(userId)
    Svc->>DB: expenses join documents by userId
    Svc-->>UI: 200 ExpenseViewResponse[]

    UI->>API: GET /expenses/id JWT
    API->>Svc: getById(userId, id)
    alt missing or foreign
        Svc-->>UI: 404
    else owned
        Svc-->>UI: 200 ExpenseViewResponse
    end
```

---

## 8. Data model

PostgreSQL; `BIGINT` identity keys. Schema source of truth: `db/migrations/`. Seeded categories: `db/migrations/002_seed_categories.sql` (Food & Drink, Transport, Shopping, Housing, Health, Entertainment, Utilities, Travel, Education, Other).

### Table responsibilities

| Table | Responsibility |
| ----- | -------------- |
| `users` | Auth identity (`email`, `password_hash`) |
| `documents` | Uploaded file metadata + processing status |
| `document_extractions` | Untrusted proposed fields for review (at most one per document) |
| `categories` | Reusable labels; inactive kept for history but not for new picks |
| `expenses` | User-approved financial record; source of truth for list/dashboard |

Expense ownership: `expenses.document_id → documents.user_id` (no `expenses.user_id`). No `users.role`.

ER diagram: [diagrams/er-diagram.mmd](diagrams/er-diagram.mmd).

```mermaid
erDiagram
    users ||--o{ documents : owns
    documents ||--o| document_extractions : has
    documents ||--o| expenses : may_produce
    categories ||--o{ expenses : classifies
    categories ||--o{ document_extractions : "optional proposed"
```

### Cardinalities

| Relationship | Cardinality |
| ------------ | ----------- |
| User → Document | 1 : N |
| Document → DocumentExtraction | 1 : 0..1 |
| Document → Expense | 1 : 0..1 |
| Category → Expense | 1 : N |

### Database-enforceable invariants (summary)

- Unique email, category name/slug; at most one extraction and one expense per document.
- Hard-deleting a document cascades its extraction; expense FK to document is `ON DELETE RESTRICT`.
- Amount and file size checks; currency allowlist when set.

### Application rules (summary)

- Writing extractions must never insert an expense.
- Expense is created only on explicit approve, in one transaction with `status = SAVED`.
- No expense while status is `UPLOADED` / `PROCESSING` / `REVIEW_REQUIRED` / `PROCESSING_FAILED`.
- Lists and dashboard query `expenses` only.
- Owner isolation on every document/expense load.
- Pending delete removes document + file; Flow C expense delete keeps the file and sets `REVIEW_REQUIRED`.

Column-level detail lives in `001_create_mvp_schema.sql` and the ER diagram source — this doc does not repeat every column.

---

## 9. Open decisions

Still open until the relevant phase:

- Digital-only PDF vs scanned PDF support depth.
- Which OCR engine / AI extraction approach replaces the mock.
- How long to keep raw OCR text.
- Retry limits and timeouts.
- Whether field-level confidence scores are needed after MVP.
- Exact category labels may still be refined (slugs should stay stable).

Already decided:

- Sync processing in the upload request (mock today).
- Manual-continue creates/clears an **empty** `document_extractions` row.
- File bytes via `GET /documents/{id}/file`; review DTO includes `fileUrl`.
- Upload MIME/size and `{UPLOAD_DIR}/{userId}/{uuid}{ext}` layout.
- Currencies `EUR` / `USD` / `GBP`; hard delete only; no `users.role` / no `expenses.user_id`.

---

## 10. Principles

- **Human-in-the-loop:** AI proposes; the user confirms.
- **Backend as trust boundary:** security and business rules do not depend on the frontend.
- **Graceful degradation:** partial or failed extraction falls back to manual entry.
- **Clear ownership:** every document and expense belongs to one user in the MVP.
- **No premature overengineering:** add infrastructure and AI complexity only when needed.
- **Documentation follows reality:** update this file when decisions change.

---

## 11. Change log

| Date | Change |
| ---- | ------ |
| 2026-09-11 | Step 12: `GET /expenses`, `GET /expenses/{id}` (`ExpenseViewResponse`); list/details UI; approve navigates to `#/expenses`; Flow C current contract. |
| 2026-09-11 | Step 11: `POST /documents/{id}/approve` — atomic `expenses` insert + `SAVED`; `expense` package + review Approve UI; extraction left as history. |
| 2026-09-08 | Compacted architecture doc: system shape (deployment, packages, frontend, API), linked detail diagrams, removed duplicated inline flows; Step 10 mock/review contract kept. |
| 2026-09-08 | Step 10: sync mock processing; review DTO + file stream; retry / continue-manual / pending DELETE; empty extraction on manual-continue. |
| 2026-07-31 | Upload MIME/size and storage path rules; initial `POST/GET /documents` contract. |
| 2026-07-27 | Auth flows, JWT contract, stack notes. |
| 2026-07-21 | Tech stack chosen (Spring Boot + Vite). |
| 2026-07-17 | MVP relational model and ER diagram. |
| 2026-07-16 | Flow A and Flow C activity diagrams. |
| 2026-07-14 | Initial architecture draft and Flow B. |

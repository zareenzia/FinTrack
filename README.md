# 💰 FinTrack — Personal Finance Platform

> A full-stack personal finance platform built with **Spring Boot 4** and **Vanilla JavaScript**. Track income, expenses, transfers, and recurring bills across real bank/cash/credit-card accounts; plan budgets and monthly goals; manage investments, loans, subscriptions, and gold holdings; and get AI-powered insights, coaching, and a chat assistant grounded in your own data — all from a clean, responsive UI with light/dark theming and a fully customizable sidebar.

---

## 📋 Table of Contents

- [Features](#-features)
- [Tech Stack](#-tech-stack)
- [Project Structure](#-project-structure)
- [Database Schema](#-database-schema)
- [REST API Reference](#-rest-api-reference)
- [Authentication & Security](#-authentication--security)
- [UI & Frontend Architecture](#-ui--frontend-architecture)
- [Pages & Navigation](#-pages--navigation)
- [Getting Started](#-getting-started)
- [Configuration](#-configuration)
- [Build & Run](#-build--run)
- [Known Limitations](#-known-limitations)
- [Roadmap](#-roadmap)

---

## ✨ Features

### 💳 Dashboard
- Real-time stat cards (income, expense, balance, savings, assets, net worth) that auto-shrink text to always fit on one line
- **Expense by Category** (Doughnut/Bar) and **Monthly Trend** (Line/Bar) charts via Chart.js
- A page-wide **Monthly View** selector drives every dashboard widget (stat cards, charts, category breakdown, Recent Transactions) from one place
- **Recent Transactions** card with Month / Category / Type / Paid-From-Account filters, a **Net Total** row (income adds, expense/savings/outflow-transfer subtracts, external-source transfers count as income), and one-click **CSV export/import**
- AI-generated **Dashboard Summary** card (today's insight, biggest expense, budget/savings status, one recommendation, health score) — see [AI Financial Assistant](#-ai-financial-assistant--coach)

### 💸 Transactions
- Income / Expense / Savings / Transfer entry, each with category, date, and an optional linked account
- **Bulk Add** — add several transactions (or transfers) in one modal, each row independently validated; partial failures keep only the failed rows so you don't re-enter everything
- **Log Cash Transfer** between two of your own accounts, *or* to/from money outside the app — an **External Source/Destination** option (🌐) lets you record salary coming in or cash spent externally without inventing a fake account. An optional checkbox lets an external transfer *also* count toward Income/Expense totals (e.g. salary), so you don't have to log it twice
- **Spend from Savings** — mark an expense as funded from your savings bucket; it still counts as a full expense but also draws down the savings total for the month, matching the same netting rule used everywhere else (dashboard, analytics, monthly trend)
- **Credit cards behave like real liabilities**: a purchase increases what you owe, a payment (transfer into the card) decreases it, refunds decrease it — see [Accounts](#-accounts--credit-cards) below
- **Recurring Transactions** (own tab on the Transactions page): schedule a bill/income/transfer to auto-post on a Daily/Weekly/Monthly/Quarterly/Yearly cadence with a custom interval, optional end date, editable next-run date, pause/resume, and a **Next Month Forecast** tab (expected income, outflow, net, and a per-section breakdown)
- **Filters everywhere**: Recent Transactions and Recurring Transactions both have Search/Type/Category/Frequency/Status/Sort/Page-size controls that stay on one line
- **CSV export & import** for both one-off and recurring transactions:
  - Export always respects the current filters, not just the visible page
  - Import auto-creates any category name it doesn't recognize (color/type inferred), and deliberately **rejects Transfer rows** rather than guessing which account a "From → To" label refers to — money movement isn't something worth risking a wrong guess on. Non-transfer rows fall back to "no account" if the account name can't be matched, rather than picking a wrong one

### 🏦 Accounts & Credit Cards
- Track Bank, Cash, Mobile Financial Service (MFS), and Credit Card accounts, each with a live running balance
- **Credit cards are modeled as liabilities, not wallets**: spending on a card increases the outstanding balance instead of decreasing it; a payment (transfer to the card) reduces it; the sign-flip is centralized in one balance service so every code path (create/edit/delete/transfer) stays consistent
- Per-card **credit limit behavior**: `WARN` (default, purchase succeeds with a warning) or `BLOCK` (purchase rejected) when a charge would exceed the limit; overpaying a card is always blocked
- Computed card stats: available credit, utilization %, an estimated minimum payment, and days until the statement is due
- Per-account **ledger** view (`/api/accounts/{id}/ledger`) with a running balance, filterable by date range, category, type, or merchant text
- Deleting an account is blocked if any transaction still references it

### 🗂️ Categories
- **Bulk category creation** — add several categories in one form, each as its own row with live preview
- A predefined 20-color palette (swatch picker) instead of a free color input, plus the existing icon picker
- Categories are unique per user (case-insensitive) and can optionally be scoped to a transaction type

### 📊 Budget Planner
- **Budget Plans** for a Month/Quarter/Year period: planned income, planned savings, notes, per-category budget lines, and savings-goal lines, with a live "budgeted vs. actual" view and a computed 0–100 budget health score
- **Duplicate** a plan or **Copy Previous Period** to roll a budget forward instantly
- Reusable **Budget Templates** (income/savings defaults + category rows) that can be applied to instantiate a new plan for any period
- **Export** any plan's report as CSV, Excel, or PDF
- A daily scheduled job (plus a login-time catch-up) checks every active plan and raises an in-app notification when spending crosses a budget threshold

### 🎯 Financial Planner
- **Investments** — stocks, mutual funds, ETFs, bonds, crypto, fixed deposits, or other; tracks quantity/purchase/current price with computed value, profit/loss, and return %
- **Loans** — personal/home/car/education/business, or money borrowed/lent to someone; tracks principal, interest rate, EMI, remaining balance, and status, with a one-click "Pay EMI" action that reduces the balance and auto-closes the loan at zero
- **Subscriptions** — recurring paid services (monthly/yearly), with auto-renewal flag and days-until-renewal
- **Wishlist / Savings Goals** — target amount, saved amount, target date, priority, with a "mark complete" action
- One summary endpoint rolls all four up into a single dashboard view

### 🥇 Gold Assets
- Track gold holdings by name, type (Ornament/Bar/Coin/Custom), purity (18K–24K, Traditional, Custom), and weight in Gram/Vori/Ana/Rati/Point — all units auto-convert
- **Live gold price sync** (scraped from goldr.org on a schedule, or triggered manually) with an **Automatic vs. Manual** pricing mode per user
- Computed current value, gain/loss, gain/loss %, and an adjustable "sell discount %" to preview realistic resale price
- **CSV export/import**: import requires a name and a positive weight; Gold Type/Purity/Weight Unit default sensibly when blank but are **rejected outright if provided-and-invalid** (a typo'd purity would silently misstate the asset's value, so this one is stricter than the transaction importer)

### 🤖 AI Financial Assistant & Coach
- **Chat** with an assistant that can call live tools grounded in your real data — balances, monthly income/expense/savings, category breakdowns, recent transactions, budget status, net worth, gold holdings, and month-over-month comparisons — plus genuine **RAG retrieval**: your message is embedded and the most relevant of your own transactions/notes/todos/accounts/gold-assets/budget-plans/past chat turns are pulled in as context (pgvector cosine similarity, strictly scoped to your own data)
- **Financial Health Score** (0–100) with a breakdown across savings rate, budget adherence, cash-flow stability, cash reserve months, and net-worth trend
- Deterministic (non-LLM), threshold-based **Insights** — e.g. "You spent 40% more on Dining this month than your 3-month average" — plus evidence-cited **Recommendations**, a dedicated **Budget Coach** and **Savings Coach**, and a structured **Monthly Report** (summary, asset growth, top purchases, health score, recommendations) for any month
- Full conversation management (create/rename/delete/list), and per-user AI settings (model, max tokens, temperature, on/off, and a toggle for each coaching feature)
- Works without any OpenAI billing for the retrieval/indexing pipeline via a mock embedding provider (`ai.embedding.provider=mock`) — see [Known Limitations](#-known-limitations) for the chat/tool-calling gap

### 📝 Notes
- Rich-text notes (Quill editor, including checklists) with pinning, archiving, tags, and a 12-swatch pastel color picker
- Full-text search across title and content

### ✅ To-Dos
- Title, description, due date/time, priority, category, and status, with search and status/priority filters

### 🎨 UI / UX
- **Fully customizable sidebar** — drag-and-drop reorder, show/hide, and pin favorite modules; preferences persist to both `localStorage` and the server. Dashboard and Settings are locked in place
- Built-in floating **Calculator** widget accessible from the sidebar on every page
- **Notifications** bell with unread badge (budget alerts, etc.)
- **Light/Dark theme** with system-preference detection and smooth transitions, plus a selectable accent color theme
- Fully responsive — sidebar collapses to a hamburger menu on mobile

---

## 🛠 Tech Stack

| Layer | Technology |
|---|---|
| **Backend** | Spring Boot 4.1.0 |
| **Language** | Java 17 |
| **Security** | Spring Security 6 + JWT (jjwt 0.12.3, HS512) |
| **Persistence** | Spring Data JPA (Hibernate) |
| **Database** | PostgreSQL (Neon-hosted, serverless) with the `pgvector` extension for AI embeddings |
| **AI** | OpenAI Responses API (chat + tool-calling) and Embeddings API (`text-embedding-3-small`), with a mock embedding provider for billing-free RAG testing |
| **Document generation** | Apache POI (Excel export), Apache PDFBox (PDF export), Jsoup (HTML parsing/stripping) |
| **Build Tool** | Gradle 8 |
| **Frontend** | Vanilla JavaScript (no framework), static HTML per page |
| **CSS Framework** | Bootstrap 5.3 |
| **Icons** | Font Awesome 6.4 |
| **Charts** | Chart.js |
| **Rich text** | Quill (Notes editor), `marked` + `DOMPurify` (Markdown rendering in the AI chat) |

---

## 📁 Project Structure

```
finzin/
├── build.gradle                                # Gradle build config
├── settings.gradle
├── gradlew / gradlew.bat                       # Gradle wrapper
│
└── src/
    ├── main/
    │   ├── java/org/example/finzin/
    │   │   ├── FinzinApplication.java          # Spring Boot entry point
    │   │   │
    │   │   ├── config/
    │   │   │   ├── SecurityConfig.java         # Spring Security filter chain
    │   │   │   ├── JwtAuthFilter.java          # JWT extraction from cookie/header
    │   │   │   ├── DatabaseMigration.java      # Idempotent raw-SQL migrations run at startup
    │   │   │   ├── AsyncConfig.java            # @Async executor for RAG indexing / gold sync
    │   │   │   ├── JacksonConfig.java
    │   │   │   └── WebConfig.java
    │   │   │
    │   │   ├── entity/                         # 26 JPA entities — see Database Schema
    │   │   │
    │   │   ├── repository/                     # One Spring Data repository per entity
    │   │   │
    │   │   ├── service/
    │   │   │   ├── AuthService.java, JwtTokenProvider.java, PasswordService.java
    │   │   │   ├── AccountBalanceService.java  # Single source of truth for balance math incl. credit-card sign-inversion
    │   │   │   ├── CreditCardService.java      # Credit limit validation, stats, ledger
    │   │   │   ├── RecurringTransactionService.java, RecurringTransactionScheduler.java, RecurringTransactionExecutionService.java
    │   │   │   ├── Budget*.java                # BudgetService, BudgetPlanService, BudgetTemplateService, BudgetScheduler, BudgetExportService
    │   │   │   ├── FinancialSummaryService.java
    │   │   │   ├── NotificationService.java
    │   │   │   └── gold/                       # GoldAssetService, GoldPriceScraper, GoldPriceSyncService, GoldPriceScheduler, GoldWeightConverter
    │   │   │
    │   │   ├── ai/                             # AI Assistant + Financial Coach
    │   │   │   ├── AIController.java, AICoachApiController.java, AiSettingsApiController.java
    │   │   │   ├── AIService.java, ConversationService.java, FinancialToolExecutor.java, PromptBuilder.java
    │   │   │   ├── FinancialHealthService.java, InsightService.java, RecommendationService.java, MonthlyReportService.java, DashboardSummaryService.java
    │   │   │   ├── OpenAIClient.java, OpenAIException.java
    │   │   │   └── rag/                        # DocumentIndexer, EmbeddingService, SemanticSearchService, VectorRepository, OpenAIEmbeddingClient, MockEmbeddingClient
    │   │   │
    │   │   └── web/                            # REST controllers — see REST API Reference
    │   │
    │   └── resources/
    │       ├── application.properties          # DB, server, JWT, gold sync config
    │       └── static/                         # Served as-is by Spring Boot — no templating engine
    │           ├── css/style.css                # All styles + CSS variables + dark mode
    │           ├── js/
    │           │   ├── auth.js, sidebar.js, profile.js  # Cross-page auth guard, sidebar injection, profile modal
    │           │   ├── accounts.js, assets.js           # Account Configuration, Gold Assets
    │           │   ├── budget-planner.js, financial-planner.js
    │           │   ├── notes.js, ai-assistant.js, calculator.js
    │           ├── login.html, signup.html, profile.html
    │           ├── dashboard.html, transactions.html
    │           ├── budget-planner.html, financial-planner.html, assets.html
    │           ├── notes.html, todos.html, settings.html, ai-assistant.html
    │
    └── test/
        └── java/org/example/finzin/            # Mockito-based service tests + Spring context tests
```

---

## 🗄 Database Schema

Schema is managed by a combination of Hibernate (`ddl-auto=update`, creates new tables/columns automatically) **and** `DatabaseMigration.java` — an idempotent raw-SQL runner executed at startup for changes Hibernate won't do safely on its own (dropping a stale constraint, backfilling data, re-scoping a unique key, recomputing a derived column). If you're chasing a schema quirk that doesn't match the entity class, check that file first.

### Core

**`users`** — a registered account. `id`, `full_name`, `username` (unique), `email` (unique), `password_hash` (BCrypt), `profile_picture`, `created_at`, `updated_at`.

**`categories`** — a label for classifying transactions/budgets. `id`, `user_id` (null = global), `name`, `description`, `color`, `icon`, `category_type` (nullable — null/"general" applies to any type). Unique on `(user_id, name)` — a stale globally-scoped auto-named constraint from an earlier schema version was dropped by migration since it caused false duplicate-name errors across different users.

### Accounts, Transactions & Recurring

**`accounts`** — a bank/cash/MFS/credit-card instrument. `id`, `user_id`, `account_type`, `bank_name`, `account_nickname`, `account_number`, `card_type`, `linked_account_id`, `provider`, `mobile_number`, `credit_limit`, `statement_day`, `due_day`, `credit_limit_behavior` (`WARN`/`BLOCK`, default `WARN`), `opening_balance`, `current_balance`, `status`, timestamps.

**`transactions`** — a single money movement. `id`, `user_id`, `amount`, `description`, `category_id` (FK, nullable — null for transfers), `transaction_type` (`income`/`expense`/`savings`/`transfer`), `date`, `created_at`, `source_account_id`, `destination_account_id` (either may be null on a transfer to mean "outside the tracked accounts", but not both), `is_auto_generated`, `recurring_transaction_id`, `from_savings`.

**`recurring_transactions`** — a schedule that materializes real transactions. `id`, `user_id`, `transaction_name`, `description`, `transaction_type`, `category_id`, `amount`, `source_account_id`, `destination_account_id`, `frequency`, `interval_value`, `start_date`, `end_date`, `next_execution_date`, `last_execution_date`, `status` (`ACTIVE`/`PAUSED`/`COMPLETED`).

### Budgeting

**`category_budgets`** — a per-category budget line. `id`, `user_id`, `budget_plan_id` (nullable — links into a plan), `category_id`, `period` ("yyyy-MM"), `budget_amount`. Unique on `(budget_plan_id, category_id)` (re-scoped by migration from an earlier per-user-per-period uniqueness).

**`budget_plans`** — a named budgeting period. `id`, `user_id`, `name`, `period_type` (`MONTH`/`QUARTER`/`YEAR`), `period`, `start_date`, `end_date`, `planned_income`, `planned_savings`, `notes`, `status` (`ACTIVE`/`ARCHIVED`).

**`savings_budgets`** — a savings goal within a plan. `id`, `budget_plan_id`, `category_id`, `target_amount`, `initial_amount` (pre-existing savings not backed by a transaction), `storage_account_id`, `source_account_id`, `source_description`. Unique on `(budget_plan_id, category_id)`.

**`budget_templates`** / **`budget_template_categories`** — reusable templates and their category/amount/is-savings line rows.

### Financial Planner

**`investments`** — `id`, `user_id`, `name`, `investment_type`, `platform`, `purchase_date`, `quantity`, `purchase_price`, `current_price`, `notes`.

**`loans`** — `id`, `user_id`, `loan_name`, `loan_type`, `lender_borrower`, `principal_amount`, `interest_rate`, `emi_amount`, `loan_start_date`, `loan_end_date`, `remaining_balance`, `payment_frequency`, `status` (`ACTIVE`/`CLOSED`/`OVERDUE`), `notes`.

**`subscriptions`** — `id`, `user_id`, `name`, `category`, `billing_cycle`, `cost`, `renewal_date`, `payment_method`, `payment_account`, `auto_renewal`, `status`, `notes`.

**`wishlist_goals`** — `id`, `user_id`, `goal_name`, `category`, `target_amount`, `saved_amount`, `target_date`, `priority`, `status`, `icon`, `notes`.

**`net_worth_snapshots`** — a monthly point-in-time snapshot, upserted as a side effect of the AI Health Score calculation. `id`, `user_id`, `snapshot_month`, `net_worth`, `total_assets`, `balance`, `total_savings_contributed`. Unique on `(user_id, snapshot_month)`.

### Gold Assets

**`gold_assets`** — `id`, `user_id`, `asset_name`, `description`, `gold_type`, `purity`, `weight`, `weight_unit`, `purchase_date`, `purchase_price`, `current_value` (recalculated on each price sync), `notes`.

**`gold_prices`** — historical fetched price points. `id`, `unit`, `purity`, `market_price`, `old_selling_price`, `source`, `retrieved_at`.

**`gold_price_settings`** — one row per user. `id`, `user_id` (unique), `mode` (`AUTOMATIC`/`MANUAL`), `manual_prices_json`.

### AI / RAG

**`ai_conversations`** — `id`, `user_id`, `title`.

**`ai_messages`** — `id`, `conversation_id`, `user_id`, `role` (`user`/`assistant`/`tool`), `content`, `tool_name`, `token_count`.

**`ai_settings`** — one row per user. `provider`, `model`, `max_tokens`, `temperature`, `enabled`, `developer_mode`, plus five feature toggles (proactive insights, budget coaching, savings coaching, monthly reports, dashboard summary).

**`ai_document_embeddings`** — RAG index metadata. `id`, `user_id`, `entity_type`, `entity_id`, `title`, `content`, `content_hash`, `metadata`. Unique on `(user_id, entity_type, entity_id)`. **Not fully mapped by the entity class** — the `embedding vector(1536)` column (pgvector, HNSW cosine-similarity index) is written/read via raw JDBC so Hibernate schema validation never has to understand the `vector` type.

### Other

**`assets`** — a manually tracked non-cash asset (separate from Gold Assets). `id`, `user_id`, `name`, `type`, `description`, `value`.

**`notes`** — `id`, `user_id`, `title`, `content` (rich HTML), `color`, `tags`, `pinned`, `archived`, `done`.

**`todos`** — `id`, `user_id`, `title`, `description`, `due_date`, `due_time`, `priority`, `category`, `status`, `completed`, `color`.

**`notifications`** — `id`, `user_id`, `type`, `title`, `message`, `related_entity_type`, `related_entity_id`, `is_read`.

**`sidebar_preferences`** — one row per user. `user_id` (unique), `preferences_json` (array of `{id, visible, pinned, displayOrder}`).

---

## 📡 REST API Reference

All routes are prefixed `/api`. Auth is carried via JWT in the `Authorization: Bearer <token>` header, or (on most controllers) an `Authorization` cookie fallback — see [Authentication & Security](#-authentication--security) for the important caveat about the anonymous fallback.

### 🔐 Auth — `/api/auth`
| Method & Path | Purpose |
|---|---|
| `POST /register` | Full registration (fullName, username, email, password) |
| `POST /register-simple` | Simplified registration (email + password only) |
| `POST /login` | Login; also triggers recurring-transaction catch-up and budget-alert checks for that user |
| `GET /me` | Current user profile (includes `profileComplete` flag) |
| `PUT /profile` | Update fullName/username |
| `GET /check-username?username=` | Availability check |
| `POST /change-password` | Change password |
| `POST /profile-picture` | Upload a profile picture (multipart, ≤5MB, JPG/PNG/WebP) |
| `DELETE /profile-picture` | Remove profile picture |

### 🗂️ Categories — `/api/categories`
`GET /` (optional `?type=`) · `POST /` · `PUT /{id}` · `DELETE /{id}` — case-insensitive unique name per user enforced both client-side and via a DB constraint fallback.

### 💸 Transactions — `/api/transactions`
| Method & Path | Purpose |
|---|---|
| `GET /?category_id=&type=&limit=100` | List, sorted by date descending |
| `POST /` | Create — see the transfer and credit-card notes below |
| `PUT /{id}` | Update (reverses old balance effect, validates, reapplies) |
| `DELETE /{id}` | Delete (reverses balance effect) |

Notable conventions:
- **Transfers**: `sourceAccountId`/`destinationAccountId` may each be `null` to mean "outside the tracked accounts" (external source/destination), but not both. An optional flag lets an external transfer also post as a real income/expense.
- **Credit cards**: creating/updating a transaction against a credit-card account routes through `AccountBalanceService`, which can reject the request (400, `CreditCardValidationException`, when `creditLimitBehavior=BLOCK` and the limit would be exceeded, or on any overpayment) or attach a non-fatal `warning` string to the response body (when `creditLimitBehavior=WARN`).
- **fromSavings**: an expense with `fromSavings=true` forces `sourceAccountId` to null and nets against the month's savings total.

### 🏦 Accounts — `/api/accounts`
| Method & Path | Purpose |
|---|---|
| `GET /` | List accounts |
| `GET /summary` | Totals by type + total available + total credit outstanding |
| `POST /` | Create (balance seeded from `openingBalance`) |
| `PUT /{id}` | Update (re-derives `currentBalance` from the opening-balance delta) |
| `DELETE /{id}` | Delete — blocked (400) if any transaction still references it |
| `PATCH /{id}/status` | Toggle ACTIVE/INACTIVE |
| `GET /{id}/ledger?startDate=&endDate=&category=&type=&merchant=` | Running-balance ledger, filterable |

Credit-card accounts additionally return `availableCredit`, `utilizationPercent`, `minimumPaymentEstimate`, and `daysUntilDue`.

### 🔁 Recurring Transactions — `/api/recurring-transactions`
| Method & Path | Purpose |
|---|---|
| `GET /` | List all |
| `GET /upcoming?days=14` | Occurrences due within N days |
| `POST /` | Create |
| `PUT /{id}` | Update — an explicit `nextExecutionDate` wins outright (rejected if in the past or not after `lastExecutionDate`); otherwise the date only recalculates when the schedule itself changed, so a cosmetic edit never reschedules a due date |
| `PATCH /{id}/status` | `ACTIVE`/`PAUSED`/`COMPLETED` — resuming jumps to the next occurrence rather than backfilling missed runs |
| `DELETE /{id}` | Delete the schedule (does not touch already-generated transactions) |

### 📊 Budgets — `/api/budgets` (simple) & `/api/budget-plans` & `/api/budget-templates`
| Method & Path | Purpose |
|---|---|
| `GET /api/budgets?period=` | List simple per-category budgets for a month |
| `GET /api/budgets/status?period=` | Budgeted vs. actual per category |
| `POST /api/budgets` · `DELETE /api/budgets/{id}` | Create/delete a simple budget line |
| `GET /api/budget-plans?period=&status=&search=&sort=` | List/filter plans (with summary + score) |
| `GET /api/budget-plans/current` | The active plan, or `{hasCurrent:false}` |
| `GET /api/budget-plans/{id}/full` | Full detail: category statuses, savings statuses, summary, score |
| `POST /api/budget-plans` · `PUT /{id}` · `PATCH /{id}/archive` · `DELETE /{id}` | Plan CRUD/lifecycle |
| `POST /api/budget-plans/{id}/duplicate` | Clone a plan |
| `POST /api/budget-plans/copy-previous` | Roll a plan forward from the prior period |
| `POST /{id}/categories` · `DELETE /{id}/categories/{budgetId}` | Category budget lines within a plan |
| `POST /{id}/savings` · `DELETE /{id}/savings/{savingsId}` | Savings goal lines within a plan |
| `GET /{id}/export/{format}` | Export the plan as `csv` / `excel` / `pdf` |
| `GET /api/budget-templates` · `POST /` · `PUT /{id}` · `DELETE /{id}` | Template CRUD |
| `POST /api/budget-templates/{id}/apply` | Instantiate a plan from a template |

### 🎯 Financial Planner — `/api/financial-planner`
| Method & Path | Purpose |
|---|---|
| `GET /summary` | Combined dashboard across all four sub-modules |
| `GET/POST/PUT/DELETE /investments{,/{id}}` | Investment CRUD |
| `GET/POST/PUT/DELETE /loans{,/{id}}` + `POST /loans/{id}/pay-emi` | Loan CRUD + EMI payment |
| `GET/POST/PUT/DELETE /subscriptions{,/{id}}` | Subscription CRUD |
| `GET/POST/PUT/DELETE /goals{,/{id}}` + `POST /goals/{id}/complete` | Wishlist goal CRUD + completion |

### 🥇 Gold Assets — `/api/gold`
| Method & Path | Purpose |
|---|---|
| `GET/POST/PUT/DELETE /assets{,/{id}}` | Gold asset CRUD (returns `weightConversions`, `gainLoss`, `gainLossPct`) |
| `GET /prices/current` | Latest prices per purity/unit + sync status |
| `POST /prices/sync` | Trigger an async price sync |
| `POST /prices/mode` | Set `AUTOMATIC` or `MANUAL` pricing (with manual price map) |
| `GET /summary` | Total value, weight, asset count, current price per purity |
| `GET /convert-weight?value=&unit=` | Pure unit-conversion utility |

### 📝 Notes — `/api/notes`
`GET /?search=&archived=` · `POST /` · `PUT /{id}` (partial) · `DELETE /{id}` — response includes an HTML-stripped `preview` field.

### ✅ To-Dos — `/api/todos`
`GET /?search=&status=&priority=` · `POST /` · `PUT /{id}` (partial) · `DELETE /{id}` — setting `completed=true` also sets `status=completed`.

### 📈 Analytics — `/api/analytics`
`GET /summary` · `GET /category-breakdown?type=` · `GET /monthly` (income/expense/savings per month, savings netted by `fromSavings`).

### 🔔 Notifications — `/api/notifications`
`GET /` · `GET /unread-count` · `PATCH /{id}/read` · `PATCH /read-all`.

### 🧩 Sidebar Preferences — `/api/sidebar-preferences`
`GET /` · `PUT /` (body `{preferencesJson}`) · `DELETE /` (reset to defaults).

### 🤖 AI Assistant — `/api/ai`
| Method & Path | Purpose |
|---|---|
| `POST /chat` | Send a message (RAG + tool-calling loop); errors are tagged (`RATE_LIMIT`, `AUTH_ERROR`, `NOT_CONFIGURED`, `DISABLED`, `TIMEOUT`, …) with an HTTP status and a `retryable` flag |
| `GET/POST /conversations` · `PUT/DELETE /conversations/{id}` | Conversation management |
| `GET /conversations/{id}/messages` | Message history (system/tool messages hidden from the client) |
| `GET /health` | Financial Health Score + breakdown |
| `GET /insights` | Generated insights |
| `GET /recommendations` | Evidence-cited recommendations |
| `GET /budget-coach` / `GET /savings-coach` | Targeted coaching advice |
| `GET /dashboard-summary` | AI-generated dashboard blurb |
| `GET /monthly-report?month=yyyy-MM` | Structured monthly report |
| `GET /settings` · `PUT /settings` | Model/tokens/temperature + feature toggles (`maxTokens` 100–4000, `temperature` 0–2) |

---

## 🔐 Authentication & Security

### JWT Strategy
- On login/register, the server issues an **HS512-signed JWT** (`userId`, `username`, `email` claims), returned in the response body and also set as a 7-day `Authorization` cookie
- Unlike some earlier versions of this project, the signing secret (`app.jwt.secret`) is now a **fixed configured value** in `application.properties`, not regenerated on every restart — tokens survive a server restart as long as the secret doesn't change
- `JwtAuthFilter` checks the `Authorization: Bearer <token>` header first, falls back to the `Authorization` cookie, and on success sets `userId` as a request attribute that every controller reads via `request.getAttribute("userId")`

### ⚠️ Anonymous-request fallback
Nearly every controller's `getUserId(request)` helper defaults to **a hardcoded user ID** when the request attribute is absent (i.e. no valid token was presented) — a leftover "demo mode" convenience rather than a hard failure. This means an unauthenticated request against most endpoints doesn't get rejected; it silently reads/writes that one fallback account. This is fine for local development but **must not reach a real multi-tenant deployment** without being replaced by a proper 401 response. If you're writing scripts or automated tests against this API, always pass an explicit, valid `Authorization` header — do not rely on cookie fallback or omit auth entirely, or you may find yourself reading (or writing!) someone else's data.

### Page-Level Guards
`PageController` checks `request.getAttribute("userId")`: authenticated users get forwarded to the requested page; unauthenticated users on a protected route are redirected to `/login`; authenticated users hitting `/`, `/login`, or `/signup` are redirected to `/dashboard`. Note: `profile.html` (the post-signup onboarding screen) is reached directly as a static file and is **not** covered by this gate.

### Spring Security
- CSRF disabled (stateless JWT app); custom form login and HTTP Basic both disabled
- All routes are `permitAll()` at the Spring Security layer — actual enforcement happens in `PageController` (pages) and each controller's `getUserId()` (API), not in the security filter chain
- Passwords hashed with **BCrypt**

---

## 🎨 UI & Frontend Architecture

### No Framework — Pure JS + Static HTML
Every page is a standalone `.html` file served directly from `src/main/resources/static/` — no Thymeleaf/Freemarker/templating engine. Some pages keep their logic in a dedicated `js/*.js` file (assets, budget-planner, financial-planner, notes, ai-assistant, accounts); others (dashboard, transactions, todos, login/signup/profile) embed it inline in the page's own `<script>` block.

### Sidebar Injection & Customization
`sidebar.js` is included on every protected page and, on `DOMContentLoaded`, wraps existing body content in `#main-content`, injects the `<aside id="sidebar">`, and wires up:
- **Drag-and-drop reordering, show/hide, and pinning** of sidebar modules — preferences are stored per-user in `localStorage` and best-effort synced to `PUT /api/sidebar-preferences`; Dashboard and Settings are locked in place
- Collapse/expand state, compact mode, and a mobile hamburger + overlay
- A floating **Calculator** popup, the **Notifications** panel, and the **Profile** modal, all injected on demand

### Theme System
Controlled by a `data-theme` attribute on `<html>` (`""` = light, `"dark"` = dark), applied *before first paint* to avoid a flash of the wrong theme. Priority: `localStorage` → system `prefers-color-scheme` → light. A separate selectable accent color theme layers on top via `data-color-theme`. Bootstrap's own CSS variables are overridden so its components respect both.

### Recent Transactions Layout
Each row is a CSS Grid (`110px 1fr 160px 140px 120px 130px 82px` — Date/Description/Category/Paid-From/Type/Amount/Actions), animating in with a staggered fade-and-slide keyframe. The Net Total row reuses the same grid so its amount column lines up with every row above it.

---

## 📄 Pages & Navigation

| URL | HTML File | Description |
|---|---|---|
| `/` | — | Redirects to `/login` or `/dashboard` |
| `/login`, `/signup` | `login.html`, `signup.html` | Combined login/signup screen |
| — | `profile.html` | Post-signup onboarding (not routed via `PageController`; reached directly) |
| `/dashboard` | `dashboard.html` | Stats, charts, Recent Transactions, AI dashboard summary |
| `/transactions` | `transactions.html` | Transactions + Recurring Transactions + Next Month Forecast (all three tabs) |
| `/budget-planner` | `budget-planner.html` | Budget plans, templates, category/savings budgets, export |
| `/financial-planner` | `financial-planner.html` | Investments · Loans · Subscriptions · Wishlist Goals (4 tabs) |
| `/assets` | `assets.html` | Gold asset tracking + live pricing |
| `/notes` | `notes.html` | Rich-text notes |
| `/todos` | `todos.html` | To-do list |
| `/ai-assistant` | `ai-assistant.html` | AI chat assistant |
| `/settings` | `settings.html` | Appearance, currency/localization, categories, account configuration, AI settings, about |
| `/recurring-transactions` | — | Redirects to `/transactions` (merged into that page) |

### Sidebar Navigation (in order)
Home · Transactions · Budget Planner · Notes · To-Do · Assets · Financial Planner · Calculator (opens a popup, doesn't navigate) · Settings — plus a fixed bottom section for Notifications, AI Assistant, Theme toggle, Profile, and Logout. Every item except Home and Settings can be hidden, reordered, or pinned via the sidebar customizer.

---

## 🚀 Getting Started

### Prerequisites

| Tool | Minimum Version |
|---|---|
| Java JDK | 17 |
| PostgreSQL | 13+ (with the `vector` extension available, if you want real AI/RAG search) |
| Gradle | 8 (or use the `gradlew` wrapper) |

### 1. Clone the repository

```bash
git clone https://github.com/zareenzia/FinTrack.git
cd FinTrack
```

### 2. Set up PostgreSQL

Any Postgres 13+ instance works (this project currently points at a Neon serverless instance by default — see below). To use your own:

```sql
CREATE DATABASE fintrack;
CREATE USER fintrack_user WITH ENCRYPTED PASSWORD 'yourpassword';
GRANT ALL PRIVILEGES ON DATABASE fintrack TO fintrack_user;
```

If you want real semantic search (not the mock embedding provider), also run `CREATE EXTENSION IF NOT EXISTS vector;` — `DatabaseMigration.java` will otherwise try to enable it itself on startup.

### 3. Configure the application

`src/main/resources/application.properties` currently contains a live connection string, JWT secret, and other values checked directly into the repo for convenience. **Before deploying anywhere real**, replace these with environment variables:

```properties
# Server
server.port=8585

# Database
spring.datasource.url=jdbc:postgresql://<host>/<database>?sslmode=require
spring.datasource.username=<username>
spring.datasource.password=<password>

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect

# JWT (must be ≥ 64 chars for HS512)
app.jwt.secret=<a long random secret>

# AI Assistant (optional — omit to run with the AI features disabled)
openai.api.key=<your OpenAI API key>
# ai.embedding.provider=mock   # uncomment to exercise RAG indexing/retrieval without OpenAI billing
```

### 4. Build

```bash
./gradlew build -x test        # Linux/macOS
.\gradlew.bat build -x test    # Windows
```

### 5. Run

```bash
./gradlew bootRun        # Linux/macOS
.\gradlew.bat bootRun     # Windows
```

The app starts at **[http://localhost:8585](http://localhost:8585)** and redirects to the login page.

---

## ⚙️ Configuration

| Property | Default | Description |
|---|---|---|
| `server.port` | `8585` | HTTP port |
| `spring.datasource.url` / `username` / `password` | — | PostgreSQL connection |
| `spring.jpa.hibernate.ddl-auto` | `update` | Schema management — combined with `DatabaseMigration.java` for anything Hibernate can't do safely (see [Database Schema](#-database-schema)) |
| `app.jwt.secret` | — | HS512 signing key, ≥ 64 chars |
| `app.upload.dir` | `user-uploads/profiles` | Where profile pictures are stored |
| `openai.api.key` | — | OpenAI API key; if unset, AI chat/embeddings calls fail with `NOT_CONFIGURED` |
| `openai.api.base-url` | `https://api.openai.com/v1` | OpenAI API base URL |
| `openai.embedding.model` | `text-embedding-3-small` | Embedding model for RAG |
| `ai.embedding.provider` | `openai` | Set to `mock` to use deterministic fake embeddings (no OpenAI billing needed, but no real semantic matching either) |
| `gold.source.url` | `https://www.goldr.org/` | Gold price scrape source |
| `gold.sync.enabled` | `true` | Enable scheduled gold price sync |
| `gold.sync.interval-minutes` | `1440` | Sync frequency |

---

## 🔨 Build & Run

```bash
# Full build (with tests)
.\gradlew.bat build

# Build skipping tests (faster)
.\gradlew.bat build -x test

# Run in development
.\gradlew.bat bootRun

# Build a fat JAR for deployment
.\gradlew.bat bootJar
# Output: build/libs/finzin-0.0.1-SNAPSHOT.jar

# Run the JAR directly
java -jar build/libs/finzin-0.0.1-SNAPSHOT.jar
```

---

## ⚠️ Known Limitations

- **OpenAI quota vs. rate limit**: a genuinely exhausted/unfunded OpenAI account and a transient rate limit both surface to the user as the same "our AI assistant is a bit busy, try again in a moment" message — retrying won't fix a billing problem. Worth distinguishing if this ships to real users.
- **Anonymous-request fallback**: see [Authentication & Security](#-authentication--security) — several controllers silently fall back to a fixed user ID instead of rejecting unauthenticated requests. Fine for local dev, not for production.
- **Financial Planner request bodies**: the investments/loans/subscriptions/goals endpoints accept raw `Map<String,Object>` bodies rather than typed, validated request records, unlike almost every other controller.
- **CSV import never guesses at transfers**: for both one-off and recurring transactions, a Transfer-type row in an imported CSV is always rejected rather than parsed, since reliably mapping an account name back to an ID for money that will move automatically isn't safe to get wrong. Add transfers manually via "Log Cash Transfer" instead.
- **Secrets committed in `application.properties`**: the live Neon connection string and JWT secret currently live directly in the repo rather than environment variables — fine for a shared dev sandbox, not for a real deployment (see [Getting Started](#-getting-started)).

---

## 🗺 Roadmap

- [ ] Externalize secrets (DB credentials, JWT key, OpenAI key) to environment variables / a secrets manager
- [ ] Replace the anonymous-request fallback with a proper 401 across all controllers
- [ ] Distinguish OpenAI quota-exhausted errors from transient rate limiting in the chat UI
- [ ] Typed, validated request DTOs for the Financial Planner endpoints
- [ ] Email verification on registration; password reset flow
- [ ] Multi-currency support
- [ ] Mobile PWA support
- [ ] Broader automated test coverage (current suite is Mockito-based service tests + a handful of Spring context tests)

---

## 📄 License

This project is open source. See [LICENSE](LICENSE) for details.

---

<div align="center">
  <sub>Built with ❤️ using Spring Boot &amp; Vanilla JS</sub>
</div>

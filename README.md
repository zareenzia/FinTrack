# 💰 FinTrack — Personal Finance Tracker

> A full-stack personal finance management web application built with **Spring Boot 4** and **Vanilla JavaScript**. Track your income and expenses, manage assets, organize notes, handle to-dos, and visualize your financial health — all from a clean, modern, responsive UI with light/dark theme support.

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
- [Screenshots](#-screenshots)
- [Roadmap](#-roadmap)

---

## ✨ Features

### 💳 Dashboard
- **6 real-time stat cards** — Total Income, Total Expense, Balance, Savings Rate, Assets Value, Net Worth
- Stat card amounts auto-shrink font size to always fit on a single line regardless of value size
- **Expense by Category** — interactive Doughnut or Bar chart with a color-coded legend
- **Monthly Trend** — Line or Bar chart showing income vs. expense over time
- **Recent Transactions** — modern card-list layout (not a boring table) with:
  - Date badge (weekday + month+day + year)
  - Type indicator dot (green for income, red for expense)
  - Category chip with smart icon detection based on category name keywords
  - Type badge (Income / Expense pill)
  - Color-coded amount with `+`/`−` prefix
  - Per-row delete button
  - Smart pagination — First / Prev / numbered pages with ellipsis / Next / Last

### 💸 Transactions
- Create, view, and delete financial transactions
- Categorize every transaction (income or expense)
- Filter by category or transaction type
- Dates stored as `LocalDateTime` with flexible parsing (ISO, `yyyy-MM-dd HH:mm:ss`, plain date, etc.)
- Default limit of 100 transactions per request (configurable via `?limit=N`)

### 🗂️ Categories
- Create custom categories with a **name**, **description**, **color** (hex), and **icon**
- Unique category names enforced per user
- Categories power the chart breakdown, transaction filters, and the colorful category chips in the transaction list

### 🏦 Assets
- Track owned assets with name, type, description, and value
- Asset values are summed to produce the **Assets Value** and **Net Worth** stats on the dashboard
- Full CRUD: create, update, delete

### 📝 Notes
- Rich notes with **title**, **content** (multi-line), **color** (customizable background), and **tags**
- **Pin** important notes to the top
- **Archive** notes to declutter without deleting
- Full-text search across title and content
- Notes auto-sort: pinned first, then by `updatedAt` descending

### ✅ To-Dos
- Task management with **title**, **description**, **due date**, **due time**, **priority** (low / medium / high), **category**, **status** (pending / in_progress / completed), and **color**
- Mark complete, update status independently
- Filter by status and priority
- Search by title
- Only non-completed todos are returned by default

### 📊 Analytics
- `/api/analytics/summary` — total income, expense, balance, savings rate, total assets, net worth, transaction count
- `/api/analytics/savings` — dedicated savings summary endpoint
- `/api/analytics/category-breakdown` — expense totals per category with color
- `/api/analytics/monthly` — income and expense totals by `YYYY-MM` for trend charting

### 🎨 UI / UX
- **Collapsible left sidebar** replacing traditional top navbar — persists collapse state in `localStorage`
- **Light / Dark theme toggle** with smooth CSS transitions, system preference detection, and `localStorage` persistence
- **Responsive** — sidebar collapses to icon-only on mobile with a hamburger toggle
- Active page is highlighted in the sidebar
- Animated stat card icons (subtle floating loop)
- Custom color palette: Primary `#18230F`, Secondary `#27391C`, Accent `#255F38`, Surface `#1F7D53`
- Full CSS variable system (`--color-primary`, `--color-secondary`, `--color-accent`, `--color-surface`, etc.) for easy theming

---

## 🛠 Tech Stack

| Layer | Technology |
|---|---|
| **Backend** | Spring Boot 4.1.0 |
| **Language** | Java 17 |
| **Security** | Spring Security 6 + JWT (jjwt 0.12.3, HS512) |
| **Persistence** | Spring Data JPA (Hibernate) |
| **Database** | PostgreSQL |
| **Build Tool** | Gradle 8 |
| **Frontend** | Vanilla JavaScript (no framework) |
| **CSS Framework** | Bootstrap 5.3 |
| **Icons** | Font Awesome 6.4 |
| **Charts** | Chart.js |
| **Fonts / CDN** | jsDelivr CDN for Bootstrap + Cloudflare CDN for FA |

---

## 📁 Project Structure

```
finzin/
├── build.gradle                          # Gradle build config
├── settings.gradle
├── gradlew / gradlew.bat                 # Gradle wrapper
│
└── src/
    └── main/
        ├── java/org/example/finzin/
        │   ├── FinzinApplication.java     # Spring Boot entry point
        │   │
        │   ├── config/
        │   │   ├── SecurityConfig.java    # Spring Security filter chain
        │   │   └── JwtAuthFilter.java     # JWT extraction from cookie/header
        │   │
        │   ├── entity/
        │   │   ├── UserEntity.java        # users table
        │   │   ├── CategoryEntity.java    # categories table
        │   │   ├── TransactionEntity.java # transactions table
        │   │   ├── AssetEntity.java       # assets table
        │   │   ├── NoteEntity.java        # notes table
        │   │   └── TodoEntity.java        # todos table
        │   │
        │   ├── repository/
        │   │   ├── UserRepository.java
        │   │   ├── CategoryRepository.java
        │   │   ├── TransactionRepository.java
        │   │   ├── AssetRepository.java
        │   │   ├── NoteRepository.java
        │   │   └── TodoRepository.java
        │   │
        │   ├── service/
        │   │   ├── AuthService.java       # Register, login, profile update
        │   │   ├── JwtTokenProvider.java  # Token generation & validation
        │   │   └── PasswordService.java   # BCrypt password hashing
        │   │
        │   └── web/
        │       ├── PageController.java    # Route → HTML page forwarding
        │       ├── AuthController.java    # /api/auth/** REST endpoints
        │       ├── FinanceApiController.java  # All finance REST endpoints
        │       ├── LoginRequest.java
        │       ├── RegisterRequest.java
        │       ├── SimplifiedRegisterRequest.java
        │       └── ProfileUpdateRequest.java
        │
        └── resources/
            ├── application.properties     # DB, server, JPA config
            └── static/                    # Served as-is by Spring Boot
                ├── css/
                │   └── style.css          # All styles + CSS variables + dark mode
                ├── js/
                │   ├── sidebar.js         # Sidebar injection + theme toggle
                │   ├── auth.js            # Auth guard helpers
                │   └── auth-ui.js         # UI auth state helpers
                ├── login.html
                ├── signup.html
                ├── dashboard.html
                ├── transactions.html
                ├── notes.html
                ├── todos.html
                └── profile.html
```

---

## 🗄 Database Schema

All tables are managed by Hibernate (JPA) with `spring.jpa.hibernate.ddl-auto` set to `update`.

### `users`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK) | Auto-generated |
| `full_name` | VARCHAR | Nullable (set after signup) |
| `username` | VARCHAR(30) | Unique, nullable |
| `email` | VARCHAR | Unique, required |
| `password_hash` | VARCHAR | BCrypt hashed |
| `created_at` | TIMESTAMP | Set on insert |
| `updated_at` | TIMESTAMP | Updated on every save |

### `categories`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK) | Auto-generated |
| `user_id` | BIGINT | FK → users.id |
| `name` | VARCHAR | Required; unique per user |
| `description` | TEXT | Optional |
| `color` | VARCHAR | Hex color string (e.g. `#3498db`) |
| `icon` | VARCHAR | Icon name (e.g. `tag`, `home`) |

> **Unique constraint**: `(user_id, name)` — category names are unique per user.

### `transactions`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK) | Auto-generated |
| `user_id` | BIGINT | FK → users.id |
| `amount` | DOUBLE | Required |
| `description` | TEXT | Required |
| `category_id` | BIGINT | FK → categories.id |
| `transaction_type` | VARCHAR | `income` or `expense` |
| `date` | TIMESTAMP | Transaction date |
| `created_at` | TIMESTAMP | Record creation time |

### `assets`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK) | Auto-generated |
| `user_id` | BIGINT | FK → users.id |
| `name` | VARCHAR | Required |
| `type` | VARCHAR | e.g. `Real Estate`, `Vehicle`, `General` |
| `description` | TEXT | Optional |
| `value` | DOUBLE | Asset's monetary value |
| `created_at` | TIMESTAMP | Last updated |

### `notes`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK) | Auto-generated |
| `user_id` | BIGINT | FK → users.id |
| `title` | VARCHAR | Required |
| `content` | TEXT | Multi-line body |
| `color` | VARCHAR | Hex background color (default `#FFE082`) |
| `tags` | TEXT | Comma-separated or JSON string |
| `pinned` | BOOLEAN | Default `false` |
| `archived` | BOOLEAN | Default `false` |
| `created_at` | TIMESTAMP | Immutable |
| `updated_at` | TIMESTAMP | Updated on every save |

### `todos`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGINT (PK) | Auto-generated |
| `user_id` | BIGINT | FK → users.id |
| `title` | VARCHAR | Required |
| `description` | TEXT | Optional |
| `due_date` | DATE | Optional |
| `due_time` | VARCHAR | Optional time string |
| `priority` | VARCHAR | `low` / `medium` / `high` (default `medium`) |
| `category` | VARCHAR | Free-text category label |
| `status` | VARCHAR | `pending` / `in_progress` / `completed` (default `pending`) |
| `completed` | BOOLEAN | Default `false`; setting to `true` also sets `status = completed` |
| `color` | VARCHAR | Hex color (default `#29B6F6`) |
| `created_at` | TIMESTAMP | Immutable |
| `updated_at` | TIMESTAMP | Updated on every save |

---

## 📡 REST API Reference

All API routes are prefixed with `/api`. Authentication is carried via JWT in either:
- **Cookie**: `Authorization=<token>` (set automatically on login/register)
- **Header**: `Authorization: Bearer <token>`

---

### 🔐 Auth — `/api/auth`

#### `POST /api/auth/register`
Register a new user with full details.

**Request body:**
```json
{
  "fullName": "Jane Doe",
  "username": "janedoe",
  "email": "jane@example.com",
  "password": "securepass",
  "confirmPassword": "securepass"
}
```

**Response `201`:**
```json
{
  "token": "<jwt>",
  "user": { "id": 1, "fullName": "Jane Doe", "username": "janedoe", "email": "jane@example.com" }
}
```

Also sets an `Authorization` cookie valid for **7 days**.

---

#### `POST /api/auth/register-simple`
Simplified registration using only email + password (username auto-generated).

**Request body:**
```json
{
  "email": "jane@example.com",
  "password": "securepass",
  "confirmPassword": "securepass"
}
```

**Response `201`:** Same structure as full register.

---

#### `POST /api/auth/login`
Authenticate an existing user.

**Request body:**
```json
{
  "usernameOrEmail": "janedoe",
  "password": "securepass"
}
```

**Response `200`:**
```json
{
  "token": "<jwt>",
  "user": { "id": 1, "fullName": "Jane Doe", "username": "janedoe", "email": "jane@example.com" }
}
```

Also sets an `Authorization` cookie valid for **7 days**.

---

#### `GET /api/auth/me`
Get the currently authenticated user's profile.

**Headers:** `Authorization: Bearer <token>`

**Response `200`:**
```json
{
  "userId": 1,
  "fullName": "Jane Doe",
  "username": "janedoe",
  "email": "jane@example.com",
  "profileComplete": true
}
```

---

#### `PUT /api/auth/profile`
Update the authenticated user's full name and username.

**Headers:** `Authorization: Bearer <token>`

**Request body:**
```json
{
  "fullName": "Jane A. Doe",
  "username": "jane_doe"
}
```

**Response `200`:** Updated user object.

---

### 🗂️ Categories — `/api/categories`

#### `GET /api/categories`
Returns all categories for the current user, sorted by ID.

**Response `200`:**
```json
[
  { "id": 1, "name": "Food", "description": "Groceries & eating out", "color": "#e74c3c", "icon": "utensils" }
]
```

#### `POST /api/categories`
Create a new category.

**Request body:**
```json
{
  "name": "Transport",
  "description": "Fuel, bus, taxi",
  "color": "#3498db",
  "icon": "car"
}
```

**Response `201`:** The created category object.  
**Error `400`:** If name is blank or already exists for this user.

#### `PUT /api/categories/{id}`
Update an existing category. All fields are optional except `name`.

**Response `200`:** Updated category.  
**Error `404`:** If category not found or belongs to another user.

#### `DELETE /api/categories/{id}`
Delete a category.

**Response `204`:** No content.

---

### 💰 Transactions — `/api/transactions`

#### `GET /api/transactions`
Returns transactions for the current user, sorted by date descending.

**Query parameters:**
| Param | Type | Default | Description |
|---|---|---|---|
| `limit` | Integer | `100` | Max records to return |
| `category_id` | Long | — | Filter by category |
| `type` | String | — | Filter by `income` or `expense` |

**Response `200`:**
```json
[
  {
    "id": 5,
    "amount": 120.50,
    "description": "Grocery shopping",
    "category_id": 1,
    "category_name": "Food",
    "transaction_type": "expense",
    "date": "2024-06-15T10:30:00",
    "created_at": "2024-06-15T10:31:00"
  }
]
```

#### `POST /api/transactions`
Create a new transaction.

**Request body:**
```json
{
  "amount": 3500.00,
  "description": "Monthly salary",
  "category_id": 3,
  "transaction_type": "income",
  "date": "2024-06-01"
}
```

`date` accepts multiple formats: `ISO LocalDateTime`, `yyyy-MM-dd HH:mm:ss`, `yyyy-MM-dd`. If omitted, defaults to now.

**Response `201`:** The created transaction.  
**Error `400`:** Missing fields, invalid category, or invalid `transaction_type`.

#### `DELETE /api/transactions/{id}`
Delete a transaction.

**Response `204`:** No content.  
**Error `404`:** Transaction not found or belongs to another user.

---

### 🏦 Assets — `/api/assets`

#### `GET /api/assets`
Returns all assets for the current user, sorted by ID.

**Response `200`:**
```json
[
  { "id": 1, "name": "Car", "type": "Vehicle", "description": "Honda Civic 2022", "value": 18000.00, "created_at": "..." }
]
```

#### `POST /api/assets`
Create a new asset.

**Request body:**
```json
{
  "name": "Savings Account",
  "type": "Bank",
  "description": "Emergency fund",
  "value": 5000.00
}
```

**Response `201`:** The created asset.

#### `PUT /api/assets/{id}`
Update an existing asset.

**Response `200`:** Updated asset.

#### `DELETE /api/assets/{id}`
Delete an asset.

**Response `204`:** No content.

---

### 📝 Notes — `/api/notes`

#### `GET /api/notes`
Returns notes for the current user. Non-archived notes, pinned first, then by `updatedAt` descending.

**Query parameters:**
| Param | Type | Description |
|---|---|---|
| `search` | String | Full-text search across title and content |

**Response `200`:**
```json
[
  {
    "id": 1,
    "title": "Budget Plan Q3",
    "content": "Focus on reducing dining expenses...",
    "color": "#FFE082",
    "tags": "budget,planning",
    "pinned": true,
    "archived": false,
    "created_at": "...",
    "updated_at": "..."
  }
]
```

#### `POST /api/notes`
Create a new note.

**Request body:**
```json
{
  "title": "Investment Ideas",
  "content": "Look into index funds...",
  "color": "#B2EBF2",
  "tags": "investment,ideas",
  "pinned": false
}
```

Defaults: `color = #FFE082`, `pinned = false`, `archived = false`.

**Response `201`:** The created note.

#### `PUT /api/notes/{id}`
Update a note. All fields are optional (partial update supported).

**Response `200`:** Updated note.

#### `DELETE /api/notes/{id}`
Delete a note permanently.

**Response `204`:** No content.

---

### ✅ To-Dos — `/api/todos`

#### `GET /api/todos`
Returns incomplete todos for the current user.

**Query parameters:**
| Param | Type | Description |
|---|---|---|
| `search` | String | Search by title |
| `status` | String | Filter: `pending`, `in_progress`, `completed` |
| `priority` | String | Filter: `low`, `medium`, `high` |

**Response `200`:**
```json
[
  {
    "id": 1,
    "title": "Review monthly budget",
    "description": "Check all categories against targets",
    "due_date": "2024-07-01",
    "due_time": "09:00",
    "priority": "high",
    "category": "Finance",
    "status": "pending",
    "completed": false,
    "color": "#29B6F6",
    "created_at": "...",
    "updated_at": "..."
  }
]
```

#### `POST /api/todos`
Create a new to-do.

**Request body:**
```json
{
  "title": "Pay electricity bill",
  "description": "Due end of month",
  "dueDate": "2024-07-31",
  "dueTime": "18:00",
  "priority": "medium",
  "category": "Bills",
  "color": "#FFCC02"
}
```

Defaults: `priority = medium`, `status = pending`, `completed = false`, `color = #29B6F6`.

**Response `201`:** The created to-do.

#### `PUT /api/todos/{id}`
Update a to-do (partial update). Setting `completed: true` automatically sets `status = completed`.

**Response `200`:** Updated to-do.

#### `DELETE /api/todos/{id}`
Delete a to-do.

**Response `204`:** No content.

---

### 📊 Analytics — `/api/analytics`

#### `GET /api/analytics/summary`
High-level financial summary for the current user.

**Response `200`:**
```json
{
  "total_income": 8500.00,
  "total_expense": 3200.00,
  "balance": 5300.00,
  "total_savings": 5300.00,
  "savings_rate": 62.35,
  "total_assets": 23000.00,
  "net_worth": 28300.00,
  "transaction_count": 47
}
```

#### `GET /api/analytics/savings`
Dedicated savings overview.

**Response `200`:**
```json
{
  "total_income": 8500.00,
  "total_expense": 3200.00,
  "total_savings": 5300.00,
  "savings_rate": 62.35
}
```

#### `GET /api/analytics/category-breakdown`
Expense totals grouped by category (used to render the Expense by Category chart).

**Response `200`:**
```json
[
  { "category": "Food", "color": "#e74c3c", "total": 850.00, "count": 12 },
  { "category": "Transport", "color": "#3498db", "total": 320.00, "count": 8 }
]
```

#### `GET /api/analytics/monthly`
Income and expense totals per calendar month (used to render the Monthly Trend chart).

**Response `200`:**
```json
[
  { "month": "2024-04", "type": "income",  "total": 4200.00 },
  { "month": "2024-04", "type": "expense", "total": 1800.00 },
  { "month": "2024-05", "type": "income",  "total": 4300.00 },
  { "month": "2024-05", "type": "expense", "total": 1600.00 }
]
```

---

## 🔐 Authentication & Security

### JWT Strategy
- On login/register, the server generates a **HS512-signed JWT** containing `userId`, `username`, and `email` as claims
- Token expiry: **7 days**
- The token is returned in **both** the response body (for `localStorage`) and as an **HTTP cookie** (`Authorization`) for seamless browser requests
- `JwtAuthFilter` intercepts all requests:
  1. Checks the `Authorization: Bearer <token>` header
  2. Falls back to the `Authorization` cookie
  3. On success, sets `userId` as a `HttpServletRequest` attribute — downstream controllers read this via `request.getAttribute("userId")`

### Page-Level Guards
`PageController` checks `request.getAttribute("userId")`:
- **Authenticated**: forwards to the requested HTML page
- **Unauthenticated** on a protected route: redirects to `/login`
- **Authenticated** on `/`, `/login`, `/signup`: redirects to `/dashboard`

### Spring Security
- CSRF disabled (stateless JWT app)
- Custom form login and HTTP Basic both disabled
- All routes technically `permitAll()` at the Spring Security layer — actual auth enforcement is done in `PageController` (pages) and `FinanceApiController` via `getUserId()` (API)
- Passwords are hashed with **BCrypt** via `PasswordService`

---

## 🎨 UI & Frontend Architecture

### No Framework — Pure JS + Static HTML
Spring Boot serves static files directly from `src/main/resources/static/`. There is no Thymeleaf, Freemarker, or any other templating engine. Every page is a standalone `.html` file.

### Sidebar Injection via `sidebar.js`
Instead of copy-pasting the sidebar HTML into every page, `sidebar.js` is included as a `<script>` tag at the bottom of each protected page. On `DOMContentLoaded` it:
1. Wraps all existing body content in a `#main-content` div
2. Inserts the `<aside id="sidebar">` before it
3. Adds the `has-sidebar` class to `<body>`, which switches the layout to `display: flex; flex-direction: row`
4. Sets the active menu item by matching `window.location.pathname`
5. Restores collapse state from `localStorage`
6. Applies the saved theme before the page renders (IIFE at top of file, preventing flash of wrong theme)

### Theme System
Themes are controlled by a `data-theme` attribute on the `<html>` element:
- `data-theme=""` → Light mode (`:root` CSS variables)
- `data-theme="dark"` → Dark mode (`[data-theme="dark"]` overrides)

**Bootstrap internal variables** (`--bs-body-color`, `--bs-heading-color`, etc.) are overridden in the dark block so Bootstrap components respect the theme.

**30+ CSS variables** cover every surface: backgrounds, text, borders, inputs, cards, modals, tables, toasts, sidebar, and stat cards.

**Priority**: `localStorage('fintrack_theme')` → system `prefers-color-scheme` → light default.

### CSS Variable Palette
```css
/* Brand palette */
--color-primary:   #18230F;   /* Page background */
--color-secondary: #27391C;   /* Dividers, hover, secondary sections */
--color-accent:    #255F38;   /* Active states, icons, buttons */
--color-surface:   #1F7D53;   /* Stat card backgrounds */

/* Light mode */
--bg-body:     #f0f4f1;
--bg-card:     #ffffff;
--text-body:   #18230F;
--text-muted:  #6c757d;

/* Dark mode (overrides) */
--bg-body:     #18230F;
--bg-card:     #1e2d16;
--text-body:   #d4edda;
```

### Stat Cards
- Fixed minimum height (`130px`), equal width via Bootstrap `col-md-2`
- `fitStatAmounts()` — JavaScript function that shrinks font size from the CSS `clamp()` default down 1px at a time until `scrollWidth ≤ available card width`. Runs after data loads and on `window.resize`
- Animated FontAwesome icon (16–24px) positioned `absolute bottom-right`, colored with `--stat-card-icon-color`, loops with a subtle float animation

### Recent Transactions Layout
Each transaction row uses CSS Grid with 6 columns:

```
110px   | 1fr     | 160px    | 120px  | 130px   | 50px
Date    | Desc    | Category | Type   | Amount  | Delete
```

Rows animate in with `@keyframes txRowIn` (fade + slide up), staggered by `animation-delay: ${idx * 40}ms`.

---

## 📄 Pages & Navigation

| URL | HTML File | Description |
|---|---|---|
| `/` | — | Redirects to `/login` or `/dashboard` |
| `/login` | `login.html` | Login form. Stores JWT in `localStorage` and cookie |
| `/signup` | `signup.html` | Registration form (full or simplified) |
| `/dashboard` | `dashboard.html` | Stats, charts, recent transactions |
| `/transactions` | `transactions.html` | Full transaction list + add form + assets |
| `/notes` | `notes.html` | Notes grid with search, pin, archive, color |
| `/todos` | `todos.html` | To-do list with priorities, statuses, filters |
| `/profile` | `profile.html` | User profile view/edit |
| `/settings` | `settings.html` | App settings *(in progress)* |

### Sidebar Navigation (all protected pages)
```
┌─────────────────┐
│  FinTrack  [≡]  │  ← collapse toggle
├─────────────────┤
│ 🏠 Dashboard    │
│ 💸 Transactions │
│ 📝 Notes        │
│ ✅ To-Dos       │
│ ⚙️  Settings    │
├─────────────────┤
│ [☀️/🌙] Theme  │  ← light/dark toggle
│ 👤 User Name    │
│ 🚪 Logout       │
└─────────────────┘
```

---

## 🚀 Getting Started

### Prerequisites

| Tool | Minimum Version |
|---|---|
| Java JDK | 17 |
| PostgreSQL | 13+ |
| Gradle | 8 (or use `gradlew` wrapper) |

### 1. Clone the repository

```bash
git clone https://github.com/zareenzia/FinTrack.git
cd FinTrack
```

### 2. Set up the PostgreSQL database

```sql
CREATE DATABASE fintrack;
CREATE USER fintrack_user WITH ENCRYPTED PASSWORD 'yourpassword';
GRANT ALL PRIVILEGES ON DATABASE fintrack TO fintrack_user;
```

### 3. Configure the application

Edit `src/main/resources/application.properties`:

```properties
# Server
server.port=8585

# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/fintrack
spring.datasource.username=fintrack_user
spring.datasource.password=yourpassword
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA / Hibernate
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect

# Jackson (Java 8 date/time support)
spring.jackson.serialization.write-dates-as-timestamps=false
```

> **`ddl-auto=update`** means Hibernate will automatically create or update tables on startup. No manual schema migration is needed for a fresh database.

### 4. Build

```bash
# Windows
.\gradlew.bat build -x test

# Linux / macOS
./gradlew build -x test
```

### 5. Run

```bash
# Windows
.\gradlew.bat bootRun

# Linux / macOS
./gradlew bootRun
```

The app starts at **[http://localhost:8585](http://localhost:8585)** and redirects to the login page.

---

## ⚙️ Configuration

| Property | Default | Description |
|---|---|---|
| `server.port` | `8585` | HTTP port the server listens on |
| `spring.datasource.url` | — | PostgreSQL JDBC URL |
| `spring.datasource.username` | — | DB username |
| `spring.datasource.password` | — | DB password |
| `spring.jpa.hibernate.ddl-auto` | `update` | Schema management strategy |
| `spring.jpa.show-sql` | `false` | Log generated SQL queries |

### JWT
The JWT secret key is **generated fresh on every application startup** (in-memory `SecretKey` via `Keys.secretKeyFor(HS512)`). This means:
- All tokens are invalidated when the server restarts
- For production, replace with a stable externalized secret key stored in environment variables or a secrets manager

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

## 🗺 Roadmap

- [ ] Persistent JWT secret (environment variable / secrets vault)
- [ ] Email verification on registration
- [ ] Password reset flow
- [ ] Export transactions to CSV / PDF
- [ ] Budget limits per category with alerts
- [ ] Recurring transactions
- [ ] Multi-currency support
- [ ] Settings page (currency symbol, fiscal year start, notification preferences)
- [ ] Mobile PWA support
- [ ] Unit & integration tests

---

## 📄 License

This project is open source. See [LICENSE](LICENSE) for details.

---

<div align="center">
  <sub>Built with ❤️ using Spring Boot &amp; Vanilla JS</sub>
</div>

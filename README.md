# E-Commerce Order Management & Search Service — Java Port

Polyglot persistence POC ported from Python/FastAPI to **Java 17 + Spring
Boot**, behavior-identical: **PostgreSQL** owns orders, **MongoDB** owns the
catalog, **Elasticsearch** powers admin search. Vue 3 SPA reused unchanged.

| Screen | Reads from | Writes to |
|---|---|---|
| 1 Storefront | MongoDB products | — (cart is local state) |
| 2 Checkout | MongoDB validate → PostgreSQL transaction | PostgreSQL + ES dual-write |
| 3 Admin search | Elasticsearch only | — |
| 4 Order details | PostgreSQL (truth) + ES sync badge | PostgreSQL status → ES update |
| 5 Catalog admin | MongoDB | MongoDB only |

## Technology Stack

- Java 17, Spring Boot 3.2, Maven
- Spring Data JPA + PostgreSQL, Spring Data MongoDB (`MongoTemplate`),
  Elasticsearch via REST (`RestClient`) + typed client for CRUD
- Vue 3 + Vite (unchanged), Nginx, Docker Compose

## Prerequisites

- Docker Desktop (Linux containers)
- Java 17+ and Maven 3.9+ and Node.js LTS (only for host-side runs)

## Clone / Environment

```bash
git clone <new-repo-url>
cd ecommerce-java
```

Copy `.env.example` to `.env` if you want non-default secrets. **Never
commit `.env`.** Compose works out of the box with dev defaults.

## Run Application

```powershell
docker compose up --build
```

Then seed the demo data (users, products, orders, rename, ES reindex):

```powershell
Invoke-RestMethod -Method Post http://localhost:8080/api/admin/seed -TimeoutSec 300
```

Open **http://localhost:8080**, log in as John Doe, follow the demo script
in the original README (storefront → checkout → admin search → order
details → catalog admin).

## API mapping (FastAPI → Spring Boot, all behavior identical)

| FastAPI | Spring Boot |
|---|---|
| `GET /api/health` | `GET /api/health` |
| `GET /api/users` | `GET /api/users` |
| `GET /api/products?...` (+envelope) | `GET /api/products?...` (same envelope keys) |
| `GET /api/products/categories` | `GET /api/products/categories` |
| `GET /api/products/{id}` | `GET /api/products/{id}` |
| `POST /api/products` → 201 | `POST /api/products` → 201 |
| `PUT /api/products/{id}` | `PUT /api/products/{id}` |
| `POST /api/orders` → 201 | `POST /api/orders` → 201 |
| `GET /api/orders?limit=` | `GET /api/orders?limit=` |
| `GET /api/orders/{id}` (+`es_in_sync`) | `GET /api/orders/{id}` (+`es_in_sync`) |
| `PATCH /api/orders/{id}/status` | `PATCH /api/orders/{id}/status` |
| `POST /api/search/orders` | `POST /api/search/orders` (same shape + aggs) |
| `POST /api/admin/seed\|reindex\|seed-catalog` | `POST /api/admin/seed\|reindex\|seed-catalog` |

Validation messages, `{message}` errors, status codes (200/201/400/404/500),
datetime shape (`...T...757Z` millis), sort whitelist, 5000-row import cap —
all preserved. No login endpoint exists in either version (dropdown login).

## Project structure

```text
ecommerce-java/
├── backend/                 # Spring Boot (config, controller×6, service×4,
│                            # repository×3, model×7, error×2)
├── client/                  # Vue 3 SPA (unchanged)
├── sql/postgres_schema.sql  # users/orders/order_items (init container)
├── deploy/nginx.conf        # / -> frontend:5175, /api/ -> backend:8000
├── docker-compose.yml       # postgres + mongo + es + init + backend + frontend + nginx
└── README.md
```

## Docker commands

```powershell
docker compose up --build
docker compose ps
docker compose logs backend
docker compose down          # keep data
docker compose down -v       # erase all demo data
```

## Port conflicts (this dev machine)

Host already uses `:27017` (native mongod). For local validation only, run
with a temp override mapping mongo to `:27018` (repo file keeps standard
ports). Backend :8000, frontend :5175, nginx :8080.

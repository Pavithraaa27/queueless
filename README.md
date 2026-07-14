# QueueLess

> No more standing in line — check in remotely, track your position live, get a wait estimate that actually adapts.

Live queue management with real-time wait-time estimation. Check in to a business remotely,
watch your position update live, and get an ETA that adapts to how the queue is actually moving —
not a static number.

**GitHub topics:** `spring-boot` `java` `react` `typescript` `websocket` `redis` `postgresql` `jwt-authentication` `spring-security` `real-time` `material-ui` `zustand` `docker` `rest-api` `full-stack`


## Why this exists

Standing in physical lines at clinics, salons, and government offices wastes time that could be
spent anywhere else. QueueLess lets people check in remotely and only show up when their turn is close.

## Architecture

- **Backend** — Spring Boot 3.3 (Java 21): JWT auth, Spring Data JPA + PostgreSQL, Redis caching,
  WebSocket (STOMP) for live queue broadcasts, an adaptive ETA algorithm.
- **Frontend** — React 19 + TypeScript (Vite), MUI, Zustand, STOMP/SockJS for live updates.

### The ETA algorithm

Each business tracks a rolling average service time, updated with an exponential moving average
every time a ticket is completed — so the estimate adapts to how a business is running *today*,
not a number set once at signup. A waiting customer's ETA is:

```
(people ahead in line) × (business's rolling avg service time)
+ (remaining time on whoever's currently being served)
```

See `QueueService.recalculateQueue` in the backend for the implementation.

## Running locally

### With Docker (recommended)

```bash
docker compose up --build
```

- Frontend: http://localhost:5173
- Backend API: http://localhost:8080
- Swagger docs: http://localhost:8080/swagger-ui.html

### Without Docker

**Backend** (needs Postgres + Redis running locally, or point `DB_HOST`/`REDIS_HOST` env vars elsewhere):

```bash
cd backend
mvn spring-boot:run
```

**Frontend**:

```bash
cd frontend
npm install
npm run dev
```

## Project structure

```
queueless/
├── backend/            Spring Boot API
│   └── src/main/java/com/pavithra/queueless/
│       ├── entity/      JPA entities (User, Business, Ticket, ServiceLog)
│       ├── repository/  Spring Data repositories
│       ├── service/     Business logic, incl. the ETA algorithm
│       ├── controller/  REST endpoints
│       ├── security/    JWT auth
│       ├── config/      Security, WebSocket, Redis config
│       └── exception/   Global exception handling
├── frontend/            React + TypeScript SPA
│   └── src/
│       ├── pages/        Route-level pages
│       ├── components/   Shared UI (incl. the ticket-stub card)
│       ├── hooks/        useLiveQueue (STOMP subscription), useGeolocation
│       ├── api/          REST client functions
│       └── store/        Zustand auth store
└── docker-compose.yml
```

## API overview

| Method | Endpoint                          | Description                        |
|--------|------------------------------------|-------------------------------------|
| POST   | `/api/auth/register`               | Create an account                   |
| POST   | `/api/auth/login`                  | Log in, get a JWT                   |
| GET    | `/api/businesses`                  | List businesses (optional `?category=`) |
| GET    | `/api/businesses/nearby`           | List businesses within a radius, sorted by distance (`?lat=&lng=&radiusKm=`) |
| POST   | `/api/businesses`                  | Create a business (owner only)      |
| PATCH  | `/api/businesses/{id}/accepting`   | Toggle whether it's accepting check-ins |
| GET    | `/api/businesses/{id}/analytics`   | Today's stats for the owner (check-ins, served, no-shows, avg time) |
| GET    | `/api/businesses/{id}/insight`      | Plain-English daily summary (rule-based, or LLM-generated if configured) |
| POST   | `/api/queue/{businessId}/check-in` | Join a queue                        |
| POST   | `/api/queue/{businessId}/call-next`| Owner calls the next customer       |
| DELETE | `/api/queue/tickets/{id}`          | Cancel your ticket                  |
| GET    | `/api/queue/{businessId}/snapshot` | Current queue state (REST fallback) |

Live updates are pushed over WebSocket at `/ws`, topic `/topic/queue/{businessId}`.

### Nearby search

`findNearby` in `BusinessService` computes distance with the Haversine formula (great-circle
distance between two lat/lng points), filters to the requested radius, and sorts by distance —
no external geocoding API needed. The frontend's "Near me" button uses the browser's Geolocation
API to get the user's coordinates, then calls this endpoint.

## Demo data

On first run against an empty database, a demo business ("Sunrise Family Clinic") is
automatically seeded with a bit of history and a live queue, so you're not staring at an
empty app. All demo accounts use the password `demo1234`:

- **Owner login:** `owner@demo.queueless.app`
- A few demo customer accounts are already checked into the queue (e.g. `demo.kiran.shah@queueless.app`) - or just register a fresh customer account to check in yourself.

This only seeds once - if any business already exists, nothing happens on subsequent
restarts. To disable it entirely (e.g. before a public deployment), set `SEED_DEMO_DATA=false`
in `docker-compose.yml` or as an environment variable.

## No-show handling

If a business calls a customer (`callNext`) and that ticket sits in "in service" for far
longer than the business's usual service time with no completion, a background job
(`QueueService.expireNoShows`, runs every 30s) assumes the customer didn't show up, marks
the ticket `NO_SHOW`, and automatically calls the next person - so one no-show doesn't
stall the whole line. No-shows are deliberately excluded from the ETA average, since no
actual service happened.

## Owner analytics

`GET /api/businesses/{id}/analytics` (owner-only) returns today's check-in count, how many
were served, how many no-showed, and today's average service time - all computed from data
the queue already produces (`Ticket` and `ServiceLog` records), no separate tracking needed.
Shown as a small stats panel on the owner dashboard, refreshed every 15s.

## Daily AI insight

`GET /api/businesses/{id}/insight` (owner-only) turns the analytics above into a short,
plain-English summary - "You've served 9 of 12 check-ins today, with 1 no-show. Average
service time is around 6 minutes." instead of raw numbers.

This is **disabled by default and works with zero configuration** - out of the box it uses
a rule-based template (`AiInsightService.templatedInsight`), no external API, no cost. To
upgrade it to real LLM-generated prose:

1. Get an API key from **console.anthropic.com**
2. Set these environment variables (in `docker-compose.yml` or your deployment platform):
   ```
   AI_INSIGHTS_ENABLED=true
   ANTHROPIC_API_KEY=sk-ant-...
   ```

The result is cached per business per day (in Redis), so even with a real API key it's
called at most once daily - not on every dashboard refresh. If the API call ever fails
(bad key, rate limit, network issue), it silently falls back to the same rule-based
template rather than breaking the dashboard.

## Tests

```bash
cd backend
mvn test
```

Covers:
- The ETA algorithm itself — position/wait-time assignment, and the exponential moving average
  that updates a business's average service time after each completed ticket.
- Queue operations — check-in validation (duplicate check-ins, businesses not accepting check-ins),
  call-next progression, cancellation, and Redis cache hit/miss behavior on the snapshot endpoint.
- No-show expiration — tickets past the grace period get marked NO_SHOW and the next person is
  automatically called; tickets within the grace period are left alone; no-shows don't affect
  the ETA average.
- Owner analytics — correct counts/averages returned, and non-owners are rejected.
- Daily AI insight — the rule-based fallback template, caching behavior (cached result
  returned without recomputing), and the zero-check-ins edge case.
- Auth — registration, duplicate-email rejection, login.
- Business ownership authorization — only the owning user can toggle their business's check-in status.
- Nearby search — distance calculation and radius filtering.
- An end-to-end integration test that boots the real Spring context (H2 in-memory DB, real security
  filter chain) and hits `/api/auth/register` and `/api/auth/login` over HTTP.

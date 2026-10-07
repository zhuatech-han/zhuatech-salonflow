[中文](README.md) | [English](README.en.md)

<img src="frontend/public/brand/logo.jpg" width="56" alt="ZhiHua Technology logo">

# ZhiHua SalonFlow · Salon Appointments, Scheduling and Settlement

**ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)** · [Official website](https://www.zhuatech.cn/).

Version 1.0.0 is a self-hosted learning edition for hair, nail and beauty salons: customer self-booking, staff schedules, reception, service execution, split receipts and refunds against original payments. Java 21 / Spring Boot / Vue 3 / MySQL support responsive Chinese and English operating screens. No AI model is required.

**Source available for individual learning, technical research and non-commercial exchange.** Commercial use, paid deployment, client delivery, SaaS, resale and commercial training require prior written company authorization under the existing [LICENSE](LICENSE). This is not an OSI-approved open-source license. Third-party components retain their own terms; software is provided as-is.

## Scenarios and actual workflow

For small salons evaluating a private appointment entry and developers studying or customizing such systems. Customers register and choose available times; reception assists customers; assigned stylists perform their own service. A booking contains **one service, one stylist and at most one room/seat**, rather than a package.

```text
Customer/reception booking → Arrival → Assigned stylist starts → Finishes → Price confirmation → Receipts → Settled
        ↓ Reschedule / cancel / no-show                                      ↓ Split receipts   ↓ Original-payment refunds
```

Availability checks skills, weekly shifts, break, leave, disabled resources, other bookings, cleanup buffer and the customer's own overlaps. Prices/durations/buffers freeze on booking; catalog edits do not rewrite existing appointments. Booking amounts are not revenue. Reports separate receipts, refunds, net receipts, completed-service sales and outstanding balance. Refunds append immutable entries without deleting the original payment or changing original sales.

## Implemented features and boundaries

| Module | Implemented operations |
| --- | --- |
| Customer end | Fixed-role registration/login, service/staff/date/slot selection, own appointments, rescheduling/cancellation, profile/email consent and password change |
| Reception | Per-staff day agenda and searchable/filterable/sortable/paginated lists, assisted booking, arrival, no-show and reassignment |
| Services | Categories, price/duration/buffer, staff skills and linked accounts, own service start/finish and notes |
| Capacity | One shift/optional break per staff weekday, leave, room/seat blocks, conflict checks and ongoing-service occupancy |
| Settlement | Reasoned discounts, free service without fake zero receipts, manual cash/bank/external-payment records, split receipts and capped original-payment refunds |
| Customer records | Contact/notes/consent, enable/disable and atomic JSON import of 1–500 records |
| Notifications | Persistent confirmation/reschedule/cancel queue, 24-hour reminders, stale revision cancellation, consent checks, bounded failure retry and status |
| Reports | Salon-local date ranges, receipt/refund CSV, original appointment print and today's work |
| Administration | Users, roles, permissions, menus, departments/salons, data scopes, payment dictionaries, settings, business events and audit |

Not implemented: multi-service packages, prepaid wallets, memberships/points/passes, inventory/procurement, payroll/commissions, shared cross-salon capacity, marketplace, SMS, WhatsApp notifications, tax invoices or payment gateways. External payment records describe already verified transactions; this source does not charge accounts. SMTP acceptance is not delivery/read confirmation. It is not a medical-record or payment-clearing system.

## Actual running screens

These existing version screens came from an isolated fresh MySQL deployment. TEST records and transactions are fictional acceptance fixtures, absent from normal first installation.

| Staff/customer login | Customer home |
| --- | --- |
| ![Login entry](docs/images/screenshots/login.png) | ![Customer services and bookings](docs/images/screenshots/client-home.png) |
| Staff agenda and reception | Mobile customer booking |
| ![Day agenda](docs/images/screenshots/calendar.png) | ![Mobile booking](docs/images/screenshots/mobile-booking.png) |
| Service catalog management | Original service and receipt/refund details |
| ![Services](docs/images/screenshots/services.png) | ![Appointment detail](docs/images/screenshots/appointment.png) |
| Account administration | Actual receipt/refund reporting |
| ![Accounts](docs/images/screenshots/users.png) | ![Reports](docs/images/screenshots/reports.png) |
| Roles and data scopes | English operation of the same business |
| ![Role permissions](docs/images/screenshots/roles.png) | ![English interface](docs/images/screenshots/english.png) |

Customer views only expose own appointments; stylist views only assigned work; reception/managers follow department scope. The administration end manages accounts/catalog/configuration. API authorization is checked independently of menu visibility.

## Requirements and startup

Docker Engine/Desktop and Compose v2 run the complete system without host Java/Node. Separate development uses Java **21**, Maven **3.9**, Node.js **24.19.0** and Python **3**, with MySQL **8.4**. Pinned versions include Spring Boot **4.0.7**, Vue **3.5.40**, Vite **8.1.5**, MariaDB JDBC **3.5.10** and Nginx **1.29**.

At the repository root:

```sh
python3 scripts/init-env.py
# Privately read ADMIN_PASSWORD from .env; never upload it.
docker compose config --quiet
docker compose up -d --build --wait
```

Open `http://127.0.0.1:8100/`; health is `http://127.0.0.1:8100/actuator/health`. Administrator username defaults to `admin`; password is independently random in the private mode-0600 file. There is no universal password. The initialization script refuses to overwrite existing files. Passwords require 12–72 characters and a maximum of 72 UTF-8 bytes with upper/lowercase letters and digits.

First-empty-database startup creates administrator, five roles, permissions, menus, payment dictionary and settings, without fake appointments or receipts. `SEED_DEMO=true` adds explicitly DEMO service/seat examples only; staff and schedules are still required. Initialization variables never reset restored/existing passwords. Initially customer registration is enabled; settings can disable it.

Set company name, IANA timezone and two-decimal currency before booking; timezone/currency freeze after the first appointment. Then create categories/services, linked stylist accounts/profiles, skills, shifts and resources. Change private `WEB_PORT` for a conflict without stopping other projects. The default gateway only binds localhost; MySQL/backend ports stay private.

### Configuration

| Variable | Purpose/default |
| --- | --- |
| `MYSQL_ROOT_PASSWORD` / `DATABASE_PASSWORD` | Required independent strong database secrets |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | First-empty-database administrator; username defaults to admin |
| `WEB_PORT` / `BIND_ADDRESS` | 8100 / 127.0.0.1 |
| `SEED_DEMO` | false; optional labelled examples, no fake transactions |
| `COOKIE_SECURE` | false for local HTTP; true for trusted HTTPS |
| `DATABASE_URL` / `DATABASE_USER` | Optional external MySQL; default internal mysql/salonflow |
| `SMTP_HOST` / `SMTP_FROM` | Blank gives NOT_CONFIGURED, without pretending to send |
| `SMTP_PORT` | 587 |
| `SMTP_USERNAME` / `SMTP_PASSWORD` | Private optional SMTP authentication |
| `SMTP_STARTTLS` / `SMTP_SSL` | true / false; use provider-appropriate settings, not both |
| `REMINDERS_ENABLED` | true; false stops all email dispatch, not just reminders |

Changed environment requires recreation of the affected container. Runtime business settings change through administration. For host development start a private MySQL and inject its reachable URL/user/password and administrator variables safely; `.env` is not automatically loaded into Java. Compose's mysql hostname is not a host endpoint.

```sh
docker compose up -d mysql --wait
# Supply privately configured DATABASE_URL/USER/PASSWORD and ADMIN_PASSWORD.
mvn -B -f backend/pom.xml spring-boot:run
cd frontend
npm ci --no-audit --no-fund
npm run dev
```

Vite localhost 5173 proxies backend 8080; deployed Nginx provides same-origin `/api`. See [deployment](docs/deployment.md) for reachable local-database configuration. No service key is needed locally. Actual email dispatch needs your own SMTP configuration and valid recipient consent.

## Architecture, directories and database initialization

```text
backend/          Java API, authentication, business transactions, email jobs and tests
  src/main/resources/db/migration/V1__salon_schema.sql
frontend/         Vue customer/staff/admin screens, bilingual UI and Nginx proxy
scripts/          Private initialization, actual HTTP acceptance and release checks
docs/             Manuals, architecture, database, API, deployment, security and screenshots
compose.yaml      Health-dependent services and private MySQL volume
LICENSE           Own-source non-commercial license
```

Vue → same-origin Nginx → Spring Boot/Security/JPA/Flyway → MySQL. Session plus CSRF and live account/role/scope checks protect operations. UTC times display in the salon timezone; money uses two-decimal values. READ_COMMITTED and a department-configuration row lock protect occupancy and receipt/refund additions; revisions reject stale screens, command fingerprints prevent duplicate accepted actions, and foreign keys protect history.

Database `zhuatech_salonflow` has **22** business/configuration tables plus Flyway history. Accounts/permissions, catalog/staff/resources, customers/shifts/blocks, appointments/payment entries/events, message jobs and mutation stamps are defined in `V1__salon_schema.sql`. Flyway creates the fresh schema; JPA validates it. Add later migrations without changing applied SQL; backup and test recovery/upgrade first. See [database](docs/database.md) and [architecture](docs/architecture.md).

One shift/optional break per weekday can end at 24:00. Starts use 15-minute boundaries within 90 days, with customer advance notice. Ambiguous local times and service spans crossing daylight-saving changes are rejected, including direct API calls. Ongoing service continues to block capacity until explicitly finished. Catalog changes preserve existing booking snapshots.

Only the assigned stylist starts/finishes service; administrators cannot impersonate them. Free service completes without zero-value ledger entries. Payments require verified references, positive amounts and available balance; refunds identify a payment on that original appointment and cannot exceed its remaining refundable amount. Idempotent retry requires the original key and identical content, rather than blindly creating another payment command.

## Email, security and known limits

Queued email covers booking/reschedule/cancel and a 24-hour reminder for early bookings. It checks enabled customer/account, consent, current appointment revision and SMTP configuration. Old revisions cancel; failures retry every five minutes up to three attempts, with explicit retry after configuration repair. SENT means SMTP accepted. Network uncertainty may cause duplicates; no exactly-once or read guarantee is claimed. Tests use a local receiver only, not real mailboxes.

BCrypt cost 12, HttpOnly/SameSite Strict sessions, 30-minute expiry, CSRF for login/registration/writes and live account revocation are implemented. Customer registration always assigns a fixed customer role. Linked business identities and the last effective administrator are protected. Login/registration limits are in-memory and reset at process restart; public gateways require their own rate limits. Email consent is stored without email-ownership verification.

Single-instance design targets small-salon evaluation. Each entity list caps at 10,000 records with in-memory scope/filtering/pagination; daily agenda caps at 100. SMTP also holds the department lock, with bounded batches/timeouts, so this is not a high-concurrency/multitenant guarantee. No native app, automated charging, medical records or production/security certification is provided. The deployer evaluates suitability, availability, data retention and actual local obligations.

## Tests and deployment acceptance

```sh
mvn -B -f backend/pom.xml spotless:check test package
cd frontend
npm ci --no-audit --no-fund
npm run format:check
npm run lint
npm test
npm run build
cd ..
docker compose config --quiet
docker compose build
python3 scripts/release-check.py
git diff --check
```

Rules and HTTP/database tests cover occupancy/buffers, ongoing work, snapshots, invalid states, parallel payments, idempotency, over-receipt/refund, fixed customer role, customer/staff/salon isolation, atomic import, DST and SMTP statuses. H2 tests do not replace actual MySQL acceptance. Docker Maven builds run tests, without skipping them.

Only on an explicitly disposable fresh test instance:

```sh
python3 scripts/smoke.py --base http://127.0.0.1:8100 --env /path/to/private-test.env --allow-test-writes --state /path/to/private-test-state.json
```

Replace placeholder paths with your isolated settings and private mode-0600 state location. The script creates TEST fixtures, not real transactions. Verify browser customer/staff operations, both languages/mobile layout, original ledger and role scopes after restart, Flyway history and recovery into a separate fresh volume. See [testing](docs/testing.md).

## Deployment, backup and troubleshooting

Public deployment requires trusted HTTPS, `COOKIE_SECURE=true`, least privilege and protected registration/admin/backup access. External MySQL uses trusted CA and `sslMode=verify-full`; the internal Compose trust setting is not suitable for an untrusted network. Keep real secrets, customer records, cookies, SMTP passwords, backups and unredacted logs outside Git.

Use the scoped consistent MySQL dump/import procedures in [deployment](docs/deployment.md), with private restricted files outside source control. Restore into a different project, unused gateway port and fresh MySQL volume before starting application services. Verify original passwords, appointments, receipt/refund history, frozen snapshots and permission isolation. `down` preserves volumes; `down -v` removes that project's database and is only for explicitly disposable resources. Do not clean other applications or overwrite business data during rehearsal.

For login failure use the matching private password; initialization variables do not reset existing accounts. For missing slots check skills, schedules/breaks, leave/resources, notice windows and occupancy. On 403 check role and current scope. On stale revisions refresh and inspect the original ledger before retrying. For failed migrations inspect version/source without deleting business volumes or changing applied SQL to hide validation failures. NOT_CONFIGURED/FAILED email requires configuration/consent checks, not a claim of successful delivery.

## License, contributions and contact

Preserve company attribution, existing [LICENSE](LICENSE) and [third-party notices](docs/third-party.md). Issues should contain safe reproduction, version and expected/actual behavior; contributions should be small, verified and free of client code/data or incompatible licenses. Report vulnerabilities privately without credentials or personal records; see [security](docs/security.md). The [manual](docs/manual.md), [API](docs/api.md) and existing [English guide](docs/english-guide.md) provide further operation details.

**ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)**. Commercial authorization, customization, private deployment and system integration:

- Website: [https://www.zhuatech.cn/](https://www.zhuatech.cn/)
- Email: [han@zhuatech.cn](mailto:han@zhuatech.cn)
- Email: [jack@zhuatech.cn](mailto:jack@zhuatech.cn)
- WhatsApp: [+86 17521234993](https://wa.me/8617521234993)

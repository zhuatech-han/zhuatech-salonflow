# SalonFlow setup and operation

知华科技（上海如静知华信息科技有限公司） · https://www.zhuatech.cn/ · 商业授权与定制开发微信：zhuatech / zhuatech2

ZhuaTech SalonFlow 1.0.0 is a source-available, non-commercial learning edition. Commercial deployment, client delivery, paid hosting and resale require written authorization. See the root LICENSE. Company: 上海如静知华信息科技有限公司; website https://www.zhuatech.cn/; commercial inquiries on WeChat: zhuatech / zhuatech2.

## Start

Install Docker Engine and Compose v2. Run `python3 scripts/init-env.py` at the repository root, then `docker compose up -d --build --wait`. The script generates unique strong passwords in a private `.env` and refuses to overwrite it. Open http://127.0.0.1:8100/, use username `admin` and ADMIN_PASSWORD from your private file. There is no universal demo password. Click EN to switch operating screens.

Only the administrator, roles, permissions, menus and settings are seeded. SEED_DEMO=true adds explicitly fictional example services and a seat; it does not create bookings or revenue. For a port conflict set WEB_PORT in `.env`. Database and backend ports remain internal. Exposing the site requires HTTPS and COOKIE_SECURE=true, independent secrets, proxy rate limits and backups.

## Configure a salon

Set companyName, currency and timezone in Settings before creating any appointment. Timezone is an IANA identifier such as America/New_York; currency must have two decimal places. Both become locked after the first appointment to protect old records.

Create service categories and services, including price, duration, cleanup buffer and any required room/seat. Create a staff login with the Stylist role, then a staff profile linked to it and its eligible services. Create rooms/seats where required. Add weekday shifts with one optional break per day; a shift can end at24:00. Add staff leave or resource blocks as needed. Times use the salon timezone, not the browser timezone. Ambiguous local times and appointments crossing a daylight-saving clock change are rejected, including direct API submissions.

## Actual workflow

A customer can register with the fixed Customer role, sign in, choose a service, eligible staff and optional resource, select a date and one available time. A receptionist can instead create a client and book on their behalf. Slots are revalidated when submitted, including breaks, time off, cleanup buffers and the customer's other appointments. One appointment contains one service, one stylist and at most one resource; starts are on15-minute boundaries within90days.

Reception records arrival. The assigned stylist signs in, starts service and finishes with notes. An administrator cannot perform service while impersonating the stylist; reassign a booked/arrived appointment to another eligible available stylist if necessary. Ongoing service continues to block capacity beyond its planned end until explicitly finished.

Checkout sets the final amount with an optional justified discount. Record actual payments, including multiple partial payments. Cash, bank and external payment are manual ledger methods, not payment gateway integrations. Overpayment is rejected. A free service completes without a fake zero-value payment. After settlement, authorized staff may record refunds against the exact original payment, within its remaining refundable amount and with reference and reason. Original sales and payments remain unchanged.

Customers see only their own appointments and can reschedule/cancel while the advance notice window permits. Stylists see assigned appointments. Reception/manager access follows the salon department. The original price/duration/buffer stay frozen when rescheduling; changing the service catalog never rewrites old bookings. Refresh a stale appointment before another action; retry with the same request key only when content is identical.

Reports separate actual receipts, refunds, net receipts and completed-service sales. Receipt/refund dates follow the salon timezone; outstanding balance is the current total, not revenue for a selected date range. CSV export and printable appointment details contain business data only.

## Email and limits

Configure SMTP_HOST, SMTP_FROM, SMTP_PORT and optional credentials in the private environment file. Use STARTTLS or SSL according to your provider. Blank configuration is recorded as NOT_CONFIGURED, not as a successful send. Client email consent is required; email ownership is not verified in this edition. Messages include confirmation, rescheduling, cancellation and a24-hour reminder for sufficiently early bookings. Outdated appointment revisions are cancelled. Failure retries every5minutes up to3attempts; manual retry is available after fixing configuration. SENT means SMTP accepted, not delivered or read. Ambiguous SMTP failure can cause duplicates. REMINDERS_ENABLED=false disables all message dispatch.

No prepaid wallets, packages, memberships, inventory, payroll, tax invoices, marketplace, native app, SMS, WhatsApp or automated payment processing. Database lists are limited to10,000rows per entity and day agendas to100entries. This implementation targets learning and small salon evaluation, not a large multi-tenant platform.

For checks run backend `mvn -B spotless:check test package`, frontend `npm ci`, format:check, lint, test and build. Docker builds execute backend tests. For a disposable database only, run `python3 scripts/smoke.py --base http://127.0.0.1:8100 --env .env --allow-test-writes`. Preserve secrets and real clients outside version control. See Chinese deployment/API/security manuals for backup, restore and configuration details; file paths and endpoints remain the same.

# Supabase PostgreSQL setup

The supabase Spring profile connects to the project's Session pooler on port 5432.
The default profile still uses local H2. Supabase hosts the database; the Java
application still runs on your computer.

## Start from PowerShell

Stop the existing app with Ctrl+C, then run these commands from the project root:

```powershell
$securePassword = Read-Host "Supabase database password" -AsSecureString
$env:SUPABASE_DB_PASSWORD = [System.Net.NetworkCredential]::new("", $securePassword).Password
.\mvnw.cmd clean spring-boot:run "-Dspring-boot.run.profiles=supabase"
```

After stopping the app, remove the password from that terminal with:

```powershell
Remove-Item Env:SUPABASE_DB_PASSWORD
Remove-Variable securePassword
```

## Start from Git Bash (IntelliJ terminal)

Stop the existing app with Ctrl+C, then run these commands from the project root:

```bash
read -rsp "Supabase database password: " SUPABASE_DB_PASSWORD; echo
export SUPABASE_DB_PASSWORD
./mvnw.cmd clean spring-boot:run -Dspring-boot.run.profiles=supabase
```

Enter your Supabase **database password** at the hidden prompt, not your Supabase
account password or an API key. It is passed separately from the JDBC URL, so
special characters do not need percent-encoding. Do not paste it in chat or Git.

The profile already contains the non-secret host and database username:
- Host: aws-0-us-east-2.pooler.supabase.com
- Port: 5432
- Database: postgres
- Username: postgres.jxnpxymmxypfhhfcoimf

Optionally override SUPABASE_DB_URL (JDBC URL without embedded credentials) and
SUPABASE_DB_USER to use a different database. Keep sslmode=require or stronger.
For certificate validation, configure sslmode=verify-full and the appropriate
trusted root certificate for the endpoint.

After stopping the app, remove the password from that terminal with:
```bash
unset SUPABASE_DB_PASSWORD
```

Environment variables apply only to programs launched from that terminal.
IntelliJ's Run button needs its own environment variables and the supabase active
profile. A .env file is not loaded automatically by Spring Boot.

## What happens on the first start

Flyway creates the mavworks schema and applies V1__create_mavworks_tables.sql.
Hibernate validates the schema instead of changing it automatically.
V2 enables account-deletion cascades. V3 updates listing categories and statuses
to match the application. The clean launch removes obsolete compiled migrations
from previous builds, including the former duplicate V2 listing migration.
V4 adds conversations and provider reviews, including one-review-per-user/listing
uniqueness and rating constraints. Restart with the Supabase profile to apply it.
V5 adds persisted messages and block-list records for the messaging workflow.
V6 adds reports and the pending administrator review queue. Set
`MAVWORKS_ADMIN_USER_IDS` to a comma-separated list of trusted existing `app_users.id`
values to grant administrator access on the next login; the default grants none.
V7 adds report decisions, warning explanations, and account bans. Admins can open
reports, save a VALID/INVALID decision, and filter the archived results by status.
V8 adds listing expiration, view counts, styles, expanded categories, conversation
read markers, and listing references on reports. Existing listings receive an
expiration date 30 days after creation. New listings must supply expires_at;
the application initializes it to 30 days after creation.
V9 adds WITHDRAWN to the listing status constraint for owner withdrawal and
renewal. It preserves existing listings and their history. Start the updated
branch with the Supabase profile to apply pending migrations automatically.
When sharing the database, keep the team's application code and migrations in
sync; a schema ahead of an older checkout can cause validation or insert errors.
The existing development terms initializer adds demo terms if the table is empty.
Existing H2 accounts and listings are not transferred; register a new test account
on Supabase. H2 files are preserved.

In Supabase's Table Editor, select the **mavworks** schema to inspect the tables.
The application uses direct JDBC access and Spring Security, not Supabase Auth.
Do not expose this schema through the Supabase Data API.

The connection pool is limited to three connections per running application.
Stop unused app instances, especially when teammates share the free database.

## Return to local H2

```bash
./mvnw.cmd spring-boot:run
```

## Troubleshooting

- Password authentication failed: check the database password and project username.
- Missing SUPABASE_DB_PASSWORD: set it in the same terminal used to launch Maven.
- Connection timeout: confirm the Supabase project is active and the network permits port 5432.
- Port 8080 in use: stop the previous application.
- Tables missing in Table Editor: choose mavworks rather than public.
- Existing nonempty mavworks schema: inspect it before migrating; do not reset or
  baseline an unknown schema blindly.

Migrations are versioned. Add a new migration for future table changes rather
than editing an already-applied migration.

## Deleting accounts

Restart the updated application with the `supabase` profile to apply Flyway V2
(`src/main/java/db/migration/postgresql/V2__cascade_account_deletion.java`).
Wait for startup to finish before retrying a deletion in the Table Editor.
The migration updates existing foreign keys to `ON DELETE CASCADE`; it does not
delete any users itself. V1 remains unchanged so existing migration checksums stay valid.

Deleting a row from `mavworks.app_users` then permanently removes that user's
terms acceptance records, listings, and listing image database records. Shared
terms documents and other accounts remain. Image files in external storage are
not deleted by a database cascade. This applies to direct SQL/Table Editor deletions
as well as application deletions; no account-deletion screen is added.

New Hibernate-created local databases use the same cascade rules. Existing H2
databases created before this change may still have their old constraints;
Hibernate's `update` mode does not reliably replace existing foreign keys.

## Verification

Run ./mvnw.cmd test without the supabase profile. Tests use isolated local
databases. SupabaseSchemaTest checks migration/mapping compatibility with H2's
PostgreSQL mode; it does not verify live PostgreSQL semantics or connectivity.

References:
- https://supabase.com/docs/guides/getting-started/quickstarts/spring-boot
- https://docs.spring.io/spring-boot/how-to/data-initialization.html

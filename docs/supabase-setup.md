# Supabase database and image storage setup

The supabase Spring profile connects to the project's Session pooler on port 5432.
It also uses Supabase Storage for shared listing and profile images. The default
profile still uses local H2 and local image files. Supabase hosts the shared data;
the Java application still runs on your computer.

## Image storage setup

In the team's Supabase project, open Storage and create a bucket named
`mavworks-images`. Keep it private, allow `image/jpeg` and `image/png`, and set
its file size limit to 5 MB. Bucket creation is a separate setup step; Flyway
does not create it. No application database migration is added for this change.

The supabase profile requires these environment variables:

| Variable | Value |
| --- | --- |
| `SUPABASE_DB_PASSWORD` | The PostgreSQL database password. |
| `SUPABASE_URL` | The project HTTPS URL, such as `https://your-project-id.supabase.co`, without an API path. |
| `SUPABASE_STORAGE_SECRET_KEY` | A backend secret API key beginning with `sb_secret_`, from Project Settings → API Keys. |

The project URL is different from the PostgreSQL pooler address. The secret API
key is different from the database password and publishable key. Despite the
variable name, it grants elevated project access and is not restricted to this
bucket. Keep it in the backend environment, never in committed files or browser
code. The Java services enforce application sign-in and image ownership checks;
profile picture requests also enforce profile visibility and blocking rules.

Use existing team credentials without rotating or revoking keys used by other
app instances. All instances using shared images need this implementation and
the same project and bucket configuration.

New account registration also requires SMTP configuration; see
[Email verification](email-verification.md). Existing accounts can sign in
without email delivery configured.

## Start from PowerShell

Stop the existing app with Ctrl+C, then run these commands from the project root:

```powershell
$securePassword = Read-Host "Supabase database password" -AsSecureString
$env:SUPABASE_DB_PASSWORD = [System.Net.NetworkCredential]::new("", $securePassword).Password
Remove-Variable securePassword

$env:SUPABASE_URL = Read-Host "Supabase project URL"
$storageSecret = Read-Host "Supabase storage secret key" -AsSecureString
$env:SUPABASE_STORAGE_SECRET_KEY = [System.Net.NetworkCredential]::new("", $storageSecret).Password
Remove-Variable storageSecret

.\mvnw.cmd clean spring-boot:run "-Dspring-boot.run.profiles=supabase"
```

For another restart in the same terminal, run only the Maven launch command.
The environment variables remain available. When finished, close the terminal
or remove the variables explicitly:

```powershell
Remove-Item Env:SUPABASE_DB_PASSWORD -ErrorAction SilentlyContinue
Remove-Item Env:SUPABASE_STORAGE_SECRET_KEY -ErrorAction SilentlyContinue
Remove-Item Env:SUPABASE_URL -ErrorAction SilentlyContinue
```

## Start from Git Bash (IntelliJ terminal)

Stop the existing app with Ctrl+C, then run these commands from the project root:

```bash
read -rsp "Supabase database password: " SUPABASE_DB_PASSWORD; echo
export SUPABASE_DB_PASSWORD
read -rp "Supabase project URL: " SUPABASE_URL
export SUPABASE_URL
read -rsp "Supabase storage secret key: " SUPABASE_STORAGE_SECRET_KEY; echo
export SUPABASE_STORAGE_SECRET_KEY
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

When finished, close the terminal or remove the variables explicitly:
```bash
unset SUPABASE_DB_PASSWORD SUPABASE_STORAGE_SECRET_KEY SUPABASE_URL
```

Environment variables apply only to programs launched from that terminal.
IntelliJ's Run button needs its own environment variables and the supabase active
profile. A .env file is not loaded automatically by Spring Boot. Stopping Maven
with Ctrl+C does not remove environment variables; starting a new terminal or
removing them requires entering them again.

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
For local testing with the Supabase profile, an optional `data/supabase-admin.properties`
file can set `mavworks.admin-user-ids`. This file is ignored by Git and is not packaged
in the application. It is resolved relative to the application's working directory.
Restart the application after changing the allowlist, then sign out and sign in.
V7 adds report decisions, warning explanations, and account bans. Admins can open
reports, save a VALID/INVALID decision, and filter the archived results by status.
V8 adds listing expiration, view counts, styles, expanded categories, conversation
read markers, and listing references on reports. Existing listings receive an
expiration date 30 days after creation. New listings must supply expires_at;
the application initializes it to 30 days after creation.
V9 adds WITHDRAWN to the listing status constraint for owner withdrawal and
renewal. It preserves existing listings and their history. Start the updated
branch with the Supabase profile to apply pending migrations automatically.
V10 adds service appointment durations and the bookings table for service
appointment requests. Start the updated branch with the Supabase profile to
apply it automatically; no manual database changes are needed.
V11 updates appointment statuses, links bookings to conversations, and adds
user availability. V12 converts availability to 30-minute slots.
V13 adds registration email verification records. V14 adds the optional reply
column to reviews if absent and aligns an existing column to VARCHAR(2000).
V14 has been applied to the shared database; keep the applied migration unchanged.
When sharing the database, keep the team's application code and migrations in
sync; a schema ahead of an older checkout can cause validation or insert errors.
With development terms seeding enabled, startup publishes the bundled terms for
an empty table or the original development demo; other published terms are retained.
Existing H2 accounts and listings are not transferred; register a new test account
on Supabase. H2 files are preserved.

In Supabase's Table Editor, select the **mavworks** schema to inspect the tables.
The application uses direct JDBC access and Spring Security, not Supabase Auth.
Do not expose this schema through the Supabase Data API.

The connection pool is limited to three connections per running application.
Stop unused app instances, especially when teammates share the free database.

## Existing images and separate app instances

Switching to the supabase profile does not transfer files from
`data/listing-images`. Existing database references continue to use the same
filenames. To retain those images, upload the matching local files to the bucket
root with their original stored filenames. Do not delete local copies before
verifying the transfer. For disposable test data, create new listings with fresh
uploads or replace profile pictures through Edit profile.

The owner listing dialog does not edit images yet. Old references without a
matching bucket object will not display an image. App instances still running
the local-storage implementation cannot display new bucket-only images, even
though they can read the listings from the shared database.

## Return to local H2

```bash
./mvnw.cmd spring-boot:run
```

## Troubleshooting

- Password authentication failed: check the database password and project username.
- Missing SUPABASE_DB_PASSWORD, SUPABASE_URL or SUPABASE_STORAGE_SECRET_KEY: set
  the variable in the same terminal used to launch Maven.
- Storage upload/download failed: check the project URL, backend secret key,
  bucket name and bucket restrictions. Changing the database password does not
  change the storage API key.
- Older images missing: check whether their referenced files were uploaded to
  the bucket; a database record alone does not contain the image.
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
SupabaseImageStorageTest uses a mock HTTP client to check requests, ownership,
validation and failure handling without credentials or shared storage writes.
The automated suite passed with 150 tests. Live bucket uploads were verified,
and teammates confirmed the branch works on their app instances. Live profile-picture
and blocked profile access checks still need explicit verification.

For live verification, coordinate shared test data with the team. Create a test
listing with one image, confirm its file appears in `mavworks-images`, and open
it from another account. Restart the app and repeat the check. Test a profile
picture, then verify both images from a second app instance running the updated
code. Confirm blocked profile pictures remain inaccessible.

References:
- https://supabase.com/docs/guides/getting-started/quickstarts/spring-boot
- https://docs.spring.io/spring-boot/how-to/data-initialization.html

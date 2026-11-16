# Hosting your own MavWorks instance

MavWorks is a student project. The repository can be used for local development
or an independent deployment. Continued maintenance and availability of the
team's pilot website are not guaranteed. A separate host needs its own database,
storage, SMTP account and deployment credentials.

## Local development

Follow [Initial setup](../README.md#initial-setup), then run the app with Java 21
and the Maven Wrapper. The default profile uses local H2 and local images.
Configure [email delivery](email-verification.md) to create accounts. There are
no bundled demo passwords or verification bypasses. Use a mailbox you control
that meets the registration domain rule below.

Run `./mvnw clean verify` on macOS/Linux or `.\mvnw.cmd clean verify` in
PowerShell before submitting changes. See [Project structure](project-structure.md)
for the Java packages and workflows.

## Your own database and images

1. Create your own Supabase project and save its database password securely.
2. In the project's connection settings, select the Session pooler and copy its
   host, port and username. Configure all of the following; omitting the database
   URL or username would retain the original team's defaults:

   | Variable | Value |
   | --- | --- |
   | `SUPABASE_DB_URL` | `jdbc:postgresql://YOUR-POOLER-HOST:5432/postgres?sslmode=require` |
   | `SUPABASE_DB_USER` | The Session pooler username from your project |
   | `SUPABASE_DB_PASSWORD` | Your database password |
   | `SUPABASE_URL` | `https://YOUR-PROJECT-ID.supabase.co` |
   | `SUPABASE_STORAGE_SECRET_KEY` | Your backend secret API key |

3. Create a private `mavworks-images` bucket, allowing JPEG and PNG files up to
   5 MB. See [Supabase setup](supabase-setup.md) for details.
4. Start with `prod,supabase` profiles and the email settings below. Flyway creates
   the application schema and applies the repository's migrations on startup.
   Do not copy the team's Flyway history or manually mark migrations as applied.
   Use a separate project for development if production data must remain unchanged.

## Email and operator settings

Configure `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, and
`MAIL_FROM` using your own authorized sender and SMTP credentials. Set
`MAVWORKS_PUBLIC_BASE_URL` to your instance's HTTPS URL for password-reset and
unlock links. See [Production email](production-email.md) for TLS settings.
If your provider restricts sending by source IP, authorize the host's outbound
addresses, not visitors' addresses.

Set `MAVWORKS_APPEAL_EMAIL` to a monitored address. Review the operator details,
contacts and policies in `src/main/resources/legal/terms-of-service.txt` for your
instance. The default terms seeder publishes bundled terms to an empty database;
editing the file does not replace terms already published. Follow the
[terms guidance](../README.md#create-an-account) when publishing revisions.

To create the first administrator:

1. Register and verify a trusted account through the application.
2. In Supabase SQL Editor, find its ID with
   `SELECT id, username FROM mavworks.app_users;`.
3. Set `MAVWORKS_ADMIN_USER_IDS` to that ID (comma-separated for multiple admins)
   in the host environment and restart the app.
4. Sign out and sign back in to load the administrator role. Check that Reports
   and Moderate listings are available. Never reuse account IDs from another database.

## Hosting and automatic deployment

Build the production JAR with Java 21:

```powershell
.\mvnw.cmd clean package "-Dvaadin.productionMode=true"
```

Use `./mvnw` instead on macOS/Linux. With the environment configured, a Java 21
host can run `java -jar target/mavworks-1.0.0.jar --spring.profiles.active=prod,supabase`.
Provide HTTPS through the hosting platform or reverse proxy. Production uses
secure session cookies. The repository also includes a Dockerfile using the
same production profiles; supply secrets at runtime.

For Azure, create your own Linux Java 21 / Java SE Web App in a region allowed
by your subscription, then apply the settings above. See [Azure deployment](azure-deployment.md)
for manual deployment and startup checks. Replace the example app name, resource
group and website URL with your own.

The committed GitHub workflow targets the team's Azure app and identity. Forking
it does not copy the GitHub secrets or grant Azure access. In your new Web App's
Deployment Center, connect your repository and `main` branch using GitHub Actions
and a user-assigned identity (OIDC). You need permission to manage the repository
and Azure identity. Let Azure configure the identity and repository secrets.
Replace the inherited deployment workflow with the one for your app; do not leave
two deployment workflows active. Retain Java 21 and the production build command
`bash ./mvnw -B clean package -Dvaadin.productionMode=true`. Confirm the workflow's
app name and secret references belong to your deployment.

See [Microsoft's deployment instructions](https://learn.microsoft.com/en-us/azure/app-service/deploy-continuous-deployment)
for the Deployment Center steps. A successful workflow should build, test and
deploy the JAR. Application secrets belong in Azure's environment settings, not
the workflow file. Check the deployed site after the first run.

## Adapting and maintaining the app

Registration currently accepts only `@mavs.uta.edu`. This is a code rule, not an
environment setting. To adapt it, update the email pattern and error in
`RegistrationValidation.java`, the placeholder in `AccountCreateView.java`, and
related tests, terms and documentation. Keep mailbox verification enabled.

The pilot assumes one app instance; spam counters are in memory. Scaling to
multiple instances needs shared rate limiting and a session strategy. Image
content is not automatically moderated, so someone must monitor reports.

An ongoing host is responsible for dependency updates, database/storage backups,
SMTP credentials and quotas, reports, support mail and hosting charges. Before
ending the pilot, decide what data to retain and notify users as appropriate.
Disable automatic deployment, revoke unused credentials, and remove paid resources
when no longer needed. Keep the repository and release tag available for others
who want to continue development.

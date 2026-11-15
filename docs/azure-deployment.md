# Azure deployment

The pilot runs on Azure App Service using Linux, Java 21, and Java SE. Supabase continues to store the database and uploaded images.

Website: https://mavworks-pilot-dqauafcqe3ctfhb0.westus3-01.azurewebsites.net

The Web App is named `mavworks-pilot` in West US 3. Confirm the resource group in Azure Overview; the commands below use `mavworks-pilot`. Confirm the selected hosting plan and cost in Azure. Student credits cover charges while available. Set a budget alert to track usage; alerts do not stop spending.

## Environment variables

Set these in the Web App's Environment variables and apply the changes:

- `SPRING_PROFILES_ACTIVE=prod,supabase`
- `SUPABASE_DB_PASSWORD`: the database password
- `SUPABASE_URL`: the Supabase project URL
- `SUPABASE_STORAGE_SECRET_KEY`: the shared image storage secret key
- `MAVWORKS_PUBLIC_BASE_URL`: the HTTPS website address above
- Email settings from [production email](production-email.md)

The Supabase profile supplies the current database host, username, and image bucket. Override `SUPABASE_DB_URL` and `SUPABASE_DB_USER` only if the connection changes. Set `MAVWORKS_ADMIN_USER_IDS` to existing trusted account IDs if administrator access is needed. Local credential files are not used in production. Keep secrets out of Git and deployment commands.

Confirm HTTPS Only and Always On are enabled in the Web App settings. Applying environment changes restarts the app and can interrupt active forms and sessions. Production startup requires email settings even when testing with existing accounts.

## Build and deploy

From the project folder, with Java 21 selected:

```powershell
.\mvnw.cmd clean package "-Dvaadin.productionMode=true"
```

This runs tests and creates `target/mavworks-1.0-SNAPSHOT.jar`. Vaadin may also generate `src/main/bundles/prod.bundle`. Upload the JAR using Manage files in Azure Cloud Shell, then run in Bash:

```bash
az webapp deploy --resource-group mavworks-pilot --name mavworks-pilot --src-path "$HOME/mavworks-1.0-SNAPSHOT.jar" --type jar
```

Deployment is manual; pushing to GitHub does not update the website. Record the deployed commit when releasing. The first pilot deployment used `cd9d93b`. Confirm the build succeeds and review pending database migrations before each deployment. Startup with the Supabase profile applies pending Flyway migrations to the shared database. Do not delete migration history to silence warnings.

## Checks after deployment

Wait for startup to finish, then use a fresh browser tab. Test registration and verification, password reset and unlock links, listings and images from another account, messages, and appointment changes. Test the mobile layout too. Existing-account login does not verify email delivery.

Use Monitoring > Log stream for application errors. If needed, enable console logging in Cloud Shell:

```bash
az webapp log config --resource-group mavworks-pilot --name mavworks-pilot --docker-container-logging filesystem
```

The email teammate checks Brevo delivery logs and verified sender settings. Do not enable SMTP debug output or share passwords, verification codes, or message bodies in logs.

Pilot status on November 15, 2026: existing-account flows were manually checked. New-account email delivery is still under investigation. Supabase contains V21 (profile banners), while the deployed source contains migrations through V20; coordinate that migration with its author before changing it.

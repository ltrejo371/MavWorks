# Azure deployment

The pilot runs on Azure App Service using Linux, Java 21, and Java SE. Supabase continues to store the database and uploaded images.

Website: https://mavworks-pilot-dqauafcqe3ctfhb0.westus3-01.azurewebsites.net

The Web App is named `mavworks-pilot` in West US 3. Confirm the resource group in Azure Overview; the commands below use `mavworks-pilot`. Confirm the selected hosting plan and cost in Azure. Student credits cover charges while available. Set a budget alert to track usage; alerts do not stop spending.

For a new Azure account or fork, follow [Hosting your own instance](self-hosting.md)
to configure your own resources and GitHub deployment identity.

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

This runs tests and creates `target/mavworks-1.0.0.jar`. Vaadin may also generate `src/main/bundles/prod.bundle`. Upload the JAR using Manage files in Azure Cloud Shell, then run in Bash:

```bash
az webapp deploy --resource-group mavworks-pilot --name mavworks-pilot --src-path "$HOME/mavworks-1.0.0.jar" --type jar
```

Pushes and merges to `main` run `.github/workflows/main_mavworks-pilot.yml`, which tests, builds the production JAR and deploys it to Azure using OIDC. Feature branch pushes do not deploy. The commands above are a manual fallback. Record the deployed commit when releasing. Confirm the build succeeds and review pending database migrations before each deployment. Startup with the Supabase profile applies pending Flyway migrations to the shared database. Do not delete migration history to silence warnings.

## Checks after deployment

Wait for startup to finish, then use a fresh browser tab. Test registration and verification, password reset and unlock links, listings and images from another account, messages, and appointment changes. Test the mobile layout too. Existing-account login does not verify email delivery.

Use Monitoring > Log stream for application errors. If needed, enable console logging in Cloud Shell:

```bash
az webapp log config --resource-group mavworks-pilot --name mavworks-pilot --docker-container-logging filesystem
```

The email teammate checks Brevo delivery logs and verified sender settings. Do not enable SMTP debug output or share passwords, verification codes, or message bodies in logs.

Release 1.0.0 is the student pilot. Supabase contains V21 (profile banners); keep shared migration history intact and coordinate any migration changes with the team.

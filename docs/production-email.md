# Email when MavWorks launches

Brevo can serve both local and hosted MavWorks instances. The production app uses hosting environment secrets, never data/mail.properties. Local development continues loading that git-ignored file. The Docker build excludes local credentials, databases, logs, and environment files.

## Hosting configuration

Use profiles prod,supabase (the Docker image default). Set the existing Supabase database/storage environment variables described in [Supabase setup](supabase-setup.md), plus:

| Variable | Value |
| --- | --- |
| SMTP_HOST | smtp-relay.brevo.com |
| SMTP_PORT | 587 |
| SMTP_USERNAME | The SMTP login shown in Brevo Settings → SMTP & API |
| SMTP_PASSWORD | A Brevo SMTP key, stored as a hosting secret |
| MAIL_FROM | Your verified sender address |
| MAVWORKS_PUBLIC_BASE_URL | Your real HTTPS website URL, including any context path |

Keep SMTP_AUTH=true, SMTP_STARTTLS=true, and SMTP_SSL=false for port 587. Use an SMTP key, not your Brevo account password or API key. A separate production key makes rotation and revocation independent of local development. No credentials belong in Git, Docker build arguments, frontend code, or this document.

Production startup rejects missing email settings and non-HTTPS or localhost recovery URLs. Verification codes do not contain a website URL. Password-reset and unlock emails use MAVWORKS_PUBLIC_BASE_URL, so those links open the deployed website. This setting does not publish or host the website; deployment and the actual domain are still needed.

## Sender and launch checks

The current Mavworks0@gmail.com sender is verified. For public launch, authenticate a domain you control in Brevo and use a sender such as no-reply@YOUR-DOMAIN. Follow Brevo's displayed DNS instructions, then set MAIL_FROM to that verified address. You cannot authenticate gmail.com or uta.edu unless you administer that domain.

After deployment, test registration, reset, and unlock emails to an account you control. Confirm that links contain the real HTTPS address and that messages arrive. Monitor delivery and quota in Brevo. Keys expire on their configured expiry date and can also expire after 90 days without a successful send; keep the key's lifecycle in your deployment maintenance process.

Provider references: [SMTP settings](https://help.brevo.com/hc/en-us/articles/7924908994450-Send-transactional-emails-using-Brevo-SMTP), [SMTP key lifecycle](https://help.brevo.com/hc/en-us/articles/7959631848850-Create-and-manage-your-SMTP-keys).

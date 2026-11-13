# Registration email verification

Registration sends a six-digit verification code to a validated `@mavs.uta.edu` address. The user enters that code on the same registration page before the account and terms agreement are created atomically. Successful registration retains the existing automatic sign-in behavior. Existing accounts are unchanged.

Codes expire after 10 minutes and allow at most five incorrect attempts. Failed attempts persist across requests. Resends require 60 seconds between messages and are capped at five per email address per hour. A successful resend replaces the old code and token. Failed email delivery rolls back the pending proof and preserves any previously delivered code. Successful verification consumes the proof to prevent reuse. Pending proofs store a salted code hash and do not contain account passwords.

## Email delivery configuration

For local development, copy [mail.properties.example](mail.properties.example) to `data/mail.properties`, replace every placeholder with your SMTP provider's settings, and restart the app. The file is ignored by Git and loaded automatically by both the default and Supabase profiles. Never commit credentials. If the SMTP provider requires an app password or an SMTP-specific key, use that credential rather than your regular sign-in password.

Alternatively, configure these environment variables before starting the app:

- `SMTP_HOST`: your SMTP provider's hostname.
- `SMTP_PORT`: defaults to `587`.
- `SMTP_USERNAME` and `SMTP_PASSWORD`: SMTP credentials.
- `MAIL_FROM`: a sender address authorized by your email provider.
- `SMTP_AUTH` and `SMTP_STARTTLS`: default to `true` for authenticated SMTP with STARTTLS on port 587.
- `SMTP_SSL`: defaults to `false`. For a provider using implicit TLS on port 465, use `SMTP_SSL=true` and `SMTP_STARTTLS=false`. Authentication should remain enabled.
- For a local test SMTP server only, authentication and TLS can be disabled.

Credentials are not included in source control. The application uses Spring Boot's SMTP mail support with finite network timeouts. Missing configuration and SMTP authentication failures produce distinct setup messages. Connection or delivery failures show a retry message. No account is created on failure, and a retry remains available. Diagnostic logs omit addresses, codes, credentials, and provider exception text. There is no console-code fallback or verification bypass. See [Spring Boot email configuration](https://docs.spring.io/spring-boot/reference/io/email.html) for additional provider settings.

Flyway V13 creates `email_verifications` in the managed database. The local Hibernate profile creates the new table automatically. No live email delivery or production database migration is performed by the code update; deployment needs valid SMTP configuration and a real delivery smoke test.

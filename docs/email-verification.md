# Registration email verification

Registration sends a six-digit verification code to a validated `@mavs.uta.edu` address. The user enters that code on the same registration page before the account and terms agreement are created atomically. Successful registration retains the existing automatic sign-in behavior. Existing accounts are unchanged.

Codes expire after 10 minutes and allow at most five incorrect attempts. Failed attempts persist across requests. Resends require 60 seconds between messages and are capped at five per email address per hour. A successful resend replaces the old code and token. Failed email delivery rolls back the pending proof and preserves any previously delivered code. Successful verification consumes the proof to prevent reuse. Pending proofs store a salted code hash and do not contain account passwords.

## Email delivery configuration

Configure these environment variables before enabling registration in deployment:

- `SMTP_HOST`: your SMTP provider's hostname.
- `SMTP_PORT`: defaults to `587`.
- `SMTP_USERNAME` and `SMTP_PASSWORD`: SMTP credentials.
- `MAIL_FROM`: a sender address authorized by your email provider.
- `SMTP_AUTH` and `SMTP_STARTTLS`: default to `true`; turn them off only for an appropriate local test mail server.

Credentials are not included in source control. The application uses Spring Boot's SMTP mail support with finite network timeouts. If no sender is configured or delivery fails, registration displays an error and creates no account. There is no console-code fallback or verification bypass. See [Spring Boot email configuration](https://docs.spring.io/spring-boot/reference/io/email.html) for additional provider settings.

Flyway V13 creates `email_verifications` in the managed database. The local Hibernate profile creates the new table automatically. No live email delivery or production database migration is performed by the code update; deployment needs valid SMTP configuration and a real delivery smoke test.

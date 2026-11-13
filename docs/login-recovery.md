# Login and account recovery

Three consecutive incorrect passwords lock an existing account. A successful login resets the failure count; empty required fields do not count. Account-row locking serializes concurrent attempts. A lock remains until the account owner follows an unlock email or completes password reset. Administrative bans cannot be removed through recovery.

The login page offers Forgot Password and Resend account unlock email. Both use the account email. Requests return the same response for known, unknown, banned, and throttled accounts. Email requests are limited to one per minute and five per hour per account; the automatic third-failure lock notification is sent immediately. Email failures do not undo a lock. A user can request another recovery email after the cooldown.

Recovery links contain 256-bit random tokens; only SHA-256 hashes are stored. Links expire after 30 minutes. Each account has one current recovery link: a resend replaces the earlier link, and completing recovery consumes it. Reset and unlock links cannot be interchanged. Opening an unlock link requires a confirmation click so email scanners cannot unlock accounts. Reset requires matching passwords of at least eight characters and at most 72 UTF-8 bytes, consistent with registration. Passwords are hashed with BCrypt.

Password reset also unlocks the account. Locking and recovery invalidate existing authenticated sessions on their next request. Login uses session cookies without remember-me. Idle sessions expire after 30 minutes, and Vaadin heartbeat requests do not keep an idle session alive. Browser session-restore features can restore session cookies; closing a browser is not a reliable server-side logout signal. Explicit logout and the idle timeout end the session.

## Deployment

- Apply Flyway migration V17 (automatically with the Supabase profile). Local H2 uses Hibernate schema update.
- Set MAVWORKS_PUBLIC_BASE_URL to the HTTPS application URL, including any context path, for example https://market.example.edu/mavworks. Local development defaults to http://localhost:8080. Links never derive their origin from a request Host header.
- Configure the existing SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD, SMTP_AUTH, SMTP_STARTTLS, and MAIL_FROM environment settings. No credentials are stored in this change.
- Delivery errors are logged without email addresses or tokens. Verify actual delivery using a test account in the deployment environment.

## Design references

[OWASP password recovery guidance](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html) informs the single-use, expiring tokens and generic recovery messages. [Vaadin session lifecycle](https://vaadin.com/docs/latest/flow/advanced/application-lifecycle) describes excluding heartbeat requests from idle-session expiration.

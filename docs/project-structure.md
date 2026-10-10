# MavWorks project structure

Packages under com.mavworks:
- auth: login and account-creation screens.
- security: database authentication, password encoder and session helpers.
- model/repository: JPA domain classes and persistence interfaces.
- service: account and terms implementations; listing/profile/image contracts.
- dto: form requests, search filters and public profile projection.
- base.ui: shared navigation and layout.
- landing.ui: Home at /, with shared published listings, search and filters.
  /market remains an alias for old bookmarks.
- dashboard: personal account information and owned listings at /dashboard.
- listing/profile: remaining placeholder screens.
Home is the destination after login and registration. Home and Dashboard have
separate sidebar entries.

## Account workflow

Registration validates a 3–30 character username, a @mavs.uta.edu email,
matching passwords of at least 8 characters (maximum 72 UTF-8 bytes), and the
current terms version. Usernames and emails are trimmed and stored lowercase.
Passwords are BCrypt hashes. The user, embedded profile and timestamped terms
acceptance are saved in one transaction. Duplicate usernames/emails are rejected.
Registration automatically signs in and opens Home. If automatic
login fails after saving, the screen directs the user to the login page.

Login uses database accounts. The old user/user, mod/mod and admin/admin demo
accounts are no longer provided. New accounts receive the USER role.
Email syntax/domain validation does not verify ownership of that mailbox.

The development database is stored under data/mavworks relative to the project
working directory and survives restarts. Run the app from the folder containing
pom.xml; data/ is ignored by Git. Run only one instance against this database.

A clearly labeled development terms record is created only when the terms table
is empty and mavworks.seed-development-terms=true. Replace it with team-approved
content before deployment and disable this option. Missing terms disable signup;
a stale terms version requires refreshing and accepting the current version.

Automatic login uses Spring Security servlet integration:
https://docs.spring.io/spring-security/reference/servlet/integrations/servlet-api.html
RegistrationLogin additionally rotates the session ID after authentication.

## Remaining work

1. Listing creation, image storage/validation and listing details. Home
   already displays published listings; an empty database shows an empty state.
2. Account/profile editing and public profiles; Dashboard currently displays
   account information and owned listings read-only.
3. Agree on the category list (currently OTHER only), add pagination for large
   catalogs and refine marketplace navigation.
4. Password reset and email delivery; the existing forgot-password control is
   not implemented yet.
5. Replace demo terms, agree on categories and complete use-case validation.

Messaging, reviews, blocking, reports and moderation remain deferred.

## Verification

Run ./mvnw.cmd test in Git Bash or .\\mvnw.cmd test in PowerShell.
AccountWorkflowTest uses an isolated file database under target/ and closes/reopens
the application context to verify persistence. It also checks password hashing,
invalid/duplicate registration, incorrect credentials, form-login sessions and
automatic-login session rotation. ProjectStructureTest checks entity relationships.

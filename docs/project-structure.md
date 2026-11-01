# MavWorks project structure

Packages under com.mavworks:
- auth: login and account-creation screens.
- security: database authentication, password encoder and session helpers.
- model/repository: JPA domain classes and persistence interfaces.
- service: account, terms, listings, profiles, images, messaging, reviews and moderation logic.
- dto: form requests, search filters and public listing/profile projections.
- base.ui: shared navigation and layout.
- landing.ui: Home at /, with shared published listings, search and filters.
  /market remains an alias for old bookmarks.
- dashboard: account editing and owner listing management at /dashboard.
- listing: listing creation and details screens.
- profile: public profiles, available listings and provider reviews.
- messaging: participant conversations and sale confirmation.
- admin: report review and moderation screens.
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

The default H2 development database is stored under data/mavworks relative to the project
working directory and survives restarts. Run the app from the folder containing
pom.xml; data/ is ignored by Git. Run only one instance against this database.

A clearly labeled development terms record is created only when the terms table
is empty and mavworks.seed-development-terms=true. Replace it with team-approved
content before deployment and disable this option. Missing terms disable signup;
a stale terms version requires refreshing and accepting the current version.

Automatic login uses Spring Security servlet integration:
https://docs.spring.io/spring-security/reference/servlet/integrations/servlet-api.html
RegistrationLogin additionally rotates the session ID after authentication.

## Listing management workflow

DashboardView loads account information and owned listings through
AccountDashboardService. Its OwnerListing record includes the listing details,
status and expiration date, including listings no longer shown in the marketplace.
ManageListingDialog lets the owner edit details, withdraw or renew a listing.
ListingManagementService checks the signed-in owner, locks the listing and
applies changes in a transaction. Sold and administrator-removed listings cannot
be edited, withdrawn or renewed through these controls.

New listings expire after 30 days. ListingRepository limits marketplace search
and public-profile listings to ACTIVE rows with a future expiration date.
Expired listings remain stored. Withdrawal changes the status to WITHDRAWN;
renewal changes it to ACTIVE and sets expiration to 30 days from renewal.
REMOVED is reserved for administrator removal. Flyway V8 introduces expiration
and other marketplace fields; V9 adds WITHDRAWN to the database status constraint.

MessagingService starts conversations for available listings and preserves
existing conversations after a listing becomes unavailable. ConversationService
confirms a sale and closes the conversation after checking the provider, blocks
and message history. Product confirmation marks the listing SOLD; service
confirmation leaves the listing's status and expiration unchanged.

ReviewService supplies public-profile listings and reviews. Rating averages and
counts include all reviews, while individual review visibility follows mutual
blocking rules. University email addresses are excluded from public profiles.

## Remaining work

1. Email ownership verification, password reset and email delivery.
2. Replace development terms with team-approved content and complete use-case
   validation for the student pilot.
3. Add pagination for large catalogs and refine marketplace navigation.
4. Extend owner listing editing to images if included in the next iteration.

Listing creation, public profiles, account editing, owner listing management,
messaging, reviews, blocking, reports and administrator report decisions are
implemented. Further policy and usability changes should build on those workflows.

## Verification

Run ./mvnw.cmd test in Git Bash or .\\mvnw.cmd test in PowerShell.
AccountWorkflowTest uses an isolated file database under target/ and closes/reopens
the application context to verify persistence. It also checks password hashing,
invalid/duplicate registration, incorrect credentials, form-login sessions and
automatic-login session rotation. ProjectStructureTest checks entity relationships.

MessagingWorkflowTest also checks sold-product behavior, owner-only listing
editing, validation, withdrawal, renewal and preservation of existing conversations.
It runs Flyway against an isolated H2 database in PostgreSQL mode. For manual
verification, use two test accounts to edit, withdraw and renew listings, confirm
product and service sales, and check marketplace and profile visibility.

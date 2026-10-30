# MavWorks

Built with Java 21, Spring Boot, Vaadin, and Maven.

## Requirements

- **JDK 21** — [Download Eclipse Temurin](https://adoptium.net/temurin/releases/?version=21)
- **Git**
- **IDE:** IntelliJ IDEA or VS Code
    - VS Code: install **Extension Pack for Java**.

No separate Maven or database installation is needed. The project includes the Maven Wrapper and uses H2 for development.

## Initial setup

1. Clone the repository:

   ```bash
   git clone https://github.com/ltrejo371/MavWorks.git
   cd MavWorks
   ```

2. Open this folder in your IDE. It should contain `pom.xml`.
    - **IntelliJ:** allow Maven import to finish and set the Project SDK to Java 21.
    - **VS Code:** allow Java project import to finish and use JDK 21.

3. Check that Maven is using Java 21:

   **Windows PowerShell**
   ```powershell
   .\mvnw.cmd --version
   ```

   **macOS / Linux**
   ```bash
   ./mvnw --version
   ```

   If another Java version appears, set `JAVA_HOME` to your JDK 21 installation and reopen the terminal.

## Run the app

Run from the folder containing `pom.xml`.

**Windows PowerShell**
```powershell
.\mvnw.cmd spring-boot:run
```

**macOS / Linux**
```bash
./mvnw spring-boot:run
```

Open **http://localhost:8080**. The first run may take a few minutes while dependencies download.

Stop the app with **Ctrl+C** in the terminal.

Alternatively, run the `main()` method in `Application.java` from your IDE.

## Build and test

Stop the running app first.

**Windows PowerShell**
```powershell
.\mvnw.cmd clean verify
```

**macOS / Linux**
```bash
./mvnw clean verify
```

A successful check ends with `BUILD SUCCESS`.

## Working on changes

Start each task from an updated `main` and create your own branch:

```bash
git switch main
git pull --ff-only
git switch -c feature/your-feature
```

Edit code, restart the app, and refresh the browser to check changes.

Push your branch and open a pull request into `main`. Keep credentials and personal IDE settings out of Git.
## Create an account

Restart the app after pulling account changes. In Git Bash, run:

```bash
./mvnw.cmd spring-boot:run
```

Open http://localhost:8080/create-account. Enter a unique username, a
@mavs.uta.edu email and matching passwords (at least 8 characters), then read and
accept the displayed development terms. Registration signs you in automatically.

The old demo logins (user/user, mod/mod and admin/admin) are no longer available.
Accounts are saved in data/mavworks and survive restarts. Always run from the
project root, and do not run two instances against the same database.
Email ownership verification and password reset are not implemented yet.
Development terms are placeholders for testing; replace them before public use.

## Create a listing

Sign in, open **Dashboard**, select **Add Listing**, and enter a name, price,
product/service type, and description. Select **Done** to publish; the listing
then appears on your dashboard and Home, with links to its details and images.

Current defaults for the use case's open questions: images are optional, with
up to five JPEG/PNG files, each at most 5 MB and 20 megapixels. Category is
optional (defaults to Other). Prices may be zero through 9,999,999,999.99 with
at most two decimal places, matching the database's price column.
Images are validated by content and stored under `data/listing-images`.
Set `mavworks.images-directory` to change that directory; deployments need a
persistent volume shared by application instances and included in backups.

## Provider reviews

Open a listing's **View provider profile and reviews** link, then select **Review**.
Only listings from your conversations closed after provider-confirmed sales are
offered. Select a listing, choose a rating from 1 to 5, optionally write a comment
(up to 2000 characters), and submit. The review is saved and displayed on the
provider's profile. Failed submissions preserve the form for retry.

Current policy: one review per user per listing; no self-reviews, editing, or
provider replies. Eligibility and uniqueness are enforced on the server, with a
database uniqueness constraint as well. Profile reviews are visible to other
signed-in users, consistent with the existing marketplace access rules.

In the chat, the provider selects **Confirm sale and close** after completing a
sale. This requires at least one sent message and verifies the provider's identity;
merely closing a conversation or marking a listing sold does not qualify a review.
Without a qualifying persisted conversation, the Review button explains that a
completed sale is required. Flyway V4 adds conversation and review tables on
Supabase; local H2 adds them through the existing Hibernate schema-update setting.

## Messaging

Select **Message** on a listing to start or reopen its conversation. Both users
can send replies and revisit the saved conversation using **Messages** in the
sidebar. Opening a listing concurrently reuses one conversation; starts are
serialized per buyer. Existing conversations remain available after a sale.
The provider uses Messages to select the particular buyer they want to reply to.

Messages must contain text and be at most 4000 characters. Unsent drafts are
discarded when leaving the chat; a failed send retains the draft for retry.
Messages are rendered as plain text and are persisted before success is shown.
Only the two participants can read or send messages. Saved blocks in either
direction prevent opening and sending, including when a block is added after the
chat is opened. The block-management UI is a separate, unimplemented use case.

Chats load the most recent 100 messages, offer **Load older messages**, and check
for new messages every three seconds while open. A **Refresh** button is also
available. These are implementation defaults, not a measured response-time SLA;
loading and sending still depend on the database and network.
Confirmed, closed conversations retain read-only history and unlock the review
flow on the provider profile. An empty conversation cannot be confirmed as a sale.
Flyway V5 adds messages and block-list tables; restart with the Supabase profile
to apply it. Existing conversations and reviews are retained.

## Supabase connection

See [Supabase setup](docs/supabase-setup.md) for the password prompt and launch command.
The supabase profile uses online PostgreSQL; the default profile continues to use local H2.

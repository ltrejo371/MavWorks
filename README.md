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
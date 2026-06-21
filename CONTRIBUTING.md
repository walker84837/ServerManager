# Contributing to ServerManager

:balloon: Thank you for considering contributing to ServerManager! This guide will help you get started.

## Getting Started

### Prerequisites

- **Java 21** minimum (required for compilation and runtime)
- **Git** (for version control and contributions)
- A compatible code editor (IntelliJ IDEA, Neovim, VS Code with Java extensions)

### Clone the Repository

```bash
git clone https://github.com/walker84837/ServerManager.git
```

## Building

|Task|Example Command|Notes|
|---|---|---|
|Full build|`./gradlew shadowJar`|The built JAR will be in `build/libs/`.|
|Compile only|`./gradlew compileJava`||
|Unit tests|`./gradlew test`|No tests currently exist.|

### Version Flags

- `./gradlew shadowJar -Pver=v1.0` → `ServerManager-1.0.jar`
- `./gradlew shadowJar -Pver=v1.0-RC-1` → `ServerManager-1.0-SNAPSHOT.jar`
- No `-Pver` flag → timestamp-based snapshot (e.g., `2026-06-20T120000Z-SNAPSHOT`)

## Development Setup

### Importing from an IDE

1. Import the project as a **Gradle project** in your IDE
2. Ensure Java 21 is configured as the project JDK
3. Enable annotation processing (for Lombok)

### Dependencies

- **Paper API** (`compileOnly`) - Provided by the server at runtime
- **Runtime dependencies** - Loaded via `ServerManagerLoader` (PluginLoader API), declared in [libs.version.toml](gradle/libs.versions.toml)

**Note**: Both `build.gradle.kts` and `ServerManagerLoader.java` must be kept in sync for runtime dependencies.

## Code Style

### General Conventions

- **Lombok** - Use `@Getter` for trivial getters
- **`var`** - Used for local variable type inference and simplicity
- **Logging** - Use `logger` field via `getLogger()` (JUL)
- **MiniMessage** - All user-facing messages use MiniMessage with palette tags

### Message Formatting

Instead of hardcoding color names, **use palette tags from `PaletteConfig`**. ServerManager reads color palettes from the `config.yml` file to ensure consistent colors in user messages.

*Example*:

```java
// Correct
sender.sendRichMessage("<success>Operation completed!</success>", plugin.getMessageTheme().getPaletteResolver());

// Incorrect
sender.sendRichMessage("<green>Operation completed!</green>");
```

Available palette tags:
- `<primary>`
- `<secondary>`
- `<foreground>`
- `<placeholder>`
- `<success>`
- `<failure>`
- `<warning>`
- `<details>`

### Threading

- **We don't call the Bukkit scheduler directly**, as it will not work on Folia servers
- **Use `SchedulerAdapter` for async tasks** as it delegates to the correct scheduler APIs for Paper vs Folia
- **Virtual threads** are currently used via `Executors.newVirtualThreadPerTaskExecutor()` for process management

```java
// Correct
plugin.getSchedulerAdapter().runNow(() -> {
    // Async task
});

// Incorrect
Bukkit.getScheduler().runTaskLater(plugin, () -> {
    // Don't do this
});
```

## Architecture

### Commands

- All commands implement the `PluginCommand` interface
- Registration happens in `CommandRegistrar`
- Permissions are registered automatically via `registerIfNotExists()`

### Configuration

- **Main config:** `plugins/ServerManager/config.yml`
- **Service configs:** `plugins/ServerManager/services/*.yml`
- **Cron configs:** `plugins/ServerManager/cron/*.yml`

Config classes use `configlib-paper` with `@Configuration` annotation and `LOWER_KEBAB_CASE` name format.

## Submitting Changes

1. **Create a branch** from `main`:
   ```bash
   git switch -c feature/your-feature-name
   ```

2. **Make changes** following the code style above

3. **Test** with both Paper and Folia if possible:
   - Compile: `./gradlew compileJava`
   - Build: `./gradlew shadowJar`
   - Test on a local server

4. **Commit** with a clear message:
   ```bash
   git commit -m "feat: add new feature description"
   ```

5. **Push and open a PR:**
   ```bash
   git push origin feature/your-feature-name
   ```

6. **Describe your changes** in the PR description, including:
   - The problem this PR solves
   - A brief overview of the implemented solution
   - How to test the changes (if it requires special setup)
   - Any breaking changes or migration notes (if any)

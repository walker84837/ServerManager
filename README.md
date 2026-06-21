# ServerManager

![License: LGPL-3.0](https://www.gnu.org/graphics/lgplv3-147x51.png)
![Version: 0.1.0-beta](https://img.shields.io/badge/version-0.1.0--beta-blue)
![Java](https://img.shields.io/badge/Java-21-orange)
![Platform](https://img.shields.io/badge/Paper%2FFolia-1.21+-green)

A Paper/Folia plugin for managing external processes and system resources from Minecraft.

## Features

- **Process Management**. Start, stop, and monitor external processes (e.g., Discord bots, proxy servers, backup scripts)
- **System Monitoring**. View RAM usage, storage usage, CPU load, and system health
- **Package Installation**. Install system packages via apt, dnf, pacman, winget, choco, or brew
- **Terminal Commands**. Execute arbitrary shell commands from Minecraft
- **Cron Job Scheduling**. Run Minecraft commands on a schedule using cron expressions
- **Discord Webhooks**. Get notified about process events (start, stop, failures, OOM kills)
- **OOM Killer**. Automatically terminate memory-heavy processes when exceeding limits
- **Paste Service Integration**. Long command output is automatically uploaded or saved to file
- **Folia Support**. Fully compatible with Folia's regional threading model

## Requirements

- **Server:** Paper 1.21+ or Folia
- **Java:** Java 21

## Installation

1. Download the latest JAR from [GitHub Releases](https://github.com/walker84837/ServerManager/releases)
2. Place the JAR in your server's `plugins/` folder
3. Restart the server
4. Configure `config.yml` and create service/cron configs as needed

## Configuration

### Main Config (`config.yml`)

Generated automatically on first run. Key options:

```yaml
# Discord webhook integration
discord-webhooks-enabled: false
discord-webhook-url: ""

# Cron job scheduling
cron-jobs-enabled: false

# Out-Of-Memory killer
oom-killer-enabled: false
total-memory-limit-mb: 0  # 0 = no limit

# Package management commands (security-sensitive)
package-management-enabled: false

# Message color palette (all colors use MiniMessage format)
palette:
  primary: BLUE
  secondary: GREEN
  foreground: WHITE
  placeholder: GRAY
  success: GREEN
  failure: RED
  warning: YELLOW
  details: DARK_AQUA

# Paste service for long command output
paste-service:
  max-output-length: 5000  # Characters before truncation
  upload:
    url: "https://paste.myst.rs/api/v3/paste"
    method: "POST"
    format: "json"
    body: '{"expiresIn": "1d", "pasties": [{"title": "output.txt", "language": "text", "code": "{content}"}]}'
    selector: "._id"
    url-template: "https://paste.myst.rs/{result}"
```

### Service Configs (`plugins/ServerManager/services/*.yml`)

Define managed processes (e.g., Discord bots, proxies, daemons):

```yaml
# Example: Discord bot service
program: "/usr/bin/python3"
args:
  - "/opt/discord-bot/main.py"
working-directory: "/opt/discord-bot"
environment:
  DISCORD_TOKEN: "your-bot-token-here"
  API_URL: "https://api.example.com"
  LOG_LEVEL: "DEBUG"
duration: 0              # 0 = run indefinitely
duration-unit: MINUTES   # SECONDS, MINUTES, HOURS, DAYS
kill-mode: SOFT          # SOFT (graceful) or FORCE (immediate)
auto-restart: true       # Restart if the process stops
pre-launch-commands: []  # Commands to run before starting
after-death-commands: [] # Commands to run after termination
```

**Note:** Environment variables inherit the system environment by default (preserving `PATH`, `JAVA_HOME`, etc.), with configured variables overriding or adding to them.

### Cron Configs (`plugins/ServerManager/cron/*.yml`)

Schedule Minecraft commands using cron expressions:

```yaml
# Example: Daily server restart at 3 AM
expression: "0 0 3 * * ?"
command: "restart"
enabled: true
```

**Cron Expression Format:** `seconds minutes hours day-of-month month day-of-week`

Examples:
- `0 0 12 * * ?` - Every day at noon
- `0 0/5 * * * ?` - Every 5 minutes
- `0 0 1 1 * ?` - Every January 1st at midnight

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/process start <program>` | Start a managed service | `servermanager.command.process` |
| `/process stop <program>` | Stop a managed service | `servermanager.command.process` |
| `/process list` | List all processes and services | `servermanager.command.process` |
| `/process reload` | Reload all configurations | `servermanager.command.process` |
| `/terminal <command>` | Execute a shell command | `servermanager.command.terminal` |
| `/system install <package>` | Install a system package | `servermanager.command.system` |
| `/system ram` | Show RAM usage | `servermanager.command.system` |
| `/system storage` | Show disk usage | `servermanager.command.system` |
| `/system run <command>` | Execute a shell command | `servermanager.command.system` |
| `/system health` | Full system health report | `servermanager.command.system` |

All commands default to **OP only**.

## Permissions

| Permission Node | Default | Description |
|-----------------|---------|-------------|
| `servermanager.command.process` | OP | Access to process management commands |
| `servermanager.command.terminal` | OP | Execute terminal commands |
| `servermanager.command.system` | OP | System management commands |

## Discord Webhooks

Enable `discord-webhooks-enabled: true` and set `discord-webhook-url` to receive notifications for:

- Process started successfully
- Process stopped (duration limit reached)
- Process failed to start
- Process failed to stop
- Process terminated unexpectedly
- OOM killer terminated a process

**Webhook URL Setup:**
1. Go to your Discord server channel settings
2. Integrations → Webhooks → New Webhook
3. Copy the webhook URL and paste it in `config.yml`

## Paste Service

Long command output (exceeding `max-output-length`) is automatically:

1. **Uploaded** to the configured paste service (default: paste.myst.rs, 1-day expiry)
2. **Saved to file** if upload fails or is disabled (`plugins/ServerManager/pastes/`)

The paste service URL and format can be customized in `config.yml`.

## Security Considerations

> [!WARNING]
> This plugin grants **shell access to OP players**. Only grant permissions to trusted staff.

### Package Management Requires Elevated Privileges

> [!NOTE]
> This feature is disabled by default. Enable it by setting `package-management-enabled` to true in your config.yml.
>
> Enable it **only** if you need `/system install` functionality.

The `/system install` command runs package manager commands with root/admin privileges:

- **Linux**: Uses `sudo`, so the Minecraft server user needs sudo access. For unattended operation, configure [passwordless sudo](#creating-a-dedicated-user) for your package manager.
- **Windows**: Run the server as Administrator, or ensure winget/choco can install without elevation prompts.
- **macOS**: Homebrew may require sudo depending on installation location.

### Creating a Dedicated User

Create a dedicated user account with limited sudo access. We'll call it `minecraft`, for demonstration purposes:

```bash
# Create dedicated system user
sudo useradd -r -m -s /bin/bash minecraft

# Create sudoers file
sudo visudo -f /etc/sudoers.d/servermanager
```

Add this line to allow passwordless package manager access:

```
minecraft ALL=(ALL) NOPASSWD: /usr/bin/apt, /usr/bin/dnf, /usr/bin/pacman
```

**Never run the Minecraft server as root.** Use a dedicated user with minimal privileges.

## Building from Source

```bash
git clone https://github.com/walker84837/ServerManager.git
cd ServerManager
./gradlew shadowJar
```

The built JAR will be in `build/libs/`.

**Version Flags**:
- `./gradlew shadowJar -Pver=v1.0` -> `ServerManager-1.0.jar`
- `./gradlew shadowJar -Pver=v1.0-RC-1` -> `ServerManager-1.0-SNAPSHOT.jar`
- No `-Pver` flag -> timestamp-based snapshot

## License

This project is licensed under the **GNU Lesser General Public License v3.0** (LGPL-3.0). See the [LICENSE](LICENSE) file for details.

## Support

- **Issues**: <https://github.com/walker84837/ServerManager/issues>
- **Discussions**: <https://github.com/walker84837/ServerManager/discussions>

# PlexKillstreaks

A lightweight, configurable killstreak plugin for Paper servers.

PlexKillstreaks tracks player killstreaks, saves player data using SQLite, announces configurable milestones, and provides a clean `/streak` interface.

## ✨ Features

- 🔥 Current killstreak tracking
- 🏆 Highest killstreak tracking
- ⚡ Configurable killstreak milestones
- 📢 Configurable milestone announcements
- 🔊 Configurable sounds
- 📊 Configurable streak progress bar
- 🎯 Next milestone tracking
- 💬 Clean `/streak` interface
- 🔄 `/streaks` alias
- ⚙️ `/streakreload`
- 🗃️ SQLite data persistence
- ⚡ Asynchronous database handling
- 🔌 PlaceholderAPI support
- 🎨 Fully configurable messages and branding
- 🛡️ Safe player loading and shutdown handling
- 📦 Paper 1.21+

## 🎮 Commands

| Command | Description | Permission |
|---|---|---|
| `/streak` | View your current and highest killstreak | None |
| `/streaks` | Alias for `/streak` | None |
| `/streakreload` | Reload the plugin configuration | `plexkillstreaks.reload` |

## 🔐 Permissions

| Permission | Description | Default |
|---|---|---|
| `plexkillstreaks.reload` | Allows the player to reload the configuration | OP |

`/streak` and `/streaks` are available to everyone by default.

## 🔌 PlaceholderAPI

PlexKillstreaks supports PlaceholderAPI.

### Available Placeholders

```text
%plexkillstreaks_current%
%plexkillstreaks_highest%
%plexkillstreaks_next_milestone%
%plexkillstreaks_progress%
%plexkillstreaks_is_active%
%plexkillstreaks_loaded%
```

## 📋 Requirements

- Paper 1.21+
- Java 21
- PlaceholderAPI is optional

## 📥 Installation

1. Download the latest PlexKillstreaks `.jar`.
2. Place the `.jar` inside your server's `plugins` folder.
3. Start or restart your server.
4. Open `plugins/PlexKillstreaks/config.yml`.
5. Customize the plugin to your server.
6. Restart the server or use `/streakreload`.

## ⚙️ Configuration

PlexKillstreaks is designed to work on any Paper server and does not contain server-specific branding.

The following can be customized through `config.yml`:

- Plugin prefix
- Messages
- Killstreak milestones
- Milestone announcements
- Streak-ended announcements
- Sounds
- Action bar
- Progress bar
- Branding
- Other plugin settings

## 💾 Data Storage

PlexKillstreaks uses SQLite to store player killstreak data.

Player data includes:

- Current killstreak
- Highest killstreak

Database operations are handled asynchronously to minimize impact on the server thread.

## 🛠️ Building From Source

### Requirements

- Java 21
- Git
- No system-wide Gradle installation is required

The project includes the Gradle Wrapper.

### Clone the repository

```bash
git clone https://github.com/ApplexDev/PlexKillstreaks.git
cd PlexKillstreaks
```

### Build

On Windows:

```powershell
.\gradlew.bat clean build
```

On Linux/macOS:

```bash
./gradlew clean build
```

The compiled plugin will be located in:

```text
build/libs/
```

## 🧑‍💻 Development

PlexKillstreaks is written in Java and built using Gradle.

### Project Structure

```text
PlexKillstreaks/
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── plexdev/
│       │           └── plexkillstreaks/
│       └── resources/
│           ├── config.yml
│           └── plugin.yml
├── gradle/
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
├── .gitignore
├── LICENSE
└── README.md
```

## 🐛 Issues & Suggestions

Found a bug or have an idea for PlexKillstreaks?

Please open an issue on GitHub:

https://github.com/ApplexDev/PlexKillstreaks/issues

When reporting a bug, include:

- Minecraft version
- Paper version
- PlexKillstreaks version
- Relevant console errors
- Steps to reproduce the issue

## 📜 License

PlexKillstreaks is licensed under the MIT License.

See [`LICENSE`](LICENSE) for the full license text.

## 👤 Author

**Plex**

GitHub:

https://github.com/ApplexDev

## 📦 Links

- Source Code: https://github.com/ApplexDev/PlexKillstreaks
- Issues: https://github.com/ApplexDev/PlexKillstreaks/issues

---

Made with ❤️ by Plex.

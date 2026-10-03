# PlexKillstreaks

> Lightweight, configurable killstreak tracking for modern Paper servers.

PlexKillstreaks tracks current and highest player killstreaks, announces configurable milestones, exposes PlaceholderAPI values, and stores player data using SQLite.

## Features

- Current and highest killstreak tracking
- Configurable milestones and announcements
- Configurable sounds, action bar, and progress display
- SQLite persistence
- Asynchronous database handling
- Optional PlaceholderAPI integration
- Reloadable configuration
- Paper 1.21+
- Java 21

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/streak` | View your current and highest killstreak | `plexkillstreaks.streak` |
| `/streaks` | Alias for `/streak` | `plexkillstreaks.streak` |
| `/streakreload` | Reload the plugin configuration | `plexkillstreaks.reload` |

## Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `plexkillstreaks.streak` | Everyone | View killstreak information |
| `plexkillstreaks.reload` | OP | Reload the configuration |

## PlaceholderAPI

PlaceholderAPI is optional.

```text
%plexkillstreaks_current%
%plexkillstreaks_highest%
%plexkillstreaks_next_milestone%
%plexkillstreaks_progress%
%plexkillstreaks_is_active%
%plexkillstreaks_loaded%
```

## Requirements

- Paper 1.21+
- Java 21
- PlaceholderAPI when placeholder support is required

## Installation

1. Download the latest PlexKillstreaks JAR.
2. Place it in the server's `plugins` folder.
3. Start or restart the server.
4. Configure `plugins/PlexKillstreaks/config.yml`.
5. Use `/streakreload` after supported configuration changes.

## Building From Source

The Gradle Wrapper is included.

### Windows

```powershell
.\gradlew.bat clean build
```

### Linux / macOS

```bash
./gradlew clean build
```

Build output is written to `build/libs/`.

## Documentation and Support

- [PlexDocs](https://github.com/PlexStudios/PlexDocs)
- [Issues](https://github.com/PlexStudios/PlexKillstreaks/issues)
- [Contributing](CONTRIBUTING.md)
- [Support](SUPPORT.md)

## License

PlexKillstreaks is licensed under the MIT License. See [LICENSE](LICENSE).

## Plex Studios

PlexKillstreaks is developed by **Applex** as part of the **Plex Studios** plugin ecosystem.

Website: [applex.oriko.lk](https://applex.oriko.lk)

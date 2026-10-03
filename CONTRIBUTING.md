# Contributing to PlexKillstreaks

Thanks for wanting to improve PlexKillstreaks.

## Before You Start

For significant behavioral changes, open an issue first so the scope can be discussed before substantial work begins.

For bug fixes, include a clear reproduction whenever possible.

## Development Requirements

- Java 21
- Git
- The included Gradle Wrapper

## Build

Windows:

```powershell
.\gradlew.bat clean build
```

Linux or macOS:

```bash
./gradlew clean build
```

## Pull Requests

Keep pull requests focused on one problem or feature.

Please make sure that:

- The project builds successfully.
- Existing behavior is preserved unless intentionally changed.
- New configuration remains clear and practical.
- No unnecessary dependencies are introduced.
- Player-facing behavior remains configurable where appropriate.
- Documentation is updated when commands, permissions, configuration, or placeholders change.

Explain what changed, why it changed, and how it was tested.

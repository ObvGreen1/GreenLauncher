# GreenLauncher

A lightweight Minecraft launcher built with JavaFX. Manages multiple instances, downloads game files and Java runtimes, and launches with Fabric or Forge.

Not affiliated with Mojang or Microsoft. Minecraft is a trademark of Mojang AB.

## Features

- Multiple instances with independent versions, mods, and saves
- Fabric and Forge loader support
- Modrinth integration for browsing and installing mods
- Automatic Java runtime download and management
- Offline accounts with deterministic UUIDs
- Dark and light themes
- Per-instance and global memory allocation
- Optional SOCKS5 / HTTP proxy support
- Desktop shortcut launch via `--launch <instance-id>`
- Standalone Windows build — no Java installation required

## For Users

### Download

Grab the latest release from the [Releases page](../../releases) and download the `GreenLauncher-1.0.0-windows.zip` asset.

### Install

1. Unzip the archive anywhere you have write access (for example `C:\Games\GreenLauncher`).
2. Double-click `GreenLauncher.exe`.

The first launch creates its data directory and downloads the assets it needs.

> The `.exe` depends on the `app/` and `runtime/` folders next to it. Do not move or rename the `.exe` on its own — keep the whole extracted folder together.

### Requirements

Windows 10 or newer, 64-bit. No separate Java installation needed — the runtime is bundled.

### Where your data lives

All launcher data goes to `%APPDATA%\GreenLauncher`:

```
%APPDATA%\GreenLauncher\
├── accounts.json       account list
├── settings.json       preferences
├── instances\          one folder per instance
│   └── <id>\
│       ├── instance.json
│       └── minecraft\
│           ├── mods\  saves\  resourcepacks\  shaderpacks\
└── runtimes\           downloaded Java runtimes
```

Each instance keeps its own `minecraft` folder, so mods and saves never leak between instances.

## For Developers

### Requirements

- JDK 21 or newer (the build targets release 21)
- [`jpackage`](https://docs.oracle.com/en/java/javase/21/jdk/jpackage/) for producing the Windows binary — ships with the JDK
- WiX Toolset 3, only if you want an `.msi` installer

### Build from source

```bash
git clone https://github.com/ObvGreen/GreenLauncher.git
cd GreenLauncher
```

Run the app directly with a Gradle-launched JVM:

```bash
./gradlew run          # Linux / macOS
gradlew.bat run        # Windows
```

Run the tests:

```bash
./gradlew test
```

### Package the Windows executable

```bash
./gradlew packageZip
```

This runs `installDist`, then `jpackage --type app-image`, then zips the result. Output lands in `dist/`:

- `dist/GreenLauncher/` — the unpacked app image with `GreenLauncher.exe`
- `dist/GreenLauncher-1.0.0-windows.zip` — the distributable archive

For an MSI installer, install WiX Toolset 3 and add a `jpackage --type msi` invocation. Note that the app image is unsigned, so Windows SmartScreen will warn on first run.

### Project layout

```
src/main/java/me/obvgreen/
├── Launch.java              bootstrap main class (see below)
├── Main.java                JavaFX Application, stage setup
├── core/
│   ├── account/             accounts.json load/save, offline UUIDs
│   ├── download/            version manifests, game file download
│   ├── instance/            instance model and store
│   ├── java/                Java runtime download and resolution
│   ├── launch/              process spawning, log capture
│   ├── loader/              Fabric and Forge installation
│   ├── modrinth/            Modrinth search and install
│   ├── mojang/              Mojang version API
│   └── settings/            preferences model and store
├── ui/                      JavaFX views, dialogs, theming
└── util/                    HTTP, JSON, path helpers
```

`Launch` exists because `Main` extends `Application`, and the JavaFX launcher refuses to run a subclassed main class. `Launch.main` is the real entry point declared in `build.gradle.kts`.

## Contributing

1. Fork the repository.
2. Create a branch: `git checkout -b my-change`.
3. Make your change and run `./gradlew test`.
4. Commit with a clear message and open a pull request.

## License

MIT — see [LICENSE](LICENSE).

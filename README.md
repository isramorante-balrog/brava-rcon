# Brava - Complete Rust RCON

An open-source Android admin panel for Rust game servers. It connects over WebRCON, so you can
run your server from your phone without logging into the game.

Not affiliated with or endorsed by Facepunch Studios. "Rust" is a trademark of Facepunch Studios.

## What it does today

- Multiple saved servers, stored encrypted on the device
- Dashboard: players, queue, server FPS, memory, uptime
- Quick command buttons you can edit (airdrop, patrol heli, anything you add)
- Players: search, kick, ban with reason, mute, give items, private notes, Steam profile
- Ban list with unban
- Live console with filters, command history
- Live chat, with replies as SERVER
- Plugin list with load, unload and reload (Carbon)
- Hosting panel (Pterodactyl): start, restart, stop, RAM, CPU and disk, even when RCON is down
- Permissions: show, grant and revoke for groups and players (Carbon)
- Player map on the game grid
- App lock with fingerprint, face or the phone PIN
- Alerts and scheduled commands through the companion plugin (see below)
- English and Spanish, picked from the phone language

## Planned

- Server-down alert (the plugin cannot report its own server being off)
- Kits editor, terrain image behind the player map

## Install

Download the newest APK from the [Releases](../../releases) page and open it on Android 8.0 or newer.
You will need to allow installs from your browser or file manager.

## Connect

1. Add a server with its IP, RCON port and RCON password.
2. Optional: add your hosting panel address, the server ID and a panel API key.

Rust's RCON is not encrypted. Avoid using it on public Wi-Fi.

## Companion plugin (alerts and schedules)

Copy `server-plugin/BravaCompanion.cs` into `carbon/plugins` (or `oxide/plugins`). Then open
More > Alerts and schedules in the app.

Alerts are delivered by [ntfy](https://ntfy.sh), a free open-source push service: install the ntfy
app and subscribe to the topic you set. Anyone who knows the topic can read the alerts, so pick a
long random one, or point the plugin at your own ntfy server with `brava.server`.

## Build

    ./gradlew assembleDebug

Needs JDK 17 and the Android SDK (API 35). Every push to `main` is built by GitHub Actions.

## License

GPL-3.0. See [LICENSE](LICENSE).

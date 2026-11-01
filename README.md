# TBans

A lightweight, simple, and customizable ban plugin for Velocity proxies, featuring MiniMessage formatting and multi-language support.

## Features
- **Lightweight:** Designed specifically for Velocity proxies without unnecessary bloat.
- **Multi-Language Support:** Easily customizable messages. Includes `en_EN` and `de_DE` translations by default.
- **Rich Text Formatting:** Fully supports MiniMessage for creating detailed gradients, hover events, and beautiful chat components.
- **Broadcast System:** Staff members can receive notifications when punishments are executed.
- **Staff Protection:** Allows securing specified players or staff members from being punished by others.
- **Audit System:** Keep staff accountable via `/blame`, and easily view a player's previous infractions via `/history`.
- **Automated Builds:** Every push to `main` automatically builds and publishes a release with incremental versioning (e.g., `1.0-SNAPSHOT-1`).

## Commands & Permissions

| Command | Permission | Description |
| ------- | ---------- | ----------- |
| `/ban <player> <time> <reason>` | `tbans.ban` | Bans a player from the network for a specified time. |
| `/banip <player/ip> <time> <reason>` | `tbans.banip` | Bans an IP address. Can target a player's known IPs or a raw IP. |
| `/unban <player> [reason]` | `tbans.unban` | Unbans a previously banned player. |
| `/kick <player> [reason]` | `tbans.kick` | Kicks an online player from the proxy network. |
| `/alts <player>` | `tbans.command.alts` | Search for linked accounts (alts). |
| `/tbans` | None | View plugin version and build information. |
| `/tbans reload` / `/tbans migrate [from] [to]` | `tbans.admin` | Reload config, or copy data between JSON and MongoDB storage. |
| `/history <player>` (or `/checkban`) | `tbans.history` | Check a player's ban status and view their punishment history. |
| `/blame <staff>` | `tbans.blame` | See the latest actions (bans, unbans) executed by a particular staff member. |

## Alt Detection

TBans includes a privacy-preserving alt-account detection system that identifies potential linked accounts without storing sensitive information.

### How it works
1. **IP Hashing:** When a player joins, their IP address is hashed using **SHA-256** combined with a unique **randomly generated salt** (stored in `config.yml`). Raw IP addresses are **never stored** on disk or in memory.
2. **Link Window:** Accounts are considered "linked" if they have connected from the same hashed IP within a configurable timeframe (`alt-link-days`, default 7 days).
3. **Recursive Search:** The `/alts` command performs a recursive search up to **20 hops**. This can identify "chains" of accounts (e.g., if Player A and Player B share IP 1, and Player B and Player C share IP 2, the system will identify all three as potentially linked).

### Configuration
In `config.yml`, you can customize:
- `salt`: The security salt for IP hashing. Changing this will invalidate all existing links (useful for a "reset").
- `alt-link-days`: The maximum time gap between connections on the same IP to be considered a direct link.

## Storage (JSON / MongoDB)

By default TBans stores everything in JSON files in the plugin folder. You can switch to MongoDB in `config.yml`:

```yaml
storage-type: mongodb        # json | mongodb
mongodb-uri: mongodb://user:password@localhost:27017
mongodb-database: tbans
```

If MongoDB can't be reached on startup or reload, TBans logs an error and falls back to the JSON files, so bans are still enforced.

### Migrating from JSON to MongoDB
1. Set `mongodb-uri` and `mongodb-database` in `config.yml` (keep `storage-type: json` for now) and run `/tbans reload`.
2. Run `/tbans migrate` (same as `/tbans migrate json mongodb`). This copies all bans, IP bans, names and IP logs to MongoDB. Running it again is safe: entries are overwritten, not duplicated.
3. Set `storage-type: mongodb` and run `/tbans reload`.

`/tbans migrate mongodb json` works the other way round, e.g. for a backup. Both require `tbans.admin`.

## JSON API

TBans features a minimalistic JSON API for external integrations, allowing you to manage bans, kicks, and view player history or linked accounts remotely.

**Detailed documentation can be found here: [API.md](API.md)**

## Additional Permissions

- `tbans.notify` : Players with this permission will receive broadcast messages whenever a player is banned, unbanned, or kicked.
- `tbans.god` : Players with this permission are protected and cannot be banned or kicked by other players. Useful for administrators.

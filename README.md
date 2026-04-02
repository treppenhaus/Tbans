# TBans

A lightweight, simple, and customizable ban plugin for Velocity proxies, featuring MiniMessage formatting and multi-language support.

## Features
- **Lightweight:** Designed specifically for Velocity proxies without unnecessary bloat.
- **Multi-Language Support:** Easily customizable messages. Includes `en_EN` and `de_DE` translations by default.
- **Rich Text Formatting:** Fully supports MiniMessage for creating detailed gradients, hover events, and beautiful chat components.
- **Broadcast System:** Staff members can receive notifications when punishments are executed.
- **Staff Protection:** Allows securing specified players or staff members from being punished by others.
- **Audit System:** Keep staff accountable via `/blame`, and easily view a player's previous infractions via `/history`.

## Commands & Permissions

| Command | Permission | Description |
| ------- | ---------- | ----------- |
| `/ban <player> <time> <reason>` | `tbans.ban` | Bans a player from the network for a specified time. |
| `/unban <player> [reason]` | `tbans.unban` | Unbans a previously banned player. |
| `/kick <player> [reason]` | `tbans.kick` | Kicks an online player from the proxy network. |
| `/history <player>` (or `/checkban`) | `tbans.history` | Check a player's ban status and view their punishment history. |
| `/blame <staff>` | `tbans.blame` | See the latest actions (bans, unbans) executed by a particular staff member. |

## Additional Permissions

- `tbans.notify` : Players with this permission will receive broadcast messages whenever a player is banned, unbanned, or kicked.
- `tbans.god` : Players with this permission are protected and cannot be banned or kicked by other players. Useful for administrators.

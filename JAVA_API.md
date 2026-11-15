# TBans Java API

Velocity plugins running on the same proxy as TBans can use its native Java API. Unlike the
[JSON API](API.md) it needs no HTTP server or token, and it reads straight from TBans' in-memory
data, so calls are fast and never block on network I/O.

The API is **read-only**: it looks up bans and history. To ban, unban or kick, use the commands or
the JSON API.

## Setup

### 1. Add the dependency

TBans is available through [JitPack](https://jitpack.io/#treppenhaus/Tbans). Use a release tag
(e.g. `v1.0-SNAPSHOT-42`, see [Releases](https://github.com/treppenhaus/Tbans/releases)) or a commit hash as the version.

Use `provided` scope: TBans is installed on the proxy, so its classes must **not** be shaded into your plugin.

**Maven**
```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.treppenhaus</groupId>
    <artifactId>Tbans</artifactId>
    <version>VERSION</version>
    <scope>provided</scope>
    <exclusions>
        <exclusion>
            <groupId>*</groupId>
            <artifactId>*</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

**Gradle (Kotlin DSL)**
```kotlin
repositories { maven("https://jitpack.io") }
dependencies {
    compileOnly("com.github.treppenhaus:Tbans:VERSION") { isTransitive = false }
}
```

### 2. Declare the plugin dependency

Velocity only lets your plugin see TBans' classes, and only loads TBans first, if you declare a
dependency on the plugin id `tbans`:

```java
@Plugin(id = "myplugin", dependencies = {
        @Dependency(id = "tbans", optional = true)   // or optional = false if TBans is required
})
public class MyPlugin { ... }
```

### 3. Get the API

```java
import eu.treppi.tbans.api.TbansApi;
import eu.treppi.tbans.api.TbansProvider;

if (TbansProvider.isAvailable()) {
    TbansApi tbans = TbansProvider.get();
}
```

TBans registers the API in its constructor, so it is available from your plugin's constructor
onwards. `TbansProvider.get()` throws an `IllegalStateException` if TBans isn't loaded.

With an optional dependency, only touch TBans classes after checking that the plugin is installed,
e.g. `proxyServer.getPluginManager().isLoaded("tbans")`. Otherwise you get a `NoClassDefFoundError`.

## API Reference

All types are in the `eu.treppi.tbans.api` package. Every method is thread-safe and returns
immutable snapshots.

### `TbansApi`

| Method | Description |
| ------ | ----------- |
| `Optional<Ban> getBanByCode(String code)` | Finds a ban by its code. Input is normalized (case-insensitive, spaces / dashes ignored). Can return expired, lifted or IP bans; check `active()` and `ipBan()`. |
| `Optional<Ban> getActiveBan(UUID player)` | The ban currently in force for the player, or empty. Does not check IP bans. |
| `boolean isBanned(UUID player)` | Whether the player is currently banned. Does not check IP bans. |
| `List<Punishment> getHistory(UUID player)` | All bans, unbans and kicks of the player, oldest first. |

### `Ban` (record)

| Field | Description |
| ----- | ----------- |
| `code()` | The ban code. |
| `target()` | The banned player's UUID, or `null` for IP bans. |
| `ipBan()` | `true` if this is an IP ban. |
| `executor()` | Who issued it. `00000000-0000-0000-0000-000000000000` means console / JSON API. |
| `timestamp()` | When it was issued (epoch millis). |
| `expiry()` | When it expires (epoch millis), `-1` if permanent. `isPermanent()` is a shortcut. |
| `reason()` | The ban reason. |
| `active()` | `true` if, when the snapshot was taken, this exact ban was in force: not expired, not lifted by an unban and not replaced by a newer ban. |

### `Punishment` (record)

One history entry: `type()` (`PunishmentType.BAN`, `UNBAN` or `KICK`), `code()` (bans only, otherwise
`null`), `target()`, `executor()`, `timestamp()`, `expiry()` (`-1` for permanent bans and non-bans) and `reason()`.

### `BanCodes`

Static helpers that work without TBans being loaded (as long as the classes are on the classpath):

| Method | Description |
| ------ | ----------- |
| `normalize(String input)` | Strips whitespace and dashes and upper-cases the input. |
| `isValidFormat(String input)` | `true` if the input looks like a ban code (current 8 character or legacy 6 hex format). Doesn't check that it exists. |
| `isLegacyFormat(String input)` | `true` for legacy 6 hex character codes. |

See [Ban Codes](README.md#ban-codes) for the format.

## Example: verifying a banned player by their ban code

A Discord bot on the proxy lets banned players link their account by entering the ban code from
their ban screen instead of an in-game code:

```java
TbansApi tbans = TbansProvider.get();
String input = event.getOption("code").getAsString();

if (!BanCodes.isValidFormat(input)) {
    reply("That is not a valid ban code.");
    return;
}

Optional<Ban> result = tbans.getBanByCode(input);
if (result.isEmpty() || result.get().ipBan()) {
    reply("Unknown ban code.");
    return;
}

Ban ban = result.get();
if (!ban.active()) {
    reply("This ban is no longer active. Join the server and use /verify instead.");
    return;
}

UUID player = ban.target();
// Your own checks, e.g. that this player is not verified already, then store the link.
```

Ban codes are shown to staff and players often share screenshots of their ban screen, so treat a
ban code as proof of identity only together with checks like these. Also rate-limit attempts.

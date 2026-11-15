package eu.treppi.tbans.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import eu.treppi.tbans.api.BanCodes;
import eu.treppi.tbans.storage.Storage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BanManager {
    private Map<UUID, List<BanEvent>> playerEvents = new HashMap<>();
    private Map<String, List<BanEvent>> ipEvents = new HashMap<>();
    private Map<String, UUID> nameToUuid = new HashMap<>();
    private Map<UUID, String> uuidToName = new HashMap<>();
    // Correctly-cased names (names.json only stores lowercased names)
    private Map<UUID, String> displayNames = new ConcurrentHashMap<>();
    // Normalized ban code -> owner, for O(1) code lookups and uniqueness checks.
    // Guarded by "this" like playerEvents / ipEvents.
    private final Map<String, CodeEntry> codeIndex = new HashMap<>();
    private Storage storage;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final SecureRandom CODE_RANDOM = new SecureRandom();

    public BanManager(Storage storage) {
        setStorage(storage);
    }

    /** Switches the storage backend and reloads all data from it. */
    public synchronized void setStorage(Storage storage) {
        this.storage = storage;
        playerEvents = storage.loadPlayerEvents();
        ipEvents = storage.loadIpEvents();
        rebuildCodeIndex();
        loadNames();
    }

    public synchronized BanEvent banPlayer(UUID target, UUID executor, long durationMillis, String reason) {
        long timestamp = System.currentTimeMillis();
        long expiry = (durationMillis == -1) ? -1 : (timestamp + durationMillis);
        String code = generateUniqueCode();
        BanEvent event = new BanEvent(BanEvent.Type.BAN, target, executor, timestamp, expiry, reason, code);
        addEvent(target, event);
        return event;
    }

    public synchronized BanEvent unbanPlayer(UUID target, UUID executor, String reason) {
        long timestamp = System.currentTimeMillis();
        BanEvent event = new BanEvent(BanEvent.Type.UNBAN, target, executor, timestamp, -1, reason);
        addEvent(target, event);
        return event;
    }

    public synchronized BanEvent kickPlayer(UUID target, UUID executor, String reason) {
        long timestamp = System.currentTimeMillis();
        BanEvent event = new BanEvent(BanEvent.Type.KICK, target, executor, timestamp, -1, reason);
        addEvent(target, event);
        return event;
    }

    private void addEvent(UUID target, BanEvent event) {
        List<BanEvent> events = playerEvents.computeIfAbsent(target, k -> new ArrayList<>());
        events.add(event);
        indexCode(event, new CodeLookupResult(CodeLookupResult.Type.UUID, target.toString()));
        storage.savePlayerEvents(target, events, playerEvents);
    }

    public synchronized BanEvent banIp(String ipHash, UUID executor, long durationMillis, String reason) {
        long timestamp = System.currentTimeMillis();
        long expiry = (durationMillis == -1) ? -1 : (timestamp + durationMillis);
        String code = generateUniqueCode();
        BanEvent event = new BanEvent(BanEvent.Type.BAN, null, executor, timestamp, expiry, reason, code);
        addIpEvent(ipHash, event);
        return event;
    }

    public synchronized BanEvent unbanIp(String ipHash, UUID executor, String reason) {
        long timestamp = System.currentTimeMillis();
        BanEvent event = new BanEvent(BanEvent.Type.UNBAN, null, executor, timestamp, -1, reason);
        addIpEvent(ipHash, event);
        return event;
    }

    private void addIpEvent(String ipHash, BanEvent event) {
        List<BanEvent> events = ipEvents.computeIfAbsent(ipHash, k -> new ArrayList<>());
        events.add(event);
        indexCode(event, new CodeLookupResult(CodeLookupResult.Type.IP_HASH, ipHash));
        storage.saveIpEvents(ipHash, events, ipEvents);
    }

    public synchronized boolean isIpBanned(String ipHash) {
        return isInForce(getLatestIpBan(ipHash));
    }

    public synchronized BanEvent getLatestIpBan(String ipHash) {
        return latestBanIn(ipEvents.get(ipHash));
    }

    public synchronized boolean isBanned(UUID uuid) {
        return isInForce(getLatestBan(uuid));
    }

    public synchronized BanEvent getLatestBan(UUID uuid) {
        return latestBanIn(playerEvents.get(uuid));
    }

    /**
     * Returns the newest BAN in a history unless an UNBAN came after it. KICKs are ignored.
     * The returned ban may be expired; see {@link #isInForce(BanEvent)}.
     */
    private static BanEvent latestBanIn(List<BanEvent> events) {
        if (events == null)
            return null;
        for (int i = events.size() - 1; i >= 0; i--) {
            BanEvent event = events.get(i);
            if (event.getType() == BanEvent.Type.BAN)
                return event;
            if (event.getType() == BanEvent.Type.UNBAN)
                return null;
        }
        return null;
    }

    private static boolean isInForce(BanEvent ban) {
        if (ban == null)
            return false;
        return ban.getExpiry() == -1 || ban.getExpiry() > System.currentTimeMillis();
    }

    /**
     * Whether this exact ban is the one currently in force for its owner (not expired, not lifted
     * and not superseded by a newer ban).
     */
    public synchronized boolean isActiveBan(BanEvent ban, CodeLookupResult owner) {
        if (ban == null || owner == null)
            return false;
        BanEvent latest = owner.type == CodeLookupResult.Type.UUID
                ? latestBanIn(playerEvents.get(UUID.fromString(owner.value)))
                : latestBanIn(ipEvents.get(owner.value));
        return latest == ban && isInForce(ban);
    }

    public synchronized List<BanEvent> getEventsByExecutor(UUID executorUuid) {
        List<BanEvent> events = new ArrayList<>();
        for (List<BanEvent> playerHistory : playerEvents.values()) {
            for (BanEvent event : playerHistory) {
                if (executorUuid.equals(event.getExecutorUUID())) {
                    events.add(event);
                }
            }
        }
        return events;
    }

    public synchronized boolean isCodeInUse(String code) {
        String normalized = BanCodes.normalize(code);
        return normalized != null && codeIndex.containsKey(normalized);
    }

    public static class CodeLookupResult {
        public enum Type {
            UUID, IP_HASH
        }

        public final Type type;
        public final String value;

        public CodeLookupResult(Type type, String value) {
            this.type = type;
            this.value = value;
        }
    }

    /** A ban together with the player / IP hash it belongs to. */
    public record CodeEntry(CodeLookupResult owner, BanEvent ban) {
    }

    /** Finds the owner of a ban code (case-insensitive, spaces / dashes ignored), or null. */
    public synchronized CodeLookupResult lookupByCode(String code) {
        CodeEntry entry = findByCode(code);
        return entry == null ? null : entry.owner();
    }

    /** Finds the ban with this code and its owner (case-insensitive, spaces / dashes ignored), or null. */
    public synchronized CodeEntry findByCode(String code) {
        String normalized = BanCodes.normalize(code);
        return normalized == null ? null : codeIndex.get(normalized);
    }

    /** @return a copy of the player's history, oldest first */
    public synchronized List<BanEvent> getEvents(UUID uuid) {
        if (uuid == null)
            return new ArrayList<>();
        return new ArrayList<>(playerEvents.getOrDefault(uuid, List.of()));
    }

    /** @return a copy of the IP hash's history, oldest first */
    public synchronized List<BanEvent> getIpEvents(String ipHash) {
        if (ipHash == null)
            return new ArrayList<>();
        return new ArrayList<>(ipEvents.getOrDefault(ipHash, List.of()));
    }

    /** Generates a new random ban code that no existing ban uses. Caller must hold the lock. */
    private String generateUniqueCode() {
        char[] chars = new char[BanCodes.LENGTH];
        String code;
        do {
            for (int i = 0; i < chars.length; i++) {
                chars[i] = BanCodes.ALPHABET.charAt(CODE_RANDOM.nextInt(BanCodes.ALPHABET.length()));
            }
            code = new String(chars);
        } while (codeIndex.containsKey(code));
        return code;
    }

    private void indexCode(BanEvent event, CodeLookupResult owner) {
        if (event.getCode() != null) {
            codeIndex.put(BanCodes.normalize(event.getCode()), new CodeEntry(owner, event));
        }
    }

    /**
     * Rebuilds the code index from the loaded histories. Bans stored without a code (very old data)
     * get a new one, which is saved so it stays the same across restarts.
     */
    private void rebuildCodeIndex() {
        codeIndex.clear();
        playerEvents.forEach((uuid, events) -> {
            CodeLookupResult owner = new CodeLookupResult(CodeLookupResult.Type.UUID, uuid.toString());
            events.forEach(event -> indexCode(event, owner));
        });
        ipEvents.forEach((hash, events) -> {
            CodeLookupResult owner = new CodeLookupResult(CodeLookupResult.Type.IP_HASH, hash);
            events.forEach(event -> indexCode(event, owner));
        });

        playerEvents.forEach((uuid, events) -> {
            if (assignMissingCodes(events, new CodeLookupResult(CodeLookupResult.Type.UUID, uuid.toString()))) {
                storage.savePlayerEvents(uuid, events, playerEvents);
            }
        });
        ipEvents.forEach((hash, events) -> {
            if (assignMissingCodes(events, new CodeLookupResult(CodeLookupResult.Type.IP_HASH, hash))) {
                storage.saveIpEvents(hash, events, ipEvents);
            }
        });
    }

    private boolean assignMissingCodes(List<BanEvent> events, CodeLookupResult owner) {
        boolean changed = false;
        for (BanEvent event : events) {
            if (event.getType() == BanEvent.Type.BAN && event.getCode() == null) {
                event.setCode(generateUniqueCode());
                indexCode(event, owner);
                changed = true;
            }
        }
        return changed;
    }

    // Name Resolution Cache
    public void updateNameCache(UUID uuid, String name) {
        if (name == null || name.isEmpty())
            return;
        nameToUuid.put(name.toLowerCase(), uuid);
        uuidToName.put(uuid, name);
        displayNames.put(uuid, name);
        storage.saveName(uuid, name, nameToUuid, displayNames);
    }

    public UUID getUuidFromName(String name) {
        if (name == null)
            return null;
        return nameToUuid.get(name.toLowerCase());
    }

    public String getNameFromUuid(UUID uuid) {
        if (uuid == null)
            return "Unknown";
        if (uuid.equals(new UUID(0, 0)))
            return "Console";
        String display = displayNames.get(uuid);
        if (display != null)
            return display;
        return uuidToName.getOrDefault(uuid, uuid.toString());
    }

    /**
     * Resolves the current in-game name of a UUID. Uses the cache first, then
     * playerdb.co and finally the Mojang session server as a fallback.
     * Never completes exceptionally; falls back to {@link #getNameFromUuid(UUID)}.
     */
    public CompletableFuture<String> resolveName(UUID uuid) {
        if (uuid == null || uuid.equals(new UUID(0, 0))) {
            return CompletableFuture.completedFuture(getNameFromUuid(uuid));
        }
        String cached = displayNames.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return fetchNameFromPlayerDb(uuid)
                .thenCompose(name -> name != null ? CompletableFuture.completedFuture(name)
                        : fetchNameFromMojang(uuid))
                .thenApply(name -> {
                    if (name != null) {
                        updateNameCache(uuid, name);
                        return name;
                    }
                    return getNameFromUuid(uuid);
                })
                .exceptionally(ex -> getNameFromUuid(uuid));
    }

    private CompletableFuture<String> fetchNameFromPlayerDb(UUID uuid) {
        return HTTP_CLIENT.sendAsync(
                HttpRequest.newBuilder()
                        .uri(URI.create("https://playerdb.co/api/player/minecraft/" + uuid))
                        .timeout(Duration.ofSeconds(4))
                        .header("User-Agent", "Tbans Velocity Plugin")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
                    if (response.statusCode() != 200)
                        return (String) null;
                    try {
                        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                        if (json.get("success").getAsBoolean()) {
                            return json.getAsJsonObject("data")
                                    .getAsJsonObject("player")
                                    .get("username").getAsString();
                        }
                    } catch (Exception ignored) {
                    }
                    return null;
                }).exceptionally(ex -> null);
    }

    private CompletableFuture<String> fetchNameFromMojang(UUID uuid) {
        return HTTP_CLIENT.sendAsync(
                HttpRequest.newBuilder()
                        .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/"
                                + uuid.toString().replace("-", "")))
                        .timeout(Duration.ofSeconds(4))
                        .header("User-Agent", "Tbans Velocity Plugin")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
                    if (response.statusCode() != 200)
                        return (String) null;
                    try {
                        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                        if (json != null && json.has("name")) {
                            return json.get("name").getAsString();
                        }
                    } catch (Exception ignored) {
                    }
                    return null;
                }).exceptionally(ex -> null);
    }

    public CompletableFuture<UUID> resolveUuid(String name) {
        try {
            // Name lookup for UUIDs happens separately via resolveName()
            return CompletableFuture.completedFuture(UUID.fromString(name));
        } catch (IllegalArgumentException ignored) {
        }

        UUID cached = getUuidFromName(name);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return HTTP_CLIENT.sendAsync(
                HttpRequest.newBuilder()
                        .uri(URI.create("https://playerdb.co/api/player/minecraft/" + name))
                        .timeout(Duration.ofMillis(1500))
                        .header("User-Agent", "Tbans Velocity Plugin")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
                    if (response.statusCode() != 200)
                        return null;
                    try {
                        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                        if (json.get("success").getAsBoolean()) {
                            String uuidStr = json.getAsJsonObject("data")
                                    .getAsJsonObject("player")
                                    .get("raw_id").getAsString();

                            if (uuidStr.length() == 32) {
                                uuidStr = uuidStr.replaceFirst(
                                        "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                                        "$1-$2-$3-$4-$5");
                            }

                            UUID uuid = UUID.fromString(uuidStr);
                            String resolvedName = json.getAsJsonObject("data")
                                    .getAsJsonObject("player")
                                    .get("username").getAsString();
                            updateNameCache(uuid, resolvedName);
                            return uuid;
                        }
                    } catch (Exception ignored) {
                    }
                    return null;
                }).exceptionally(ex -> null);
    }

    public synchronized List<String> getBannedNames() {
        return playerEvents.keySet().stream()
                .filter(this::isBanned)
                .map(uuid -> uuidToName.getOrDefault(uuid, uuid.toString()))
                .collect(Collectors.toList());
    }

    public List<String> getAllCachedNames() {
        return new ArrayList<>(nameToUuid.keySet());
    }

    private void loadNames() {
        nameToUuid = storage.loadNameIndex();
        uuidToName = new HashMap<>();
        for (Map.Entry<String, UUID> entry : nameToUuid.entrySet()) {
            uuidToName.put(entry.getValue(), entry.getKey());
        }
        displayNames = new ConcurrentHashMap<>(storage.loadDisplayNames());
    }
}

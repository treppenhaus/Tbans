package eu.treppi.tbans.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class BanManager {
    private Map<UUID, List<BanEvent>> playerEvents = new HashMap<>();
    private Map<String, UUID> nameToUuid = new HashMap<>();
    private Map<UUID, String> uuidToName = new HashMap<>();
    private final File storageFile;
    private final File namesFile;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    public BanManager(Path dataDirectory) {
        if (!dataDirectory.toFile().exists()) {
            dataDirectory.toFile().mkdirs();
        }
        this.storageFile = dataDirectory.resolve("uuid-bans.json").toFile();
        this.namesFile = dataDirectory.resolve("names.json").toFile();
        loadBans();
        loadNames();
    }

    public void banPlayer(UUID target, UUID executor, long durationMillis, String reason) {
        long timestamp = System.currentTimeMillis();
        long expiry = (durationMillis == -1) ? -1 : (timestamp + durationMillis);
        addEvent(target, new BanEvent(BanEvent.Type.BAN, target, executor, timestamp, expiry, reason));
    }

    public void unbanPlayer(UUID target, UUID executor, String reason) {
        long timestamp = System.currentTimeMillis();
        addEvent(target, new BanEvent(BanEvent.Type.UNBAN, target, executor, timestamp, -1, reason));
    }

    public void kickPlayer(UUID target, UUID executor, String reason) {
        long timestamp = System.currentTimeMillis();
        addEvent(target, new BanEvent(BanEvent.Type.KICK, target, executor, timestamp, -1, reason));
    }

    private void addEvent(UUID target, BanEvent event) {
        playerEvents.computeIfAbsent(target, k -> new ArrayList<>()).add(event);
        saveBans();
    }

    public boolean isBanned(UUID uuid) {
        BanEvent latest = getLatestBan(uuid);
        if (latest == null) return false;
        
        if (latest.getExpiry() == -1) return true;
        return latest.getExpiry() > System.currentTimeMillis();
    }

    public BanEvent getLatestBan(UUID uuid) {
        if (!playerEvents.containsKey(uuid))
            return null;

        List<BanEvent> events = playerEvents.get(uuid);
        for (int i = events.size() - 1; i >= 0; i--) {
            BanEvent event = events.get(i);
            if (event.getType() == BanEvent.Type.BAN)
                return event;
            if (event.getType() == BanEvent.Type.UNBAN)
                return null;
            // Ignore KICK
        }
        return null;
    }

    public List<BanEvent> getEventsByExecutor(UUID executorUuid) {
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

    public List<BanEvent> getEvents(UUID uuid) {
        if (uuid == null) return new ArrayList<>();
        return playerEvents.getOrDefault(uuid, new ArrayList<>());
    }

    public Map<UUID, List<BanEvent>> getAllEvents() {
        return playerEvents;
    }

    // Name Resolution Cache
    public void updateNameCache(UUID uuid, String name) {
        if (name == null || name.isEmpty())
            return;
        nameToUuid.put(name.toLowerCase(), uuid);
        uuidToName.put(uuid, name);
        saveNames();
    }

    public UUID getUuidFromName(String name) {
        if (name == null)
            return null;
        return nameToUuid.get(name.toLowerCase());
    }

    public String getNameFromUuid(UUID uuid) {
        if (uuid == null) return "Unknown";
        if (uuid.equals(new UUID(0, 0))) return "Console";
        return uuidToName.getOrDefault(uuid, uuid.toString());
    }

    public CompletableFuture<UUID> resolveUuid(String name) {
        UUID cached = getUuidFromName(name);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return HTTP_CLIENT.sendAsync(
                HttpRequest.newBuilder()
                        .uri(URI.create("https://playerdb.co/api/player/minecraft/" + name))
                        .header("User-Agent", "Tbans Velocity Plugin")
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        ).thenApply(response -> {
            if (response.statusCode() != 200) return null;
            try {
                JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
                if (json.get("success").getAsBoolean()) {
                    String uuidStr = json.getAsJsonObject("data")
                            .getAsJsonObject("player")
                            .get("raw_id").getAsString();
                    
                    if (uuidStr.length() == 32) {
                        uuidStr = uuidStr.replaceFirst(
                                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                                "$1-$2-$3-$4-$5"
                        );
                    }
                    
                    UUID uuid = UUID.fromString(uuidStr);
                    updateNameCache(uuid, name);
                    return uuid;
                }
            } catch (Exception ignored) {}
            return null;
        });
    }

    public List<String> getBannedNames() {
        return playerEvents.keySet().stream()
                .filter(this::isBanned)
                .map(uuid -> uuidToName.getOrDefault(uuid, uuid.toString()))
                .collect(Collectors.toList());
    }

    public List<String> getAllCachedNames() {
        return new ArrayList<>(nameToUuid.keySet());
    }

    private void loadBans() {
        if (!storageFile.exists())
            return;
        try (Reader reader = new FileReader(storageFile)) {
            playerEvents = GSON.fromJson(reader, new TypeToken<Map<UUID, List<BanEvent>>>() {
            }.getType());
            if (playerEvents == null)
                playerEvents = new HashMap<>();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveBans() {
        try (Writer writer = new FileWriter(storageFile)) {
            GSON.toJson(playerEvents, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadNames() {
        if (!namesFile.exists())
            return;
        try (Reader reader = new FileReader(namesFile)) {
            nameToUuid = GSON.fromJson(reader, new TypeToken<Map<String, UUID>>() {
            }.getType());
            if (nameToUuid != null) {
                for (Map.Entry<String, UUID> entry : nameToUuid.entrySet()) {
                    uuidToName.put(entry.getValue(), entry.getKey());
                }
            } else {
                nameToUuid = new HashMap<>();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveNames() {
        try (Writer writer = new FileWriter(namesFile)) {
            GSON.toJson(nameToUuid, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

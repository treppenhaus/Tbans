package eu.treppi.tbans.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BanManager {
    private Map<UUID, List<BanEvent>> playerEvents = new HashMap<>();
    private final File storageFile;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public BanManager(Path dataDirectory) {
        if (!dataDirectory.toFile().exists()) {
            dataDirectory.toFile().mkdirs();
        }
        this.storageFile = dataDirectory.resolve("uuid-bans.json").toFile();
        loadBans();
    }

    public void banPlayer(UUID target, UUID executor, long durationMillis, String reason) {
        long timestamp = System.currentTimeMillis();
        long expiry = timestamp + durationMillis;
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
        if (!playerEvents.containsKey(uuid)) return false;

        List<BanEvent> events = playerEvents.get(uuid);
        if (events.isEmpty())
            return false;

        BanEvent lastEvent = events.get(events.size() - 1);
        if (lastEvent.getType() != BanEvent.Type.BAN)
            return false;

        // It is a BAN event
        if (System.currentTimeMillis() > lastEvent.getExpiry()) {
            return false;
        }

        return true;
    }

    public BanEvent getLatestBan(UUID uuid) {
        if (!playerEvents.containsKey(uuid)) return null;

        List<BanEvent> events = playerEvents.get(uuid);
        for (int i = events.size() - 1; i >= 0; i--) {
            BanEvent event = events.get(i);
            if (event.getType() == BanEvent.Type.BAN)
                return event;
            if (event.getType() == BanEvent.Type.UNBAN)
                return null;
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
        return playerEvents.getOrDefault(uuid, new ArrayList<>());
    }

    public Map<UUID, List<BanEvent>> getAllEvents() {
        return playerEvents;
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

    public static class BanEvent {
        public enum Type {
            BAN, UNBAN, KICK
        }

        private final Type type;
        private final UUID targetUUID;
        private final UUID executorUUID;
        private final long timestamp;
        private final long expiry;
        private final String reason;

        public BanEvent(Type type, UUID targetUUID, UUID executorUUID, long timestamp, long expiry, String reason) {
            this.type = type;
            this.targetUUID = targetUUID;
            this.executorUUID = executorUUID;
            this.timestamp = timestamp;
            this.expiry = expiry;
            this.reason = reason;
        }

        public Type getType() {
            return type;
        }

        public UUID getTargetUUID() {
            return targetUUID;
        }

        public UUID getExecutorUUID() {
            return executorUUID;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public long getExpiry() {
            return expiry;
        }

        public String getReason() {
            return reason;
        }
    }
}

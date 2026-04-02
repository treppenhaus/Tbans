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

public class BanManager {
    private Map<String, List<BanEvent>> playerEvents = new HashMap<>();
    private final File storageFile;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public BanManager(Path dataDirectory) {
        if (!dataDirectory.toFile().exists()) {
            dataDirectory.toFile().mkdirs();
        }
        this.storageFile = dataDirectory.resolve("bans.json").toFile();
        loadBans();
    }

    public void banPlayer(String playerName, String bannerName, long durationMillis, String reason) {
        long timestamp = System.currentTimeMillis();
        long expiry = timestamp + durationMillis;
        addEvent(playerName, new BanEvent(BanEvent.Type.BAN, playerName, bannerName, timestamp, expiry, reason));
    }

    public void unbanPlayer(String playerName, String unbannerName, String reason) {
        long timestamp = System.currentTimeMillis();
        addEvent(playerName, new BanEvent(BanEvent.Type.UNBAN, playerName, unbannerName, timestamp, -1, reason));
    }

    private void addEvent(String playerName, BanEvent event) {
        String name = playerName.toLowerCase();
        playerEvents.computeIfAbsent(name, k -> new ArrayList<>()).add(event);
        saveBans();
    }

    public boolean isBanned(String playerName) {
        String name = playerName.toLowerCase();
        if (!playerEvents.containsKey(name))
            return false;

        List<BanEvent> events = playerEvents.get(name);
        if (events.isEmpty())
            return false;

        BanEvent lastEvent = events.get(events.size() - 1);
        if (lastEvent.getType() == BanEvent.Type.UNBAN)
            return false;

        // It is a BAN event
        if (System.currentTimeMillis() > lastEvent.getExpiry()) {
            return false;
        }

        return true;
    }

    public BanEvent getLatestBan(String playerName) {
        String name = playerName.toLowerCase();
        if (!playerEvents.containsKey(name))
            return null;

        List<BanEvent> events = playerEvents.get(name);
        for (int i = events.size() - 1; i >= 0; i--) {
            BanEvent event = events.get(i);
            if (event.getType() == BanEvent.Type.BAN)
                return event;
            if (event.getType() == BanEvent.Type.UNBAN)
                return null;
        }
        return null;
    }

    public List<BanEvent> getEventsByExecutor(String executorName) {
        List<BanEvent> events = new ArrayList<>();
        for (List<BanEvent> playerHistory : playerEvents.values()) {
            for (BanEvent event : playerHistory) {
                if (event.getExecutorName().equalsIgnoreCase(executorName)) {
                    events.add(event);
                }
            }
        }
        return events;
    }

    public List<BanEvent> getEvents(String playerName) {
        return playerEvents.getOrDefault(playerName.toLowerCase(), new ArrayList<>());
    }

    private void loadBans() {
        if (!storageFile.exists())
            return;
        try (Reader reader = new FileReader(storageFile)) {
            playerEvents = GSON.fromJson(reader, new TypeToken<Map<String, List<BanEvent>>>() {
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
            BAN, UNBAN
        }

        private final Type type;
        private final String targetName;
        private final String executorName;
        private final long timestamp;
        private final long expiry;
        private final String reason;

        public BanEvent(Type type, String targetName, String executorName, long timestamp, long expiry, String reason) {
            this.type = type;
            this.targetName = targetName;
            this.executorName = executorName;
            this.timestamp = timestamp;
            this.expiry = expiry;
            this.reason = reason;
        }

        public Type getType() {
            return type;
        }

        public String getTargetName() {
            return targetName;
        }

        public String getExecutorName() {
            return executorName;
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

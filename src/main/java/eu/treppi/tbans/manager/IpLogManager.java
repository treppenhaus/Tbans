package eu.treppi.tbans.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class IpLogManager {
    private final File logFile;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private List<IpEntry> ipLogs = new ArrayList<>();

    public IpLogManager(Path dataDirectory) {
        if (!dataDirectory.toFile().exists()) {
            dataDirectory.toFile().mkdirs();
        }
        this.logFile = dataDirectory.resolve("IPs.json").toFile();
        loadLogs();
    }

    private void loadLogs() {
        if (!logFile.exists())
            return;
        try (Reader reader = new FileReader(logFile)) {
            ipLogs = GSON.fromJson(reader, new TypeToken<List<IpEntry>>() {
            }.getType());
            if (ipLogs == null)
                ipLogs = new ArrayList<>();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveLogs() {
        try (Writer writer = new FileWriter(logFile)) {
            GSON.toJson(ipLogs, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void logIp(UUID playerUuid, String ip, long salt) {
        String hash = hashIp(ip, salt);

        IpEntry entry = findEntry(hash);
        if (entry == null) {
            entry = new IpEntry(hash);
            ipLogs.add(entry);
        }

        if (!entry.hasPlayer(playerUuid)) {
            entry.addPlayer(playerUuid);
            saveLogs();
        }
    }

    public List<String> getIpHashes(UUID playerUuid) {
        List<String> hashes = new ArrayList<>();
        for (IpEntry entry : ipLogs) {
            if (entry.hasPlayer(playerUuid)) {
                hashes.add(entry.getIpHash());
            }
        }
        return hashes;
    }

    public String hashIp(String ip, long salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = ip + salt;
            byte[] encodedhash = digest.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    public Map<UUID, Long> getAlts(UUID targetUuid, int daysLimit) {
        Map<UUID, Long> alts = new HashMap<>();
        long limitMillis = daysLimit * 24L * 60L * 60L * 1000L;

        for (IpEntry entry : ipLogs) {
            Optional<PlayerInfo> targetInfo = entry.getPlayers().stream()
                    .filter(p -> p.getPlayerUuid().equals(targetUuid))
                    .findFirst();

            if (targetInfo.isPresent()) {
                long targetTime = targetInfo.get().getFirstTimeSeen();
                for (PlayerInfo other : entry.getPlayers()) {
                    if (other.getPlayerUuid().equals(targetUuid))
                        continue;

                    if (Math.abs(other.getFirstTimeSeen() - targetTime) <= limitMillis) {
                        // Store the earliest connection time we found for this alt on this shared IP
                        alts.merge(other.getPlayerUuid(), other.getFirstTimeSeen(), Math::min);
                    }
                }
            }
        }
        return alts;
    }

    public Map<UUID, Integer> getRecursiveAlts(UUID targetUuid, int maxHops) {
        Map<UUID, Integer> distances = new HashMap<>();
        Queue<UUID> queue = new LinkedList<>();

        distances.put(targetUuid, 0);
        queue.add(targetUuid);

        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            int currentDist = distances.get(current);

            if (currentDist >= maxHops)
                continue;

            // Find all IPs used by this player
            for (IpEntry entry : ipLogs) {
                if (entry.hasPlayer(current)) {
                    // For each IP, find all players who used it
                    for (PlayerInfo other : entry.getPlayers()) {
                        if (!distances.containsKey(other.getPlayerUuid())) {
                            distances.put(other.getPlayerUuid(), currentDist + 1);
                            queue.add(other.getPlayerUuid());
                        }
                    }
                }
            }
        }

        distances.remove(targetUuid); // Don't include the target themselves
        return distances;
    }

    private IpEntry findEntry(String hash) {
        for (IpEntry entry : ipLogs) {
            if (entry.getIpHash().equals(hash))
                return entry;
        }
        return null;
    }

    public static class IpEntry {
        @SerializedName("iphashvalue")
        private String ipHash;

        private List<PlayerInfo> players = new ArrayList<>();

        public IpEntry(String ipHash) {
            this.ipHash = ipHash;
        }

        public String getIpHash() {
            return ipHash;
        }

        public List<PlayerInfo> getPlayers() {
            return players;
        }

        public boolean hasPlayer(UUID uuid) {
            for (PlayerInfo info : players) {
                if (info.getPlayerUuid().equals(uuid))
                    return true;
            }
            return false;
        }

        public void addPlayer(UUID uuid) {
            players.add(new PlayerInfo(uuid, System.currentTimeMillis()));
        }
    }

    public static class PlayerInfo {
        private UUID playeruuid;

        @SerializedName("first_time_seen")
        private long firstTimeSeen;

        public PlayerInfo(UUID playeruuid, long firstTimeSeen) {
            this.playeruuid = playeruuid;
            this.firstTimeSeen = firstTimeSeen;
        }

        public UUID getPlayerUuid() {
            return playeruuid;
        }

        public long getFirstTimeSeen() {
            return firstTimeSeen;
        }
    }
}

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
        if (!logFile.exists()) return;
        try (Reader reader = new FileReader(logFile)) {
            ipLogs = GSON.fromJson(reader, new TypeToken<List<IpEntry>>() {}.getType());
            if (ipLogs == null) ipLogs = new ArrayList<>();
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

    private String hashIp(String ip, long salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = ip + salt;
            byte[] encodedhash = digest.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    private IpEntry findEntry(String hash) {
        for (IpEntry entry : ipLogs) {
            if (entry.getIpHash().equals(hash)) return entry;
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

        public String getIpHash() { return ipHash; }
        
        public boolean hasPlayer(UUID uuid) {
            for (PlayerInfo info : players) {
                if (info.getPlayerUuid().equals(uuid)) return true;
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

        public UUID getPlayerUuid() { return playeruuid; }
    }
}

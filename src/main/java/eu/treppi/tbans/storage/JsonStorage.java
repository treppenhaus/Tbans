package eu.treppi.tbans.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import eu.treppi.tbans.manager.BanEvent;
import eu.treppi.tbans.manager.IpLogManager;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.*;

/** Stores data in JSON files inside the plugin data directory (the original TBans format). */
public class JsonStorage implements Storage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final File playerEventsFile;
    private final File ipEventsFile;
    private final File namesFile;
    private final File displayNamesFile;
    private final File ipLogsFile;

    public JsonStorage(Path dataDirectory) {
        if (!dataDirectory.toFile().exists()) {
            dataDirectory.toFile().mkdirs();
        }
        this.playerEventsFile = dataDirectory.resolve("uuid-bans.json").toFile();
        this.ipEventsFile = dataDirectory.resolve("ip-bans.json").toFile();
        this.namesFile = dataDirectory.resolve("names.json").toFile();
        this.displayNamesFile = dataDirectory.resolve("uuid-names.json").toFile();
        this.ipLogsFile = dataDirectory.resolve("IPs.json").toFile();
    }

    @Override
    public String getName() {
        return "json";
    }

    @Override
    public Map<UUID, List<BanEvent>> loadPlayerEvents() {
        Map<UUID, List<BanEvent>> map = read(playerEventsFile, new TypeToken<Map<UUID, List<BanEvent>>>() {
        }.getType());
        return map != null ? new HashMap<>(map) : new HashMap<>();
    }

    @Override
    public Map<String, List<BanEvent>> loadIpEvents() {
        Map<String, List<BanEvent>> map = read(ipEventsFile, new TypeToken<Map<String, List<BanEvent>>>() {
        }.getType());
        return map != null ? new HashMap<>(map) : new HashMap<>();
    }

    @Override
    public Map<String, UUID> loadNameIndex() {
        Map<String, UUID> map = read(namesFile, new TypeToken<Map<String, UUID>>() {
        }.getType());
        return map != null ? new HashMap<>(map) : new HashMap<>();
    }

    @Override
    public Map<UUID, String> loadDisplayNames() {
        Map<UUID, String> map = read(displayNamesFile, new TypeToken<Map<UUID, String>>() {
        }.getType());
        return map != null ? new HashMap<>(map) : new HashMap<>();
    }

    @Override
    public List<IpLogManager.IpEntry> loadIpLogs() {
        List<IpLogManager.IpEntry> list = read(ipLogsFile, new TypeToken<List<IpLogManager.IpEntry>>() {
        }.getType());
        return list != null ? new ArrayList<>(list) : new ArrayList<>();
    }

    @Override
    public synchronized void savePlayerEvents(UUID target, List<BanEvent> events, Map<UUID, List<BanEvent>> all) {
        write(playerEventsFile, all);
    }

    @Override
    public synchronized void saveIpEvents(String ipHash, List<BanEvent> events, Map<String, List<BanEvent>> all) {
        write(ipEventsFile, all);
    }

    @Override
    public synchronized void saveName(UUID uuid, String name, Map<String, UUID> nameIndex,
            Map<UUID, String> displayNames) {
        write(namesFile, nameIndex);
        write(displayNamesFile, displayNames);
    }

    @Override
    public synchronized void saveIpEntry(IpLogManager.IpEntry entry, List<IpLogManager.IpEntry> all) {
        write(ipLogsFile, all);
    }

    @Override
    public synchronized void importAll(StorageData data) {
        Map<UUID, List<BanEvent>> playerEvents = loadPlayerEvents();
        playerEvents.putAll(data.playerEvents());
        write(playerEventsFile, playerEvents);

        Map<String, List<BanEvent>> ipEvents = loadIpEvents();
        ipEvents.putAll(data.ipEvents());
        write(ipEventsFile, ipEvents);

        Map<String, UUID> names = loadNameIndex();
        names.putAll(data.nameIndex());
        write(namesFile, names);

        Map<UUID, String> displayNames = loadDisplayNames();
        displayNames.putAll(data.displayNames());
        write(displayNamesFile, displayNames);

        Map<String, IpLogManager.IpEntry> ipLogs = new LinkedHashMap<>();
        for (IpLogManager.IpEntry entry : loadIpLogs()) {
            ipLogs.put(entry.getIpHash(), entry);
        }
        for (IpLogManager.IpEntry entry : data.ipLogs()) {
            ipLogs.put(entry.getIpHash(), entry);
        }
        write(ipLogsFile, new ArrayList<>(ipLogs.values()));
    }

    @Override
    public void close() {
    }

    private <T> T read(File file, Type type) {
        if (!file.exists())
            return null;
        try (Reader reader = new FileReader(file)) {
            return GSON.fromJson(reader, type);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void write(File file, Object data) {
        try (Writer writer = new FileWriter(file)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

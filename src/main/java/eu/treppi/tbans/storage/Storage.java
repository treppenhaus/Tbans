package eu.treppi.tbans.storage;

import eu.treppi.tbans.manager.BanEvent;
import eu.treppi.tbans.manager.IpLogManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistence backend for all TBans data. Managers keep everything in memory and
 * write changes through this interface. The {@code all} parameters let file-based
 * backends rewrite the whole file, while database backends only write the changed entry.
 */
public interface Storage extends AutoCloseable {

    String getName();

    Map<UUID, List<BanEvent>> loadPlayerEvents();

    Map<String, List<BanEvent>> loadIpEvents();

    /** lowercase name -> uuid (includes old names) */
    Map<String, UUID> loadNameIndex();

    /** uuid -> correctly cased current name */
    Map<UUID, String> loadDisplayNames();

    List<IpLogManager.IpEntry> loadIpLogs();

    void savePlayerEvents(UUID target, List<BanEvent> events, Map<UUID, List<BanEvent>> all);

    void saveIpEvents(String ipHash, List<BanEvent> events, Map<String, List<BanEvent>> all);

    void saveName(UUID uuid, String name, Map<String, UUID> nameIndex, Map<UUID, String> displayNames);

    void saveIpEntry(IpLogManager.IpEntry entry, List<IpLogManager.IpEntry> all);

    /** Loads everything this backend holds. */
    default StorageData loadAll() {
        return new StorageData(loadPlayerEvents(), loadIpEvents(), loadNameIndex(), loadDisplayNames(), loadIpLogs());
    }

    /**
     * Writes all given data into this backend. Entries with the same key are overwritten,
     * entries that only exist in this backend are kept.
     */
    void importAll(StorageData data);

    @Override
    void close();
}

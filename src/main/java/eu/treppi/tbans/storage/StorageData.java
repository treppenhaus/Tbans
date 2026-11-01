package eu.treppi.tbans.storage;

import eu.treppi.tbans.manager.BanEvent;
import eu.treppi.tbans.manager.IpLogManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Full snapshot of all TBans data, used for migrations between backends. */
public record StorageData(
        Map<UUID, List<BanEvent>> playerEvents,
        Map<String, List<BanEvent>> ipEvents,
        Map<String, UUID> nameIndex,
        Map<UUID, String> displayNames,
        List<IpLogManager.IpEntry> ipLogs) {

    public String summary() {
        return playerEvents.size() + " player histories, "
                + ipEvents.size() + " IP ban histories, "
                + nameIndex.size() + " names, "
                + displayNames.size() + " display names, "
                + ipLogs.size() + " IP log entries";
    }
}

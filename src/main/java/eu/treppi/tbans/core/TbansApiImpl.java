package eu.treppi.tbans.core;

import eu.treppi.tbans.api.Ban;
import eu.treppi.tbans.api.Punishment;
import eu.treppi.tbans.api.PunishmentType;
import eu.treppi.tbans.api.TbansApi;
import eu.treppi.tbans.manager.BanEvent;
import eu.treppi.tbans.manager.BanManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** {@link TbansApi} backed by the in-memory {@link BanManager} state. */
final class TbansApiImpl implements TbansApi {

    private final BanManager banManager;

    TbansApiImpl(BanManager banManager) {
        this.banManager = banManager;
    }

    @Override
    public Optional<Ban> getBanByCode(String code) {
        // Lock so the lookup and the "active" check see the same state
        synchronized (banManager) {
            BanManager.CodeEntry entry = banManager.findByCode(code);
            if (entry == null) {
                return Optional.empty();
            }
            BanManager.CodeLookupResult owner = entry.owner();
            boolean ipBan = owner.type == BanManager.CodeLookupResult.Type.IP_HASH;
            UUID target = ipBan ? null : UUID.fromString(owner.value);
            return Optional.of(toBan(entry.ban(), target, ipBan, banManager.isActiveBan(entry.ban(), owner)));
        }
    }

    @Override
    public Optional<Ban> getActiveBan(UUID player) {
        if (player == null) {
            return Optional.empty();
        }
        synchronized (banManager) {
            if (!banManager.isBanned(player)) {
                return Optional.empty();
            }
            return Optional.of(toBan(banManager.getLatestBan(player), player, false, true));
        }
    }

    @Override
    public boolean isBanned(UUID player) {
        return player != null && banManager.isBanned(player);
    }

    @Override
    public List<Punishment> getHistory(UUID player) {
        return banManager.getEvents(player).stream()
                .map(e -> new Punishment(PunishmentType.valueOf(e.getType().name()), e.getCode(),
                        e.getTargetUUID(), e.getExecutorUUID(), e.getTimestamp(), e.getExpiry(), e.getReason()))
                .toList();
    }

    private static Ban toBan(BanEvent event, UUID target, boolean ipBan, boolean active) {
        return new Ban(event.getCode(), target, ipBan, event.getExecutorUUID(), event.getTimestamp(),
                event.getExpiry(), event.getReason(), active);
    }
}

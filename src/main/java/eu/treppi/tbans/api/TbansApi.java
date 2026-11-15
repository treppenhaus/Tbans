package eu.treppi.tbans.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only Java API of TBans for other Velocity plugins on the same proxy.
 *
 * <p>Get the instance with {@link TbansProvider#get()}. All methods are thread-safe, never block on
 * network I/O and return immutable snapshots, so they can be called from any thread.
 */
public interface TbansApi {

    /**
     * Looks up a ban by its code (ban ID). Input is {@link BanCodes#normalize(String) normalized}
     * first, so lowercase, spaces and dashes are fine.
     *
     * <p>The result can be an old, expired or lifted ban, or an IP ban; check {@link Ban#active()}
     * and {@link Ban#ipBan()}.
     *
     * @param code the ban code, may be {@code null}
     * @return the ban, or empty if no ban has this code
     */
    Optional<Ban> getBanByCode(String code);

    /**
     * @param player the player's UUID
     * @return the ban currently in force for the player (not IP bans), or empty if not banned
     */
    Optional<Ban> getActiveBan(UUID player);

    /**
     * @param player the player's UUID
     * @return {@code true} if the player is currently banned (not checking IP bans)
     */
    boolean isBanned(UUID player);

    /**
     * @param player the player's UUID
     * @return the player's full punishment history (bans, unbans, kicks), oldest first
     */
    List<Punishment> getHistory(UUID player);
}

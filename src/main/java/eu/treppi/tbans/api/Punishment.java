package eu.treppi.tbans.api;

import java.util.UUID;

/**
 * Immutable snapshot of one entry in a player's punishment history.
 *
 * @param type      what happened
 * @param code      the ban code, only set for {@link PunishmentType#BAN} entries, otherwise {@code null}
 * @param target    the punished player, {@code null} for IP bans
 * @param executor  who executed it; {@code 00000000-0000-0000-0000-000000000000} means the console / API
 * @param timestamp when it happened (epoch millis)
 * @param expiry    when a ban expires (epoch millis), {@code -1} for permanent bans and non-ban entries
 * @param reason    the given reason
 */
public record Punishment(
        PunishmentType type,
        String code,
        UUID target,
        UUID executor,
        long timestamp,
        long expiry,
        String reason) {

    /** @return {@code true} if this is a ban without an expiry date */
    public boolean isPermanent() {
        return type == PunishmentType.BAN && expiry == -1;
    }
}

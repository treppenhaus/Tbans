package eu.treppi.tbans.api;

import java.util.UUID;

/**
 * Immutable snapshot of a single ban, as returned by {@link TbansApi}.
 *
 * @param code      the unique ban code (ban ID) shown to the banned player
 * @param target    the banned player, {@code null} if this is an IP ban
 * @param ipBan     {@code true} if this ban targets a (hashed) IP address instead of a player
 * @param executor  who issued the ban; {@code 00000000-0000-0000-0000-000000000000} means the console / API
 * @param timestamp when the ban was issued (epoch millis)
 * @param expiry    when the ban expires (epoch millis), {@code -1} if permanent
 * @param reason    the ban reason
 * @param active    {@code true} if, at the time this snapshot was taken, this exact ban is the one
 *                  currently in force for its target: it has not expired, the target was not unbanned
 *                  afterwards and no newer ban replaced it
 */
public record Ban(
        String code,
        UUID target,
        boolean ipBan,
        UUID executor,
        long timestamp,
        long expiry,
        String reason,
        boolean active) {

    /** @return {@code true} if the ban has no expiry date */
    public boolean isPermanent() {
        return expiry == -1;
    }
}

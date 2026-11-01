package eu.treppi.tbans.util;

import eu.treppi.tbans.manager.ConfigManager;
import eu.treppi.tbans.manager.LanguageManager;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.UUID;

public class MessageUtils {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final UUID CONSOLE_UUID = new UUID(0, 0);
    private static final String DEFAULT_PLAYER_HOVER = "<#af86b3>UUID: <#8af0e6>{uuid}</#8af0e6>\n<#d58af0>Click to copy</#d58af0></#af86b3>";

    public static String format(String msg, String target, String executor, String duration, String reason,
            String code, ConfigManager config) {
        return format(msg, target, null, executor, null, duration, reason, code, config, null);
    }

    /**
     * Same as {@link #format(String, String, String, String, String, String, ConfigManager)} but
     * makes {player} / {executor} hoverable (shows UUID) and clickable (copies UUID) when a UUID is given.
     */
    public static String format(String msg, String target, UUID targetUuid, String executor, UUID executorUuid,
            String duration, String reason, String code, ConfigManager config, LanguageManager lang) {
        String headFormat = config.getHeadFormat();
        String targetHead = target != null ? headFormat.replace("{player}", target) : "";
        String executorHead = executor != null ? headFormat.replace("{player}", executor) : "";

        String hoverTemplate = getHoverTemplate(lang);
        String targetText = playerTag(target != null ? target : "Unknown", targetUuid, hoverTemplate);
        String executorText = playerTag(executor != null ? executor : "Console", executorUuid, hoverTemplate);

        return msg.replace("{player}", targetText)
                .replace("{target_head}", targetHead)
                .replace("{executor}", executorText)
                .replace("{executor_head}", executorHead)
                .replace("{duration}", duration != null ? duration : "Permanent")
                .replace("{reason}", reason != null ? reason : "No reason provided.")
                .replace("{ban_code}", code != null ? code : "Unknown");
    }

    /**
     * Wraps a player name in MiniMessage hover/click tags so staff can see and copy the UUID.
     */
    public static String playerTag(String name, UUID uuid, String hoverTemplate) {
        String safeName = MM.escapeTags(name);
        if (uuid == null || uuid.equals(CONSOLE_UUID)) {
            return safeName;
        }
        String uuidStr = uuid.toString();
        String hover = hoverTemplate
                .replace("{uuid}", uuidStr)
                .replace("{player}", safeName)
                .replace("'", "\\'");
        return "<hover:show_text:'" + hover + "'><click:copy_to_clipboard:" + uuidStr + ">" + safeName
                + "</click></hover>";
    }

    private static String getHoverTemplate(LanguageManager lang) {
        if (lang == null) {
            return DEFAULT_PLAYER_HOVER;
        }
        String msg = lang.getMessage("player_hover");
        return msg.startsWith("Missing config") ? DEFAULT_PLAYER_HOVER : msg;
    }
}

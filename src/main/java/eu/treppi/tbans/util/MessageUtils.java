package eu.treppi.tbans.util;

import eu.treppi.tbans.manager.ConfigManager;

public class MessageUtils {
    public static String format(String msg, String target, String executor, String duration, String reason,
            ConfigManager config) {
        String headFormat = config.getHeadFormat();
        String targetHead = target != null ? headFormat.replace("{player}", target) : "";
        String executorHead = executor != null ? headFormat.replace("{player}", executor) : "";

        return msg.replace("{player}", target != null ? target : "Unknown")
                .replace("{target_head}", targetHead)
                .replace("{executor}", executor != null ? executor : "Console")
                .replace("{executor_head}", executorHead)
                .replace("{duration}", duration != null ? duration : "Permanent")
                .replace("{reason}", reason != null ? reason : "No reason provided.");
    }
}

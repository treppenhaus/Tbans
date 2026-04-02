package eu.treppi.tbans.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import eu.treppi.tbans.manager.BanManager;
import eu.treppi.tbans.manager.LanguageManager;
import eu.treppi.tbans.util.TimeUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class HistoryCommand implements SimpleCommand {

    private final BanManager banManager;
    private final LanguageManager languageManager;
    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    public HistoryCommand(BanManager banManager, LanguageManager languageManager) {
        this.banManager = banManager;
        this.languageManager = languageManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!source.hasPermission("tbans.history")) {
            source.sendMessage(mm.deserialize(languageManager.getMessage("no_permission")));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length < 1) {
            source.sendMessage(mm.deserialize(languageManager.getMessage("history.usage")));
            return;
        }

        String targetName = args[0];
        List<BanManager.BanEvent> events = banManager.getEvents(targetName);

        String banStatus;
        if (events.isEmpty()) {
            banStatus = languageManager.getMessage("history.status_never");
        } else if (banManager.isBanned(targetName)) {
            banStatus = languageManager.getMessage("history.status_banned");
        } else {
            banStatus = languageManager.getMessage("history.status_expired");
        }

        String header = languageManager.getMessage("history.header").replace("{player}", targetName);
        source.sendMessage(mm.deserialize(header));
        source.sendMessage(mm.deserialize(banStatus));
        String divider = languageManager.getMessage("history.divider");
        source.sendMessage(mm.deserialize(divider));

        if (events.isEmpty()) {
            String emptyMsg = languageManager.getMessage("history.empty").replace("{player}", targetName);
            source.sendMessage(mm.deserialize(emptyMsg));
            return;
        }

        for (BanManager.BanEvent event : events) {
            String date = DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(event.getTimestamp()));
            String typeColor = event.getType() == BanManager.BanEvent.Type.BAN ? 
                languageManager.getMessage("blame.type_ban") : languageManager.getMessage("blame.type_unban");
            
            String expiryInfo = "";
            if (event.getType() == BanManager.BanEvent.Type.BAN) {
                if (event.getExpiry() > 0) {
                    String expiry = DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(event.getExpiry()));
                    long remaining = event.getExpiry() - System.currentTimeMillis();
                    if (remaining > 0) {
                        String remainingStr = TimeUtils.formatRemainingTime(remaining);
                        expiryInfo = languageManager.getMessage("history.expiry_until_left")
                                .replace("{expiry}", expiry)
                                .replace("{left}", remainingStr);
                    } else {
                        expiryInfo = languageManager.getMessage("history.expiry_until")
                                .replace("{expiry}", expiry);
                    }
                } else {
                    expiryInfo = languageManager.getMessage("history.expiry_permanent");
                }
            }
            
            String msg = languageManager.getMessage("history.action_line")
                    .replace("{date}", date)
                    .replace("{type}", typeColor)
                    .replace("{executor}", event.getExecutorName())
                    .replace("{reason}", event.getReason())
                    .replace("{expiry_info}", expiryInfo);
            
            source.sendMessage(mm.deserialize(msg));
        }
        source.sendMessage(mm.deserialize(divider));
    }
}

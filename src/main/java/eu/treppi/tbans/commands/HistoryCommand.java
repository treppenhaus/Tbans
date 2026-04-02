package eu.treppi.tbans.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import eu.treppi.tbans.manager.BanManager;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class HistoryCommand implements SimpleCommand {

    private final BanManager banManager;
    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    public HistoryCommand(BanManager banManager) {
        this.banManager = banManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!source.hasPermission("tbans.history")) {
            source.sendMessage(mm.deserialize("<gradient:#ff5555:#ff0000><b>TBans</b></gradient> <gray>»</gray> <red>You do not have permission to use this command!</red>"));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length < 1) {
            source.sendMessage(mm.deserialize("<gradient:#ff5555:#ffaa00><b>TBans</b></gradient> <gray>»</gray> <red>Usage: /history <player></red>"));
            return;
        }

        String targetName = args[0];
        List<BanManager.BanEvent> events = banManager.getEvents(targetName);

        String banStatus;
        if (events.isEmpty()) {
            banStatus = "<gradient:#55ff55:#aaffaa>BANSTATUS: NEVER</gradient>";
        } else if (banManager.isBanned(targetName)) {
            banStatus = "<gradient:#ff5555:#aa0000>BANSTATUS: BANNED</gradient>";
        } else {
            banStatus = "<gradient:#ffaa00:#ffff55>BANSTATUS: EXPIRED / UNBANNED</gradient>";
        }

        source.sendMessage(mm.deserialize("\n<gradient:#ffaa00:#ffff55><b>History for " + targetName + "</b></gradient>"));
        source.sendMessage(mm.deserialize(banStatus));
        source.sendMessage(mm.deserialize("<gray>-----------------------------------</gray>"));

        if (events.isEmpty()) {
            return;
        }

        for (BanManager.BanEvent event : events) {
            String date = DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(event.getTimestamp()));
            String typeColor = event.getType() == BanManager.BanEvent.Type.BAN ? "<red>BAN</red>" : "<green>UNBAN</green>";
            
            String msg = "<gray>[" + date + "] " + typeColor + " by <yellow>" + event.getExecutorName() + "</yellow>: <white>" + event.getReason() + "</white>";
            
            if (event.getType() == BanManager.BanEvent.Type.BAN) {
                if (event.getExpiry() > 0) {
                    String expiry = DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(event.getExpiry()));
                    long remaining = event.getExpiry() - System.currentTimeMillis();
                    if (remaining > 0) {
                        String remainingStr = eu.treppi.tbans.util.TimeUtils.formatRemainingTime(remaining);
                        msg += " <dark_gray>(until " + expiry + ", " + remainingStr + " left)</dark_gray>";
                    } else {
                        msg += " <dark_gray>(until " + expiry + ")</dark_gray>";
                    }
                } else {
                    msg += " <dark_gray>(Permanent)</dark_gray>";
                }
            }
            
            source.sendMessage(mm.deserialize(msg));
        }
        source.sendMessage(mm.deserialize("<gray>-----------------------------------</gray>"));
    }
}

package eu.treppi.tbans.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import eu.treppi.tbans.manager.BanManager;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class BlameCommand implements SimpleCommand {

    private final BanManager banManager;
    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    public BlameCommand(BanManager banManager) {
        this.banManager = banManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!source.hasPermission("tbans.blame")) {
            source.sendMessage(mm.deserialize("<gradient:#ff5555:#ff0000><b>TBans</b></gradient> <gray>»</gray> <red>You do not have permission to use this command!</red>"));
            return;
        }

        String[] args = invocation.arguments();

        if (args.length < 1) {
            source.sendMessage(mm.deserialize("<gradient:#ff5555:#ffaa00><b>TBans</b></gradient> <gray>»</gray> <red>Usage: /blame <staff></red>"));
            return;
        }

        String staffName = args[0];
        List<BanManager.BanEvent> events = banManager.getEventsByExecutor(staffName);

        if (events.isEmpty()) {
            source.sendMessage(mm.deserialize("<gradient:#ff5555:#ffaa00><b>TBans</b></gradient> <gray>»</gray> <gray>No actions found by <yellow>" + staffName + "</yellow></gray>"));
            return;
        }

        int totalActions = events.size();
        long banCount = events.stream().filter(e -> e.getType() == BanManager.BanEvent.Type.BAN).count();
        long unbanCount = events.stream().filter(e -> e.getType() == BanManager.BanEvent.Type.UNBAN).count();

        source.sendMessage(mm.deserialize("\n<gradient:#55ff55:#aaffaa><b>Staff Audit: " + staffName + "</b></gradient>"));
        source.sendMessage(mm.deserialize("<gray>Total Actions: <white>" + totalActions + "</white> (<red>" + banCount + " Bans</red>, <green>" + unbanCount + " Unbans</green>)</gray>"));
        source.sendMessage(mm.deserialize("<gray>Recent 5 Actions:</gray>"));

        int startIndex = Math.max(0, events.size() - 5);
        for (int i = events.size() - 1; i >= startIndex; i--) {
            BanManager.BanEvent event = events.get(i);
            String date = DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(event.getTimestamp()));
            String typeColor = event.getType() == BanManager.BanEvent.Type.BAN ? "<red>BAN</red>" : "<green>UNBAN</green>";
            
            source.sendMessage(mm.deserialize("<gray>[" + date + "] " + typeColor + " <yellow>" + event.getTargetName() + "</yellow> <dark_gray>»</dark_gray> <white>" + event.getReason() + "</white></gray>"));
        }
        
        source.sendMessage(mm.deserialize("<gray>-----------------------------------</gray>"));
    }
}

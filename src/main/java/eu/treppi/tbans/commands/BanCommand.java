package eu.treppi.tbans.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import eu.treppi.tbans.manager.BanManager;
import eu.treppi.tbans.util.TimeUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Arrays;
import java.util.Optional;

public class BanCommand implements SimpleCommand {

    private final ProxyServer server;
    private final BanManager banManager;
    private static final MiniMessage mm = MiniMessage.miniMessage();

    public BanCommand(ProxyServer server, BanManager banManager) {
        this.server = server;
        this.banManager = banManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!source.hasPermission("tbans.ban")) {
            source.sendMessage(mm.deserialize("<gradient:#ff5555:#ff0000><b>TBans</b></gradient> <gray>»</gray> <red>You do not have permission to use this command!</red>"));
            return;
        }

        String[] args = invocation.arguments();

        if (args.length < 3) {
            source.sendMessage(mm.deserialize("<gradient:#ff5555:#ffaa00><b>TBans</b></gradient> <gray>»</gray> <red>Usage: /ban <player> <time> <reason></red>"));
            return;
        }

        String targetName = args[0];
        String timeStr = args[1];
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        long duration = TimeUtils.parseTime(timeStr);
        if (duration == -1) {
            source.sendMessage(mm.deserialize("<gradient:#ff5555:#ffaa00><b>TBans</b></gradient> <gray>»</gray> <red>Invalid time format!</red>"));
            return;
        }

        Optional<Player> targetPlayer = server.getPlayer(targetName);
        if (targetPlayer.isPresent()) {
            targetPlayer.get().disconnect(mm.deserialize(
                "<gradient:#ff5555:#ff0000><b>YOU ARE BANNED!</b></gradient>\n\n" +
                "<gray>Duration: <yellow>" + timeStr + "</yellow>\n" +
                "<gray>Reason: <white>" + reason + "</white>"
            ));
        }

        String bannerName = source instanceof Player ? ((Player) source).getUsername() : "Console";
        banManager.banPlayer(targetName, bannerName, duration, reason);
        source.sendMessage(mm.deserialize(
            "<gradient:#55ff55:#00aa00><b>SUCCESS</b></gradient> <gray>»</gray> <white>Banned <yellow>" + targetName + "</yellow> for <yellow>" + timeStr + "</yellow> (<gray>" + reason + "</gray>)</white>"
        ));
    }
}

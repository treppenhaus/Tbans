package eu.treppi.tbans.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Arrays;
import java.util.Optional;

public class KickCommand implements SimpleCommand {

    private final ProxyServer server;
    private static final MiniMessage mm = MiniMessage.miniMessage();

    public KickCommand(ProxyServer server) {
        this.server = server;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!source.hasPermission("tbans.kick")) {
            source.sendMessage(mm.deserialize("<gradient:#ff5555:#ff0000><b>TBans</b></gradient> <gray>»</gray> <red>You do not have permission to use this command!</red>"));
            return;
        }

        String[] args = invocation.arguments();

        if (args.length < 1) {
            source.sendMessage(mm.deserialize("<gradient:#ff5555:#ffaa00><b>TBans</b></gradient> <gray>»</gray> <red>Usage: /kick <player> [reason]</red>"));
            return;
        }

        String targetName = args[0];
        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "No reason provided.";

        Optional<Player> targetPlayer = server.getPlayer(targetName);
        if (targetPlayer.isPresent()) {
            targetPlayer.get().disconnect(mm.deserialize(
                "<gradient:#ff5555:#ff0000><b>YOU WERE KICKED!</b></gradient>\n\n" +
                "<gray>Reason: <white>" + reason + "</white>\n\n" +
                "<gradient:#ffaa00:#ffff55>Please read our rules before rejoining.</gradient>"
            ));
            source.sendMessage(mm.deserialize(
                "<gradient:#55ff55:#00aa00><b>SUCCESS</b></gradient> <gray>»</gray> <white>Kicked <yellow>" + targetName + "</yellow> (<gray>" + reason + "</gray>)</white>"
            ));
        } else {
            source.sendMessage(mm.deserialize("<gradient:#ff5555:#ffaa00><b>TBans</b></gradient> <gray>»</gray> <red>Player not found or not online!</red>"));
        }
    }
}

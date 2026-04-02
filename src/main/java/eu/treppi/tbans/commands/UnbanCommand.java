package eu.treppi.tbans.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import eu.treppi.tbans.manager.BanManager;
import net.kyori.adventure.text.minimessage.MiniMessage;

public class UnbanCommand implements SimpleCommand {

    private final BanManager banManager;
    private static final MiniMessage mm = MiniMessage.miniMessage();

    public UnbanCommand(BanManager banManager) {
        this.banManager = banManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!source.hasPermission("tbans.unban")) {
            source.sendMessage(mm.deserialize("<gradient:#ff5555:#ff0000><b>TBans</b></gradient> <gray>»</gray> <red>You do not have permission to use this command!</red>"));
            return;
        }

        String[] args = invocation.arguments();

        if (args.length < 1) {
            source.sendMessage(mm.deserialize("<gradient:#ff5555:#ffaa00><b>TBans</b></gradient> <gray>»</gray> <red>Usage: /unban <player> [reason]</red>"));
            return;
        }

        String targetName = args[0];
        String reason = args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : "No reason provided.";

        if (!banManager.isBanned(targetName)) {
            source.sendMessage(mm.deserialize("<gradient:#ff5555:#ffaa00><b>TBans</b></gradient> <gray>»</gray> <red>This player is not banned!</red>"));
            return;
        }

        String unbannerName = source instanceof Player ? ((Player) source).getUsername() : "Console";
        banManager.unbanPlayer(targetName, unbannerName, reason);

        source.sendMessage(mm.deserialize(
            "<gradient:#55ff55:#00aa00><b>SUCCESS</b></gradient> <gray>»</gray> <white>Unbanned <yellow>" + targetName + "</yellow> (<gray>" + reason + "</gray>)</white>"
        ));
    }
}

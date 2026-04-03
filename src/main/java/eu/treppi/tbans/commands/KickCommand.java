package eu.treppi.tbans.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import eu.treppi.tbans.manager.BanManager;
import eu.treppi.tbans.manager.LanguageManager;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class KickCommand implements SimpleCommand {

    private final ProxyServer server;
    private final BanManager banManager;
    private final LanguageManager languageManager;
    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final UUID CONSOLE_UUID = new UUID(0, 0);

    public KickCommand(ProxyServer server, BanManager banManager, LanguageManager languageManager) {
        this.server = server;
        this.banManager = banManager;
        this.languageManager = languageManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!source.hasPermission("tbans.kick")) {
            source.sendMessage(mm.deserialize(languageManager.getMessage("no_permission")));
            return;
        }

        String[] args = invocation.arguments();

        if (args.length < 1) {
            source.sendMessage(mm.deserialize(languageManager.getMessage("kick.usage")));
            return;
        }

        String targetName = args[0];
        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "No reason provided.";

        Optional<Player> targetPlayer = server.getPlayer(targetName);
        if (targetPlayer.isPresent()) {
            if (targetPlayer.get().hasPermission("tbans.god")) {
                source.sendMessage(mm.deserialize(languageManager.getMessage("kick.cannot_punish")));
                return;
            }

            UUID targetUuid = targetPlayer.get().getUniqueId();
            UUID executorUuid = source instanceof Player ? ((Player) source).getUniqueId() : CONSOLE_UUID;
            banManager.kickPlayer(targetUuid, executorUuid, reason);

            String disconnectMsg = languageManager.getMessage("kick.disconnect_screen").replace("{reason}", reason);
            targetPlayer.get().disconnect(mm.deserialize(disconnectMsg));
            
            String successMsg = languageManager.getMessage("kick.success")
                    .replace("{player}", targetName)
                    .replace("{reason}", reason);
            source.sendMessage(mm.deserialize(successMsg));

            String executor = source instanceof Player ? ((Player) source).getUsername() : "Console";
            String broadcastMsg = languageManager.getMessage("kick.broadcast")
                    .replace("{player}", targetName)
                    .replace("{executor}", executor)
                    .replace("{reason}", reason);
            for (Player p : server.getAllPlayers()) {
                if (p.hasPermission("tbans.notify")) {
                    p.sendMessage(mm.deserialize(broadcastMsg));
                }
            }
        } else {
            source.sendMessage(mm.deserialize(languageManager.getMessage("kick.not_found")));
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            return server.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}

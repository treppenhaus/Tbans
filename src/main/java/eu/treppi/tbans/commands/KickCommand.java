package eu.treppi.tbans.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import eu.treppi.tbans.manager.LanguageManager;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Arrays;
import java.util.Optional;

public class KickCommand implements SimpleCommand {

    private final ProxyServer server;
    private final LanguageManager languageManager;
    private static final MiniMessage mm = MiniMessage.miniMessage();

    public KickCommand(ProxyServer server, LanguageManager languageManager) {
        this.server = server;
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
            String disconnectMsg = languageManager.getMessage("kick.disconnect_screen").replace("{reason}", reason);
            targetPlayer.get().disconnect(mm.deserialize(disconnectMsg));
            
            String successMsg = languageManager.getMessage("kick.success")
                    .replace("{player}", targetName)
                    .replace("{reason}", reason);
            source.sendMessage(mm.deserialize(successMsg));
        } else {
            source.sendMessage(mm.deserialize(languageManager.getMessage("kick.not_found")));
        }
    }
}

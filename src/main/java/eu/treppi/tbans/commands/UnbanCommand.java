package eu.treppi.tbans.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import eu.treppi.tbans.manager.BanManager;
import eu.treppi.tbans.manager.LanguageManager;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Arrays;

public class UnbanCommand implements SimpleCommand {

    private final ProxyServer server;
    private final BanManager banManager;
    private final LanguageManager languageManager;
    private static final MiniMessage mm = MiniMessage.miniMessage();

    public UnbanCommand(ProxyServer server, BanManager banManager, LanguageManager languageManager) {
        this.server = server;
        this.banManager = banManager;
        this.languageManager = languageManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!source.hasPermission("tbans.unban")) {
            source.sendMessage(mm.deserialize(languageManager.getMessage("no_permission")));
            return;
        }

        String[] args = invocation.arguments();

        if (args.length < 1) {
            source.sendMessage(mm.deserialize(languageManager.getMessage("unban.usage")));
            return;
        }

        String targetName = args[0];
        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "No reason provided.";

        if (!banManager.isBanned(targetName)) {
            source.sendMessage(mm.deserialize(languageManager.getMessage("unban.not_banned")));
            return;
        }

        String unbannerName = source instanceof Player ? ((Player) source).getUsername() : "Console";
        banManager.unbanPlayer(targetName, unbannerName, reason);

        String successMsg = languageManager.getMessage("unban.success")
                .replace("{player}", targetName)
                .replace("{reason}", reason);
        source.sendMessage(mm.deserialize(successMsg));

        String broadcastMsg = languageManager.getMessage("unban.broadcast")
                .replace("{player}", targetName)
                .replace("{executor}", unbannerName)
                .replace("{reason}", reason);
        for (Player p : server.getAllPlayers()) {
            if (p.hasPermission("tbans.notify")) {
                p.sendMessage(mm.deserialize(broadcastMsg));
            }
        }
    }
}

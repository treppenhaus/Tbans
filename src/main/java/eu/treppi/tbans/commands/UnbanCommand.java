package eu.treppi.tbans.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import eu.treppi.tbans.manager.BanManager;
import eu.treppi.tbans.manager.ConfigManager;
import eu.treppi.tbans.manager.LanguageManager;
import eu.treppi.tbans.util.MessageUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class UnbanCommand implements SimpleCommand {

    private final ProxyServer server;
    private final BanManager banManager;
    private final LanguageManager languageManager;
    private final ConfigManager configManager;
    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final UUID CONSOLE_UUID = new UUID(0, 0);

    public UnbanCommand(ProxyServer server, BanManager banManager, LanguageManager languageManager,
            ConfigManager configManager) {
        this.server = server;
        this.banManager = banManager;
        this.languageManager = languageManager;
        this.configManager = configManager;
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
        String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : "No reason provided.";

        // Asynchronous resolution
        banManager.resolveUuid(targetName).thenAccept(uuid -> {
            if (uuid == null) {
                try {
                    UUID directUuid = UUID.fromString(targetName);
                    executeUnban(source, directUuid, targetName, reason);
                } catch (IllegalArgumentException e) {
                    source.sendMessage(mm.deserialize(languageManager.getMessage("unban.not_found")));
                }
            } else {
                executeUnban(source, uuid, targetName, reason);
            }
        });
    }

    private void executeUnban(CommandSource source, UUID targetUuid, String targetName, String reason) {
        if (!banManager.isBanned(targetUuid)) {
            source.sendMessage(mm.deserialize(languageManager.getMessage("unban.not_banned")));
            return;
        }

        UUID executorUuid = source instanceof Player ? ((Player) source).getUniqueId() : CONSOLE_UUID;
        String unbannerName = source instanceof Player ? ((Player) source).getUsername() : "Console";
        banManager.unbanPlayer(targetUuid, executorUuid, reason);

        String successMsg = MessageUtils.format(languageManager.getMessage("unban.success"), targetName, null, "",
                reason, configManager);
        source.sendMessage(mm.deserialize(successMsg));

        String broadcastMsg = MessageUtils.format(languageManager.getMessage("unban.broadcast"), targetName,
                unbannerName, "", reason, configManager);
        for (Player p : server.getAllPlayers()) {
            if (p.hasPermission("tbans.notify")) {
                p.sendMessage(mm.deserialize(broadcastMsg));
            }
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!invocation.source().hasPermission("tbans.unban")) {
            return List.of();
        }

        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            return banManager.getBannedNames().stream()
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .collect(java.util.stream.Collectors.toList());
        }
        return List.of();
    }
}

package eu.treppi.tbans.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import eu.treppi.tbans.manager.BanManager;
import eu.treppi.tbans.manager.LanguageManager;
import eu.treppi.tbans.util.TimeUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class BanCommand implements SimpleCommand {

    private final ProxyServer server;
    private final BanManager banManager;
    private final LanguageManager languageManager;
    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final UUID CONSOLE_UUID = new UUID(0, 0);

    public BanCommand(ProxyServer server, BanManager banManager, LanguageManager languageManager) {
        this.server = server;
        this.banManager = banManager;
        this.languageManager = languageManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!source.hasPermission("tbans.ban")) {
            source.sendMessage(mm.deserialize(languageManager.getMessage("no_permission")));
            return;
        }

        String[] args = invocation.arguments();

        if (args.length < 1) {
            source.sendMessage(mm.deserialize(languageManager.getMessage("ban.usage")));
            return;
        }

        String targetName = args[0];
        long duration;
        String timeStr;
        String reason;

        if (args.length >= 2) {
            long parsedDuration = TimeUtils.parseTime(args[1]);
            if (parsedDuration != -1) {
                duration = parsedDuration;
                timeStr = args[1];
                reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                        : "No reason provided.";
            } else {
                duration = -1;
                timeStr = "Permanent";
                reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            }
        } else {
            duration = -1;
            timeStr = "Permanent";
            reason = "No reason provided.";
        }

        // Asynchronous resolution
        banManager.resolveUuid(targetName).thenAccept(uuid -> {
            if (uuid == null) {
                try {
                    UUID directUuid = UUID.fromString(targetName);
                    executeBan(source, directUuid, targetName, duration, timeStr, reason);
                } catch (IllegalArgumentException e) {
                    source.sendMessage(mm.deserialize(languageManager.getMessage("ban.not_found")));
                }
            } else {
                executeBan(source, uuid, targetName, duration, timeStr, reason);
            }
        });
    }

    private void executeBan(CommandSource source, UUID targetUuid, String targetName, long duration, String timeStr,
            String reason) {
        Optional<Player> targetPlayer = server.getPlayer(targetUuid);
        if (targetPlayer.isPresent()) {
            if (targetPlayer.get().hasPermission("tbans.god")) {
                source.sendMessage(mm.deserialize(languageManager.getMessage("ban.cannot_punish")));
                return;
            }
            String disconnectMsg = languageManager.getMessage("ban.disconnect_screen")
                    .replace("{duration}", timeStr)
                    .replace("{reason}", reason);
            targetPlayer.get().disconnect(mm.deserialize(disconnectMsg));
        }

        UUID bannerUuid = source instanceof Player ? ((Player) source).getUniqueId() : CONSOLE_UUID;
        String bannerName = source instanceof Player ? ((Player) source).getUsername() : "Console";

        banManager.banPlayer(targetUuid, bannerUuid, duration, reason);

        String successMsg = languageManager.getMessage("ban.success")
                .replace("{player}", targetName)
                .replace("{duration}", timeStr)
                .replace("{reason}", reason);
        source.sendMessage(mm.deserialize(successMsg));

        String broadcastMsg = languageManager.getMessage("ban.broadcast")
                .replace("{player}", targetName)
                .replace("{executor}", bannerName)
                .replace("{duration}", timeStr)
                .replace("{reason}", reason);
        for (Player p : server.getAllPlayers()) {
            if (p.hasPermission("tbans.notify")) {
                p.sendMessage(mm.deserialize(broadcastMsg));
            }
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!invocation.source().hasPermission("tbans.ban")) {
            return List.of();
        }

        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase();

            Set<String> suggestions = new HashSet<>();
            suggestions.addAll(server.getAllPlayers().stream().map(Player::getUsername).toList());
            suggestions.addAll(banManager.getAllCachedNames());

            return suggestions.stream()
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}

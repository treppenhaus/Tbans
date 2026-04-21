package eu.treppi.tbans.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import eu.treppi.tbans.manager.BanManager;
import eu.treppi.tbans.manager.ConfigManager;
import eu.treppi.tbans.manager.IpLogManager;
import eu.treppi.tbans.manager.LanguageManager;
import eu.treppi.tbans.util.TimeUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class BanIpCommand implements SimpleCommand {
    private final ProxyServer server;
    private final BanManager banManager;
    private final LanguageManager languageManager;
    private final ConfigManager configManager;
    private final IpLogManager ipLogManager;
    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final UUID CONSOLE_UUID = new UUID(0, 0);

    private static final Pattern IP_PATTERN = Pattern.compile(
            "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$");

    public BanIpCommand(ProxyServer server, BanManager banManager, LanguageManager languageManager,
            ConfigManager configManager, IpLogManager ipLogManager) {
        this.server = server;
        this.banManager = banManager;
        this.languageManager = languageManager;
        this.configManager = configManager;
        this.ipLogManager = ipLogManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!source.hasPermission("tbans.banip")) {
            source.sendMessage(mm.deserialize(languageManager.getMessage("no_permission")));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length < 1) {
            source.sendMessage(mm.deserialize("<red>Usage: /banip <player|ip> [time] [reason]"));
            return;
        }

        String target = args[0];
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

        UUID executorUuid = source instanceof Player ? ((Player) source).getUniqueId() : CONSOLE_UUID;
        String executorName = source instanceof Player ? ((Player) source).getUsername() : "Console";

        if (IP_PATTERN.matcher(target).matches()) {
            String hash = ipLogManager.hashIp(target, configManager.getSalt());
            executeIpBan(source, hash, target, executorUuid, executorName, duration, timeStr, reason);
        } else {
            banManager.resolveUuid(target).thenAccept(uuid -> {
                if (uuid == null) {
                    source.sendMessage(mm.deserialize(languageManager.getMessage("ban.not_found")));
                    return;
                }

                List<String> hashes = ipLogManager.getIpHashes(uuid);
                if (hashes.isEmpty()) {
                    Optional<Player> player = server.getPlayer(uuid);
                    if (player.isPresent()) {
                        String ip = player.get().getRemoteAddress().getAddress().getHostAddress();
                        hashes = List.of(ipLogManager.hashIp(ip, configManager.getSalt()));
                    }
                }

                if (hashes.isEmpty()) {
                    source.sendMessage(mm.deserialize("<red>Could not find any IP history for this player."));
                    return;
                }

                for (String hash : hashes) {
                    executeIpBan(source, hash, target, executorUuid, executorName, duration, timeStr, reason);
                }
            });
        }
    }

    private void executeIpBan(CommandSource source, String hash, String targetName, UUID executorUuid,
            String executorName,
            long duration, String timeStr, String reason) {
        banManager.banIp(hash, executorUuid, duration, reason);

        // Disconnect players with this IP hash
        for (Player p : server.getAllPlayers()) {
            String pIp = p.getRemoteAddress().getAddress().getHostAddress();
            String pHash = ipLogManager.hashIp(pIp, configManager.getSalt());
            if (pHash.equals(hash)) {
                String disconnectMsg = languageManager.getMessage("ban.disconnect_screen")
                        .replace("{duration}", timeStr)
                        .replace("{reason}", reason + " (IP Ban)");
                p.disconnect(mm.deserialize(disconnectMsg));
            }
        }

        source.sendMessage(mm.deserialize("<green>IP Ban successful for <yellow>" + targetName));

        // Broadcast to staff
        String broadcastMsg = languageManager.getMessage("ban.broadcast")
                .replace("{player}", targetName + " (IP)")
                .replace("{executor}", executorName)
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
        if (!invocation.source().hasPermission("tbans.banip"))
            return List.of();

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

package eu.treppi.tbans.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import eu.treppi.tbans.manager.BanEvent;
import eu.treppi.tbans.manager.BanManager;
import eu.treppi.tbans.manager.ConfigManager;
import eu.treppi.tbans.manager.IpLogManager;
import eu.treppi.tbans.manager.LanguageManager;
import eu.treppi.tbans.util.MessageUtils;
import eu.treppi.tbans.util.TimeUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class BanCommand implements SimpleCommand {

    private final ProxyServer server;
    private final BanManager banManager;
    private final LanguageManager languageManager;
    private final ConfigManager configManager;
    private final IpLogManager ipLogManager;
    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final UUID CONSOLE_UUID = new UUID(0, 0);

    public BanCommand(ProxyServer server, BanManager banManager, LanguageManager languageManager,
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
        if (!source.hasPermission("tbans.ban")) {
            source.sendMessage(mm.deserialize(languageManager.getMessage("no_permission")));
            return;
        }

        String[] args = invocation.arguments();

        if (args.length < 1) {
            source.sendMessage(mm.deserialize(languageManager.getMessage("ban.usage")));
            return;
        }

        boolean banAlts = false;
        List<String> argList = new ArrayList<>(Arrays.asList(args));
        if (argList.remove("--alts")) {
            banAlts = true;
        }
        String[] filteredArgs = argList.toArray(new String[0]);

        if (filteredArgs.length < 1) {
            source.sendMessage(mm.deserialize(languageManager.getMessage("ban.usage")));
            return;
        }

        String targetName = filteredArgs[0];
        long duration;
        String timeStr;
        String reason;

        if (filteredArgs.length >= 2) {
            long parsedDuration = TimeUtils.parseTime(filteredArgs[1]);
            if (parsedDuration != -1) {
                duration = parsedDuration;
                timeStr = filteredArgs[1];
                reason = filteredArgs.length > 2 ? String.join(" ", Arrays.copyOfRange(filteredArgs, 2, filteredArgs.length))
                        : "No reason provided.";
            } else {
                duration = -1;
                timeStr = "Permanent";
                reason = String.join(" ", Arrays.copyOfRange(filteredArgs, 1, filteredArgs.length));
            }
        } else {
            duration = -1;
            timeStr = "Permanent";
            reason = "No reason provided.";
        }

        final boolean finalBanAlts = banAlts;
        // Asynchronous resolution
        banManager.resolveUuid(targetName).thenAccept(uuid -> {
            if (uuid == null) {
                try {
                    UUID directUuid = UUID.fromString(targetName);
                    executeBan(source, directUuid, targetName, duration, timeStr, reason, finalBanAlts);
                } catch (IllegalArgumentException e) {
                    source.sendMessage(mm.deserialize(languageManager.getMessage("ban.not_found")));
                }
            } else {
                executeBan(source, uuid, targetName, duration, timeStr, reason, finalBanAlts);
            }
        });
    }

    private void executeBan(CommandSource source, UUID targetUuid, String targetName, long duration, String timeStr,
            String reason, boolean banAlts) {
        Optional<Player> targetPlayer = server.getPlayer(targetUuid);
        if (targetPlayer.isPresent() && targetPlayer.get().hasPermission("tbans.god")) {
            source.sendMessage(mm.deserialize(languageManager.getMessage("ban.cannot_punish")));
            return;
        }

        UUID bannerUuid = source instanceof Player ? ((Player) source).getUniqueId() : CONSOLE_UUID;
        String bannerName = source instanceof Player ? ((Player) source).getUsername() : "Console";

        banSinglePlayer(source, targetUuid, targetName, bannerUuid, bannerName, duration, timeStr, reason);

        if (banAlts) {
            int daysLimit = configManager.getAltLinkDays();
            Map<UUID, Long> alts = ipLogManager.getAlts(targetUuid, daysLimit);
            if (alts != null && !alts.isEmpty()) {
                for (UUID altUuid : alts.keySet()) {
                    if (banManager.isBanned(altUuid)) {
                        continue;
                    }
                    Optional<Player> altPlayer = server.getPlayer(altUuid);
                    if (altPlayer.isPresent() && altPlayer.get().hasPermission("tbans.god")) {
                        continue;
                    }
                    String altName = banManager.getNameFromUuid(altUuid);
                    banSinglePlayer(source, altUuid, altName, bannerUuid, bannerName, duration, timeStr, reason);
                }
            }
        }
    }

    private void banSinglePlayer(CommandSource source, UUID targetUuid, String targetName, UUID bannerUuid, String bannerName, long duration, String timeStr, String reason) {
        Optional<Player> targetPlayer = server.getPlayer(targetUuid);
        BanEvent event = banManager.banPlayer(targetUuid, bannerUuid, duration, reason);

        if (targetPlayer.isPresent()) {
            String disconnectMsg = languageManager.getMessage("ban.disconnect_screen")
                    .replace("{duration}", timeStr)
                    .replace("{reason}", reason)
                    .replace("{ban_code}", event.getCode());
            targetPlayer.get().disconnect(mm.deserialize(disconnectMsg));
        }

        String resolvedTargetName = banManager.getNameFromUuid(targetUuid);
        String successMsg = MessageUtils.format(languageManager.getMessage("ban.success"), resolvedTargetName, null, timeStr,
                reason, event.getCode(), configManager);
        source.sendMessage(mm.deserialize(successMsg));

        String broadcastMsg = MessageUtils.format(languageManager.getMessage("ban.broadcast"), resolvedTargetName, bannerName,
                timeStr, reason, event.getCode(), configManager);
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

        if (args.length >= 2) {
            String lastArg = args[args.length - 1];
            if (!lastArg.isEmpty() && "--alts".startsWith(lastArg.toLowerCase())) {
                return List.of("--alts");
            }
        }

        return List.of();
    }
}

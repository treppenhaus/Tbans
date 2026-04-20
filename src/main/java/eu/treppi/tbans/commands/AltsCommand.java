package eu.treppi.tbans.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import eu.treppi.tbans.manager.BanManager;
import eu.treppi.tbans.manager.ConfigManager;
import eu.treppi.tbans.manager.IpLogManager;
import eu.treppi.tbans.manager.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class AltsCommand implements SimpleCommand {
    private final ProxyServer server;
    private final BanManager banManager;
    private final LanguageManager languageManager;
    private final ConfigManager configManager;
    private final IpLogManager ipLogManager;
    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    public AltsCommand(ProxyServer server, BanManager banManager, LanguageManager languageManager,
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
        if (!source.hasPermission("tbans.command.alts")) {
            source.sendMessage(mm.deserialize(languageManager.getMessage("no_permission")));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length < 1) {
            source.sendMessage(mm.deserialize("<red>Usage: /alts <player>"));
            return;
        }

        String targetName = args[0];
        source.sendMessage(mm.deserialize("<gray><italic>Searching for alts, this may take some time..."));

        banManager.resolveUuid(targetName).thenAcceptAsync(uuid -> {
            if (uuid == null) {
                source.sendMessage(mm.deserialize(languageManager.getMessage("ban.not_found")));
                return;
            }

            int daysLimit = configManager.getAltLinkDays();
            Map<UUID, Long> alts = ipLogManager.getAlts(uuid, daysLimit);
            Map<UUID, Integer> recursiveAlts = ipLogManager.getRecursiveAlts(uuid, 20);

            // Remove direct alts from recursive results to avoid duplication
            for (UUID directAlt : alts.keySet()) {
                recursiveAlts.remove(directAlt);
            }

            source.sendMessage(mm.deserialize(
                    "<gray>Linked accounts for <yellow>" + targetName + " <gray>(limit: " + daysLimit + " days):"));

            if (alts.isEmpty()) {
                source.sendMessage(mm.deserialize("<red>No linked accounts found."));
            } else {
                for (Map.Entry<UUID, Long> entry : alts.entrySet()) {
                    UUID altUuid = entry.getKey();
                    long firstSeen = entry.getValue();
                    String altName = banManager.getNameFromUuid(altUuid);
                    String dateStr = DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(firstSeen));

                    source.sendMessage(mm.deserialize("<gray>- <yellow>" + altName + " <gray>(Joined: " + dateStr
                            + ") <italic><dark_gray>(Reason: same-iphash)"));
                }
            }

            if (!recursiveAlts.isEmpty()) {
                source.sendMessage(mm.deserialize(""));
                source.sendMessage(mm.deserialize("<dark_gray><italic>Further search (up to 20 hops):"));
                for (Map.Entry<UUID, Integer> entry : recursiveAlts.entrySet()) {
                    UUID altUuid = entry.getKey();
                    int hops = entry.getValue();
                    String altName = banManager.getNameFromUuid(altUuid);
                    source.sendMessage(
                            mm.deserialize("<dark_gray>- <gray>" + altName + " <dark_gray>(Hops: " + hops + ")"));
                }
            }
        });
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!invocation.source().hasPermission("tbans.command.alts"))
            return List.of();

        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            Set<String> suggestions = new HashSet<>();
            suggestions.addAll(server.getAllPlayers().stream().map(Player::getUsername).collect(Collectors.toSet()));
            suggestions.addAll(banManager.getAllCachedNames());
            return suggestions.stream()
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}

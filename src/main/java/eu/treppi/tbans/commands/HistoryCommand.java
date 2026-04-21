package eu.treppi.tbans.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import eu.treppi.tbans.manager.BanEvent;
import eu.treppi.tbans.manager.BanManager;
import eu.treppi.tbans.manager.LanguageManager;
import eu.treppi.tbans.util.TimeUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class HistoryCommand implements SimpleCommand {

    private final ProxyServer server;
    private final BanManager banManager;
    private final LanguageManager languageManager;
    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    public HistoryCommand(ProxyServer server, BanManager banManager, LanguageManager languageManager) {
        this.server = server;
        this.banManager = banManager;
        this.languageManager = languageManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!source.hasPermission("tbans.history")) {
            source.sendMessage(mm.deserialize(languageManager.getMessage("no_permission")));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length < 1) {
            source.sendMessage(mm.deserialize(languageManager.getMessage("history.usage")));
            return;
        }

        String targetName = args[0];

        // Check if it's a ban code
        BanManager.CodeLookupResult lookup = banManager.lookupByCode(targetName);
        if (lookup != null) {
            if (lookup.type == BanManager.CodeLookupResult.Type.UUID) {
                UUID uuid = UUID.fromString(lookup.value);
                String resolvedName = banManager.getNameFromUuid(uuid);
                showHistory(source, uuid, resolvedName);
                return;
            } else if (lookup.type == BanManager.CodeLookupResult.Type.IP_HASH) {
                showIpHistory(source, lookup.value);
                return;
            }
        }

        // Asynchronous resolution
        banManager.resolveUuid(targetName).thenAccept(uuid -> {
            if (uuid == null) {
                try {
                    UUID directUuid = UUID.fromString(targetName);
                    showHistory(source, directUuid, targetName);
                } catch (IllegalArgumentException e) {
                    source.sendMessage(mm.deserialize(languageManager.getMessage("history.not_found")));
                }
            } else {
                showHistory(source, uuid, targetName);
            }
        });
    }

    private void showHistory(CommandSource source, UUID targetUuid, String targetName) {
        List<BanEvent> events = banManager.getEvents(targetUuid);

        String banStatus;
        if (events.isEmpty()) {
            banStatus = languageManager.getMessage("history.status_never");
        } else if (banManager.isBanned(targetUuid)) {
            banStatus = languageManager.getMessage("history.status_banned");
        } else {
            banStatus = languageManager.getMessage("history.status_expired");
        }

        String header = languageManager.getMessage("history.header").replace("{player}", targetName);
        source.sendMessage(mm.deserialize(header));
        source.sendMessage(mm.deserialize(banStatus));
        String divider = languageManager.getMessage("history.divider");
        source.sendMessage(mm.deserialize(divider));

        if (events.isEmpty()) {
            String emptyMsg = languageManager.getMessage("history.empty").replace("{player}", targetName);
            source.sendMessage(mm.deserialize(emptyMsg));
            return;
        }

        for (BanEvent event : events) {
            String date = DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(event.getTimestamp()));
            String typeColor;
            if (event.getType() == BanEvent.Type.BAN) {
                typeColor = languageManager.getMessage("blame.type_ban");
            } else if (event.getType() == BanEvent.Type.UNBAN) {
                typeColor = languageManager.getMessage("blame.type_unban");
            } else {
                typeColor = "<yellow>KICK</yellow>";
            }

            String expiryInfo = "";
            if (event.getType() == BanEvent.Type.BAN) {
                if (event.getExpiry() == -1) {
                    expiryInfo = languageManager.getMessage("history.expiry_permanent");
                } else if (event.getExpiry() > 0) {
                    String expiry = DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(event.getExpiry()));
                    long remaining = event.getExpiry() - System.currentTimeMillis();
                    if (remaining > 0) {
                        String remainingStr = TimeUtils.formatRemainingTime(remaining);
                        expiryInfo = languageManager.getMessage("history.expiry_until_left")
                                .replace("{expiry}", expiry)
                                .replace("{left}", remainingStr);
                    } else {
                        expiryInfo = languageManager.getMessage("history.expiry_until")
                                .replace("{expiry}", expiry);
                    }
                }
            }

            // USE NAME INSTEAD OF UUID
            String executorName = banManager.getNameFromUuid(event.getExecutorUUID());

            String codeInfo = event.getCode() != null ? " <gray>[Code: " + event.getCode() + "]</gray>" : "";

            String msg = languageManager.getMessage("history.action_line")
                    .replace("{date}", date)
                    .replace("{type}", typeColor)
                    .replace("{executor}", executorName)
                    .replace("{reason}", event.getReason())
                    .replace("{expiry_info}", expiryInfo) + codeInfo;

            source.sendMessage(mm.deserialize(msg));
        }
        source.sendMessage(mm.deserialize(divider));
    }

    private void showIpHistory(CommandSource source, String ipHash) {
        List<BanEvent> events = banManager.getIpEvents(ipHash);
        String header = "<gradient:#ff5555:#aa0000><b>History for IP Hash: " + ipHash.substring(0, 10)
                + "...</b></gradient>";
        source.sendMessage(mm.deserialize(header));

        String divider = languageManager.getMessage("history.divider");
        source.sendMessage(mm.deserialize(divider));

        if (events.isEmpty()) {
            source.sendMessage(mm.deserialize("<red>No IP history found."));
            return;
        }

        for (BanEvent event : events) {
            String date = DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(event.getTimestamp()));
            String typeColor = event.getType() == BanEvent.Type.BAN ? "<red>BAN</red>" : "<green>UNBAN</green>";

            String expiryInfo = "";
            if (event.getType() == BanEvent.Type.BAN && event.getExpiry() > 0) {
                expiryInfo = " <gray>(until " + DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(event.getExpiry()))
                        + ")</gray>";
            }

            String executorName = banManager.getNameFromUuid(event.getExecutorUUID());
            String codeInfo = event.getCode() != null ? " <gray>[Code: " + event.getCode() + "]</gray>" : "";

            String msg = "<gray>[" + date + "] " + typeColor + " by " + executorName + ": " + event.getReason()
                    + expiryInfo + codeInfo;
            source.sendMessage(mm.deserialize(msg));
        }
        source.sendMessage(mm.deserialize(divider));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!invocation.source().hasPermission("tbans.history")) {
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

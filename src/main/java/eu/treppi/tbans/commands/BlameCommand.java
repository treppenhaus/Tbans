package eu.treppi.tbans.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import eu.treppi.tbans.manager.BanManager;
import eu.treppi.tbans.manager.LanguageManager;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

public class BlameCommand implements SimpleCommand {

    private final ProxyServer server;
    private final BanManager banManager;
    private final LanguageManager languageManager;
    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    public BlameCommand(ProxyServer server, BanManager banManager, LanguageManager languageManager) {
        this.server = server;
        this.banManager = banManager;
        this.languageManager = languageManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!source.hasPermission("tbans.blame")) {
            source.sendMessage(mm.deserialize(languageManager.getMessage("no_permission")));
            return;
        }

        String[] args = invocation.arguments();

        if (args.length < 1) {
            source.sendMessage(mm.deserialize(languageManager.getMessage("blame.usage")));
            return;
        }

        String staffName = args[0];
        
        // Asynchronous resolution
        banManager.resolveUuid(staffName).thenAccept(uuid -> {
            if (uuid == null) {
                try {
                    UUID directUuid = UUID.fromString(staffName);
                    showBlame(source, directUuid, staffName);
                } catch (IllegalArgumentException e) {
                    source.sendMessage(mm.deserialize(languageManager.getMessage("blame.not_found")));
                }
            } else {
                showBlame(source, uuid, staffName);
            }
        });
    }

    private void showBlame(CommandSource source, UUID staffUuid, String staffName) {
        List<BanManager.BanEvent> events = banManager.getEventsByExecutor(staffUuid);

        if (events.isEmpty()) {
            String emptyMsg = languageManager.getMessage("blame.empty").replace("{staff}", staffName);
            source.sendMessage(mm.deserialize(emptyMsg));
            return;
        }

        int totalActions = events.size();
        long banCount = events.stream().filter(e -> e.getType() == BanManager.BanEvent.Type.BAN).count();
        long unbanCount = events.stream().filter(e -> e.getType() == BanManager.BanEvent.Type.UNBAN).count();
        long kickCount = events.stream().filter(e -> e.getType() == BanManager.BanEvent.Type.KICK).count();

        String header = languageManager.getMessage("blame.header").replace("{staff}", staffName);
        source.sendMessage(mm.deserialize(header));

        String totals = languageManager.getMessage("blame.totals")
                .replace("{total}", String.valueOf(totalActions))
                .replace("{bans}", String.valueOf(banCount))
                .replace("{unbans}", String.valueOf(unbanCount));
        source.sendMessage(mm.deserialize(totals));
        source.sendMessage(mm.deserialize("<gray>Kicks: <yellow>" + kickCount));

        source.sendMessage(mm.deserialize(languageManager.getMessage("blame.recent_header")));

        int startIndex = Math.max(0, events.size() - 5);
        for (int i = events.size() - 1; i >= startIndex; i--) {
            BanManager.BanEvent event = events.get(i);
            String date = DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(event.getTimestamp()));
            String typeColor;
            if (event.getType() == BanManager.BanEvent.Type.BAN) {
                typeColor = languageManager.getMessage("blame.type_ban");
            } else if (event.getType() == BanManager.BanEvent.Type.UNBAN) {
                typeColor = languageManager.getMessage("blame.type_unban");
            } else {
                typeColor = "<yellow>KICK</yellow>";
            }

            // USE NAME INSTEAD OF UUID
            String targetName = banManager.getNameFromUuid(event.getTargetUUID());

            String actionLine = languageManager.getMessage("blame.action_line")
                    .replace("{date}", date)
                    .replace("{type}", typeColor)
                    .replace("{target}", targetName)
                    .replace("{reason}", event.getReason());
            source.sendMessage(mm.deserialize(actionLine));
        }

        source.sendMessage(mm.deserialize(languageManager.getMessage("blame.footer")));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!invocation.source().hasPermission("tbans.blame")) {
            return List.of();
        }

        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            return server.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .collect(java.util.stream.Collectors.toList());
        }
        return List.of();
    }
}

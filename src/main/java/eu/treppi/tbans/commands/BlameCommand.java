package eu.treppi.tbans.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import eu.treppi.tbans.manager.BanManager;
import eu.treppi.tbans.manager.LanguageManager;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class BlameCommand implements SimpleCommand {

    private final BanManager banManager;
    private final LanguageManager languageManager;
    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    public BlameCommand(BanManager banManager, LanguageManager languageManager) {
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
        List<BanManager.BanEvent> events = banManager.getEventsByExecutor(staffName);

        if (events.isEmpty()) {
            String emptyMsg = languageManager.getMessage("blame.empty").replace("{staff}", staffName);
            source.sendMessage(mm.deserialize(emptyMsg));
            return;
        }

        int totalActions = events.size();
        long banCount = events.stream().filter(e -> e.getType() == BanManager.BanEvent.Type.BAN).count();
        long unbanCount = events.stream().filter(e -> e.getType() == BanManager.BanEvent.Type.UNBAN).count();

        String header = languageManager.getMessage("blame.header").replace("{staff}", staffName);
        source.sendMessage(mm.deserialize(header));
        
        String totals = languageManager.getMessage("blame.totals")
                .replace("{total}", String.valueOf(totalActions))
                .replace("{bans}", String.valueOf(banCount))
                .replace("{unbans}", String.valueOf(unbanCount));
        source.sendMessage(mm.deserialize(totals));
        
        source.sendMessage(mm.deserialize(languageManager.getMessage("blame.recent_header")));

        int startIndex = Math.max(0, events.size() - 5);
        for (int i = events.size() - 1; i >= startIndex; i--) {
            BanManager.BanEvent event = events.get(i);
            String date = DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(event.getTimestamp()));
            String typeColor = event.getType() == BanManager.BanEvent.Type.BAN ? 
                languageManager.getMessage("blame.type_ban") : languageManager.getMessage("blame.type_unban");
            
            String actionLine = languageManager.getMessage("blame.action_line")
                    .replace("{date}", date)
                    .replace("{type}", typeColor)
                    .replace("{target}", event.getTargetName())
                    .replace("{reason}", event.getReason());
            source.sendMessage(mm.deserialize(actionLine));
        }
        
        source.sendMessage(mm.deserialize(languageManager.getMessage("blame.footer")));
    }
}

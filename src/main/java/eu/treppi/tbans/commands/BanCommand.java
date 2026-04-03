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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

        if (args.length < 3) {
            source.sendMessage(mm.deserialize(languageManager.getMessage("ban.usage")));
            return;
        }

        String targetName = args[0];
        String timeStr = args[1];
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        long duration = TimeUtils.parseTime(timeStr);
        if (duration == -1) {
            source.sendMessage(mm.deserialize(languageManager.getMessage("ban.invalid_time")));
            return;
        }

        UUID targetUuid;
        Optional<Player> targetPlayer = server.getPlayer(targetName);
        if (targetPlayer.isPresent()) {
            if (targetPlayer.get().hasPermission("tbans.god")) {
                source.sendMessage(mm.deserialize(languageManager.getMessage("ban.cannot_punish")));
                return;
            }
            targetUuid = targetPlayer.get().getUniqueId();
            String disconnectMsg = languageManager.getMessage("ban.disconnect_screen")
                    .replace("{duration}", timeStr)
                    .replace("{reason}", reason);
            targetPlayer.get().disconnect(mm.deserialize(disconnectMsg));
        } else {
            try {
                targetUuid = UUID.fromString(targetName);
            } catch (IllegalArgumentException e) {
                source.sendMessage(mm.deserialize(languageManager.getMessage("ban.not_found")));
                return;
            }
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
        String[] args = invocation.arguments();
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            return server.getAllPlayers().stream()
                    .map(Player::getUsername)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}

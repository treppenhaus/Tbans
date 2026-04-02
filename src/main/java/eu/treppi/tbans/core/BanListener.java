package eu.treppi.tbans.core;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import eu.treppi.tbans.manager.BanManager;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class BanListener {
    private final BanManager banManager;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final MiniMessage mm = MiniMessage.miniMessage();

    public BanListener(BanManager banManager) {
        this.banManager = banManager;
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        if (banManager.isBanned(player.getUsername())) {
            BanManager.BanEvent latestBan = banManager.getLatestBan(player.getUsername());
            if (latestBan == null) return;
            
            String expiryStr = DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(latestBan.getExpiry()));
            long remaining = latestBan.getExpiry() - System.currentTimeMillis();
            String timeRemainingStr = eu.treppi.tbans.util.TimeUtils.formatRemainingTime(remaining);
            
            event.setResult(LoginEvent.ComponentResult.denied(
                mm.deserialize(
                    "<gradient:#ff5555:#aa0000><b>YOU ARE BANNED FROM THIS NETWORK!</b></gradient>\n\n" +
                    "<gray>Reason: <yellow>" + latestBan.getReason() + "</yellow>\n" +
                    "<gray>Expires: <yellow>" + expiryStr + "</yellow>\n" +
                    "<gray>Time remaining: <yellow>" + timeRemainingStr + "</yellow>\n" +
                    "\n" +
                    "<gradient:#ffaa00:#ffff55>Appeal at domain.com</gradient>"
                )
            ));
        }
    }
}

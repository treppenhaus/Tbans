package eu.treppi.tbans.core;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import eu.treppi.tbans.manager.BanManager;
import eu.treppi.tbans.manager.LanguageManager;
import eu.treppi.tbans.util.TimeUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class BanListener {
    private final BanManager banManager;
    private final LanguageManager languageManager;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final MiniMessage mm = MiniMessage.miniMessage();

    public BanListener(BanManager banManager, LanguageManager languageManager) {
        this.banManager = banManager;
        this.languageManager = languageManager;
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        if (banManager.isBanned(player.getUsername())) {
            BanManager.BanEvent latestBan = banManager.getLatestBan(player.getUsername());
            if (latestBan == null) return;
            
            String expiryStr = DATE_TIME_FORMATTER.format(Instant.ofEpochMilli(latestBan.getExpiry()));
            long remaining = latestBan.getExpiry() - System.currentTimeMillis();
            String timeRemainingStr = TimeUtils.formatRemainingTime(remaining);
            
            String msg = languageManager.getMessage("ban.login_denied")
                    .replace("{reason}", latestBan.getReason())
                    .replace("{expiry}", expiryStr)
                    .replace("{left}", timeRemainingStr);
                    
            event.setResult(LoginEvent.ComponentResult.denied(mm.deserialize(msg)));
        }
    }
}

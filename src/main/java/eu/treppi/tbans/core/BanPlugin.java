package eu.treppi.tbans.core;

import com.google.inject.Inject;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import eu.treppi.tbans.commands.BanCommand;
import eu.treppi.tbans.commands.BlameCommand;
import eu.treppi.tbans.commands.HistoryCommand;
import eu.treppi.tbans.commands.KickCommand;
import eu.treppi.tbans.commands.UnbanCommand;
import eu.treppi.tbans.manager.BanManager;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(id = "tbans", name = "Tbans", version = "1.0-SNAPSHOT", description = "lightweight simple ban plugin for velocity", authors = {
                "treppi" })
public class BanPlugin {

        @Inject
        private Logger logger;

        private final ProxyServer server;
        private final BanManager banManager;
        private final eu.treppi.tbans.manager.LanguageManager languageManager;

        @Inject
        public BanPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
                this.server = server;
                this.logger = logger;
                this.banManager = new BanManager(dataDirectory);
                this.languageManager = new eu.treppi.tbans.manager.LanguageManager(dataDirectory, "en_EN");
        }

        @Subscribe
        public void onProxyInitialization(ProxyInitializeEvent event) {
                server.getCommandManager().register(
                                server.getCommandManager().metaBuilder("ban").build(),
                                new BanCommand(server, banManager, languageManager));
                server.getCommandManager().register(
                                server.getCommandManager().metaBuilder("unban").build(),
                                new UnbanCommand(banManager, languageManager));

                server.getCommandManager().register(
                                server.getCommandManager().metaBuilder("history").build(),
                                new HistoryCommand(banManager, languageManager));
                server.getCommandManager().register(
                                server.getCommandManager().metaBuilder("checkban").build(),
                                new HistoryCommand(banManager, languageManager));
                server.getCommandManager().register(
                                server.getCommandManager().metaBuilder("kick").build(),
                                new KickCommand(server, languageManager));
                server.getCommandManager().register(
                                server.getCommandManager().metaBuilder("blame").build(),
                                new BlameCommand(banManager, languageManager));
                server.getEventManager().register(this, new BanListener(banManager, languageManager));
                logger.info("TBans has been enabled!");
        }
}

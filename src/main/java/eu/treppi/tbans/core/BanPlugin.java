package eu.treppi.tbans.core;

import com.google.inject.Inject;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import eu.treppi.tbans.commands.AltsCommand;
import eu.treppi.tbans.commands.BanCommand;
import eu.treppi.tbans.commands.BlameCommand;
import eu.treppi.tbans.commands.BanIpCommand;
import eu.treppi.tbans.commands.HistoryCommand;
import eu.treppi.tbans.commands.KickCommand;
import eu.treppi.tbans.commands.TbansCommand;
import eu.treppi.tbans.commands.UnbanCommand;
import eu.treppi.tbans.manager.BanManager;
import eu.treppi.tbans.manager.ConfigManager;
import eu.treppi.tbans.manager.IpLogManager;
import eu.treppi.tbans.manager.LanguageManager;
import eu.treppi.tbans.manager.ApiManager;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import eu.treppi.tbans.storage.Storage;
import eu.treppi.tbans.storage.StorageData;
import eu.treppi.tbans.storage.StorageFactory;
import org.slf4j.Logger;
import org.bstats.velocity.Metrics;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;

@Plugin(id = "tbans", name = "Tbans", version = "1.0-SNAPSHOT", description = "lightweight simple ban plugin for velocity", authors = {
                "treppi" })
public class BanPlugin {

        @Inject
        private Logger logger;

        private final ProxyServer server;
        private final BanManager banManager;
        private final LanguageManager languageManager;
        private final ConfigManager configManager;
        private final IpLogManager ipLogManager;
        private final ApiManager apiManager;
        private final Metrics.Factory metricsFactory;
        private final Path dataDirectory;
        private Storage storage;
        private String version = "unknown";
        private String buildNumber = "0";

        @Inject
        public BanPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory,
                        Metrics.Factory metricsFactory) {
                this.server = server;
                this.logger = logger;
                this.metricsFactory = metricsFactory;
                this.dataDirectory = dataDirectory;
                this.configManager = new ConfigManager(dataDirectory);
                this.languageManager = new LanguageManager(dataDirectory, "en_EN");
                this.storage = StorageFactory.createConfigured(configManager, dataDirectory, logger);
                this.banManager = new BanManager(storage);
                this.ipLogManager = new IpLogManager(storage);
                this.apiManager = new ApiManager(server, banManager, languageManager, configManager, ipLogManager);
                loadVersionInfo();
        }

        private void loadVersionInfo() {
                try (InputStream is = getClass().getClassLoader().getResourceAsStream("version.properties")) {
                        if (is != null) {
                                Properties props = new Properties();
                                props.load(is);
                                this.version = props.getProperty("version", "unknown");
                                this.buildNumber = props.getProperty("build", "0");
                        }
                } catch (Exception ignored) {
                }
        }

        @Subscribe
        public void onProxyInitialization(ProxyInitializeEvent event) {
                server.getCommandManager().register(
                                server.getCommandManager().metaBuilder("ban").build(),
                                new BanCommand(server, banManager, languageManager, configManager, ipLogManager));
                server.getCommandManager().register(
                                server.getCommandManager().metaBuilder("unban").build(),
                                new UnbanCommand(server, banManager, languageManager, configManager));

                server.getCommandManager().register(
                                server.getCommandManager().metaBuilder("history").build(),
                                new HistoryCommand(server, banManager, languageManager));
                server.getCommandManager().register(
                                server.getCommandManager().metaBuilder("checkban").build(),
                                new HistoryCommand(server, banManager, languageManager));
                server.getCommandManager().register(
                                server.getCommandManager().metaBuilder("kick").build(),
                                new KickCommand(server, banManager, languageManager, configManager));
                server.getCommandManager().register(
                                server.getCommandManager().metaBuilder("blame").build(),
                                new BlameCommand(server, banManager, languageManager));
                server.getCommandManager().register(
                                server.getCommandManager().metaBuilder("alts").build(),
                                new AltsCommand(server, banManager, languageManager, configManager, ipLogManager));
                server.getCommandManager().register(
                                server.getCommandManager().metaBuilder("banip").build(),
                                new BanIpCommand(server, banManager, languageManager, configManager, ipLogManager));
                server.getCommandManager().register(
                                server.getCommandManager().metaBuilder("tbans").build(),
                                new TbansCommand(version, buildNumber, languageManager, this::reload, this::migrate));
                server.getEventManager().register(this,
                                new BanListener(banManager, languageManager, configManager, ipLogManager));

                apiManager.start();

                int pluginId = 30882;
                Metrics metrics = metricsFactory.make(this, pluginId);

                logger.info("TBans has been enabled!");
                logger.info("API server started on port {}", configManager.getApiPort());
        }

        public void reload() {
                configManager.reload();
                languageManager.reload();
                reloadStorage();
                apiManager.stop();
                apiManager.start();
        }

        /** Reconnects the configured storage backend and reloads all cached data from it. */
        private synchronized void reloadStorage() {
                Storage newStorage = StorageFactory.createConfigured(configManager, dataDirectory, logger);
                Storage old = this.storage;
                this.storage = newStorage;
                banManager.setStorage(newStorage);
                ipLogManager.setStorage(newStorage);
                if (old != newStorage) {
                        old.close();
                }
        }

        /**
         * Copies all data from one backend to another (e.g. json -> mongodb). Existing entries in the
         * target with the same key are overwritten, everything else is kept. Blocking.
         *
         * @return a summary of what was migrated
         */
        public synchronized String migrate(String from, String to) {
                if (from.equals(to)) {
                        throw new IllegalArgumentException("Source and target storage are the same.");
                }
                Storage current = this.storage;
                Storage source = from.equals(current.getName()) ? current
                                : StorageFactory.create(from, configManager, dataDirectory);
                Storage target = null;
                try {
                        target = to.equals(current.getName()) ? current
                                        : StorageFactory.create(to, configManager, dataDirectory);
                        StorageData data = source.loadAll();
                        target.importAll(data);
                        if (target == current) {
                                // Refresh caches so the imported data is active immediately
                                banManager.setStorage(current);
                                ipLogManager.setStorage(current);
                        }
                        logger.info("Migrated {} -> {}: {}", from, to, data.summary());
                        return data.summary();
                } finally {
                        if (source != current) {
                                source.close();
                        }
                        if (target != null && target != current) {
                                target.close();
                        }
                }
        }

        @Subscribe
        public void onProxyShutdown(ProxyShutdownEvent event) {
                apiManager.stop();
                storage.close();
                logger.info("TBans has been disabled!");
        }
}

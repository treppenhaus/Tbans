package eu.treppi.tbans.storage;

import eu.treppi.tbans.manager.ConfigManager;
import org.slf4j.Logger;

import java.nio.file.Path;

public final class StorageFactory {
    private StorageFactory() {
    }

    /**
     * Creates the backend named by {@code type} ("json" or "mongodb").
     * Throws if MongoDB is selected but not reachable.
     */
    public static Storage create(String type, ConfigManager config, Path dataDirectory) {
        switch (type) {
            case "json":
                return new JsonStorage(dataDirectory);
            case "mongodb":
            case "mongo":
                return new MongoStorage(config.getMongoUri(), config.getMongoDatabase());
            default:
                throw new IllegalArgumentException("Unknown storage type '" + type + "' (use json or mongodb)");
        }
    }

    /**
     * Creates the backend configured in config.yml. If it cannot be created (e.g. MongoDB is down),
     * logs the error and falls back to JSON so bans are still enforced from the local files.
     */
    public static Storage createConfigured(ConfigManager config, Path dataDirectory, Logger logger) {
        String type = config.getStorageType();
        try {
            Storage storage = create(type, config, dataDirectory);
            logger.info("Using {} storage", storage.getName());
            return storage;
        } catch (Exception e) {
            logger.error("Could not initialize '{}' storage, falling back to JSON storage!", type, e);
            return new JsonStorage(dataDirectory);
        }
    }
}

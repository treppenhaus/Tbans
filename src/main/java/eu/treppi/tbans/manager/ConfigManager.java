package eu.treppi.tbans.manager;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class ConfigManager {
    private final File configFile;
    private Map<String, Object> configData;
    private final Yaml yaml;

    public ConfigManager(Path dataDirectory) {
        if (!dataDirectory.toFile().exists()) {
            dataDirectory.toFile().mkdirs();
        }
        this.configFile = dataDirectory.resolve("config.yml").toFile();

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        this.yaml = new Yaml(options);

        loadConfig();
        checkSalt();
        checkAltDays();
        checkApiPort();
        checkApiToken();
    }

    public void reload() {
        loadConfig();
        checkSalt();
        checkAltDays();
        checkApiPort();
        checkApiToken();
    }

    private void loadConfig() {
        if (!configFile.exists()) {
            configData = new HashMap<>();
            saveConfig();
            return;
        }

        try (InputStream in = new FileInputStream(configFile)) {
            configData = yaml.load(in);
            if (configData == null)
                configData = new HashMap<>();
        } catch (IOException e) {
            e.printStackTrace();
            configData = new HashMap<>();
        }
    }

    public void saveConfig() {
        try (Writer writer = new FileWriter(configFile)) {
            yaml.dump(configData, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void checkSalt() {
        if (!configData.containsKey("salt")) {
            long salt = new Random().nextLong();
            configData.put("salt", salt);
            saveConfig();
        }
    }

    private void checkAltDays() {
        if (!configData.containsKey("alt-link-days")) {
            configData.put("alt-link-days", 7);
            saveConfig();
        }
    }

    private void checkApiPort() {
        if (!configData.containsKey("api-port")) {
            configData.put("api-port", 8869);
            saveConfig();
        }
    }

    private void checkApiToken() {
        if (!configData.containsKey("api-token")) {
            String token = java.util.UUID.randomUUID().toString();
            configData.put("api-token", token);
            saveConfig();
        }
    }

    public int getApiPort() {
        Object port = configData.get("api-port");
        if (port instanceof Number) {
            return ((Number) port).intValue();
        }
        return 8869;
    }

    public String getApiToken() {
        Object token = configData.get("api-token");
        return token != null ? token.toString() : "";
    }

    public int getAltLinkDays() {
        Object days = configData.get("alt-link-days");
        if (days instanceof Number) {
            return ((Number) days).intValue();
        }
        return 7;
    }

    public long getSalt() {
        Object salt = configData.get("salt");
        if (salt instanceof Number) {
            return ((Number) salt).longValue();
        } else if (salt instanceof String) {
            try {
                return Long.parseLong((String) salt);
            } catch (NumberFormatException ignored) {
            }
        }
        return 0L;
    }
}

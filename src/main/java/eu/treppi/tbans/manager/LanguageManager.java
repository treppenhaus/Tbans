package eu.treppi.tbans.manager;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class LanguageManager {

    private final Path dataDirectory;
    private final String langCode;
    private Map<String, Object> config;

    public LanguageManager(Path dataDirectory, String langCode) {
        this.dataDirectory = dataDirectory;
        this.langCode = langCode;
        loadLanguage();
    }

    private void loadLanguage() {
        if (!dataDirectory.toFile().exists()) {
            dataDirectory.toFile().mkdirs();
        }

        String fileName = langCode + ".yml";
        File file = dataDirectory.resolve(fileName).toFile();

        if (!file.exists()) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(fileName)) {
                if (in == null) {
                    System.err.println("Language file " + fileName + " not found in resources. Falling back to en_EN.yml");
                    if (!langCode.equals("en_EN")) {
                        copyResource("en_EN.yml", dataDirectory.resolve("en_EN.yml").toFile());
                        file = dataDirectory.resolve("en_EN.yml").toFile();
                    }
                } else {
                    Files.copy(in, file.toPath());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try (InputStream in = new FileInputStream(file)) {
            Yaml yaml = new Yaml();
            config = yaml.load(in);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void copyResource(String resourceName, File destination) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (in != null) {
                Files.copy(in, destination.toPath());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getMessage(String path) {
        if (config == null) return "Missing configuration map";
        
        String[] keys = path.split("\\.");
        Map<String, Object> current = config;
        for (int i = 0; i < keys.length - 1; i++) {
            Object obj = current.get(keys[i]);
            if (obj instanceof Map) {
                current = (Map<String, Object>) obj;
            } else {
                return "Missing config key: " + path;
            }
        }

        Object obj = current.get(keys[keys.length - 1]);
        if (obj == null) return "Missing config key: " + path;
        
        String result = obj.toString();
        // Fallback or explicit fetch to prefix, preventing infinite recursion
        if (!path.equals("prefix") && config.containsKey("prefix")) {
            String prefix = config.get("prefix").toString();
            result = result.replace("{prefix}", prefix);
        }
        return result;
    }
}

package com.molview.app.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Lightweight workspace configuration. Values are read from config/app.properties
 * when it exists, otherwise sensible defaults keep the app portable.
 */
public final class AppConfig {
    private static final Properties PROPERTIES = load();

    private AppConfig() {
    }

    private static Properties load() {
        Properties properties = new Properties();
        Path path = Path.of("config", "app.properties");
        if (Files.isRegularFile(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            } catch (IOException ignored) {
            }
        }
        return properties;
    }

    public static int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(PROPERTIES.getProperty(key, Integer.toString(fallback)).strip());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    public static boolean getBoolean(String key, boolean fallback) {
        String value = PROPERTIES.getProperty(key);
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(value.strip());
    }
}

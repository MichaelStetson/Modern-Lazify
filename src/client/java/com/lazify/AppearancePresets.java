package com.lazify;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

final class AppearancePresets {
    private AppearancePresets() {}

    static List<String> list(LazifyConfig config) throws IOException {
        Path directory = directory(config);
        if (!Files.isDirectory(directory)) return List.of();
        try (var paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".properties"))
                    .map(path -> path.getFileName().toString())
                    .map(name -> name.substring(0, name.length() - ".properties".length()))
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }
    }

    static void save(LazifyConfig config, String requestedName) throws IOException {
        Path file = presetPath(config, requestedName);
        Properties values = new Properties();
        for (LegacySettingCatalog.Option option : presetOptions()) {
            String key = option.key();
            String value = switch (option.type()) {
                case BOOLEAN -> Boolean.toString(config.getBoolean(key));
                case INTEGER -> Integer.toString(config.getInt(key));
                case DECIMAL -> Double.toString(config.getDouble(key));
                case TEXT -> config.getString(key);
            };
            values.setProperty(key, value);
        }
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file)) {
            values.store(writer, "Lazify appearance preset");
        }
    }

    static boolean load(LazifyConfig config, String requestedName) throws IOException {
        Path file = presetPath(config, requestedName);
        if (!Files.isRegularFile(file)) return false;
        Properties values = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            values.load(reader);
        }
        for (LegacySettingCatalog.Option option : presetOptions()) {
            String raw = values.getProperty(option.key());
            if (raw == null) continue;
            try {
                switch (option.type()) {
                    case BOOLEAN -> config.setBoolean(option.key(), Boolean.parseBoolean(raw));
                    case INTEGER -> config.setInt(option.key(), Integer.parseInt(raw));
                    case DECIMAL -> config.setDouble(option.key(), Double.parseDouble(raw));
                    case TEXT -> config.setString(option.key(), raw);
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid or obsolete preset values leave the current setting unchanged.
            }
        }
        config.save();
        return true;
    }

    static boolean delete(LazifyConfig config, String requestedName) throws IOException {
        return Files.deleteIfExists(presetPath(config, requestedName));
    }

    private static List<LegacySettingCatalog.Option> presetOptions() {
        return LegacySettingCatalog.all().stream().filter(option -> option.section().equals("Appearance")
                || option.section().equals("Position") && !option.key().equals("overlayX")
                && !option.key().equals("overlayY") || option.key().equals("overlayTheme")).toList();
    }

    private static Path directory(LazifyConfig config) {
        return config.file().getParent().resolve("lazify").resolve("presets");
    }

    private static Path presetPath(LazifyConfig config, String requestedName) {
        String name = sanitize(requestedName);
        if (name.isBlank()) throw new IllegalArgumentException("Preset name must contain letters or numbers.");
        return directory(config).resolve(name + ".properties");
    }

    private static String sanitize(String requestedName) {
        StringBuilder safe = new StringBuilder();
        if (requestedName == null) return "";
        for (int i = 0; i < requestedName.trim().length() && safe.length() < 48; i++) {
            char character = requestedName.trim().charAt(i);
            if (Character.isLetterOrDigit(character) || character == '.' || character == '_' || character == '-') {
                safe.append(character);
            } else if (character == ' ') {
                safe.append('_');
            }
        }
        return safe.toString();
    }
}

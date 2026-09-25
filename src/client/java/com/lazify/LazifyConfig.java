package com.lazify;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;


final class LazifyConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("lazify");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("lazify.json");
    private final Map<String, Object> settings = new LinkedHashMap<>(LegacySettingCatalog.defaults());

    synchronized void load() {
        reload();
    }

    synchronized void reload() {
        settings.clear();
        settings.putAll(LegacySettingCatalog.defaults());
        if (!Files.exists(FILE)) {
            save();
            return;
        }
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(FILE));
            if (!parsed.isJsonObject()) {
                LOGGER.warn("Could not read {}; keeping safe defaults", FILE);
                return;
            }
            JsonObject json = parsed.getAsJsonObject();
            for (LegacySettingCatalog.Option option : LegacySettingCatalog.all()) {
                JsonElement value = json.get(option.key());
                if (value != null) {
                    Object safe = decode(option, value);
                    if (safe != null) settings.put(option.key(), safe);
                }
            }
        } catch (Exception e) {
            // Do not include the exception: parser diagnostics can contain API key text.
            LOGGER.warn("Could not read {}; keeping safe defaults", FILE);
        }
    }

    synchronized void save() {
        JsonObject json = new JsonObject();
        for (LegacySettingCatalog.Option option : LegacySettingCatalog.all()) {
            Object value = settings.get(option.key());
            switch (option.type()) {
                case BOOLEAN -> json.addProperty(option.key(), (Boolean) value);
                case INTEGER -> json.addProperty(option.key(), (Integer) value);
                case DECIMAL -> json.addProperty(option.key(), (Double) value);
                case TEXT -> json.addProperty(option.key(), (String) value);
            }
        }
        Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(temporary, GSON.toJson(json) + System.lineSeparator());
            try {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveUnsupported) {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.warn("Could not save {}", FILE);
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // Best effort cleanup only.
            }
        }
    }

    synchronized boolean getBoolean(String key) {
        return (Boolean) value(key, LegacySettingCatalog.ValueType.BOOLEAN);
    }

    synchronized int getInt(String key) {
        return (Integer) value(key, LegacySettingCatalog.ValueType.INTEGER);
    }

    synchronized double getDouble(String key) {
        return (Double) value(key, LegacySettingCatalog.ValueType.DECIMAL);
    }

    synchronized String getString(String key) {
        return (String) value(key, LegacySettingCatalog.ValueType.TEXT);
    }

    synchronized void setBoolean(String key, boolean value) {
        set(key, LegacySettingCatalog.ValueType.BOOLEAN, value);
    }

    synchronized void setInt(String key, int value) {
        set(key, LegacySettingCatalog.ValueType.INTEGER, value);
    }

    synchronized void setDouble(String key, double value) {
        set(key, LegacySettingCatalog.ValueType.DECIMAL, value);
    }

    synchronized void setString(String key, String value) {
        set(key, LegacySettingCatalog.ValueType.TEXT, value);
    }

    synchronized boolean overlayEnabled() {
        return getBoolean("overlayEnabled");
    }

    synchronized void toggleOverlay() {
        setBoolean("overlayEnabled", !overlayEnabled());
        save();
    }

    synchronized String hypixelApiKey() {
        return getString("hypixelApiKey");
    }

    synchronized String bordicApiKey() {
        return getString("bordicApiKey");
    }

    synchronized String urchinApiKey() {
        return getString("urchinApiKey");
    }

    synchronized String seraphApiKey() {
        return getString("seraphApiKey");
    }

    synchronized int maxRows() {
        return getInt("maxRows");
    }

    Path file() {
        return FILE;
    }

    private Object value(String key, LegacySettingCatalog.ValueType expected) {
        LegacySettingCatalog.Option option = option(key);
        if (option.type() != expected) {
            throw new IllegalArgumentException("Setting " + key + " is " + option.type() + ", not " + expected);
        }
        return settings.get(key);
    }

    private void set(String key, LegacySettingCatalog.ValueType expected, Object value) {
        LegacySettingCatalog.Option option = option(key);
        if (option.type() != expected) {
            throw new IllegalArgumentException("Setting " + key + " is " + option.type() + ", not " + expected);
        }
        settings.put(key, normalize(option, value));
    }

    private static LegacySettingCatalog.Option option(String key) {
        LegacySettingCatalog.Option option = LegacySettingCatalog.option(key);
        if (option == null) throw new IllegalArgumentException("Unknown setting: " + key);
        return option;
    }

    private static Object decode(LegacySettingCatalog.Option option, JsonElement value) {
        if (!value.isJsonPrimitive()) return null;
        try {
            return switch (option.type()) {
                case BOOLEAN -> value.getAsJsonPrimitive().isBoolean() ? value.getAsBoolean() : null;
                case INTEGER -> value.getAsJsonPrimitive().isNumber() ? normalize(option, value.getAsBigDecimal()) : null;
                case DECIMAL -> value.getAsJsonPrimitive().isNumber() ? normalize(option, value.getAsDouble()) : null;
                case TEXT -> value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
            };
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Object normalize(LegacySettingCatalog.Option option, Object value) {
        return switch (option.type()) {
            case BOOLEAN -> (Boolean) value;
            case INTEGER -> {
                BigDecimal number = value instanceof BigDecimal decimal ? decimal : BigDecimal.valueOf(((Number) value).longValue());
                BigDecimal min = BigDecimal.valueOf(option.min());
                BigDecimal max = BigDecimal.valueOf(option.max());
                yield number.max(min).min(max).intValue();
            }
            case DECIMAL -> {
                double number = ((Number) value).doubleValue();
                if (!Double.isFinite(number)) number = ((Number) LegacySettingCatalog.defaultValue(option.key())).doubleValue();
                yield Math.max(option.min(), Math.min(option.max(), number));
            }
            case TEXT -> value == null ? "" : ((String) value).trim();
        };
    }
}

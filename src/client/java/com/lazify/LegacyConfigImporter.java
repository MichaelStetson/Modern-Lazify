package com.lazify;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class LegacyConfigImporter {
    private static final Pattern PROPERTY = Pattern.compile("^\\s*[BIDS]:([^=]+?)\\s*=\\s*(.*?)\\s*$");

    private record ImportedValue(Object value, boolean exactKey) { }

    private LegacyConfigImporter() { }

    static Path find(Path configDirectory) {
        Path nested = configDirectory.resolve("lazify").resolve("lazify.cfg");
        if (Files.isRegularFile(nested)) return nested;

        Path flat = configDirectory.resolve("lazify.cfg");
        return Files.isRegularFile(flat) ? flat : null;
    }

    static int importFile(Path file, LazifyConfig config) throws IOException {
        Map<String, ImportedValue> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            Matcher matcher = PROPERTY.matcher(line);
            if (!matcher.matches()) continue;

            String legacyKey = matcher.group(1).trim();
            String key = settingKey(legacyKey);
            if (key == null) continue;

            LegacySettingCatalog.Option option = LegacySettingCatalog.option(key);
            if (option == null) continue;
            ImportedValue existing = values.get(key);
            boolean exactKey = legacyKey.equals(key);
            if (existing != null && existing.exactKey() && !exactKey) continue;

            Object value = parseValue(option, matcher.group(2).trim());
            if (value != null) values.put(key, new ImportedValue(value, exactKey));
        }

        values.forEach((key, imported) -> apply(config, key, imported.value()));
        return values.size();
    }

    private static String settingKey(String legacyKey) {
        return switch (legacyKey) {
            case "hypixelKey" -> "hypixelApiKey";
            case "bordicKey" -> "bordicApiKey";
            case "urchinKey" -> "urchinApiKey";
            case "seraphKey" -> "seraphApiKey";
            case "skinDenick" -> "denick";
            default -> LegacySettingCatalog.option(legacyKey) == null ? null : legacyKey;
        };
    }

    private static Object parseValue(LegacySettingCatalog.Option option, String raw) {
        try {
            return switch (option.type()) {
                case BOOLEAN -> parseBoolean(raw);
                case INTEGER -> {
                    int number = new BigDecimal(raw).max(BigDecimal.valueOf(option.min()))
                            .min(BigDecimal.valueOf(option.max())).intValue();
                    if (option.key().equals("keybind")) number = lwjgl2KeyToGlfw(number);
                    yield number < 0 ? null : number;
                }
                case DECIMAL -> {
                    double number = Double.parseDouble(raw);
                    yield Double.isFinite(number) ? number : null;
                }
                case TEXT -> unquote(raw);
            };
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Boolean parseBoolean(String raw) {
        if (raw.equalsIgnoreCase("true")) return true;
        if (raw.equalsIgnoreCase("false")) return false;
        return null;
    }

    private static String unquote(String raw) {
        if (raw.isEmpty() || raw.charAt(0) != '"') return raw;
        if (raw.length() < 2 || raw.charAt(raw.length() - 1) != '"') throw new IllegalArgumentException();

        StringBuilder value = new StringBuilder(raw.length() - 2);
        for (int i = 1; i < raw.length() - 1; i++) {
            char character = raw.charAt(i);
            if (character == '\\' && i + 1 < raw.length() - 1) {
                char escaped = raw.charAt(++i);
                value.append(switch (escaped) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case '\\', '"' -> escaped;
                    default -> throw new IllegalArgumentException();
                });
            } else {
                value.append(character);
            }
        }
        return value.toString();
    }

    private static int lwjgl2KeyToGlfw(int key) {
        return switch (key) {
            case 0 -> 0;
            case 1 -> 256;
            case 2 -> 49;
            case 3 -> 50;
            case 4 -> 51;
            case 5 -> 52;
            case 6 -> 53;
            case 7 -> 54;
            case 8 -> 55;
            case 9 -> 56;
            case 10 -> 57;
            case 11 -> 48;
            case 12 -> 45;
            case 13 -> 61;
            case 14 -> 259;
            case 15 -> 258;
            case 16 -> 81;
            case 17 -> 87;
            case 18 -> 69;
            case 19 -> 82;
            case 20 -> 84;
            case 21 -> 89;
            case 22 -> 85;
            case 23 -> 73;
            case 24 -> 79;
            case 25 -> 80;
            case 26 -> 91;
            case 27 -> 93;
            case 28 -> 257;
            case 29 -> 341;
            case 30 -> 65;
            case 31 -> 83;
            case 32 -> 68;
            case 33 -> 70;
            case 34 -> 71;
            case 35 -> 72;
            case 36 -> 74;
            case 37 -> 75;
            case 38 -> 76;
            case 39 -> 59;
            case 40 -> 39;
            case 41 -> 96;
            case 42 -> 340;
            case 43 -> 92;
            case 44 -> 90;
            case 45 -> 88;
            case 46 -> 67;
            case 47 -> 86;
            case 48 -> 66;
            case 49 -> 78;
            case 50 -> 77;
            case 51 -> 44;
            case 52 -> 46;
            case 53 -> 47;
            case 54 -> 344;
            case 55 -> 332;
            case 56 -> 342;
            case 57 -> 32;
            case 58 -> 280;
            case 59, 100 -> 290;
            case 60, 101 -> 291;
            case 61, 102 -> 292;
            case 62 -> 293;
            case 63 -> 294;
            case 64 -> 295;
            case 65 -> 296;
            case 66 -> 297;
            case 67 -> 298;
            case 68 -> 299;
            case 69 -> 282;
            case 70 -> 281;
            case 71 -> 327;
            case 72 -> 328;
            case 73 -> 329;
            case 74 -> 333;
            case 75 -> 324;
            case 76 -> 325;
            case 77 -> 326;
            case 78 -> 334;
            case 79 -> 321;
            case 80 -> 322;
            case 81 -> 323;
            case 82 -> 320;
            case 83 -> 330;
            case 87 -> 300;
            case 88 -> 301;
            case 141 -> 336;
            case 156 -> 335;
            case 157 -> 345;
            case 181 -> 331;
            case 183 -> 283;
            case 184 -> 346;
            case 197 -> 284;
            case 199 -> 268;
            case 200 -> 265;
            case 201 -> 266;
            case 203 -> 263;
            case 205 -> 262;
            case 207 -> 269;
            case 208 -> 264;
            case 209 -> 267;
            case 210 -> 260;
            case 211 -> 261;
            case 219 -> 343;
            case 220 -> 347;
            case 221 -> 348;
            default -> -1;
        };
    }

    private static void apply(LazifyConfig config, String key, Object value) {
        LegacySettingCatalog.Option option = LegacySettingCatalog.option(key);
        switch (option.type()) {
            case BOOLEAN -> config.setBoolean(key, (Boolean) value);
            case INTEGER -> config.setInt(key, (Integer) value);
            case DECIMAL -> config.setDouble(key, (Double) value);
            case TEXT -> config.setString(key, (String) value);
        }
    }
}

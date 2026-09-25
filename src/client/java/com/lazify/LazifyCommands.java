package com.lazify;

import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.List;
import java.util.Locale;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class LazifyCommands {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ExecutorService LOOKUPS = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Lazify profile lookup");
        thread.setDaemon(true);
        return thread;
    });

    private LazifyCommands() {}

    static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, LazifyConfig config,
                         BedwarsMonitor monitor, PlayerStatsService statsService, Runnable openSettings) {
        dispatcher.register(root("ov", config, monitor, statsService, openSettings));
        dispatcher.register(root("overlay", config, monitor, statsService, openSettings));
        dispatcher.register(root("lazify", config, monitor, statsService, openSettings));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> root(String name, LazifyConfig config,
            BedwarsMonitor monitor, PlayerStatsService statsService, Runnable openSettings) {
        LiteralArgumentBuilder<FabricClientCommandSource> root = LiteralArgumentBuilder.<FabricClientCommandSource>literal(name)
                .executes(context -> {
                    openSettings.run();
                    return 1;
                })
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("help").executes(context -> {
                    help(context.getSource());
                    return 1;
                }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("1").executes(context -> {
                    help(context.getSource());
                    return 1;
                }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("2").executes(context -> {
                    status(context.getSource(), config, monitor);
                    return 1;
                }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("status").executes(context -> {
                    status(context.getSource(), config, monitor);
                    return 1;
                }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("sc")
                        .then(argument("username", StringArgumentType.word()).executes(context -> {
                            String username = StringArgumentType.getString(context, "username");
                            resolvePlayer(username, context.getSource(), (uuid, resolvedName) -> {
                                monitor.addManualPlayer(uuid, resolvedName);
                                feedback(context.getSource(), "Added " + resolvedName + " to the overlay.");
                            });
                            return 1;
                        })))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("hide")
                        .then(argument("username", StringArgumentType.word()).executes(context -> {
                            String username = StringArgumentType.getString(context, "username");
                            monitor.hidePlayer(username);
                            feedback(context.getSource(), username + " is now hidden.");
                            return 1;
                        })))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("unhide")
                        .then(argument("username", StringArgumentType.word()).executes(context -> {
                            String username = StringArgumentType.getString(context, "username");
                            monitor.unhidePlayer(username);
                            feedback(context.getSource(), username + " is no longer hidden.");
                            return 1;
                        })))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clearhidden").executes(context -> {
                    monitor.clearHiddenPlayers();
                    feedback(context.getSource(), "Cleared hidden players.");
                    return 1;
                }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("clear").executes(context -> {
                    monitor.clearOverlay();
                    feedback(context.getSource(), "Cleared the Lazify overlay.");
                    return 1;
                }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("reload").executes(context -> {
                    config.reload();
                    statsService.clear();
                    monitor.refreshNow();
                    feedback(context.getSource(), "Reloaded Lazify configuration and player data.");
                    return 1;
                }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("key")
                        .then(argument("provider", StringArgumentType.word())
                                .then(argument("key", StringArgumentType.greedyString()).executes(context -> {
                                    String provider = StringArgumentType.getString(context, "provider").toLowerCase(Locale.ROOT);
                                    String value = StringArgumentType.getString(context, "key").trim();
                                    String property = switch (provider) {
                                        case "urchin", "coral", "urchin/coral" -> "urchinApiKey";
                                        case "seraph" -> "seraphApiKey";
                                        case "bordic" -> "bordicApiKey";
                                        case "hypixel" -> "hypixelApiKey";
                                        default -> null;
                                    };
                                    if (property == null) {
                                        feedback(context.getSource(), "Use /ov key <urchin/coral|seraph|bordic|hypixel> <key>.");
                                        return 0;
                                    }
                                    config.setString(property, value);
                                    config.save();
                                    statsService.clear();
                                    monitor.refreshNow();
                                    feedback(context.getSource(), provider + " API key saved locally.");
                                    return 1;
                                }))))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("tag")
                        .then(argument("username", StringArgumentType.word()).executes(context -> {
                            String username = StringArgumentType.getString(context, "username");
                            resolvePlayer(username, context.getSource(), (uuid, resolvedName) -> {
                                statsService.request(uuid, resolvedName, config.hypixelApiKey(), config.bordicApiKey(),
                                        config.urchinApiKey(), config.seraphApiKey());
                                LOOKUPS.execute(() -> awaitTag(uuid, resolvedName, context.getSource(), statsService));
                            });
                            return 1;
                        })))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("tags").executes(context -> {
                    tagLegend(context.getSource());
                    return 1;
                }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("party").executes(context -> {
                    List<String> members = monitor.partyMembers().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
                    feedback(context.getSource(), members.isEmpty() ? "No detected party members." : "Detected party: " + String.join(", ", members));
                    return 1;
                }))
                .then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("set")
                        .then(argument("setting", StringArgumentType.word())
                                .then(argument("value", StringArgumentType.greedyString()).executes(context -> {
                                    return setSetting(context.getSource(), config,
                                            StringArgumentType.getString(context, "setting"),
                                            StringArgumentType.getString(context, "value"));
                                }))));

        for (LegacySettingCatalog.Option option : LegacySettingCatalog.all()) {
            if (option.key().startsWith("col") || option.key().endsWith("ApiKey")) continue;
            String command = option.key().toLowerCase(Locale.ROOT);
            LiteralArgumentBuilder<FabricClientCommandSource> setting = LiteralArgumentBuilder.<FabricClientCommandSource>literal(command);
            if (option.type() == LegacySettingCatalog.ValueType.BOOLEAN) {
                setting.executes(context -> {
                    config.setBoolean(option.key(), !config.getBoolean(option.key()));
                    config.save();
                    feedback(context.getSource(), option.label() + " " + (config.getBoolean(option.key()) ? "enabled" : "disabled"));
                    return 1;
                });
            }
            setting.then(valueArgument(option, config));
            root.then(setting);
        }

        root.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("col")
                .then(argument("column", StringArgumentType.word())
                        .executes(context -> setColumn(context.getSource(), config, monitor,
                                StringArgumentType.getString(context, "column"), null))
                        .then(argument("stateOrPosition", StringArgumentType.word()).executes(context ->
                                setColumn(context.getSource(), config, monitor,
                                        StringArgumentType.getString(context, "column"),
                                        StringArgumentType.getString(context, "stateOrPosition"))))));
        root.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("fkdrcolor")
                .then(argument("tier", StringArgumentType.word())
                        .then(argument("code", StringArgumentType.word()).executes(context -> {
                            String rawTier = StringArgumentType.getString(context, "tier");
                            String code = StringArgumentType.getString(context, "code").toLowerCase(Locale.ROOT);
                            try {
                                int tier = Integer.parseInt(rawTier);
                                if (tier < 1 || tier > 7 || code.length() != 1 || "0123456789abcdef".indexOf(code.charAt(0)) < 0) {
                                    feedback(context.getSource(), "Use /ov fkdrcolor <1-7> <0-f>.");
                                    return 0;
                                }
                                config.setString("fkdrColor" + tier, code);
                                config.save();
                                feedback(context.getSource(), "FKDR color tier " + tier + " updated.");
                                return 1;
                            } catch (NumberFormatException e) {
                                feedback(context.getSource(), "Use /ov fkdrcolor <1-7> <0-f>.");
                                return 0;
                            }
                        }))));
        root.then(LiteralArgumentBuilder.<FabricClientCommandSource>literal("statsdisplay")
                .then(argument("theme", StringArgumentType.greedyString()).executes(context ->
                        setSetting(context.getSource(), config, "statsdisplay",
                                StringArgumentType.getString(context, "theme")))));
        return root;
    }

    private static RequiredArgumentBuilder<FabricClientCommandSource, String> valueArgument(
            LegacySettingCatalog.Option option, LazifyConfig config) {
        return argument("value", StringArgumentType.greedyString()).executes(context -> setSetting(
                context.getSource(), config, option.key(), StringArgumentType.getString(context, "value")));
    }

    private static int setSetting(FabricClientCommandSource source, LazifyConfig config, String name, String raw) {
        String normalized = name.toLowerCase(Locale.ROOT);
        String keyName = normalized.equals("statsdisplay") ? "overlayTheme" : name;
        LegacySettingCatalog.Option option = LegacySettingCatalog.all().stream()
                .filter(candidate -> candidate.key().equalsIgnoreCase(keyName)
                        || candidate.key().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst().orElse(null);
        if (option == null) {
            feedback(source, "Unknown setting: " + name);
            return 0;
        }
        try {
            boolean keybind = false;
            switch (option.type()) {
                case BOOLEAN -> config.setBoolean(option.key(), parseBoolean(raw));
                case INTEGER -> {
                    int value = option.key().equals("overlayTheme") || option.key().equals("statsDisplayMode")
                            ? parseTheme(raw) : Integer.parseInt(raw.trim());
                    config.setInt(option.key(), value);
                    if (option.key().equals("overlayTheme") || option.key().equals("statsDisplayMode")) {
                        config.setInt("overlayTheme", value);
                        config.setInt("statsDisplayMode", value);
                    }
                    keybind = option.key().equals("keybind");
                }
                case DECIMAL -> config.setDouble(option.key(), Double.parseDouble(raw.trim()));
                case TEXT -> config.setString(option.key(), raw.trim());
            }
            if (keybind) LazifyClient.rebindOverlayKey(config.getInt("keybind"));
            else config.save();
            feedback(source, option.label() + " updated.");
            return 1;
        } catch (IllegalArgumentException e) {
            feedback(source, "Invalid value for " + option.label() + ".");
            return 0;
        }
    }

    private static int parseTheme(String raw) {
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "0", "lazify", "default", "overlay", "hud" -> 0;
            case "1", "nerdify" -> 1;
            case "2", "mellow", "tab", "tablist", "both" -> 2;
            default -> throw new IllegalArgumentException("Unknown overlay theme");
        };
    }

    private static boolean parseBoolean(String raw) {
        if (raw.equalsIgnoreCase("true")) return true;
        if (raw.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException("Expected true or false");
    }

    private static String columnSetting(String name) {
        String normalized = name.replaceAll("[^A-Za-z]", "").toLowerCase(Locale.ROOT);
        return LegacySettingCatalog.all().stream()
                .filter(option -> option.key().startsWith("col") && !option.key().equals("colOrder")
                        && option.key().substring(3).toLowerCase(Locale.ROOT).equals(normalized))
                .map(LegacySettingCatalog.Option::key)
                .findFirst().orElse(null);
    }

    private static int setColumn(FabricClientCommandSource source, LazifyConfig config, BedwarsMonitor monitor,
                                 String name, String stateOrPosition) {
        String setting = columnSetting(name);
        if (setting == null) {
            feedback(source, "Unknown column. Use /ov help for the column list.");
            return 0;
        }
        if (stateOrPosition == null) {
            config.setBoolean(setting, !config.getBoolean(setting));
        } else if (stateOrPosition.equalsIgnoreCase("true") || stateOrPosition.equalsIgnoreCase("false")) {
            config.setBoolean(setting, Boolean.parseBoolean(stateOrPosition));
        } else {
            try {
                int position = Integer.parseInt(stateOrPosition);
                List<String> order = new ArrayList<>();
                for (String entry : config.getString("colOrder").split(",")) {
                    String key = entry.trim().toLowerCase(Locale.ROOT);
                    if (!key.isEmpty() && !order.contains(key)) order.add(key);
                }
                String column = setting.substring(3).toLowerCase(Locale.ROOT);
                order.remove(column);
                if (position < 0 || position >= LegacySettingCatalog.all().stream()
                        .filter(option -> option.key().startsWith("col") && !option.key().equals("colOrder")).count()) {
                    feedback(source, "Column position is zero-based and must be within the 32 columns.");
                    return 0;
                }
                order.add(Math.min(position, order.size()), column);
                config.setString("colOrder", String.join(",", order));
            } catch (NumberFormatException e) {
                feedback(source, "Column state must be true, false, or a zero-based position.");
                return 0;
            }
        }
        config.save();
        monitor.refreshNow();
        feedback(source, name + (stateOrPosition == null || stateOrPosition.matches("\\d+")
                ? " column order updated." : " column " + stateOrPosition + "."));
        return 1;
    }

    private static void status(FabricClientCommandSource source, LazifyConfig config, BedwarsMonitor monitor) {
        feedback(source, "Overlay " + (config.overlayEnabled() ? "enabled" : "disabled") + "; theme "
                + switch (config.getInt("overlayTheme")) { case 1 -> "Nerdify"; case 2 -> "Mellow"; default -> "Lazify"; }
                + "; tracked players " + monitor.players().size() + ".");
        feedback(source, "API keys configured: Hypixel=" + !config.hypixelApiKey().isBlank()
                + ", Bordic=" + !config.bordicApiKey().isBlank() + ", Urchin=" + !config.urchinApiKey().isBlank()
                + ", Seraph=" + !config.seraphApiKey().isBlank() + ".");
    }


    private static void resolvePlayer(String username, FabricClientCommandSource source, ResolvedPlayer callback) {
        BedwarsMonitor monitor = LazifyClient.monitor();
        if (monitor != null) {
            BedwarsMonitor.PlayerRow row = monitor.players().stream()
                    .filter(candidate -> candidate.name().equalsIgnoreCase(username)).findFirst().orElse(null);
            if (row != null) {
                callback.accept(row.uuid(), row.name());
                return;
            }
        }
        LOOKUPS.execute(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/"
                                + URLEncoder.encode(username, StandardCharsets.UTF_8)))
                        .timeout(Duration.ofSeconds(5))
                        .header("User-Agent", "Modern-Lazify/1.0.0")
                        .GET().build();
                HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    Minecraft.getInstance().execute(() -> feedback(source, "Could not resolve " + username + " to a Minecraft profile."));
                    return;
                }
                var json = JsonParser.parseString(response.body()).getAsJsonObject();
                UUID uuid = uuidFromHex(json.get("id").getAsString());
                String resolved = json.has("name") ? json.get("name").getAsString() : username;
                Minecraft.getInstance().execute(() -> callback.accept(uuid, resolved));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Minecraft.getInstance().execute(() -> feedback(source, "Profile lookup was interrupted."));
            } catch (IOException | RuntimeException e) {
                Minecraft.getInstance().execute(() -> feedback(source, "Profile lookup failed: " + e.getMessage()));
            }
        });
    }

    private static UUID uuidFromHex(String hex) {
        if (hex == null || !hex.matches("(?i)[0-9a-f]{32}")) throw new IllegalArgumentException("Invalid profile UUID");
        return UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16)
                + "-" + hex.substring(16, 20) + "-" + hex.substring(20));
    }

    private static void awaitTag(UUID uuid, String name, FabricClientCommandSource source, PlayerStatsService service) {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(10).toMillis();
        while (System.currentTimeMillis() < deadline) {
            String tag = service.tag(uuid);
            if (!tag.isBlank()) {
                String result = tag;
                Minecraft.getInstance().execute(() -> feedback(source, name + " tags: " + result));
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        Minecraft.getInstance().execute(() -> feedback(source, "No tags returned for " + name + " (or tag lookup is still pending)."));
    }

    private static void tagLegend(FabricClientCommandSource source) {
        feedback(source, "Tag legend: [C] confirmed cheater, [CC] closet cheater, [BC] blatant cheater, [S] sniper, [BL] blacklisted.");
        feedback(source, "Urchin/Coral and Seraph tags need their own configured API keys.");
    }

    private static void help(FabricClientCommandSource source) {
        feedback(source, "/ov, /overlay, and /lazify open settings; /ov 1 prints help and /ov 2 shows status.");
        feedback(source, "/ov sc <player>, hide|unhide <player>, clearhidden, clear, reload, tag <player>, tags, party.");
        feedback(source, "/ov key <provider> <key>; /ov col <column> [true|false|position]; /ov statsdisplay <lazify|nerdify|mellow>.");
        feedback(source, "/ov fkdrcolor <1-7> <0-f>; /ov set <setting> <value>.");
    }

    private static void feedback(FabricClientCommandSource source, String text) {
        source.sendFeedback(Component.literal("[Lazify] " + text));
    }

    private static RequiredArgumentBuilder<FabricClientCommandSource, String> argument(String name,
            com.mojang.brigadier.arguments.ArgumentType<String> type) {
        return RequiredArgumentBuilder.<FabricClientCommandSource, String>argument(name, type);
    }

    @FunctionalInterface
    private interface ResolvedPlayer {
        void accept(UUID uuid, String name);
    }
}

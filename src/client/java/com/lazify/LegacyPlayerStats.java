package com.lazify;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

final class LegacyPlayerStats {
    private static final String UNAVAILABLE = "-";
    private static final String[] COLUMN_KEYS = {
            "encounters", "username", "rank", "star", "fkdr", "wlr", "bblr", "kdr",
            "kills", "finals", "beds", "wins", "dailyfkdr", "dailywlr", "dailystars",
            "dailybblr", "dailykdr", "weeklyfkdr", "weeklywlr", "weeklystars", "weeklybblr",
            "weeklykdr", "monthlyfkdr", "monthlywlr", "monthlystars", "monthlybblr", "monthlykdr",
            "winstreaks", "tags", "urchin", "session", "netlevel", "level", "ping"
    };

    private final Map<String, String> columns;
    private final boolean nicked;
    private final String nickReason;
    private final String rankPrefix;
    private final String provider;

    LegacyPlayerStats(Map<String, String> columns, boolean nicked, String nickReason, String rankPrefix, String provider) {
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        for (String key : COLUMN_KEYS) {
            copy.put(key, value(columns, key));
        }
        String tag = value(columns, "tags");
        copy.put("tags", tag);
        copy.put("urchin", tag);
        String level = value(columns, "netlevel");
        copy.put("netlevel", level);
        copy.put("level", level);
        this.columns = Collections.unmodifiableMap(copy);
        this.nicked = nicked;
        this.nickReason = nickReason == null ? "" : nickReason;
        this.rankPrefix = rankPrefix == null ? "" : rankPrefix;
        this.provider = provider == null || provider.isBlank() ? "unavailable" : provider;
    }

    static LegacyPlayerStats unavailable(String username, String provider, boolean nicked, String nickReason) {
        LinkedHashMap<String, String> values = unavailableColumns();
        values.put("username", safeUsername(username));
        if (nicked) values.put("rank", "N");
        return new LegacyPlayerStats(values, nicked, nickReason, "", provider);
    }

    static LegacyPlayerStats from(BedwarsStats stats) {
        if (stats == null) return unavailable("", "unavailable", false, "");
        LinkedHashMap<String, String> values = unavailableColumns();
        values.put("username", safeUsername(stats.username()));
        values.put("rank", stats.rank().isBlank() ? UNAVAILABLE : stats.rank());
        if (stats.hasBedwars()) {
            values.put("star", Long.toString(stats.stars()));
            values.put("fkdr", ratio(stats.fkdr()));
            values.put("wlr", ratio(stats.wlr()));
            values.put("bblr", ratio(stats.bblr()));
            values.put("kdr", ratio(stats.kdr()));
            values.put("kills", Long.toString(stats.kills()));
            values.put("finals", Long.toString(stats.finalKills()));
            values.put("beds", Long.toString(stats.bedsBroken()));
            values.put("wins", Long.toString(stats.wins()));
            values.put("winstreaks", stats.hasWinstreak() ? Long.toString(stats.winstreak()) : UNAVAILABLE);
        }
        if (stats.hasNetworkExperience() && stats.networkLevel() > 0.0) {
            values.put("netlevel", Long.toString((long) stats.networkLevel()));
            values.put("level", Long.toString((long) stats.networkLevel()));
        }
        if (stats.hasSessionTimestamps()) {
            if (stats.lastLogin() == 0L) {
                values.put("session", "API");
            } else if (stats.lastLogin() - stats.lastLogout() > -10_000L) {
                values.put("session", relativeDuration(stats.lastLogin(), System.currentTimeMillis()));
            } else {
                values.put("session", "OFFLINE");
            }
        }
        return new LegacyPlayerStats(values, false, "", stats.rankPrefix(), stats.provider());
    }

    String column(String legacyKey) {
        if (legacyKey == null) return UNAVAILABLE;
        String key = legacyKey.toLowerCase(Locale.ROOT);
        if (key.equals("seen")) key = "encounters";
        if (key.equals("netlevel")) key = "level";
        if (key.equals("urchin")) key = "tags";
        return columns.getOrDefault(key, UNAVAILABLE);
    }

    Map<String, String> columns() {
        return columns;
    }

    boolean nicked() {
        return nicked;
    }

    String nickReason() {
        return nickReason;
    }

    String rankPrefix() {
        return rankPrefix;
    }

    String provider() {
        return provider;
    }

    LegacyPlayerStats withColumns(Map<String, String> updates) {
        if (updates == null || updates.isEmpty()) return this;
        LinkedHashMap<String, String> copy = new LinkedHashMap<>(columns);
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            if (entry.getKey() == null) continue;
            String key = entry.getKey().toLowerCase(Locale.ROOT);
            String value = entry.getValue() == null ? UNAVAILABLE : entry.getValue();
            switch (key) {
                case "seen" -> copy.put("encounters", value);
                case "netlevel", "level" -> {
                    copy.put("netlevel", value);
                    copy.put("level", value);
                }
                case "urchin", "tags" -> {
                    copy.put("tags", value);
                    copy.put("urchin", value);
                }
                default -> {
                    if (copy.containsKey(key)) copy.put(key, value);
                }
            }
        }
        return new LegacyPlayerStats(copy, nicked, nickReason, rankPrefix, provider);
    }

    private static LinkedHashMap<String, String> unavailableColumns() {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        for (String key : COLUMN_KEYS) values.put(key, UNAVAILABLE);
        return values;
    }

    private static String value(Map<String, String> values, String key) {
        if (values == null) return UNAVAILABLE;
        String value = values.get(key);
        if (value == null && key.equals("tags")) value = values.get("urchin");
        if (value == null && (key.equals("level") || key.equals("netlevel"))) value = values.get("netlevel");
        return value == null ? UNAVAILABLE : value;
    }

    private static String safeUsername(String username) {
        return username == null || username.isBlank() ? UNAVAILABLE : username;
    }

    private static String ratio(double value) {
        String formatted = String.format(Locale.ROOT, "%.2f", value);
        while (formatted.endsWith("0")) formatted = formatted.substring(0, formatted.length() - 1);
        if (formatted.endsWith(".")) formatted = formatted.substring(0, formatted.length() - 1);
        return formatted;
    }

    private static String relativeDuration(long from, long to) {
        long seconds = Math.max(0L, (to - from) / 1_000L);
        long original = seconds;
        long years = seconds / 31_557_600L;
        seconds %= 31_557_600L;
        long months = seconds / 2_629_800L;
        seconds %= 2_629_800L;
        long days = seconds / 86_400L;
        seconds %= 86_400L;
        long hours = seconds / 3_600L;
        seconds %= 3_600L;
        long minutes = seconds / 60L;
        long remainingSeconds = seconds % 60L;
        StringBuilder text = new StringBuilder(8);
        int count = 0;
        if (years > 0 && count++ < 2) text.append(years).append('y');
        if (months > 0 && count++ < 2) text.append(months).append("mo");
        if (days > 0 && count++ < 2) text.append(days).append('d');
        if (hours > 0 && count++ < 2) text.append(hours).append('h');
        if (minutes > 0 && count++ < 2) text.append(minutes).append('m');
        if ((remainingSeconds > 0 && count < 2) || original == 0) text.append(remainingSeconds).append('s');
        return text.toString();
    }
}

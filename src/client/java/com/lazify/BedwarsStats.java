package com.lazify;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

record BedwarsStats(
        String username,
        String provider,
        String rank,
        String rankPrefix,
        boolean hasBedwars,
        boolean hasNetworkExperience,
        double networkLevel,
        boolean hasSessionTimestamps,
        long lastLogin,
        long lastLogout,
        long stars,
        long finalKills,
        long finalDeaths,
        long wins,
        long losses,
        long bedsBroken,
        long bedsLost,
        long kills,
        long deaths,
        boolean hasWinstreak,
        long winstreak,
        Map<String, Long> modeWinstreaks
) {
    private static final int[] FIRST_LEVEL_EXPERIENCE = {500, 1_000, 2_000, 3_500};
    private static final Map<String, String> MODE_PREFIXES = Map.of(
            "solo", "eight_one_",
            "doubles", "eight_two_",
            "threes", "four_three_",
            "fours", "four_four_",
            "4v4", "two_four_"
    );

    BedwarsStats {
        username = username == null ? "" : username;
        provider = provider == null ? "unavailable" : provider;
        rank = rank == null ? "" : rank;
        rankPrefix = rankPrefix == null ? "" : rankPrefix;
        modeWinstreaks = Collections.unmodifiableMap(new LinkedHashMap<>(modeWinstreaks));
    }

    static BedwarsStats parse(JsonObject response, String provider) {
        JsonObject player = object(response, "player");
        if (player == null) return null;
        JsonObject stats = object(player, "stats");
        JsonObject bedwars = object(stats, "Bedwars");
        JsonObject achievements = object(player, "achievements");
        return fromPlayer(player, bedwars, achievements, provider);
    }

    static BedwarsStats parse(JsonObject response) {
        return parse(response, "hypixel");
    }

    static BedwarsStats fromSession(String username, JsonObject bedwars) {
        return fromPlayer(null, bedwars, null, "bordic-sessions", username);
    }

    BedwarsStats merge(BedwarsStats fallback) {
        if (fallback == null) return this;
        boolean useFallbackBedwars = !hasBedwars && fallback.hasBedwars;
        String mergedRank = rank.isBlank() ? fallback.rank : rank;
        String mergedPrefix = rank.isBlank() ? fallback.rankPrefix : rankPrefix;
        long mergedStars = useFallbackBedwars ? fallback.stars : stars;
        if (hasBedwars && fallback.hasBedwars && fallback.stars > stars) mergedStars = fallback.stars;
        boolean useFallbackNetwork = !hasNetworkExperience && fallback.hasNetworkExperience;
        BedwarsStats bedwars = useFallbackBedwars ? fallback : this;
        boolean hasWs = bedwars.hasWinstreak;
        long ws = bedwars.winstreak;
        LinkedHashMap<String, Long> modes = new LinkedHashMap<>(bedwars.modeWinstreaks);
        String mergedProvider = combineProviders(provider, fallback.provider);
        return new BedwarsStats(
                username.isBlank() ? fallback.username : username,
                mergedProvider,
                mergedRank,
                mergedPrefix,
                hasBedwars || fallback.hasBedwars,
                hasNetworkExperience || fallback.hasNetworkExperience,
                useFallbackNetwork ? fallback.networkLevel : networkLevel,
                hasSessionTimestamps,
                lastLogin,
                lastLogout,
                mergedStars,
                useFallbackBedwars ? fallback.finalKills : finalKills,
                useFallbackBedwars ? fallback.finalDeaths : finalDeaths,
                useFallbackBedwars ? fallback.wins : wins,
                useFallbackBedwars ? fallback.losses : losses,
                useFallbackBedwars ? fallback.bedsBroken : bedsBroken,
                useFallbackBedwars ? fallback.bedsLost : bedsLost,
                useFallbackBedwars ? fallback.kills : kills,
                useFallbackBedwars ? fallback.deaths : deaths,
                hasWs,
                ws,
                modes
        );
    }

    BedwarsStats withUsername(String name) {
        if (name == null || name.isBlank() || username.equals(name)) return this;
        return new BedwarsStats(
                name, provider, rank, rankPrefix, hasBedwars, hasNetworkExperience, networkLevel,
                hasSessionTimestamps, lastLogin, lastLogout, stars, finalKills, finalDeaths, wins,
                losses, bedsBroken, bedsLost, kills, deaths, hasWinstreak, winstreak, modeWinstreaks
        );
    }


    BedwarsStats withWinstreakFallback(long overall, Map<String, Long> modeValues) {
        boolean replaceOverall = !hasWinstreak || winstreak == 0L;
        LinkedHashMap<String, Long> modes = new LinkedHashMap<>(modeWinstreaks);
        if (modeValues != null) {
            for (Map.Entry<String, Long> entry : modeValues.entrySet()) {
                if (modes.getOrDefault(entry.getKey(), 0L) == 0L && entry.getValue() > 0L) {
                    modes.put(entry.getKey(), entry.getValue());
                }
            }
        }
        if (!replaceOverall && modes.equals(modeWinstreaks)) return this;
        return new BedwarsStats(
                username, combineProviders(provider, "bordic-winstreaks"), rank, rankPrefix,
                hasBedwars, hasNetworkExperience, networkLevel, hasSessionTimestamps, lastLogin,
                lastLogout, stars, finalKills, finalDeaths, wins, losses, bedsBroken, bedsLost,
                kills, deaths, true,
                replaceOverall ? overall : winstreak, modes
        );
    }

    double fkdr() {
        return ratio(finalKills, finalDeaths);
    }

    double wlr() {
        return ratio(wins, losses);
    }

    double bblr() {
        return ratio(bedsBroken, bedsLost);
    }

    double kdr() {
        return ratio(kills, deaths);
    }

    static int levelFromExperience(long experience) {
        if (experience <= 0L) return 0;
        long prestiges = experience / 487_000L;
        long remainder = experience % 487_000L;
        long level = prestiges * 100L;
        for (int threshold : FIRST_LEVEL_EXPERIENCE) {
            if (remainder < threshold) break;
            level++;
            remainder -= threshold;
        }
        return (int) Math.min(Integer.MAX_VALUE, level + remainder / 5_000L);
    }

    private static BedwarsStats fromPlayer(JsonObject player, JsonObject bedwars, JsonObject achievements, String provider) {
        return fromPlayer(player, bedwars, achievements, provider, null);
    }

    private static BedwarsStats fromPlayer(
            JsonObject player,
            JsonObject bedwars,
            JsonObject achievements,
            String provider,
            String fallbackUsername
    ) {
        long experience = number(bedwars, "Experience", number(bedwars, "experience", 0L));
        long stars = number(achievements, "bedwars_level", 0L);
        if (stars <= 0L && experience > 0L) stars = levelFromExperience(experience);
        double networkExperience = decimal(player, "networkExp", 0.0);
        boolean hasNetworkExperience = hasNumber(player, "networkExp");
        double networkLevel = hasNetworkExperience ? networkLevel(networkExperience) : 0.0;
        LinkedHashMap<String, Long> modeWinstreaks = new LinkedHashMap<>();
        if (bedwars != null) {
            for (Map.Entry<String, String> entry : MODE_PREFIXES.entrySet()) {
                String key = entry.getValue() + "winstreak";
                if (hasNumber(bedwars, key)) modeWinstreaks.put(entry.getKey(), number(bedwars, key, 0L));
            }
        }
        boolean hasWinstreak = hasNumber(bedwars, "winstreak");
        return new BedwarsStats(
                player == null ? safe(fallbackUsername) : text(player, "displayname", safe(fallbackUsername)),
                provider,
                player == null ? "" : rank(player),
                player == null ? "" : rankPrefix(player),
                bedwars != null,
                hasNetworkExperience,
                networkLevel,
                hasNumber(player, "firstLogin") || hasNumber(player, "lastLogin") || hasNumber(player, "lastLogout"),
                number(player, "lastLogin", 0L),
                number(player, "lastLogout", 0L),
                stars,
                number(bedwars, "final_kills_bedwars", 0L),
                number(bedwars, "final_deaths_bedwars", 0L),
                number(bedwars, "wins_bedwars", 0L),
                number(bedwars, "losses_bedwars", 0L),
                number(bedwars, "beds_broken_bedwars", 0L),
                number(bedwars, "beds_lost_bedwars", 0L),
                number(bedwars, "kills_bedwars", 0L),
                number(bedwars, "deaths_bedwars", 0L),
                hasWinstreak,
                number(bedwars, "winstreak", 0L),
                modeWinstreaks
        );
    }

    private static String rank(JsonObject player) {
        String prefix = text(player, "prefix", "");
        if (!prefix.isBlank()) {
            String plain = stripFormatting(prefix).trim();
            if (plain.startsWith("[") && plain.endsWith("]") && plain.length() > 2) {
                plain = plain.substring(1, plain.length() - 1).trim();
            }
            if (!plain.isBlank()) return plain;
        }
        String staff = text(player, "rank", "");
        if (!isEmptyRank(staff)) return normalizeRank(staff);
        String monthly = text(player, "monthlyPackageRank", "");
        if (!isEmptyRank(monthly)) return normalizeRank(monthly);
        String packageRank = text(player, "newPackageRank", "");
        if (isEmptyRank(packageRank)) packageRank = text(player, "packageRank", "");
        return isEmptyRank(packageRank) ? "" : normalizeRank(packageRank);
    }

    private static String rankPrefix(JsonObject player) {
        String prefix = text(player, "prefix", "");
        if (!prefix.isBlank()) return prefix.replace('&', '\u00a7');
        String rank = rank(player);
        String plus = colorCode(text(player, "rankPlusColor", "RED"));
        return switch (rank) {
            case "VIP" -> "\u00a7a[VIP]";
            case "VIP+" -> "\u00a7a[VIP" + "\u00a76+\u00a7a]";
            case "MVP" -> "\u00a7b[MVP]";
            case "MVP+" -> "\u00a7b[MVP" + plus + "+\u00a7b]";
            case "MVP++" -> "\u00a76[MVP" + colorCode(text(player, "monthlyRankColor", "GOLD")) + "++\u00a76]";
            case "ADMIN", "OWNER" -> "\u00a7c[" + rank + "]";
            case "GM", "MOD", "HELPER" -> "\u00a72[" + rank + "]";
            case "YOUTUBE" -> "\u00a7c[YT]";
            default -> rank.isBlank() ? "" : "\u00a77[" + rank + "]";
        };
    }

    private static String normalizeRank(String rank) {
        return switch (rank) {
            case "VIP_PLUS" -> "VIP+";
            case "MVP_PLUS" -> "MVP+";
            case "SUPERSTAR" -> "MVP++";
            case "GAME_MASTER" -> "GM";
            case "MODERATOR" -> "MOD";
            case "YOUTUBER" -> "YOUTUBE";
            default -> rank;
        };
    }

    private static boolean isEmptyRank(String rank) {
        return rank == null || rank.isBlank() || rank.equalsIgnoreCase("NONE")
                || rank.equalsIgnoreCase("NORMAL");
    }

    private static String colorCode(String color) {
        return switch (color.toUpperCase(Locale.ROOT)) {
            case "BLACK" -> "\u00a70";
            case "DARK_BLUE" -> "\u00a71";
            case "DARK_GREEN" -> "\u00a72";
            case "DARK_AQUA" -> "\u00a73";
            case "DARK_RED" -> "\u00a74";
            case "DARK_PURPLE" -> "\u00a75";
            case "GOLD" -> "\u00a76";
            case "GRAY" -> "\u00a77";
            case "DARK_GRAY" -> "\u00a78";
            case "BLUE" -> "\u00a79";
            case "GREEN" -> "\u00a7a";
            case "AQUA" -> "\u00a7b";
            case "LIGHT_PURPLE" -> "\u00a7d";
            case "YELLOW" -> "\u00a7e";
            case "WHITE" -> "\u00a7f";
            default -> "\u00a7c";
        };
    }

    private static String combineProviders(String first, String second) {
        if (first == null || first.isBlank() || first.equals("unavailable")) return second;
        if (second == null || second.isBlank() || first.equals(second) || first.endsWith("+" + second)) return first;
        return first + "+" + second;
    }

    private static double ratio(long numerator, long denominator) {
        double value = denominator > 0L ? (double) numerator / denominator : numerator;
        return Math.round(value * 100.0) / 100.0;
    }

    private static double networkLevel(double experience) {
        return Math.round(((Math.sqrt(experience + 15_312.5) - 125.0 / Math.sqrt(2.0))
                / (25.0 * Math.sqrt(2.0))) * 100.0) / 100.0;
    }

    private static JsonObject object(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonObject()) return null;
        return parent.getAsJsonObject(key);
    }

    private static long number(JsonObject object, String key, long fallback) {
        if (!hasNumber(object, key)) return fallback;
        try {
            return Math.max(0L, object.get(key).getAsLong());
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static double decimal(JsonObject object, String key, double fallback) {
        if (!hasNumber(object, key)) return fallback;
        try {
            double value = object.get(key).getAsDouble();
            return Double.isFinite(value) ? Math.max(0.0, value) : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean hasNumber(JsonObject object, String key) {
        if (object == null || !object.has(key)) return false;
        JsonElement element = object.get(key);
        if (!element.isJsonPrimitive()) return false;
        try {
            element.getAsDouble();
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static String text(JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) return fallback;
        try {
            return object.get(key).getAsString();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String stripFormatting(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '\u00a7' && index + 1 < value.length()) {
                index++;
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

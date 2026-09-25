package com.lazify;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Metadata and safe defaults for settings carried forward from the legacy client. */
final class LegacySettingCatalog {
    enum ValueType { BOOLEAN, INTEGER, DECIMAL, TEXT }

    record Option(String key, String label, String section, ValueType type, double min, double max,
                  String description) { }

    private static final List<Option> OPTIONS;
    private static final Map<String, Object> DEFAULTS;
    private static final Map<String, Option> INDEX;

    static {
        List<Option> options = new ArrayList<>();
        Map<String, Object> defaults = new LinkedHashMap<>();
        add(options, defaults, "overlayEnabled", "Overlay enabled", "Overlay", ValueType.BOOLEAN, true, 0, 1, "Show the stats overlay.");
        add(options, defaults, "maxRows", "Maximum rows", "Overlay", ValueType.INTEGER, 16, 1, 32, "Maximum number of players shown.");
        integer(options, defaults, "keybind", 96, 0, 348, "General", "Overlay toggle key (GLFW key code; use Controls or this screen to rebind).");
        bool(options, defaults, "keybindHold", false, "General", "Only show the overlay while holding its keybind.");
        bool(options, defaults, "showOnTab", true, "General", "Show the overlay while holding Tab.");
        bool(options, defaults, "overlayOverTab", false, "General", "Render over rather than behind the tab list.");
        bool(options, defaults, "debug", false, "General", "Show debug information in chat.");
        bool(options, defaults, "teams", true, "General", "Color names by Bedwars team.");
        bool(options, defaults, "teamPrefix", false, "General", "Show a team-letter prefix.");
        bool(options, defaults, "showYourself", false, "General", "Include your own stats.");
        bool(options, defaults, "sendNickedToChat", true, "General", "Chat notification when a nick is detected.");
        bool(options, defaults, "sendUrchinReasonToChat", false, "General", "Chat notification with tag reasons.");
        bool(options, defaults, "disableInLobby", true, "General", "Disable auto-detection and alerts in the main lobby.");
        bool(options, defaults, "showRanks", false, "General", "Include rank prefixes in the username column.");
        bool(options, defaults, "removeFinalKill", false, "General", "Remove players after they are final-killed.");
        bool(options, defaults, "autoTablist", true, "General", "Automatically detect players in the tab list.");
        bool(options, defaults, "clearOnWho", false, "General", "Clear then repopulate on /who response.");
        bool(options, defaults, "middleClickShop", false, "Gameplay", "Use middle-click for instant shop purchases.");
        bool(options, defaults, "denick", true, "General", "Enable nick detection.");
        bool(options, defaults, "fkdrColors", true, "Appearance", "Color FKDR values by threat level.");
        bool(options, defaults, "autoWho", false, "General", "Automatically send /who on lobby join.");
        decimal(options, defaults, "whoDelay", 0.0, 0, 10, "General", "Delay in seconds before automatic /who.");
        bool(options, defaults, "hideWho", false, "General", "Hide the ONLINE message from /who.");
        bool(options, defaults, "autoPl", true, "Dodge", "Automatically request party list once per lobby.");
        bool(options, defaults, "hidePl", true, "Dodge", "Hide automatic /pl output in chat.");
        bool(options, defaults, "teamFkdrChat", false, "Dodge", "Send average team FKDR to party chat.");
        bool(options, defaults, "teamThreatChat", false, "Dodge", "Send team threat ratings to party chat.");
        bool(options, defaults, "dodgeWarning", false, "Dodge", "Warn when average lobby FKDR exceeds threshold.");
        decimal(options, defaults, "dodgeThreshold", 3.0, 0, 100000, "Dodge", "Average lobby FKDR threshold.");
        decimal(options, defaults, "teamThreatThreshold", 6.5, 0, 100000, "Dodge", "Minimum team threat score for party notification.");
        decimal(options, defaults, "threatFkdrWeight", .7, 0, 1000, "Dodge", "FKDR contribution weight.");
        decimal(options, defaults, "threatStarWeight", .35, 0, 1000, "Dodge", "Star contribution weight.");
        decimal(options, defaults, "threatWinstreakWeight", .3, 0, 1000, "Dodge", "Winstreak contribution weight.");
        decimal(options, defaults, "threatUrchinWeight", 1.4, 0, 1000, "Dodge", "Tag severity contribution weight.");
        decimal(options, defaults, "threatTeamSizeWeight", .9, 0, 1000, "Dodge", "Extra teammate contribution weight.");
        decimal(options, defaults, "threatEncounterWeight", .25, 0, 1000, "Dodge", "Encounter contribution weight.");
        decimal(options, defaults, "threatNickWeight", .75, 0, 1000, "Dodge", "Nick contribution weight.");
        bool(options, defaults, "noHurtCam", false, "Gameplay", "Disable camera tilt when damaged.");
        bool(options, defaults, "antiDebuff", false, "Gameplay", "Remove visual debuff effects.");
        bool(options, defaults, "partyDetector", true, "Party detector", "Detect parties joining the pregame lobby.");
        bool(options, defaults, "partyDetectorPing", false, "Party detector", "Play a sound when a party is detected.");
        bool(options, defaults, "partyDetectorShowMissed", true, "Party detector", "Show players who joined before you.");
        bool(options, defaults, "partyDetectorBw2s", false, "Party detector", "Detect parties in Bedwars doubles.");
        bool(options, defaults, "partyDetectorBw3s", true, "Party detector", "Detect parties in Bedwars threes.");
        bool(options, defaults, "partyDetectorBw4s", true, "Party detector", "Detect parties in Bedwars fours.");
        bool(options, defaults, "partyDetectorBw4v4", false, "Party detector", "Detect parties in Bedwars 4v4.");
        bool(options, defaults, "gameResultChat", true, "Gameplay", "Show teammates' game result stats after a game.");
        bool(options, defaults, "statFilter", false, "Stat filter", "Only show players meeting minimum FKDR or stars.");
        decimal(options, defaults, "statFilterMinFkdr", 0.0, 0, 100000, "Stat filter", "Minimum FKDR (zero disables this condition).");
        integer(options, defaults, "statFilterMinStars", 0, 0, 1000000, "Stat filter", "Minimum stars (zero disables this condition).");
        bool(options, defaults, "statFilterChat", false, "Stat filter", "Announce players matching the stat filter.");

        String[] columns = {"Encounters", "Username", "Rank", "Star", "Fkdr", "Winstreaks", "Urchin", "Session", "Level", "Ping", "Wlr", "Bblr", "Kdr", "Kills", "Finals", "Beds", "Wins", "DailyFkdr", "DailyWlr", "DailyStars", "DailyBblr", "DailyKdr", "WeeklyFkdr", "WeeklyWlr", "WeeklyStars", "WeeklyBblr", "WeeklyKdr", "MonthlyFkdr", "MonthlyWlr", "MonthlyStars", "MonthlyBblr", "MonthlyKdr"};
        for (String column : columns) {
            boolean enabled = switch (column) {
                case "Encounters", "Username", "Rank", "Star", "Fkdr", "Winstreaks", "Urchin", "Session" -> true;
                default -> false;
            };
            bool(options, defaults, "col" + column, enabled, "Columns", "Show the " + column + " column.");
        }
        text(options, defaults, "colOrder", "encounters,username,rank,star,fkdr,wlr,bblr,kdr,kills,finals,beds,wins,dailyfkdr,dailywlr,dailystars,dailybblr,dailykdr,weeklyfkdr,weeklywlr,weeklystars,weeklybblr,weeklykdr,monthlyfkdr,monthlywlr,monthlystars,monthlybblr,monthlykdr,winstreaks,urchin,session,ping,level", "Columns", "Column display order.");
        integer(options, defaults, "sortByIndex", 2, 0, 5, "Sorting", "0 encounters, 1 stars, 2 FKDR, 3 join order, 4 winstreak, 5 join time.");
        integer(options, defaults, "sortMode", 0, 0, 1, "Sorting", "0 highest first, 1 lowest first.");
        integer(options, defaults, "winstreakMode", 0, 0, 5, "Sorting", "0 overall, 1 solos, 2 doubles, 3 threes, 4 fours, 5 4v4.");
        integer(options, defaults, "statsDisplayMode", 0, 0, 2, "Sorting", "Legacy stats display mode.");
        integer(options, defaults, "encountersTimeoutMins", 30, 1, 10080, "Sorting", "Minutes before encounter counts reset.");
        integer(options, defaults, "overlayTheme", 0, 0, 2, "Overlay", "0 Lazify, 1 Nerdify, 2 Mellow.");
        integer(options, defaults, "overlayX", 2, -100000, 100000, "Position", "Overlay horizontal pixel position.");
        integer(options, defaults, "overlayY", 2, -100000, 100000, "Position", "Overlay vertical pixel position.");
        integer(options, defaults, "overlayColGap", 12, 0, 40, "Position", "Horizontal spacing between columns.");
        integer(options, defaults, "overlayRowGap", 5, 0, 20, "Position", "Vertical spacing between rows.");
        integer(options, defaults, "overlayScalePercent", 100, 50, 200, "Position", "Overlay scale percentage.");

        integer(options, defaults, "bgOpacity", 170, 0, 255, "Appearance", "Background alpha.");
        integer(options, defaults, "bgHue", 0, 0, 360, "Appearance", "Legacy background hue.");
        for (String key : new String[]{"bgR", "bgG", "bgB"}) integer(options, defaults, key, 0, 0, 255, "Appearance", "Background color component.");
        integer(options, defaults, "headerHue", 290, 0, 360, "Appearance", "Legacy header hue.");
        integer(options, defaults, "borderHue", 360, 0, 360, "Appearance", "Legacy border hue.");
        bool(options, defaults, "outlineEnabled", true, "Appearance", "Draw overlay outline.");
        bool(options, defaults, "outlineChroma", true, "Appearance", "Animate outline color.");
        for (String key : new String[]{"outlineR", "outlineG", "outlineB"}) integer(options, defaults, key, 255, 0, 255, "Appearance", "Outline color component.");
        decimal(options, defaults, "outlineWidth", 2.5, .5, 8, "Appearance", "Outline stroke width.");
        integer(options, defaults, "borderRadius", 0, 0, 16, "Appearance", "Overlay corner radius.");
        integer(options, defaults, "overlayPad", 0, 0, 24, "Appearance", "Inner overlay padding.");
        bool(options, defaults, "textShadow", true, "Appearance", "Draw text with a drop shadow.");
        bool(options, defaults, "headerBold", true, "Appearance", "Draw headers in bold.");
        bool(options, defaults, "stripeEnabled", false, "Appearance", "Enable alternating row tint.");
        rgba(options, defaults, "stripe", 255, 255, 255, 18);
        for (String row : new String[]{"Self", "Party", "Nicked", "Tagged"}) {
            boolean on = false;
            bool(options, defaults, "highlight" + row, on, "Appearance", "Tint " + row.toLowerCase() + " rows.");
            int red = switch (row) { case "Self" -> 80; case "Party" -> 80; default -> 255; };
            int green = switch (row) { case "Self" -> 180; case "Party" -> 255; case "Nicked" -> 220; default -> 60; };
            int blue = switch (row) { case "Self" -> 255; case "Party" -> 120; case "Nicked" -> 60; default -> 60; };
            int alpha = switch (row) { case "Self", "Party" -> 40; case "Nicked" -> 45; default -> 50; };
            rgba(options, defaults, "highlight" + row, red, green, blue, alpha);
        }
        integer(options, defaults, "fkdrDecimals", 2, 0, 3, "Appearance", "Decimal places for ratios.");
        bool(options, defaults, "abbreviateNumbers", false, "Appearance", "Abbreviate large counts.");
        integer(options, defaults, "pingStyle", 0, 0, 1, "Appearance", "0 number, 1 number with ms suffix.");
        integer(options, defaults, "headerAllR", 170, 0, 255, "Appearance", "Bulk header red.");
        integer(options, defaults, "headerAllG", 0, 0, 255, "Appearance", "Bulk header green.");
        integer(options, defaults, "headerAllB", 255, 0, 255, "Appearance", "Bulk header blue.");
        rgba(options, defaults, "mellowOuter", 0, 0, 0, 128);
        rgba(options, defaults, "mellowHeader", 255, 255, 255, 32);
        rgba(options, defaults, "mellowTagged", 0, 0, 0, 153);
        rgba(options, defaults, "mellowRow", 255, 255, 255, 32);
        text(options, defaults, "fkdrColor1", "7", "Appearance", "Legacy FKDR color tier.");
        text(options, defaults, "fkdrColor2", "f", "Appearance", "Legacy FKDR color tier.");
        text(options, defaults, "fkdrColor3", "e", "Appearance", "Legacy FKDR color tier.");
        text(options, defaults, "fkdrColor4", "6", "Appearance", "Legacy FKDR color tier.");
        text(options, defaults, "fkdrColor5", "c", "Appearance", "Legacy FKDR color tier.");
        text(options, defaults, "fkdrColor6", "4", "Appearance", "Legacy FKDR color tier.");
        text(options, defaults, "fkdrColor7", "5", "Appearance", "Legacy FKDR color tier.");
        text(options, defaults, "fkdrScale", "0.0:170,170,170;1.4:255,255,255;2.4:255,255,85;5.0:255,170,0;10.0:255,85,85;100.0:170,0,0;1000.0:170,0,170", "Appearance", "FKDR, WLR, BBLR, KDR color tiers.");
        text(options, defaults, "wsScale", "0.0:170,170,170;25.0:85,255,85;50.0:0,170,0;75.0:255,255,85;100.0:255,170,0;150.0:255,85,85;300.0:170,0,0;500.0:255,85,255;1000.0:170,0,170", "Appearance", "Winstreak color tiers.");
        text(options, defaults, "pingScale", "0.0:85,255,85;100.0:255,255,85;150.0:255,170,0;200.0:255,85,85", "Appearance", "Ping color tiers.");
        text(options, defaults, "sessionScale", "0.0:170,0,0;2.5:255,85,85;5.0:255,255,85;10.0:255,255,85;20.0:85,255,85;120.0:255,255,85;150.0:255,170,0;240.0:255,85,85;360.0:170,0,0", "Appearance", "Session color tiers.");
        text(options, defaults, "encountersScale", "0.0:85,255,85;2.0:255,255,85;4.0:255,170,0;6.0:255,85,85", "Appearance", "Encounter color tiers.");
        text(options, defaults, "countsScale", "0.0:170,170,170;500.0:255,255,255;2000.0:255,255,85;5000.0:255,170,0;10000.0:255,85,85;25000.0:170,0,0;50000.0:255,85,255", "Appearance", "Count color tiers.");
        text(options, defaults, "periodStarsScale", "0.0:170,170,170;1.0:85,255,85;5.0:255,255,85;10.0:255,170,0;25.0:255,85,85;50.0:255,85,255", "Appearance", "Period stars color tiers.");
        text(options, defaults, "headerColors", "", "Appearance", "Per-column header RGB colors.");
        bool(options, defaults, "wsColors", true, "Appearance", "Color-code winstreak values.");
        bool(options, defaults, "pingColors", true, "Appearance", "Color-code ping values.");
        bool(options, defaults, "sessionColors", true, "Appearance", "Color-code session duration.");
        bool(options, defaults, "encountersColors", true, "Appearance", "Color-code encounter counts.");
        bool(options, defaults, "countColors", true, "Appearance", "Color-code count values.");
        bool(options, defaults, "periodStarsColors", true, "Appearance", "Color-code period stars.");
        text(options, defaults, "hypixelApiKey", "", "API keys", "Optional Hypixel API key.");
        text(options, defaults, "bordicApiKey", "", "API keys", "Bordic API key.");
        text(options, defaults, "urchinApiKey", "", "API keys", "Urchin API key.");
        text(options, defaults, "seraphApiKey", "", "API keys", "Seraph API key.");
        Map<String, Option> index = new LinkedHashMap<>();
        for (Option option : options) {
            if (index.put(option.key(), option) != null) throw new IllegalStateException("Duplicate setting " + option.key());
        }
        OPTIONS = Collections.unmodifiableList(options);
        DEFAULTS = Collections.unmodifiableMap(defaults);
        INDEX = Collections.unmodifiableMap(index);
    }

    private LegacySettingCatalog() { }

    static List<Option> all() { return OPTIONS; }
    static Object defaultValue(String key) { return DEFAULTS.get(key); }
    static Map<String, Object> defaults() { return DEFAULTS; }
    static Option option(String key) { return INDEX.get(key); }

    private static void bool(List<Option> o, Map<String, Object> d, String k, boolean v, String s, String desc) { add(o, d, k, label(k), s, ValueType.BOOLEAN, v, 0, 1, desc); }
    private static void integer(List<Option> o, Map<String, Object> d, String k, int v, double min, double max, String s, String desc) { add(o, d, k, label(k), s, ValueType.INTEGER, v, min, max, desc); }
    private static void decimal(List<Option> o, Map<String, Object> d, String k, double v, double min, double max, String s, String desc) { add(o, d, k, label(k), s, ValueType.DECIMAL, v, min, max, desc); }
    private static void text(List<Option> o, Map<String, Object> d, String k, String v, String s, String desc) { add(o, d, k, label(k), s, ValueType.TEXT, v, 0, 0, desc); }
    private static void rgba(List<Option> o, Map<String, Object> d, String p, int r, int g, int b, int a) {
        String prefix = p.startsWith("highlight") ? p : p.substring(0, 1).toLowerCase() + p.substring(1);
        integer(o, d, prefix + "R", r, 0, 255, "Appearance", "Red color component.");
        integer(o, d, prefix + "G", g, 0, 255, "Appearance", "Green color component.");
        integer(o, d, prefix + "B", b, 0, 255, "Appearance", "Blue color component.");
        integer(o, d, prefix + "A", a, 0, 255, "Appearance", "Alpha color component.");
    }
    private static void add(List<Option> o, Map<String, Object> d, String k, String label, String section, ValueType t, Object v, double min, double max, String desc) {
        o.add(new Option(k, label, section, t, min, max, desc)); d.put(k, v);
    }
    private static String label(String key) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (i > 0 && Character.isUpperCase(c)) out.append(' ');
            out.append(i == 0 ? Character.toUpperCase(c) : c);
        }
        return out.toString();
    }
}

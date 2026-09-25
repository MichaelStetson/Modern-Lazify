package com.lazify;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;

import net.minecraft.resources.Identifier;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
public final class LazifyHud {
    private static final int TEXT = 0xFFF1ECF7;
    private static final int MUTED = 0xFFB8ACBF;
    private static final Column[] COLUMNS = {
            new Column("encounters", "colEncounters", "[E]", "enc", "encountersScale", "encountersColors"),
            new Column("username", "colUsername", "[PLAYER]", "ign", null, null),
            new Column("rank", "colRank", "[RANK]", "rank", null, null),
            new Column("star", "colStar", "[STAR]", "star", "periodStarsScale", "periodStarsColors"),
            new Column("fkdr", "colFkdr", "[FKDR]", "fkdr", "fkdrScale", "fkdrColors"),
            new Column("wlr", "colWlr", "[WLR]", "wlr", "fkdrScale", "fkdrColors"),
            new Column("bblr", "colBblr", "[BBLR]", "bblr", "fkdrScale", "fkdrColors"),
            new Column("kdr", "colKdr", "[KDR]", "kdr", "fkdrScale", "fkdrColors"),
            new Column("kills", "colKills", "[KILLS]", "kills", "countsScale", "countColors"),
            new Column("finals", "colFinals", "[FINALS]", "finals", "countsScale", "countColors"),
            new Column("beds", "colBeds", "[BEDS]", "beds", "countsScale", "countColors"),
            new Column("wins", "colWins", "[WINS]", "wins", "countsScale", "countColors"),
            new Column("dailyfkdr", "colDailyFkdr", "[DFKDR]", "dfkdr", "fkdrScale", "fkdrColors"),
            new Column("dailywlr", "colDailyWlr", "[DWLR]", "dwlr", "fkdrScale", "fkdrColors"),
            new Column("dailystars", "colDailyStars", "[DSTAR]", "dstar", "periodStarsScale", "periodStarsColors"),
            new Column("dailybblr", "colDailyBblr", "[DBBLR]", "dbblr", "fkdrScale", "fkdrColors"),
            new Column("dailykdr", "colDailyKdr", "[DKDR]", "dkdr", "fkdrScale", "fkdrColors"),
            new Column("weeklyfkdr", "colWeeklyFkdr", "[WFKDR]", "wfkdr", "fkdrScale", "fkdrColors"),
            new Column("weeklywlr", "colWeeklyWlr", "[WWLR]", "wwlr", "fkdrScale", "fkdrColors"),
            new Column("weeklystars", "colWeeklyStars", "[WSTAR]", "wstar", "periodStarsScale", "periodStarsColors"),
            new Column("weeklybblr", "colWeeklyBblr", "[WBBLR]", "wbblr", "fkdrScale", "fkdrColors"),
            new Column("weeklykdr", "colWeeklyKdr", "[WKDR]", "wkdr", "fkdrScale", "fkdrColors"),
            new Column("monthlyfkdr", "colMonthlyFkdr", "[MFKDR]", "mfkdr", "fkdrScale", "fkdrColors"),
            new Column("monthlywlr", "colMonthlyWlr", "[MWLR]", "mwlr", "fkdrScale", "fkdrColors"),
            new Column("monthlystars", "colMonthlyStars", "[MSTAR]", "mstar", "periodStarsScale", "periodStarsColors"),
            new Column("monthlybblr", "colMonthlyBblr", "[MBBLR]", "mbblr", "fkdrScale", "fkdrColors"),
            new Column("monthlykdr", "colMonthlyKdr", "[MKDR]", "mkdr", "fkdrScale", "fkdrColors"),
            new Column("winstreaks", "colWinstreaks", "[WS]", "ws", "wsScale", "wsColors"),
            new Column("urchin", "colUrchin", "[TAGS]", "tags", null, null),
            new Column("session", "colSession", "[SESSION]", "sess", "sessionScale", "sessionColors"),
            new Column("level", "colLevel", "[LVL]", "lvl", null, null),
            new Column("ping", "colPing", "[PING]", "ping", "pingScale", "pingColors")
    };

    private static LazifyConfig activeConfig;
    private static BedwarsMonitor activeMonitor;
    private static int mellowScroll;

    private final LazifyConfig config;
    private final BedwarsMonitor monitor;

    LazifyHud(LazifyConfig config, BedwarsMonitor monitor) {
        this.config = config;
        this.monitor = monitor;
        activeConfig = config;
        activeMonitor = monitor;
    }

    public static boolean shouldReplaceVanillaPlayerList() {
        Minecraft client = Minecraft.getInstance();
        return activeConfig != null && activeMonitor != null
                && activeConfig.overlayEnabled()
                && activeConfig.getInt("overlayTheme") == 2
                && activeConfig.getBoolean("showOnTab")
                && activeMonitor.inBedwars()
                && client.player != null
                && client.gui.screen() == null
                && client.options.keyPlayerList.isDown();
    }

    public static void scrollMellow(int direction) {
        if (direction == 0 || !shouldReplaceVanillaPlayerList()) return;
        mellowScroll = Math.max(0, Math.min(80, mellowScroll + Integer.signum(direction)));
    }

    private static void clampMellowScroll(int maxScroll) {
        mellowScroll = Math.max(0, Math.min(maxScroll, mellowScroll));
    }

    private static List<Column> nameFirst(List<Column> columns) {
        if (columns.size() < 2 || columns.getFirst().legacyKey.equals("username")) return columns;
        List<Column> ordered = new ArrayList<>(columns.size());
        for (Column column : columns) if (column.legacyKey.equals("username")) ordered.add(column);
        for (Column column : columns) if (!column.legacyKey.equals("username")) ordered.add(column);
        return ordered;
    }

    private List<BedwarsMonitor.PlayerRow> mellowRows(Minecraft client) {
        if (client.getConnection() == null) return List.of();
        Map<UUID, BedwarsMonitor.PlayerRow> tracked = new HashMap<>();
        for (BedwarsMonitor.PlayerRow row : monitor.players()) tracked.put(row.uuid(), row);

        List<PlayerInfo> tabPlayers = new ArrayList<>(client.getConnection().getOnlinePlayers());
        tabPlayers.removeIf(LazifyHud::excludeMellowPlayer);
        tabPlayers.sort(Comparator.comparing((PlayerInfo info) ->
                        info.getTeam() == null ? "" : info.getTeam().getName())
                .thenComparing(info -> info.getProfile().name(), String.CASE_INSENSITIVE_ORDER));
        if (tabPlayers.size() > 80) tabPlayers = tabPlayers.subList(0, 80);

        List<BedwarsMonitor.PlayerRow> rows = new ArrayList<>(tabPlayers.size());
        for (PlayerInfo info : tabPlayers) {
            UUID uuid = info.getProfile().id();
            BedwarsMonitor.PlayerRow row = tracked.get(uuid);
            if (row == null) {
                PlayerTeam team = info.getTeam();
                String teamColor = team == null ? "" : team.getColor()
                        .map(color -> color.getSerializedName()).orElse("white");
                row = new BedwarsMonitor.PlayerRow(uuid, info.getProfile().name(), info.getLatency(),
                        client.player != null && uuid.equals(client.player.getUUID()), teamColor,
                        0, null, "", System.currentTimeMillis());
            }
            if (passesMellowStatFilter(row)) rows.add(row);
        }
        return rows;
    }

    private boolean passesMellowStatFilter(BedwarsMonitor.PlayerRow row) {
        if (!config.getBoolean("statFilter")) return true;
        double minFkdr = config.getDouble("statFilterMinFkdr");
        int minStars = config.getInt("statFilterMinStars");
        if (minFkdr <= 0.0 && minStars <= 0) return true;
        if (row.stats() == null) return false;
        return minFkdr > 0.0 && statValue(row.stats().column("fkdr")) >= minFkdr
                || minStars > 0 && statValue(row.stats().column("star")) >= minStars;
    }

    private static boolean excludeMellowPlayer(PlayerInfo info) {
        if (info.getProfile() == null || info.getProfile().id() == null
                || info.getProfile().name() == null || info.getGameMode() == GameType.SPECTATOR) return true;
        String name = info.getProfile().name();
        if (name.length() >= 3) {
            char first = name.charAt(0);
            boolean repeated = true;
            for (int i = 1; i < name.length(); i++) {
                if (name.charAt(i) != first) {
                    repeated = false;
                    break;
                }
            }
            if (repeated) return true;
        }
        Component displayName = info.getTabListDisplayName();
        TextColor textColor = displayName == null ? null : displayName.getStyle().getColor();
        if (textColor != null) return textColor.getValue() == TextColor.GRAY.getValue();
        PlayerTeam team = info.getTeam();
        return team != null && team.getColor()
                .map(color -> color.getSerializedName().equals("gray")).orElse(false);
    }

    private static double statValue(String value) {
        try {
            return Double.parseDouble(stripFormatting(value).replace(",", "").trim());
        } catch (RuntimeException ignored) {
            return 0.0;
        }
    }


    private static int mellowScrollMax(int count, int visible) {
        return Math.max(0, count - visible);
    }

    private static void resetScrollWhenInactive() {
        if (!shouldReplaceVanillaPlayerList()) mellowScroll = 0;
    }
    void render(net.minecraft.client.gui.GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        int theme = config.getInt("overlayTheme");
        boolean mellow = theme == 2;
        if (!mellow) mellowScroll = 0;
        boolean positionPreview = client.gui.screen() instanceof LazifyPositionScreen;
        if (!config.overlayEnabled() || (!positionPreview && !monitor.inBedwars())
                || (!positionPreview && client.player == null)) {
            resetScrollWhenInactive();
            return;
        }
        if (!positionPreview && (mellow ? !config.getBoolean("showOnTab") || !LazifyClient.tabHeld()
                : !LazifyClient.overlayVisible())) {
            resetScrollWhenInactive();
            return;
        }

        List<Column> columns = visibleColumns();
        if (mellow) columns = nameFirst(columns);
        if (columns.isEmpty()) return;

        List<BedwarsMonitor.PlayerRow> players = mellow ? mellowRows(client) : monitor.players();
        if (mellow && players.isEmpty()) return;
        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        int rowHeight = mellow ? 12 : 12 + config.getInt("overlayRowGap");
        int top = mellow ? 20 : Math.max(0, config.getInt("overlayY"));
        int gap = mellow ? 4 : Math.max(0, config.getInt("overlayColGap"));
        int padding = mellow ? 0 : Math.max(0, config.getInt("overlayPad")) + 5;
        int[] widths = columnWidths(client, columns, players, players.size(), gap, mellow);
        int contentWidth = totalWidth(widths, gap);
        int panelWidth = padding * 2 + contentWidth;
        int headerHeight = mellow ? 12 : 28;
        if (screenWidth < 120 || screenHeight < 80 || panelWidth <= 0) return;

        float scale;
        int rowLimit;
        if (mellow) {
            int availableWidth = Math.max(100, screenWidth - 8);
            scale = panelWidth > availableWidth ? (float) availableWidth / panelWidth : 1.0f;
            int scaledHeader = Math.max(1, (int) Math.ceil(12.0f * scale));
            int scaledRow = Math.max(1, (int) Math.ceil(12.0f * scale));
            rowLimit = Math.max(1, (screenHeight - (top + scaledHeader + 8)) / scaledRow);
            rowLimit = Math.min(80, rowLimit);
        } else {
            rowLimit = config.maxRows();
            scale = Math.max(0.5f, Math.min(2.0f, config.getInt("overlayScalePercent") / 100.0f));
        }
        int rowCount = Math.min(players.size(), rowLimit);
        if (mellow) clampMellowScroll(mellowScrollMax(players.size(), rowCount));
        int firstRow = mellow ? mellowScroll : 0;
        int contentY = padding + headerHeight + (mellow ? 0 : 3);
        int panelHeight = padding * 2 + headerHeight + (mellow ? 0 : 3)
                + Math.max(1, rowCount) * rowHeight + (!mellow && players.isEmpty() ? rowHeight : 0);
        if (!mellow) {
            scale = Math.min(scale, Math.min((screenWidth - 8.0f) / panelWidth,
                    (screenHeight - 8.0f) / panelHeight));
        }
        if (scale <= 0.0f) return;
        int scaledWidth = Math.round(panelWidth * scale);
        int scaledHeight = Math.round(panelHeight * scale);
        int x = mellow ? Math.max(4, (screenWidth - scaledWidth) / 2)
                : Math.max(2, Math.min(screenWidth - scaledWidth - 2, config.getInt("overlayX")));
        int y = mellow ? top : Math.max(2, Math.min(screenHeight - scaledHeight - 2, config.getInt("overlayY")));

        int background = color(config.getInt("bgR"), config.getInt("bgG"), config.getInt("bgB"), config.getInt("bgOpacity"));
        int headerBg = mellow
                ? color(config.getInt("mellowHeaderR"), config.getInt("mellowHeaderG"), config.getInt("mellowHeaderB"), config.getInt("mellowHeaderA"))
                : color(config.getInt("headerAllR"), config.getInt("headerAllG"), config.getInt("headerAllB"), 230);
        int mellowBg = color(config.getInt("mellowOuterR"), config.getInt("mellowOuterG"), config.getInt("mellowOuterB"), config.getInt("mellowOuterA"));
        int rowBg = color(config.getInt("mellowRowR"), config.getInt("mellowRowG"), config.getInt("mellowRowB"), config.getInt("mellowRowA"));
        int taggedBg = color(config.getInt("mellowTaggedR"), config.getInt("mellowTaggedG"), config.getInt("mellowTaggedB"), config.getInt("mellowTaggedA"));
        Map<String, List<ColorStop>> scales = new HashMap<>();
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        if (mellow) {
            int inset = 4 + config.getInt("overlayPad");
            graphics.fill(-inset, -inset, panelWidth + inset, panelHeight + inset, mellowBg);
        } else {
            graphics.fill(0, 0, panelWidth, panelHeight, background);
            if (config.getBoolean("outlineEnabled")) graphics.outline(0, 0, panelWidth, panelHeight, outlineColor());
        }
        graphics.fill(padding, padding, panelWidth - padding, padding + headerHeight, headerBg);

        String title = theme == 1 ? "L A Z I F Y" : "Lazify";
        if (!mellow) graphics.text(client.font, title, padding + 2, padding + 2, TEXT, config.getBoolean("textShadow"));
        int headerY = mellow ? Math.max(0, (headerHeight - client.font.lineHeight) / 2) : padding + 15;
        int cursorX = padding;
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            String header = theme == 1 ? column.compactHeader : column.header;
            if (mellow) {
                if (header.startsWith("[") && header.endsWith("]") && header.length() > 2) {
                    header = header.substring(1, header.length() - 1);
                }
                header = header.toUpperCase(Locale.ROOT);
                if (config.getBoolean("headerBold")) header = "§l" + header + "§r";
            }
            int headerX = cursorX + 2 + (mellow && column.legacyKey.equals("username") ? 10 : 0);
            if (mellow && !column.legacyKey.equals("username") && !column.legacyKey.equals("rank")) {
                headerX = cursorX + widths[i] - 3 - client.font.width(header);
            }
            graphics.text(client.font, header, headerX, headerY, headerColor(column), config.getBoolean("textShadow"));
            cursorX += widths[i] + gap;
        }

        for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
            BedwarsMonitor.PlayerRow row = players.get(firstRow + rowIndex);
            int rowY = contentY + rowIndex * rowHeight;
            int rowBottom = rowY + rowHeight;
            int highlight = rowHighlight(row);
            if (highlight != 0) {
                graphics.fill(mellow ? 0 : 1, rowY, mellow ? panelWidth : panelWidth - 1, rowBottom, highlight);
            } else if (mellow) {
                graphics.fill(0, rowY, panelWidth, rowBottom, row.tag().isBlank() ? rowBg : taggedBg);
            } else if (config.getBoolean("stripeEnabled") && (rowIndex & 1) == 1) {
                int stripe = color(config.getInt("stripeR"), config.getInt("stripeG"), config.getInt("stripeB"), config.getInt("stripeA"));
                graphics.fill(1, rowY, panelWidth - 1, rowBottom, stripe);
            }

            cursorX = padding;
            for (int i = 0; i < columns.size(); i++) {
                Column column = columns.get(i);
                int headSpace = mellow && column.legacyKey.equals("username") ? 10 : 0;
                String text = fit(client, displayValue(column, row), widths[i] - 4 - headSpace);
                int textColor = valueColor(column, text, row, scales);
                if (mellow && column.legacyKey.equals("username")
                        && unresolvedNick(row) && row.team().isBlank()) {
                    textColor = 0xFFFFFF55;
                }
                int textX = cursorX + 2;
                if (headSpace > 0) {
                    drawMellowHead(graphics, client, row.uuid(), textX, rowY + 2);
                    textX += headSpace;
                }
                if (mellow && !column.legacyKey.equals("username") && !column.legacyKey.equals("rank")) {
                    textX = cursorX + widths[i] - 3 - client.font.width(text);
                } else if (theme == 1 && !column.legacyKey.equals("username")) {
                    textX += Math.max(0, (widths[i] - client.font.width(text)) / 2);
                }
                graphics.text(client.font, text, textX, rowY + 1, textColor, config.getBoolean("textShadow"));
                cursorX += widths[i] + gap;
            }
        }
        if (!mellow && players.isEmpty()) {
            graphics.text(client.font, "Waiting for tab-list players", padding + 4, contentY + 1, MUTED, false);
        }
        if (mellow && players.size() > rowCount) {
            String indicator = firstRow > 0 && firstRow + rowCount < players.size() ? "▲▼"
                    : firstRow > 0 ? "▲" : "▼";
            graphics.text(client.font, indicator, panelWidth - client.font.width(indicator) - 4,
                    contentY + rowCount * rowHeight - 8, TEXT, false);
        }
        graphics.pose().popMatrix();
    }


    private List<Column> visibleColumns() {
        Map<String, Column> byKey = new HashMap<>();
        for (Column column : COLUMNS) byKey.put(column.legacyKey, column);
        List<Column> ordered = new ArrayList<>(COLUMNS.length);
        for (String key : config.getString("colOrder").split(",")) {
            Column column = byKey.get(key.trim().toLowerCase(Locale.ROOT));
            if (column != null && !ordered.contains(column)) ordered.add(column);
        }
        for (Column column : COLUMNS) {
            if (!ordered.contains(column)) ordered.add(column);
        }
        return ordered.stream().filter(column -> config.getBoolean(column.settingKey)).toList();
    }

    private int[] columnWidths(Minecraft client, List<Column> columns, List<BedwarsMonitor.PlayerRow> players,
                               int rowCount, int gap, boolean mellow) {
        int[] widths = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            String header = mellow || config.getInt("overlayTheme") == 0 ? column.header : column.compactHeader;
            if (mellow) {
                if (header.startsWith("[") && header.endsWith("]") && header.length() > 2) {
                    header = header.substring(1, header.length() - 1);
                }
                header = header.toUpperCase(Locale.ROOT);
                if (config.getBoolean("headerBold")) header = "§l" + header + "§r";
            }
            int headSpace = mellow && column.legacyKey.equals("username") ? 10 : 0;
            int width = client.font.width(header) + 6 + headSpace;
            for (int row = 0; row < rowCount; row++) {
                width = Math.max(width, client.font.width(displayValue(column, players.get(row))) + 6 + headSpace);
            }
            int max = mellow ? column.legacyKey.equals("username") ? 230 : column.legacyKey.equals("rank") ? 120 : 72
                    : column.legacyKey.equals("username") ? 220 : column.legacyKey.equals("rank") ? 120 : 96;
            int min = column.legacyKey.equals("username") ? 70
                    : mellow && column.legacyKey.equals("star") ? 56
                    : mellow && column.legacyKey.equals("fkdr") ? 40
                    : mellow && column.legacyKey.equals("winstreaks") ? 42
                    : column.legacyKey.equals("rank") ? 48 : 36;
            widths[i] = Math.max(min, Math.min(max, width));
        }
        return widths;
    }

    private String displayValue(Column column, BedwarsMonitor.PlayerRow row) {
        if (column.legacyKey.equals("encounters")) return Integer.toString(row.encounters());
        if (column.legacyKey.equals("username")) {
            String team = config.getInt("overlayTheme") != 2 && config.getBoolean("teamPrefix")
                    ? teamLetter(row.team()) : "";
            String rank = config.getBoolean("showRanks") && row.stats() != null ? row.stats().rankPrefix() : "";
            String name = row.name();
            if (row.stats() != null && row.stats().nicked() && "denicked".equals(row.stats().nickReason())) {
                String realName = row.stats().column("username");
                if (realName != null && !realName.isBlank() && !realName.equals("-")
                        && !realName.equalsIgnoreCase(name)) {
                    name += " §d> §a" + realName + "§r";
                }
            }
            return team + rank + name;
        }
        if (column.legacyKey.equals("rank") && row.stats() != null && row.stats().nicked()) {
            return "§e[NICK]";
        }
        if (column.legacyKey.equals("urchin")) return nonEmpty(row.tag(), "—");
        if (column.legacyKey.equals("ping")) {
            String historical = row.stats() == null ? "" : row.stats().column("ping");
            String value = historical.isBlank() || historical.equals("-")
                    ? row.ping() < 0 ? "-" : Integer.toString(row.ping()) : historical;
            return config.getInt("pingStyle") == 1 && !value.equals("-") && !value.endsWith("ms")
                    ? value + "ms" : value;
        }
        if (row.stats() == null) return "—";
        String value = row.stats().column(column.legacyKey);
        if (value == null || value.isBlank()) return "—";
        if (isRatio(column.legacyKey)) {
            try {
                String plain = stripFormatting(value);
                return String.format(Locale.ROOT, "%." + config.getInt("fkdrDecimals") + "f", Double.parseDouble(plain));
            } catch (NumberFormatException ignored) {
                return value;
            }
        }
        if (config.getBoolean("abbreviateNumbers") && isCount(column.legacyKey)) {
            try {
                long count = Long.parseLong(stripFormatting(value));
                if (count >= 1_000_000) return String.format(Locale.ROOT, "%.1fm", count / 1_000_000.0);
                if (count >= 10_000) return String.format(Locale.ROOT, "%.1fk", count / 1_000.0);
            } catch (NumberFormatException ignored) {
                return value;
            }
        }
        return value;
    }

    private int valueColor(Column column, String text, BedwarsMonitor.PlayerRow row, Map<String, List<ColorStop>> scales) {
        if (column.legacyKey.equals("username")) {
            if (row.self() && config.getBoolean("highlightSelf")) return configuredColor("highlightSelf");
            if (containsIgnoreCase(monitor.partyMembers(), row.name()) && config.getBoolean("highlightParty")) return configuredColor("highlightParty");
            if (unresolvedNick(row) && config.getBoolean("highlightNicked")) return configuredColor("highlightNicked");
            if (!row.tag().isBlank() && config.getBoolean("highlightTagged")) return configuredColor("highlightTagged");
            if (config.getBoolean("teams")) return teamColor(row.team());
            if (row.self()) return 0xFFFFD76A;
        }
        if (column.scaleKey == null || column.colorSetting == null || !config.getBoolean(column.colorSetting)) return TEXT;
        Double value = metricValue(column.legacyKey, text);
        if (value == null) return TEXT;
        List<ColorStop> stops = scales.computeIfAbsent(column.scaleKey, key -> parseScale(config.getString(key)));
        ColorStop selected = stops.isEmpty() ? null : stops.getFirst();
        for (ColorStop stop : stops) {
            if (value < stop.minimum) break;
            selected = stop;
        }
        return selected == null ? TEXT : selected.argb;
    }

    private static boolean unresolvedNick(BedwarsMonitor.PlayerRow row) {
        return row.stats() != null && row.stats().nicked()
                && !"denicked".equals(row.stats().nickReason());
    }
    private int rowHighlight(BedwarsMonitor.PlayerRow row) {
        if (row.self() && config.getBoolean("highlightSelf")) return configuredColor("highlightSelf");
        if (containsIgnoreCase(monitor.partyMembers(), row.name()) && config.getBoolean("highlightParty")) return configuredColor("highlightParty");
        if (unresolvedNick(row) && config.getBoolean("highlightNicked")) return configuredColor("highlightNicked");
        if (!row.tag().isBlank() && config.getBoolean("highlightTagged")) return configuredColor("highlightTagged");
        return 0;
    }

    private int configuredColor(String prefix) {
        return color(config.getInt(prefix + "R"), config.getInt(prefix + "G"),
                config.getInt(prefix + "B"), config.getInt(prefix + "A"));
    }

    private int headerColor(Column column) {
        for (String part : config.getString("headerColors").split(";")) {
            String[] pieces = part.split(":", 2);
            if (pieces.length != 2 || !pieces[0].equalsIgnoreCase(column.legacyKey)) continue;
            String[] rgb = pieces[1].split(",");
            if (rgb.length != 3) break;
            try {
                return color(Integer.parseInt(rgb[0]), Integer.parseInt(rgb[1]), Integer.parseInt(rgb[2]), 255);
            } catch (NumberFormatException ignored) {
                break;
            }
        }
        return color(config.getInt("headerAllR"), config.getInt("headerAllG"), config.getInt("headerAllB"), 255);
    }

    private int outlineColor() {
        if (config.getBoolean("outlineChroma")) {
            float hue = (System.currentTimeMillis() % 6000L) / 6000.0f;
            return 0xFF000000 | java.awt.Color.HSBtoRGB(hue, 0.85f, 1.0f) & 0xFFFFFF;
        }
        return color(config.getInt("outlineR"), config.getInt("outlineG"), config.getInt("outlineB"), 255);
    }

    private static List<ColorStop> parseScale(String encoded) {
        List<ColorStop> stops = new ArrayList<>();
        for (String entry : encoded.split(";")) {
            String[] pieces = entry.split(":", 2);
            if (pieces.length != 2) continue;
            String[] rgb = pieces[1].split(",");
            if (rgb.length != 3) continue;
            try {
                int red = Integer.parseInt(rgb[0]);
                int green = Integer.parseInt(rgb[1]);
                int blue = Integer.parseInt(rgb[2]);
                stops.add(new ColorStop(Double.parseDouble(pieces[0]), color(red, green, blue, 255)));
            } catch (NumberFormatException ignored) {
            }
        }
        stops.sort((left, right) -> Double.compare(left.minimum, right.minimum));
        return stops;
    }

    private static int totalWidth(int[] widths, int gap) {
        int width = 0;
        for (int cell : widths) width += cell;
        return width + Math.max(0, widths.length - 1) * gap;
    }

    private static int color(int red, int green, int blue, int alpha) {
        return (clamp(alpha) << 24) | (clamp(red) << 16) | (clamp(green) << 8) | clamp(blue);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static boolean isRatio(String key) {
        return key.endsWith("fkdr") || key.endsWith("wlr") || key.endsWith("bblr") || key.endsWith("kdr");
    }

    private static boolean isCount(String key) {
        return key.equals("kills") || key.equals("finals") || key.equals("beds") || key.equals("wins");
    }

    private static String stripFormatting(String value) {
        return value.replaceAll("(?i)§[0-9A-FK-ORX]", "");
    }
    private static Double metricValue(String key, String formatted) {
        String value = stripFormatting(formatted).trim().toLowerCase(Locale.ROOT);
        if (key.equals("session")) {
            double minutes = 0.0;
            int index = 0;
            boolean parsed = false;
            while (index < value.length()) {
                int start = index;
                while (index < value.length() && Character.isDigit(value.charAt(index))) index++;
                if (index == start) {
                    index++;
                    continue;
                }
                long amount;
                try {
                    amount = Long.parseLong(value.substring(start, index));
                } catch (NumberFormatException ignored) {
                    return null;
                }
                int unitStart = index;
                while (index < value.length() && Character.isLetter(value.charAt(index))) index++;
                String unit = value.substring(unitStart, index);
                minutes += switch (unit) {
                    case "y" -> amount * 525600.0;
                    case "mo" -> amount * 43830.0;
                    case "d" -> amount * 1440.0;
                    case "h" -> amount * 60.0;
                    case "m" -> amount;
                    case "s" -> amount / 60.0;
                    default -> 0.0;
                };
                parsed |= !unit.isEmpty();
            }
            return parsed ? minutes : null;
        }
        int end = 0;
        if (value.startsWith("-")) end++;
        while (end < value.length() && (Character.isDigit(value.charAt(end)) || value.charAt(end) == '.')) end++;
        if (end == 0 || value.equals("-")) return null;
        try {
            return Double.parseDouble(value.substring(0, end));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }


    private static String nonEmpty(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String teamLetter(String team) {
        if (team == null || team.isBlank()) return "";
        return "[" + Character.toUpperCase(team.charAt(0)) + "] ";
    }

    private static int teamColor(String team) {
        if (team == null) return TEXT;
        return switch (team.toLowerCase(Locale.ROOT)) {
            case "red" -> 0xFFFF5555;
            case "blue" -> 0xFF5555FF;
            case "green" -> 0xFF55FF55;
            case "yellow" -> 0xFFFFFF55;
            case "aqua", "cyan" -> 0xFF55FFFF;
            case "white" -> 0xFFFFFFFF;
            case "pink" -> 0xFFFF55FF;
            case "gray", "grey" -> 0xFFAAAAAA;
            default -> TEXT;
        };
    }

    private static boolean containsIgnoreCase(Iterable<String> values, String target) {
        for (String value : values) {
            if (value.equalsIgnoreCase(target)) return true;
        }
        return false;
    }

    private static String fit(Minecraft client, String value, int width) {
        if (client.font.width(value) <= width) return value;
        String ellipsis = "…";
        int end = value.length();
        while (end > 0 && client.font.width(value.substring(0, end) + ellipsis) > width) end--;
        return end == 0 ? "" : value.substring(0, end) + ellipsis;
    }
    private static void drawMellowHead(net.minecraft.client.gui.GuiGraphicsExtractor graphics, Minecraft client,
                                       java.util.UUID uuid, int x, int y) {
        if (client.getConnection() == null) return;
        PlayerInfo info = client.getConnection().getPlayerInfo(uuid);
        if (info == null || info.getSkin() == null || info.getSkin().body() == null) return;
        Identifier texture = info.getSkin().body().texturePath();
        graphics.blit(texture, x, y, 8, 8, 8f / 64, 8f / 64, 8f / 64, 8f / 64);
        if (info.showHat()) graphics.blit(texture, x, y, 8, 8, 40f / 64, 8f / 64, 8f / 64, 8f / 64);
    }

    private record Column(String legacyKey, String settingKey, String header, String compactHeader,
                          String scaleKey, String colorSetting) {}
    private record ColorStop(double minimum, int argb) {}
}

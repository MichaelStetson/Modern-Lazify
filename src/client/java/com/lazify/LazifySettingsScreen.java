package com.lazify;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class LazifySettingsScreen extends Screen {
    private static final String[] SECTIONS = {"Overlay", "Features", "Customize", "Appearance", "Columns", "API"};
    private static final String[] API_KEYS = {"hypixelApiKey", "bordicApiKey", "urchinApiKey", "seraphApiKey"};
    private static final String[] API_LABELS = {"Hypixel API key", "Bordic API key", "Urchin API key", "Seraph API key"};
    private static final String[] THEMES = {"Lazify", "Nerdify", "Mellow"};

    private final Screen parent;
    private final LazifyConfig config;
    private final List<LegacySettingCatalog.Option> options;
    private final Map<String, Object> pending = new LinkedHashMap<>();
    private final String[] apiValues = new String[API_KEYS.length];
    private String section = "Overlay";
    private int scroll;
    private boolean capturingOverlayKey;
    private String notice = "";

    LazifySettingsScreen(Screen parent, LazifyConfig config) {
        super(Component.literal("Lazify Settings"));
        this.parent = parent;
        this.config = config;
        this.options = LegacySettingCatalog.all();
        for (LegacySettingCatalog.Option option : options) {
            pending.put(option.key(), read(option));
        }
        for (int i = 0; i < API_KEYS.length; i++) apiValues[i] = config.getString(API_KEYS[i]);
    }

    @Override
    protected void init() {
        clearWidgets();
        int margin = 14;
        int tabY = 32;
        int gap = 4;
        int tabWidth = Math.max(48, (width - margin * 2 - gap * (SECTIONS.length - 1)) / SECTIONS.length);
        for (int i = 0; i < SECTIONS.length; i++) {
            String tab = SECTIONS[i];
            addRenderableWidget(Button.builder(Component.literal(tab), button -> {
                section = tab;
                scroll = 0;
                init();
            }).bounds(margin + i * (tabWidth + gap), tabY, tabWidth, 20).build());
        }

        int top = 66;
        int bottom = height - 42;
        int rowHeight = 30;
        int visibleRows = Math.max(1, (bottom - top) / rowHeight);
        if (section.equals("API")) {
            addApiControls(top);
        } else if (section.equals("Columns")) {
            addColumnOrder(top, bottom);
            List<LegacySettingCatalog.Option> columns = options.stream()
                    .filter(option -> option.key().startsWith("col") && !option.key().equals("colOrder"))
                    .toList();
            for (int row = 0; row < visibleRows; row++) {
                int index = scroll + row;
                if (index >= columns.size()) break;
                addOptionRow(columns.get(index), row, top);
            }
        } else {
            List<LegacySettingCatalog.Option> selected = optionsForSection(section);
            for (int row = 0; row < visibleRows; row++) {
                int index = scroll + row;
                if (index >= selected.size()) break;
                addOptionRow(selected.get(index), row, top);
            }
        }

        if (section.equals("Features")) {
            addRenderableWidget(Button.builder(Component.literal("Hidden players"), button -> {
                BedwarsMonitor monitor = LazifyClient.monitor();
                if (monitor != null) Minecraft.getInstance().setScreenAndShow(new LazifyHiddenPlayersScreen(this, monitor));
            }).bounds(18, height - 30, 110, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Save & Apply"), button -> save())
                .bounds(width - 154, height - 30, 96, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width - 52, height - 30, 38, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Move HUD"), button -> {
                    save();
                    Minecraft.getInstance().setScreenAndShow(new LazifyPositionScreen(this, config));
                })
                .bounds(width - 254, height - 30, 90, 20).build());
        if (section.equals("Appearance")) {
            addRenderableWidget(Button.builder(Component.literal("Appearance presets"), button -> {
                save();
                Minecraft.getInstance().setScreenAndShow(new LazifyPresetsScreen(this, config));
            }).bounds(width - 356, height - 30, 88, 20).build());
        }
        if (scroll > 0) addRenderableWidget(Button.builder(Component.literal("▲"), button -> changeScroll(-1))
                .bounds(width - 28, 65, 18, 18).build());
        addRenderableWidget(Button.builder(Component.literal("▼"), button -> changeScroll(1))
                .bounds(width - 28, height - 52, 18, 18).build());
    }

    private List<LegacySettingCatalog.Option> optionsForSection(String selectedSection) {
        return options.stream().filter(option -> {
            String category = option.section();
            if (option.key().equals("statsDisplayMode")) return false;
            if (option.key().equals("overlayTheme")) return selectedSection.equals("Customize");
            return switch (selectedSection) {
                case "Overlay" -> category.equals("Overlay") || category.equals("Position");
                case "Features" -> category.equals("General") || category.equals("Gameplay") || category.equals("Dodge")
                        || category.equals("Party detector") || category.equals("Stat filter") || category.equals("Sorting");
                case "Appearance" -> category.equals("Appearance");
                case "Customize" -> !category.equals("Overlay") && !category.equals("Position") && !category.equals("Appearance")
                        && !category.equals("Columns") && !category.equals("API keys") && !category.equals("General")
                        && !category.equals("Gameplay") && !category.equals("Dodge") && !category.equals("Party detector")
                        && !category.equals("Stat filter") && !category.equals("Sorting");
                default -> false;
            };
        }).toList();
    }
    private void addOptionRow(LegacySettingCatalog.Option option, int row, int top) {
        int y = top + row * 30;
        int labelWidth = Math.min(250, Math.max(125, width / 3));
        int x = 18;
        String key = option.key();
        Object value = pending.get(key);
        addRenderableWidget(Button.builder(Component.literal(option.label()), button -> {})
                .bounds(x, y, labelWidth, 20).build()).active = false;
        if (key.equals("keybind")) {
            String binding = capturingOverlayKey ? "Press a key (Esc cancels)" : LazifyClient.overlayKeyLabel();
            addRenderableWidget(Button.builder(Component.literal(binding), button -> {
                capturingOverlayKey = true;
                init();
            }).bounds(x + labelWidth + 10, y, 150, 20).build());
            return;
        }
        switch (option.type()) {
            case BOOLEAN -> addRenderableWidget(Button.builder(Component.literal(Boolean.TRUE.equals(value) ? "On" : "Off"), button -> {
                pending.put(key, !Boolean.TRUE.equals(pending.get(key)));
                init();
            }).bounds(x + labelWidth + 10, y, 58, 20).build());
            case INTEGER -> {
                if (key.equals("overlayTheme")) {
                    int theme = value instanceof Number n ? Math.floorMod(n.intValue(), THEMES.length) : 0;
                    addRenderableWidget(Button.builder(Component.literal(THEMES[theme]), button -> {
                        int next = (Math.floorMod(((Number) pending.get(key)).intValue(), THEMES.length) + 1) % THEMES.length;
                        pending.put(key, next);
                        init();
                    }).bounds(x + labelWidth + 10, y, 130, 20).build());
                } else {
                    addRenderableWidget(Button.builder(Component.literal("−"), button -> adjust(option, -1))
                            .bounds(x + labelWidth + 10, y, 24, 20).build());
                    addRenderableWidget(Button.builder(Component.literal(value.toString()), button -> {})
                            .bounds(x + labelWidth + 38, y, 66, 20).build()).active = false;
                    addRenderableWidget(Button.builder(Component.literal("+"), button -> adjust(option, 1))
                            .bounds(x + labelWidth + 108, y, 24, 20).build());
                }
            }
            case DECIMAL -> {
                addRenderableWidget(Button.builder(Component.literal("−"), button -> adjust(option, -1))
                        .bounds(x + labelWidth + 10, y, 24, 20).build());
                addRenderableWidget(Button.builder(Component.literal(String.format(Locale.ROOT, "%.2f", ((Number) value).doubleValue())), button -> {})
                        .bounds(x + labelWidth + 38, y, 66, 20).build()).active = false;
                addRenderableWidget(Button.builder(Component.literal("+"), button -> adjust(option, 1))
                        .bounds(x + labelWidth + 108, y, 24, 20).build());
            }
            case TEXT -> {
                EditBox field = new EditBox(font, x + labelWidth + 10, y, Math.max(80, width - x - labelWidth - 45), 20, Component.literal(option.label()));
                field.setMaxLength(512);
                field.setValue(value == null ? "" : value.toString());
                field.setResponder(text -> pending.put(key, text));
                addRenderableWidget(field);
            }
        }
    }

    private void addColumnOrder(int top, int bottom) {
        int x = width - 210;
        int y = top;
        int rowHeight = 23;
        List<String> order = columnOrder();
        addRenderableWidget(Button.builder(Component.literal("Column order  (use ↑ / ↓)"), button -> {})
                .bounds(x, y, 190, 20).build()).active = false;
        int visible = Math.max(1, (bottom - top - 30) / rowHeight);
        for (int index = scroll; index < Math.min(order.size(), scroll + visible); index++) {
            int row = index;
            int itemY = y + 24 + (index - scroll) * rowHeight;
            String key = order.get(index);
            String display = key.startsWith("col") ? key.substring(3) : key;
            addRenderableWidget(Button.builder(Component.literal(display), button -> {})
                    .bounds(x, itemY, 100, 20).build()).active = false;
            addRenderableWidget(Button.builder(Component.literal("↑"), button -> moveColumn(row, -1))
                    .bounds(x + 104, itemY, 38, 20).build()).active = row > 0;
            addRenderableWidget(Button.builder(Component.literal("↓"), button -> moveColumn(row, 1))
                    .bounds(x + 146, itemY, 38, 20).build()).active = row < order.size() - 1;
        }
    }

    private void addApiControls(int top) {
        int rowHeight = 34;
        for (int i = 0; i < API_KEYS.length; i++) {
            final int index = i;
            int y = top + i * rowHeight;
            int labelWidth = Math.min(210, width / 3);
            addRenderableWidget(Button.builder(Component.literal(API_LABELS[i]), button -> {})
                    .bounds(18, y, labelWidth, 20).build()).active = false;
            EditBox field = new EditBox(font, 28 + labelWidth, y, Math.max(100, width - labelWidth - 54), 20, Component.literal(API_LABELS[i]));
            field.setMaxLength(256);
            field.setValue(apiValues[i] == null ? "" : apiValues[i]);
            field.addFormatter((value, firstCharacterIndex) -> FormattedCharSequence.forward("•".repeat(value.length()), net.minecraft.network.chat.Style.EMPTY));
            field.setResponder(text -> apiValues[index] = text);
            addRenderableWidget(field);
        }
        notice = "API key contents are masked while editing.";
    }

    private Object read(LegacySettingCatalog.Option option) {
        return switch (option.type()) {
            case BOOLEAN -> config.getBoolean(option.key());
            case INTEGER -> config.getInt(option.key());
            case DECIMAL -> config.getDouble(option.key());
            case TEXT -> config.getString(option.key());
        };
    }

    private void adjust(LegacySettingCatalog.Option option, int direction) {
        Object current = pending.get(option.key());
        if (option.type() == LegacySettingCatalog.ValueType.INTEGER) {
            int value = current instanceof Number n ? n.intValue() : 0;
            pending.put(option.key(), clamp(value + direction, (int) option.min(), (int) option.max()));
        } else {
            double value = current instanceof Number n ? n.doubleValue() : 0.0;
            pending.put(option.key(), Math.max(option.min(), Math.min(option.max(), value + direction * 0.1)));
        }
        init();
    }

    private List<String> columnOrder() {
        Object value = pending.getOrDefault("colOrder", config.getString("colOrder"));
        List<String> result = new ArrayList<>();
        if (value instanceof String text && !text.isBlank()) {
            for (String key : text.split(",")) {
                String normalized = key.trim().toLowerCase(Locale.ROOT);
                if (normalized.startsWith("col")) normalized = normalized.substring(3);
                if (!normalized.isEmpty() && !result.contains("col" + normalized)) result.add("col" + normalized);
            }
        }
        for (LegacySettingCatalog.Option option : options) {
            String key = option.key();
            if (!key.startsWith("col") || key.equals("colOrder")) continue;
            String normalized = key.substring(3).toLowerCase(Locale.ROOT);
            if (!result.contains("col" + normalized)) result.add("col" + normalized);
        }
        return result;
    }

    private void moveColumn(int from, int delta) {
        List<String> order = columnOrder();
        int to = from + delta;
        if (to < 0 || to >= order.size()) return;
        String item = order.remove(from);
        order.add(to, item);
        pending.put("colOrder", String.join(",", order));
        init();
    }

    private void changeScroll(int delta) {
        int count;
        if (section.equals("Columns")) {
            count = Math.max((int) options.stream().filter(option -> option.key().startsWith("col") && !option.key().equals("colOrder")).count(), columnOrder().size());
        } else if (section.equals("API")) {
            count = 0;
        } else {
            count = optionsForSection(section).size();
        }
        int visible = Math.max(1, (height - 108) / 30);
        scroll = Math.max(0, Math.min(Math.max(0, count - visible), scroll + delta));
        init();
    }
    private void save() {
        for (LegacySettingCatalog.Option option : options) {
            if (option.key().toLowerCase(Locale.ROOT).contains("apikey")) continue;
            Object value = pending.get(option.key());
            if (value == null) continue;
            switch (option.type()) {
                case BOOLEAN -> config.setBoolean(option.key(), (Boolean) value);
                case INTEGER -> config.setInt(option.key(), ((Number) value).intValue());
                case DECIMAL -> config.setDouble(option.key(), ((Number) value).doubleValue());
                case TEXT -> config.setString(option.key(), value.toString());
            }
        }
        for (int i = 0; i < API_KEYS.length; i++) config.setString(API_KEYS[i], apiValues[i] == null ? "" : apiValues[i]);
        config.save();
        notice = "Settings saved to " + config.file().getFileName();
    }

    void reloadPending() {
        pending.clear();
        for (LegacySettingCatalog.Option option : options) pending.put(option.key(), read(option));
        for (int i = 0; i < API_KEYS.length; i++) apiValues[i] = config.getString(API_KEYS[i]);
        init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, title, width / 2, 12, 0xFFFFFFFF, true);
        if (!notice.isEmpty()) graphics.text(font, notice, 18, height - 27, 0xFFBEB6C9, false);
        if (section.equals("Columns")) graphics.text(font, "Toggle columns on the left; reorder on the right.", 18, height - 50, 0xFFBEB6C9, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0) return false;
        changeScroll(verticalAmount > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!capturingOverlayKey) return super.keyPressed(event);
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            capturingOverlayKey = false;
            init();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_UNKNOWN) return true;
        pending.put("keybind", event.key());
        capturingOverlayKey = false;
        LazifyClient.rebindOverlayKey(event.key());
        init();
        return true;
    }
    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

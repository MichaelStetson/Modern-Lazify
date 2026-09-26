package com.lazify;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
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
    private static final long AUTOSAVE_DELAY_NANOS = 300_000_000L;
    private static final int RESIZE_GRIP_SIZE = 8;

    private final Screen parent;
    private final LazifyConfig config;
    private final List<LegacySettingCatalog.Option> options;
    private final Map<String, Object> pending = new LinkedHashMap<>();
    private final String[] apiValues = new String[API_KEYS.length];
    private String section = "Overlay";
    private int scroll;
    private boolean capturingOverlayKey;
    private String notice = "";
    private String draggingSetting;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int contentX;
    private int contentY;
    private int contentW;
    private int contentH;
    private List<LegacySettingCatalog.Option> visibleOptions = List.of();
    private List<String> visibleColumnOrder = List.of();
    private final List<TextFieldLayout> textFieldLayouts = new ArrayList<>();
    private int initializedVisibleRows;
    private boolean autosavePending;
    private long autosaveDeadline;
    private boolean resizing;
    private int resizeStartMouseX;
    private int resizeStartMouseY;
    private int resizeStartWidth;
    private int resizeStartHeight;
    private int resizeStartX;
    private int resizeStartY;
    private boolean draggingWindow;
    private boolean windowDragMoved;
    private int windowDragStartMouseX;
    private int windowDragStartMouseY;
    private int windowDragStartX;
    private int windowDragStartY;

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
        textFieldLayouts.clear();
        panelW = LazifyScreenStyle.windowWidth(width, config.getInt("clickGuiWidth"));
        panelH = LazifyScreenStyle.windowHeight(height, config.getInt("clickGuiHeight"));
        panelX = LazifyScreenStyle.windowPositionX(width, panelW, config.getInt("clickGuiX"));
        panelY = LazifyScreenStyle.windowPositionY(height, panelH, config.getInt("clickGuiY"));
        updateContentGeometry();

        int visibleRows = Math.max(1, contentH / 30);
        initializedVisibleRows = visibleRows;

        if (section.equals("API")) {
            visibleOptions = List.of();
            visibleColumnOrder = List.of();
            addApiControls(contentY);
        } else if (section.equals("Columns")) {
            visibleOptions = columnOptions();
            visibleColumnOrder = columnOrder();
            for (int row = 0; row < visibleRows; row++) {
                int index = scroll + row;
                if (index >= visibleOptions.size()) break;
                addOptionRow(visibleOptions.get(index), row, contentY);
            }
        } else {
            visibleOptions = optionsForSection(section);
            visibleColumnOrder = List.of();
            for (int row = 0; row < visibleRows; row++) {
                int index = scroll + row;
                if (index >= visibleOptions.size()) break;
                addOptionRow(visibleOptions.get(index), row, contentY);
            }
        }
    }
    private void updateContentGeometry() {
        contentX = panelX + 70;
        contentY = panelY + 28;
        contentW = panelW - 70;
        contentH = Math.max(1, panelY + panelH - 34 - contentY);
    }

    private int minimumVisibleRows() {
        return Math.max(1, contentH / 30);
    }

    private List<LegacySettingCatalog.Option> optionsForSection(String selectedSection) {
        return options.stream().filter(option -> {
            String category = option.section();
            if (category.equals("Interface")) return false;
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
    private List<LegacySettingCatalog.Option> columnOptions() {
        return options.stream()
                .filter(option -> option.key().startsWith("col") && !option.key().equals("colOrder"))
                .toList();
    }
    private void addOptionRow(LegacySettingCatalog.Option option, int row, int top) {
        if (option.type() != LegacySettingCatalog.ValueType.TEXT) return;
        int x = contentX + 12;
        int y = top + row * 30;
        int labelWidth = Math.min(160, Math.max(90, contentW / 3));
        int fieldX = x + labelWidth + 10;
        int fieldWidth = Math.max(80, panelX + panelW - 12 - fieldX);
        EditBox field = new EditBox(font, fieldX, y, fieldWidth, 20, Component.literal(option.label()));
        field.setMaxLength(512);
        Object value = pending.get(option.key());
        field.setValue(value == null ? "" : value.toString());
        field.setResponder(text -> setPending(option.key(), text));
        addRenderableWidget(field);
        textFieldLayouts.add(new TextFieldLayout(field, row, false));
    }


    private void addApiControls(int top) {
        int rowHeight = 34;
        int x = contentX + 12;
        int labelWidth = Math.min(140, Math.max(100, contentW / 3));
        int fieldX = x + labelWidth + 10;
        int fieldWidth = Math.max(80, panelX + panelW - 12 - fieldX);
        for (int i = 0; i < API_KEYS.length; i++) {
            final int index = i;
            int y = top + i * rowHeight;
            EditBox field = new EditBox(font, fieldX, y, fieldWidth, 20, Component.literal(API_LABELS[i]));
            field.setMaxLength(256);
            field.setValue(apiValues[i] == null ? "" : apiValues[i]);
            field.addFormatter((value, firstCharacterIndex) -> FormattedCharSequence.forward(
                    "•".repeat(value.length()), net.minecraft.network.chat.Style.EMPTY));
            field.setResponder(text -> setApiValue(index, text));
            addRenderableWidget(field);
            textFieldLayouts.add(new TextFieldLayout(field, i, true));
        }
        notice = "API key contents are masked while editing.";
    }
    private void updateTextFieldLayouts() {
        for (TextFieldLayout layout : textFieldLayouts) {
            int labelWidth = layout.api() ? Math.min(140, Math.max(100, contentW / 3))
                    : Math.min(160, Math.max(90, contentW / 3));
            int x = contentX + 12 + labelWidth + 10;
            int y = contentY + layout.row() * (layout.api() ? 34 : 30);
            int fieldWidth = Math.max(80, panelX + panelW - 12 - x);
            layout.field().setX(x);
            layout.field().setY(y);
            layout.field().setWidth(fieldWidth);
        }
    }

    private Object read(LegacySettingCatalog.Option option) {
        return switch (option.type()) {
            case BOOLEAN -> config.getBoolean(option.key());
            case INTEGER -> config.getInt(option.key());
            case DECIMAL -> config.getDouble(option.key());
            case TEXT -> config.getString(option.key());
        };
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
        setPending("colOrder", String.join(",", order));
        flushAutosave();
        init();
    }

    private void changeScroll(int delta) {
        int count = section.equals("Columns") ? Math.max(visibleOptions.size(), visibleColumnOrder.size())
                : section.equals("API") ? 0 : visibleOptions.size();
        int visible = Math.max(1, (contentH - (section.equals("Columns") ? 24 : 0)) / 30);
        scroll = Math.max(0, Math.min(Math.max(0, count - visible), scroll + delta));
        init();
    }
    private void setPending(String key, Object value) {
        LegacySettingCatalog.Option option = LegacySettingCatalog.option(key);
        if (option == null) return;
        pending.put(key, value);
        switch (option.type()) {
            case BOOLEAN -> config.setBoolean(key, (Boolean) value);
            case INTEGER -> config.setInt(key, ((Number) value).intValue());
            case DECIMAL -> config.setDouble(key, ((Number) value).doubleValue());
            case TEXT -> config.setString(key, value.toString());
        }
        scheduleAutosave();
    }

    private void setApiValue(int index, String value) {
        apiValues[index] = value;
        config.setString(API_KEYS[index], value);
        scheduleAutosave();
    }

    private void scheduleAutosave() {
        autosavePending = true;
        autosaveDeadline = System.nanoTime() + AUTOSAVE_DELAY_NANOS;
        notice = "Saving...";
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
        autosavePending = false;
        notice = "Settings saved to " + config.file().getFileName();
    }

    private void flushAutosave() {
        if (!autosavePending) return;
        config.save();
        autosavePending = false;
        notice = "Settings saved to " + config.file().getFileName();
    }

    void reloadPending() {
        pending.clear();
        for (LegacySettingCatalog.Option option : options) pending.put(option.key(), read(option));
        for (int i = 0; i < API_KEYS.length; i++) apiValues[i] = config.getString(API_KEYS[i]);
        autosavePending = false;
        init();
    }
    @Override
    public void tick() {
        super.tick();
        if (autosavePending && System.nanoTime() - autosaveDeadline >= 0) flushAutosave();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        LazifyScreenStyle.drawWindow(graphics, font, panelX, panelY, panelW, panelH, section);
        drawContentBackgrounds(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        drawSidebar(graphics, mouseX, mouseY);
        drawContent(graphics, mouseX, mouseY);
        drawFooter(graphics, mouseX, mouseY);
        drawResizeGrip(graphics, mouseX, mouseY);
    }

    private int footerY() {
        return panelY + panelH - 25;
    }

    private int doneButtonX() {
        return panelX + panelW - 48;
    }

    private int saveButtonX() {
        return doneButtonX() - 4 - 88;
    }

    private int presetsButtonX() {
        return saveButtonX() - 4 - 70;
    }

    private int moveButtonX() {
        return panelX + panelW - 96;
    }

    private int columnOrderX() {
        return panelX + panelW - 202;
    }

    private void drawContentBackgrounds(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int bodyBottom = panelY + panelH - 34;
        if (section.equals("Columns")) {
            int left = contentX + 8;
            int right = columnOrderX() - 8;
            int rows = Math.max(0, (contentH - 24) / 30);
            for (int row = 0; row < rows && scroll + row < visibleOptions.size(); row++) {
                int y = contentY + 24 + row * 30;
                int fill = row % 2 == 1 ? 0x08FFFFFF : 0;
                if (LazifyScreenStyle.contains(mouseX, mouseY, left, y, Math.max(0, right - left), 24)) {
                    fill = 0x18FFFFFF;
                }
                if (fill != 0) graphics.fill(left, y, right, y + 24, fill);
            }
            int orderX = columnOrderX();
            int orderRows = Math.max(0, (bodyBottom - contentY - 24) / 23);
            for (int row = 0; row < orderRows && scroll + row < visibleColumnOrder.size(); row++) {
                int y = contentY + 24 + row * 23;
                int fill = row % 2 == 1 ? 0x08FFFFFF : 0;
                if (LazifyScreenStyle.contains(mouseX, mouseY, orderX, y, 190, 18)) fill = 0x18FFFFFF;
                if (fill != 0) graphics.fill(orderX, y, orderX + 190, y + 18, fill);
            }
        } else {
            int rowHeight = section.equals("API") ? 34 : 30;
            int rows = section.equals("API") ? API_KEYS.length : visibleOptions.size();
            int start = section.equals("API") ? 0 : scroll;
            int visible = Math.max(0, (bodyBottom - contentY) / rowHeight);
            for (int row = 0; row < visible && start + row < rows; row++) {
                int y = contentY + row * rowHeight;
                int fill = row % 2 == 1 ? 0x08FFFFFF : 0;
                if (LazifyScreenStyle.contains(mouseX, mouseY, contentX + 8, y,
                        panelX + panelW - contentX - 16, rowHeight)) fill = 0x18FFFFFF;
                if (fill != 0) graphics.fill(contentX + 8, y, panelX + panelW - 8, y + rowHeight, fill);
            }
        }
        graphics.fill(contentX, bodyBottom, panelX + panelW, bodyBottom + 1, LazifyScreenStyle.DIVIDER);
    }

    private void drawSidebar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (int i = 0; i < SECTIONS.length; i++) {
            int y = panelY + 28 + i * 20;
            LazifyScreenStyle.drawTab(graphics, font, panelX, y, 69, 20, SECTIONS[i],
                    section.equals(SECTIONS[i]), LazifyScreenStyle.contains(mouseX, mouseY, panelX, y, 69, 20));
        }
        LazifyScreenStyle.drawButton(graphics, font, moveButtonX(), panelY + 4, 88, 16,
                "Move Overlay", mouseX, mouseY, true);
    }

    private void drawContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (section.equals("Columns")) {
            drawColumns(graphics, mouseX, mouseY);
        } else if (section.equals("API")) {
            drawApiLabels(graphics);
        } else {
            for (int row = 0; row < Math.max(0, contentH / 30); row++) {
                int index = scroll + row;
                if (index >= visibleOptions.size()) break;
                drawOption(graphics, visibleOptions.get(index), contentY + row * 30);
            }
        }
        drawScrollbar(graphics);
    }

    private void drawColumns(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int left = contentX + 8;
        int right = columnOrderX();
        int leftWidth = Math.max(0, right - left - 8);
        int bottom = panelY + panelH - 34;
        graphics.text(font, "Enabled columns", left + 2, contentY + 5, LazifyScreenStyle.TEXT, false);
        graphics.text(font, "Column order  (use ↑ / ↓)", right, contentY + 5,
                LazifyScreenStyle.TEXT, false);
        for (int row = 0; row < Math.max(0, (contentH - 24) / 30); row++) {
            int index = scroll + row;
            if (index >= visibleOptions.size()) break;
            LegacySettingCatalog.Option option = visibleOptions.get(index);
            int y = contentY + 24 + row * 30;
            String label = ellipsize(option.label(), Math.max(24, leftWidth - 34));
            graphics.text(font, label, left + 2, y + 6, LazifyScreenStyle.TEXT, false);
            drawToggle(graphics, left + leftWidth - 22, y + 7,
                    Boolean.TRUE.equals(pending.get(option.key())));
        }
        int rows = Math.max(0, (bottom - contentY - 24) / 23);
        for (int row = 0; row < rows; row++) {
            int index = scroll + row;
            if (index >= visibleColumnOrder.size()) break;
            String key = visibleColumnOrder.get(index);
            String label = key.startsWith("col") ? key.substring(3) : key;
            int y = contentY + 24 + row * 23;
            graphics.text(font, ellipsize(label, 126), right + 4, y + 5, LazifyScreenStyle.TEXT, false);
            LazifyScreenStyle.drawButton(graphics, font, right + 132, y, 18, 18,
                    "↑", mouseX, mouseY, index > 0);
            LazifyScreenStyle.drawButton(graphics, font, right + 152, y, 18, 18,
                    "↓", mouseX, mouseY, index < visibleColumnOrder.size() - 1);
        }
    }

    private void drawApiLabels(GuiGraphicsExtractor graphics) {
        for (int row = 0; row < API_KEYS.length; row++) {
            graphics.text(font, API_LABELS[row], contentX + 12, contentY + row * 34 + 6,
                    LazifyScreenStyle.TEXT, false);
        }
    }

    private void drawOption(GuiGraphicsExtractor graphics, LegacySettingCatalog.Option option, int y) {
        String key = option.key();
        int right = panelX + panelW - 12;
        int labelX = contentX + 12;
        int labelWidth = option.type() == LegacySettingCatalog.ValueType.TEXT
                ? Math.min(160, Math.max(90, contentW / 3)) + 4
                : Math.max(40, right - (option.type() == LegacySettingCatalog.ValueType.BOOLEAN ? 28
                : key.equals("keybind") ? 156 : 142) - labelX - 6);
        graphics.text(font, ellipsize(option.label(), labelWidth), labelX, y + 5,
                LazifyScreenStyle.TEXT, false);
        graphics.text(font, ellipsize(option.description(), labelWidth), labelX, y + 17,
                LazifyScreenStyle.TEXT_DIM, false);
        if (key.equals("keybind")) {
            String binding = capturingOverlayKey ? "Press a key (Esc cancels)" : LazifyClient.overlayKeyLabel();
            drawValue(graphics, binding, right, y);
            return;
        }
        Object value = pending.get(key);
        if (option.type() == LegacySettingCatalog.ValueType.BOOLEAN) {
            drawToggle(graphics, right - 20, y + 10, Boolean.TRUE.equals(value));
        } else if (key.equals("overlayTheme")) {
            int index = value instanceof Number number ? Math.floorMod(number.intValue(), THEMES.length) : 0;
            drawValue(graphics, THEMES[index], right, y);
        } else if (option.type() == LegacySettingCatalog.ValueType.INTEGER
                || option.type() == LegacySettingCatalog.ValueType.DECIMAL) {
            drawSlider(graphics, option, value, right - 80, y + 12);
        }
    }

    private void drawValue(GuiGraphicsExtractor graphics, String value, int right, int y) {
        String label = ellipsize(value, 150);
        graphics.text(font, label, right - font.width(label), y + 10, LazifyScreenStyle.TEXT, false);
    }

    private void drawToggle(GuiGraphicsExtractor graphics, int x, int y, boolean on) {
        graphics.fill(x, y, x + 20, y + 10, on ? LazifyScreenStyle.ACCENT : -12961206);
        int knobX = on ? x + 11 : x + 1;
        graphics.fill(knobX, y + 1, knobX + 8, y + 9, -2039584);
    }

    private void drawSlider(GuiGraphicsExtractor graphics, LegacySettingCatalog.Option option, Object value,
                            int x, int y) {
        double number = value instanceof Number numeric ? numeric.doubleValue() : option.min();
        double range = option.max() - option.min();
        float position = range <= 0 ? 0 : (float) ((number - option.min()) / range);
        position = Math.max(0, Math.min(1, position));
        graphics.fill(x, y, x + 80, y + 6, -14342859);
        int fill = Math.round(80 * position);
        if (fill > 0) graphics.fill(x, y, x + fill, y + 6, draggingSetting != null
                && draggingSetting.equals(option.key()) ? LazifyScreenStyle.ACCENT : LazifyScreenStyle.ACCENT_DIM);
        int knob = Math.min(x + 78, Math.max(x, x + fill));
        graphics.fill(knob - 1, y - 1, knob + 3, y + 7, -2039584);
        String text = value instanceof Number numeric && option.type() == LegacySettingCatalog.ValueType.DECIMAL
                ? String.format(Locale.ROOT, "%.2f", numeric.doubleValue()) : String.valueOf(value);
        text = ellipsize(text, 50);
        graphics.text(font, text, x - 8 - font.width(text), y - 1, -7303008, false);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics) {
        int count = section.equals("API") ? 0 : section.equals("Columns")
                ? Math.max(visibleOptions.size(), visibleColumnOrder.size()) : visibleOptions.size();
        int rowHeight = 30;
        int totalHeight = count * rowHeight;
        if (totalHeight <= contentH) return;
        int thumbHeight = Math.max(10, contentH * contentH / totalHeight);
        int maxScroll = Math.max(1, count - Math.max(1, contentH / rowHeight));
        int thumbY = contentY + (int) ((double) scroll / maxScroll * (contentH - thumbHeight));
        graphics.fill(panelX + panelW - 3, thumbY, panelX + panelW - 1, thumbY + thumbHeight, 0x40FFFFFF);
    }

    private void drawFooter(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int y = footerY();
        if (section.equals("Features")) {
            LazifyScreenStyle.drawButton(graphics, font, contentX + 8, y, 82, 18,
                    "Hidden players", mouseX, mouseY, true);
        } else if (!notice.isEmpty()) {
            graphics.text(font, ellipsize(notice, Math.max(30, presetsButtonX() - contentX - 16)),
                    contentX + 8, y + 5, LazifyScreenStyle.TEXT_DIM, false);
        }
        if (section.equals("Appearance")) {
            LazifyScreenStyle.drawButton(graphics, font, presetsButtonX(), y, 70, 18,
                    "Presets", mouseX, mouseY, true);
        }
        LazifyScreenStyle.drawButton(graphics, font, saveButtonX(), y, 88, 18,
                "Save & Apply", mouseX, mouseY, true);
        LazifyScreenStyle.drawButton(graphics, font, doneButtonX(), y, 40, 18,
                "Done", mouseX, mouseY, true);
    }
    private void drawResizeGrip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int x = panelX + panelW - RESIZE_GRIP_SIZE;
        int y = panelY + panelH - RESIZE_GRIP_SIZE;
        int color = LazifyScreenStyle.contains(mouseX, mouseY, x, y,
                RESIZE_GRIP_SIZE, RESIZE_GRIP_SIZE) ? LazifyScreenStyle.ACCENT : LazifyScreenStyle.DIVIDER;
        for (int offset = 0; offset < 5; offset++) {
            graphics.fill(x + RESIZE_GRIP_SIZE - 1 - offset, y + RESIZE_GRIP_SIZE - 1 - offset,
                    x + RESIZE_GRIP_SIZE - offset, y + RESIZE_GRIP_SIZE - offset, color);
        }
    }

    private String ellipsize(String value, int maxWidth) {
        if (value == null || font.width(value) <= maxWidth) return value == null ? "" : value;
        String suffix = "...";
        int end = value.length();
        while (end > 0 && font.width(value.substring(0, end) + suffix) > maxWidth) end--;
        return end == 0 ? "" : value.substring(0, end) + suffix;
    }

    private void setSlider(LegacySettingCatalog.Option option, double mouseX) {
        int sliderX = panelX + panelW - 12 - 80;
        double ratio = Math.max(0.0, Math.min(1.0, (mouseX - sliderX) / 80.0));
        double value = option.min() + ratio * (option.max() - option.min());
        if (option.type() == LegacySettingCatalog.ValueType.INTEGER) {
            setPending(option.key(), (int) Math.round(value));
        } else {
            setPending(option.key(), Math.max(option.min(), Math.min(option.max(),
                    Math.round(value * 100.0) / 100.0)));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        double mouseX = event.x();
        double mouseY = event.y();
        if (LazifyScreenStyle.contains(mouseX, mouseY, panelX, panelY, panelW, 24)) {
            draggingWindow = true;
            windowDragMoved = false;
            windowDragStartMouseX = (int) Math.round(mouseX);
            windowDragStartMouseY = (int) Math.round(mouseY);
            windowDragStartX = panelX;
            windowDragStartY = panelY;
            return true;
        }
        int gripX = panelX + panelW - RESIZE_GRIP_SIZE;
        int gripY = panelY + panelH - RESIZE_GRIP_SIZE;
        if (LazifyScreenStyle.contains(mouseX, mouseY, gripX, gripY,
                RESIZE_GRIP_SIZE, RESIZE_GRIP_SIZE)) {
            resizing = true;
            resizeStartMouseX = (int) Math.round(mouseX);
            resizeStartMouseY = (int) Math.round(mouseY);
            resizeStartWidth = panelW;
            resizeStartHeight = panelH;
            resizeStartX = panelX;
            resizeStartY = panelY;
            return true;
        }
        for (int i = 0; i < SECTIONS.length; i++) {
            int y = panelY + 28 + i * 20;
            if (!LazifyScreenStyle.contains(mouseX, mouseY, panelX, y, 69, 20)) continue;
            section = SECTIONS[i];
            scroll = 0;
            init();
            return true;
        }
        int footerY = footerY();
        if (LazifyScreenStyle.contains(mouseX, mouseY, doneButtonX(), footerY, 40, 18)) {
            onClose();
            return true;
        }
        if (LazifyScreenStyle.contains(mouseX, mouseY, saveButtonX(), footerY, 88, 18)) {
            save();
            return true;
        }
        if (section.equals("Appearance")
                && LazifyScreenStyle.contains(mouseX, mouseY, presetsButtonX(), footerY, 70, 18)) {
            save();
            Minecraft.getInstance().setScreenAndShow(new LazifyPresetsScreen(this, config));
            return true;
        }
        if (section.equals("Features")
                && LazifyScreenStyle.contains(mouseX, mouseY, contentX + 8, footerY, 82, 18)) {
            BedwarsMonitor monitor = LazifyClient.monitor();
            if (monitor != null) Minecraft.getInstance().setScreenAndShow(new LazifyHiddenPlayersScreen(this, monitor));
            return true;
        }
        if (mouseY >= contentY && mouseY < panelY + panelH - 34) {
            if (section.equals("Columns")) {
                int orderX = columnOrderX();
                if (mouseX >= orderX) {
                    int row = (int) ((mouseY - contentY - 24) / 23) + scroll;
                    if (row >= 0 && row < visibleColumnOrder.size()) {
                        if (LazifyScreenStyle.contains(mouseX, mouseY, orderX + 132, contentY + 24 + (row - scroll) * 23, 18, 18)) {
                            moveColumn(row, -1);
                            return true;
                        }
                        if (LazifyScreenStyle.contains(mouseX, mouseY, orderX + 152, contentY + 24 + (row - scroll) * 23, 18, 18)) {
                            moveColumn(row, 1);
                            return true;
                        }
                    }
                } else if (mouseY >= contentY + 24) {
                    int row = (int) ((mouseY - contentY - 24) / 30) + scroll;
                    if (row >= 0 && row < visibleOptions.size()) {
                        LegacySettingCatalog.Option option = visibleOptions.get(row);
                        if (mouseX >= columnOrderX() - 30) {
                            setPending(option.key(), !Boolean.TRUE.equals(pending.get(option.key())));
                            flushAutosave();
                            return true;
                        }
                    }
                }
            } else if (!section.equals("API")) {
                int row = (int) ((mouseY - contentY) / 30) + scroll;
                if (row >= 0 && row < visibleOptions.size() && clickOption(visibleOptions.get(row), mouseX)) {
                    flushAutosave();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean clickOption(LegacySettingCatalog.Option option, double mouseX) {
        int right = panelX + panelW - 12;
        String key = option.key();
        if (option.type() == LegacySettingCatalog.ValueType.BOOLEAN && mouseX >= right - 28) {
            setPending(key, !Boolean.TRUE.equals(pending.get(key)));
            return true;
        }
        if (key.equals("keybind") && mouseX >= right - 156) {
            capturingOverlayKey = true;
            init();
            return true;
        }
        if (key.equals("overlayTheme") && mouseX >= right - 142) {
            int current = ((Number) pending.get(key)).intValue();
            setPending(key, (Math.floorMod(current, THEMES.length) + 1) % THEMES.length);
            return true;
        }
        if (option.type() == LegacySettingCatalog.ValueType.INTEGER
                || option.type() == LegacySettingCatalog.ValueType.DECIMAL) {
            if (mouseX < right - 142) return false;
            setSlider(option, mouseX);
            draggingSetting = key;
            return true;
        }
        return false;
    }

    private void resizeWindow(double mouseX, double mouseY) {
        int maximumWidth = Math.max(180, width - resizeStartX - 20);
        int maximumHeight = Math.max(140, height - resizeStartY - 20);
        int minimumWidth = Math.min(300, maximumWidth);
        int minimumHeight = Math.min(220, maximumHeight);
        int newWidth = Math.max(minimumWidth, Math.min(maximumWidth,
                resizeStartWidth + (int) Math.round(mouseX - resizeStartMouseX)));
        int newHeight = Math.max(minimumHeight, Math.min(maximumHeight,
                resizeStartHeight + (int) Math.round(mouseY - resizeStartMouseY)));
        if (newWidth == panelW && newHeight == panelH) return;

        panelW = newWidth;
        panelH = newHeight;
        panelX = resizeStartX;
        panelY = resizeStartY;
        updateContentGeometry();
        setPending("clickGuiWidth", panelW);
        setPending("clickGuiHeight", panelH);
        if (minimumVisibleRows() != initializedVisibleRows) {
            int left = panelX;
            int top = panelY;
            init();
            panelX = left;
            panelY = top;
            updateContentGeometry();
        }
        updateTextFieldLayouts();
    }

    private void moveWindow(double mouseX, double mouseY) {
        int deltaX = (int) Math.round(mouseX - windowDragStartMouseX);
        int deltaY = (int) Math.round(mouseY - windowDragStartMouseY);
        if (deltaX == 0 && deltaY == 0) return;
        windowDragMoved = true;
        int maximumX = Math.max(0, width - panelW);
        int maximumY = Math.max(0, height - panelH);
        panelX = Math.max(0, Math.min(maximumX, windowDragStartX + deltaX));
        panelY = Math.max(0, Math.min(maximumY, windowDragStartY + deltaY));
        updateContentGeometry();
        setPending("clickGuiX", panelX);
        setPending("clickGuiY", panelY);
        updateTextFieldLayouts();
    }
    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingWindow && event.button() == 0) {
            moveWindow(event.x(), event.y());
            return true;
        }
        if (resizing && event.button() == 0) {
            resizeWindow(event.x(), event.y());
            return true;
        }
        if (draggingSetting != null && event.button() == 0) {
            LegacySettingCatalog.Option option = LegacySettingCatalog.option(draggingSetting);
            if (option != null) setSlider(option, event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && draggingWindow) {
            draggingWindow = false;
            if (!windowDragMoved && LazifyScreenStyle.contains(event.x(), event.y(),
                    moveButtonX(), panelY + 4, 88, 16)) {
                save();
                Minecraft.getInstance().setScreenAndShow(new LazifyPositionScreen(this, config));
            } else {
                flushAutosave();
            }
            return true;
        }
        if (event.button() == 0 && resizing) {
            resizing = false;
            flushAutosave();
            return true;
        }
        if (event.button() == 0 && draggingSetting != null) {
            draggingSetting = null;
            flushAutosave();
            return true;
        }
        return super.mouseReleased(event);
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
        setPending("keybind", event.key());
        capturingOverlayKey = false;
        LazifyClient.rebindOverlayKey(event.key());
        autosavePending = false;
        notice = "Settings saved to " + config.file().getFileName();
        init();
        return true;
    }
    @Override
    public void onClose() {
        if (autosavePending) save();
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record TextFieldLayout(EditBox field, int row, boolean api) { }
}

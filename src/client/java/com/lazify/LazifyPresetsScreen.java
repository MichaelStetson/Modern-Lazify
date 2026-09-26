package com.lazify;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.List;

final class LazifyPresetsScreen extends Screen {
    private static final int PANEL_HEIGHT = 222;
    private static final int LIST_ROWS = 5;

    private final Screen parent;
    private final LazifyConfig config;
    private List<String> names;
    private EditBox nameField;
    private String notice = "Presets contain applied appearance settings, not position or column order.";
    private boolean loadedPreset;
    private int nameScroll;

    LazifyPresetsScreen(Screen parent, LazifyConfig config) {
        super(Component.literal("Appearance Presets"));
        this.parent = parent;
        this.config = config;
        this.names = listNames(config);
    }

    private int panelWidth() {
        return Math.max(200, Math.min(380, width - 24));
    }

    private int panelX() {
        return (width - panelWidth()) / 2;
    }

    private int panelY() {
        return (height - PANEL_HEIGHT) / 2;
    }

    private int listTop() {
        return panelY() + 119;
    }

    @Override
    protected void init() {
        clearWidgets();
        int x = panelX();
        nameField = new EditBox(font, x + 18, panelY() + 44, panelWidth() - 36, 20,
                Component.literal("Preset name"));
        nameField.setMaxLength(64);
        addRenderableWidget(nameField);
        nameScroll = Math.max(0, Math.min(nameScroll, Math.max(0, names.size() - LIST_ROWS)));
    }

    private static List<String> listNames(LazifyConfig config) {
        try {
            return AppearancePresets.list(config);
        } catch (IOException e) {
            return List.of();
        }
    }

    private void save() {
        try {
            AppearancePresets.save(config, nameField.getValue());
            names = listNames(config);
            notice = "Saved preset " + nameField.getValue().trim() + ".";
        } catch (IOException | IllegalArgumentException e) {
            notice = "Could not save preset: " + e.getMessage();
        }
    }

    private void load() {
        try {
            if (AppearancePresets.load(config, nameField.getValue())) {
                loadedPreset = true;
                notice = "Loaded preset " + nameField.getValue().trim() + ".";
            } else notice = "Preset not found.";
        } catch (IOException | IllegalArgumentException e) {
            notice = "Could not load preset: " + e.getMessage();
        }
    }

    private void delete() {
        try {
            notice = AppearancePresets.delete(config, nameField.getValue())
                    ? "Deleted preset " + nameField.getValue().trim() + "." : "Preset not found.";
            names = listNames(config);
            nameScroll = Math.min(nameScroll, Math.max(0, names.size() - LIST_ROWS));
        } catch (IOException | IllegalArgumentException e) {
            notice = "Could not delete preset: " + e.getMessage();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = panelX();
        int y = panelY();
        int panelWidth = panelWidth();
        LazifyScreenStyle.drawDialog(graphics, font, x, y, panelWidth, PANEL_HEIGHT, "Appearance Presets");
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, "Preset name", x + 18, y + 31, LazifyScreenStyle.TEXT_DIM, false);

        int buttonY = y + 72;
        int buttonWidth = (panelWidth - 44) / 3;
        LazifyScreenStyle.drawButton(graphics, font, x + 18, buttonY, buttonWidth, 21, "Save",
                mouseX, mouseY, true);
        LazifyScreenStyle.drawButton(graphics, font, x + 22 + buttonWidth, buttonY, buttonWidth, 21, "Load",
                mouseX, mouseY, true);
        LazifyScreenStyle.drawButton(graphics, font, x + 26 + buttonWidth * 2, buttonY, buttonWidth, 21, "Delete",
                mouseX, mouseY, true);

        graphics.text(font, "Saved presets", x + 18, y + 101, LazifyScreenStyle.TEXT_DIM, false);
        int listTop = listTop();
        int listBottom = listTop + LIST_ROWS * 13;
        graphics.fill(x + 18, listTop - 2, x + panelWidth - 18, listBottom, 0x50101012);
        if (names.isEmpty()) {
            graphics.text(font, "No saved presets", x + 24, listTop, LazifyScreenStyle.TEXT_DIM, false);
        } else {
            for (int row = 0; row < LIST_ROWS; row++) {
                int index = nameScroll + row;
                if (index >= names.size()) break;
                int rowY = listTop + row * 13;
                boolean hovered = LazifyScreenStyle.contains(mouseX, mouseY, x + 18, rowY,
                        panelWidth - 36, 13);
                String name = names.get(index);
                graphics.text(font, name, x + 24, rowY, hovered ? LazifyScreenStyle.TEXT : LazifyScreenStyle.TEXT_DIM,
                        false);
                if (hovered) graphics.fill(x + 20, rowY + 11, x + panelWidth - 20, rowY + 12,
                        LazifyScreenStyle.ACCENT);
            }
        }
        int noticeY = y + 176;
        graphics.text(font, font.plainSubstrByWidth(notice, panelWidth - 36), x + 18, noticeY,
                LazifyScreenStyle.TEXT, false);
        LazifyScreenStyle.drawButton(graphics, font, x + panelWidth - 86, y + 194, 68, 19, "Done",
                mouseX, mouseY, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0) return false;
        double mouseX = event.x();
        double mouseY = event.y();

        int x = panelX();
        int y = panelY();
        int panelWidth = panelWidth();
        int buttonY = y + 72;
        int buttonWidth = (panelWidth - 44) / 3;
        if (LazifyScreenStyle.contains(mouseX, mouseY, x + 18, buttonY, buttonWidth, 21)) {
            save();
            return true;
        }
        if (LazifyScreenStyle.contains(mouseX, mouseY, x + 22 + buttonWidth, buttonY, buttonWidth, 21)) {
            load();
            return true;
        }
        if (LazifyScreenStyle.contains(mouseX, mouseY, x + 26 + buttonWidth * 2, buttonY, buttonWidth, 21)) {
            delete();
            return true;
        }
        int listTop = listTop();
        int row = (int) ((mouseY - listTop) / 13);
        if (LazifyScreenStyle.contains(mouseX, mouseY, x + 18, listTop, panelWidth - 36, LIST_ROWS * 13)
                && row >= 0 && row < LIST_ROWS) {
            int index = nameScroll + row;
            if (index < names.size()) nameField.setValue(names.get(index));
            return true;
        }
        if (LazifyScreenStyle.contains(mouseX, mouseY, x + panelWidth - 86, y + 194, 68, 19)) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int x = panelX();
        int panelWidth = panelWidth();
        int listTop = listTop();
        if (verticalAmount == 0 || !LazifyScreenStyle.contains(mouseX, mouseY, x + 18, listTop,
                panelWidth - 36, LIST_ROWS * 13)) return false;
        nameScroll += verticalAmount > 0 ? -1 : 1;
        nameScroll = Math.max(0, Math.min(nameScroll, Math.max(0, names.size() - LIST_ROWS)));
        return true;
    }

    @Override
    public void onClose() {
        if (loadedPreset && parent instanceof LazifySettingsScreen settings) settings.reloadPending();
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

package com.lazify;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.List;

final class LazifyPresetsScreen extends Screen {
    private final Screen parent;
    private final LazifyConfig config;
    private List<String> names;
    private EditBox nameField;
    private String notice = "Presets contain applied appearance settings, not position or column order.";
    private boolean loadedPreset;

    LazifyPresetsScreen(Screen parent, LazifyConfig config) {
        super(Component.literal("Appearance Presets"));
        this.parent = parent;
        this.config = config;
        this.names = listNames(config);
    }

    @Override
    protected void init() {
        clearWidgets();
        nameField = new EditBox(font, 18, 48, Math.max(130, width - 36), 20, Component.literal("Preset name"));
        nameField.setMaxLength(64);
        addRenderableWidget(nameField);
        addRenderableWidget(Button.builder(Component.literal("Save"), button -> {
            try {
                AppearancePresets.save(config, nameField.getValue());
                names = listNames(config);
                notice = "Saved preset " + nameField.getValue().trim() + ".";
            } catch (IOException | IllegalArgumentException e) {
                notice = "Could not save preset: " + e.getMessage();
            }
        }).bounds(18, 78, 66, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Load"), button -> {
            try {
                if (AppearancePresets.load(config, nameField.getValue())) {
                    loadedPreset = true;
                    notice = "Loaded preset " + nameField.getValue().trim() + ".";
                } else notice = "Preset not found.";
            } catch (IOException | IllegalArgumentException e) {
                notice = "Could not load preset: " + e.getMessage();
            }
        }).bounds(90, 78, 66, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Delete"), button -> {
            try {
                notice = AppearancePresets.delete(config, nameField.getValue())
                        ? "Deleted preset " + nameField.getValue().trim() + "." : "Preset not found.";
                names = listNames(config);
            } catch (IOException | IllegalArgumentException e) {
                notice = "Could not delete preset: " + e.getMessage();
            }
        }).bounds(162, 78, 66, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width - 58, height - 30, 40, 20).build());
    }

    private static List<String> listNames(LazifyConfig config) {
        try {
            return AppearancePresets.list(config);
        } catch (IOException e) {
            return List.of();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, "Saved presets: " + (names.isEmpty() ? "none" : String.join(", ", names)), 18, 112, 0xFFCCCCCC, false);
        graphics.text(font, notice, 18, height - 52, 0xFFFFFFFF, false);
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

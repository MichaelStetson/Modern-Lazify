package com.lazify;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

final class LazifyPositionScreen extends Screen {
    private final Screen parent;
    private final LazifyConfig config;
    private int x;
    private int y;
    private int dragOffsetX;
    private int dragOffsetY;
    private boolean dragging;

    LazifyPositionScreen(Screen parent, LazifyConfig config) {
        super(Component.literal("Move Lazify Overlay"));
        this.parent = parent;
        this.config = config;
        this.x = config.getInt("overlayX");
        this.y = config.getInt("overlayY");
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        String instructions = config.getInt("overlayTheme") == 2
                ? "Mellow is centered while Tab is held; its position is fixed. Press Esc to return."
                : "Drag the overlay header to move it. Press Esc to save and return.";
        graphics.text(font, instructions, 18, 18, 0xFFFFFFFF, true);
        if (config.getInt("overlayTheme") != 2) {
            int left = Math.max(2, Math.min(width - 142, x));
            int top = Math.max(2, Math.min(height - 30, y));
            graphics.outline(left, top, left + 140, top + 28, dragging ? 0xFFFFFF55 : 0xFF55FFFF);
            graphics.text(font, "Drag here", left + 5, top + 8, 0xFFFFFFFF, true);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (event.button() == 0 && config.getInt("overlayTheme") != 2) {
            int left = Math.max(2, Math.min(width - 142, x));
            int top = Math.max(2, Math.min(height - 30, y));
            if (mouseX >= left && mouseX <= left + 140 && mouseY >= top && mouseY <= top + 28) {
                dragging = true;
                dragOffsetX = (int) mouseX - left;
                dragOffsetY = (int) mouseY - top;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging && event.button() == 0) {
            x = Math.max(2, Math.min(width - 140, (int) event.x() - dragOffsetX));
            y = Math.max(2, Math.min(height - 28, (int) event.y() - dragOffsetY));
            config.setInt("overlayX", x);
            config.setInt("overlayY", y);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() {
        config.save();
        if (parent instanceof LazifySettingsScreen settings) settings.reloadPending();
        Minecraft.getInstance().setScreenAndShow(parent);
    }
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

package com.lazify;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

final class LazifyPositionScreen extends Screen {
    private final Screen parent;
    private final LazifyConfig config;
    private static final int DONE_WIDTH = 100;
    private static final int DONE_HEIGHT = 20;
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
        LazifyClient.renderPositionPreview(graphics);
        LazifyScreenStyle.drawTextCentered(graphics, font, "Drag the overlay to reposition it",
                width / 2, 10, 0xFFFFFFFF, false);
        String position = "Current position: " + x + ", " + y + "  Scale: "
                + config.getInt("overlayScalePercent") + "%";
        LazifyScreenStyle.drawTextCentered(graphics, font, position,
                width / 2, 22, 0xFFAAAAAA, false);
        if (config.getInt("overlayTheme") == 2) {
            LazifyScreenStyle.drawTextCentered(graphics, font,
                    "Mellow uses the tab list — switch to Lazify/Nerdify to position the HUD",
                    width / 2, 36, 0xFFFFAA55, false);
        }
        LazifyScreenStyle.drawButton(graphics, font, (width - DONE_WIDTH) / 2,
                height - DONE_HEIGHT - 8, DONE_WIDTH, DONE_HEIGHT, "Done",
                mouseX, mouseY, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (event.button() == 0) {
            int doneX = (width - DONE_WIDTH) / 2;
            int doneY = height - DONE_HEIGHT - 8;
            if (LazifyScreenStyle.contains(mouseX, mouseY, doneX, doneY, DONE_WIDTH, DONE_HEIGHT)) {
                onClose();
                return true;
            }
            if (config.getInt("overlayTheme") != 2) {
                LazifyHud.PreviewBounds bounds = LazifyHud.previewBounds();
                if (bounds != null && LazifyScreenStyle.contains(mouseX, mouseY,
                        bounds.x(), bounds.y(), bounds.width(), bounds.height())) {
                    dragging = true;
                    dragOffsetX = (int) mouseX - bounds.x();
                    dragOffsetY = (int) mouseY - bounds.y();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging && event.button() == 0) {
            LazifyHud.PreviewBounds bounds = LazifyHud.previewBounds();
            if (bounds != null) {
                x = Math.max(0, Math.min(width - bounds.width(), (int) event.x() - dragOffsetX));
                y = Math.max(0, Math.min(height - bounds.height(), (int) event.y() - dragOffsetY));
                config.setInt("overlayX", x);
                config.setInt("overlayY", y);
            }
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


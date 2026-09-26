package com.lazify;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

final class LazifyHiddenPlayersScreen extends Screen {
    private static final int LIST_WIDTH = 220;
    private static final int ROW_HEIGHT = 16;
    private static final int VISIBLE_ROWS = 8;

    private final Screen parent;
    private final BedwarsMonitor monitor;
    private EditBox input;
    private int scroll;
    private List<String> hidden = List.of();

    LazifyHiddenPlayersScreen(Screen parent, BedwarsMonitor monitor) {
        super(Component.literal("Hidden Players"));
        this.parent = parent;
        this.monitor = monitor;
    }

    @Override
    protected void init() {
        clearWidgets();
        int center = width / 2;
        input = new EditBox(font, center - 100, height - 52, 200, 18, Component.literal("Player name"));
        input.setMaxLength(16);
        addRenderableWidget(input);
        input.setFocused(true);
        refreshList();
    }

    private void refreshList() {
        hidden = monitor.hiddenPlayers().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        clampScroll();
    }

    private int listTop() {
        return 40;
    }

    private int rowAt(double mouseX, double mouseY) {
        int left = width / 2 - LIST_WIDTH / 2;
        if (mouseX < left || mouseX >= left + LIST_WIDTH || mouseY < listTop()) return -1;
        int row = (int) ((mouseY - listTop()) / ROW_HEIGHT);
        if (row < 0 || row >= VISIBLE_ROWS || mouseY >= listTop() + row * ROW_HEIGHT + 14
                || scroll + row >= hidden.size()) return -1;
        return row;
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(Math.max(0, hidden.size() - VISIBLE_ROWS), scroll));
    }

    private void changeScroll(int delta) {
        scroll += delta;
        clampScroll();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int center = width / 2;
        int left = center - LIST_WIDTH / 2;
        LazifyScreenStyle.drawTextCentered(graphics, font, "Hidden Players", center, 12,
                0xFFFFFFFF, false);
        LazifyScreenStyle.drawTextCentered(graphics, font, "Click a name to unhide", center, 24,
                0xFF888888, false);
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = scroll + row;
            if (index >= hidden.size()) break;
            int y = listTop() + row * ROW_HEIGHT;
            boolean hovered = rowAt(mouseX, mouseY) == row;
            graphics.text(font, hidden.get(index) + " §7(x)", left, y,
                    hovered ? -10048769 : -3355444, true);
        }
        if (hidden.isEmpty()) {
            LazifyScreenStyle.drawTextCentered(graphics, font, "No hidden players", center, 60,
                    0xFF666666, false);
        }

        int controlsY = height - 28;
        LazifyScreenStyle.drawButton(graphics, font, center - 105, controlsY, 100, 20,
                "Add Hide", mouseX, mouseY, true);
        LazifyScreenStyle.drawButton(graphics, font, center + 5, controlsY, 100, 20,
                "Clear All", mouseX, mouseY, true);
        LazifyScreenStyle.drawButton(graphics, font, center - 50, height - 4, 100, 20,
                "Done", mouseX, mouseY, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != 0) return false;
        double mouseX = event.x();
        double mouseY = event.y();
        int center = width / 2;
        int controlsY = height - 28;
        if (LazifyScreenStyle.contains(mouseX, mouseY, center - 105, controlsY, 100, 20)) {
            String name = input.getValue().trim();
            if (!name.isEmpty()) monitor.hidePlayer(name);
            input.setValue("");
            refreshList();
            return true;
        }
        if (LazifyScreenStyle.contains(mouseX, mouseY, center + 5, controlsY, 100, 20)) {
            monitor.clearHiddenPlayers();
            scroll = 0;
            refreshList();
            return true;
        }
        if (LazifyScreenStyle.contains(mouseX, mouseY, center - 50, height - 4, 100, 20)) {
            onClose();
            return true;
        }
        int row = rowAt(mouseX, mouseY);
        if (row >= 0) {
            monitor.unhidePlayer(hidden.get(scroll + row));
            refreshList();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount == 0) return false;
        changeScroll(verticalAmount > 0 ? -1 : 1);
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

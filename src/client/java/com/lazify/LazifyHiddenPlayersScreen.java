package com.lazify;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

final class LazifyHiddenPlayersScreen extends Screen {
    private final Screen parent;
    private final BedwarsMonitor monitor;
    private int scroll;

    LazifyHiddenPlayersScreen(Screen parent, BedwarsMonitor monitor) {
        super(Component.literal("Hidden Players"));
        this.parent = parent;
        this.monitor = monitor;
    }

    @Override
    protected void init() {
        clearWidgets();
        EditBox input = new EditBox(font, 18, 42, Math.max(120, width - 155), 20, Component.literal("Player name"));
        input.setMaxLength(16);
        addRenderableWidget(input);
        addRenderableWidget(Button.builder(Component.literal("Hide"), button -> {
            String name = input.getValue().trim();
            if (!name.isEmpty()) monitor.hidePlayer(name);
            init();
        }).bounds(width - 126, 42, 108, 20).build());

        List<String> names = monitor.hiddenPlayers().stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
        int visible = Math.max(0, (height - 132) / 25);
        for (int row = 0; row < visible; row++) {
            int index = scroll + row;
            if (index >= names.size()) break;
            String name = names.get(index);
            int y = 74 + row * 25;
            addRenderableWidget(Button.builder(Component.literal(name), button -> {})
                    .bounds(18, y, Math.max(120, width - 155), 20).build()).active = false;
            addRenderableWidget(Button.builder(Component.literal("Unhide"), button -> {
                monitor.unhidePlayer(name);
                init();
            }).bounds(width - 126, y, 108, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Clear all"), button -> {
            monitor.clearHiddenPlayers();
            scroll = 0;
            init();
        }).bounds(18, height - 30, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(width - 58, height - 30, 40, 20).build());
        if (scroll > 0) addRenderableWidget(Button.builder(Component.literal("▲"), button -> changeScroll(-1))
                .bounds(width - 28, 72, 18, 18).build());
        if (scroll + visible < names.size()) addRenderableWidget(Button.builder(Component.literal("▼"), button -> changeScroll(1))
                .bounds(width - 28, height - 52, 18, 18).build());
    }

    private void changeScroll(int delta) {
        int visible = Math.max(0, (height - 132) / 25);
        int count = monitor.hiddenPlayers().size();
        scroll = Math.max(0, Math.min(Math.max(0, count - visible), scroll + delta));
        init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(font, "Hidden names are kept locally for this session.", 18, 18, 0xFFCCCCCC, false);
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

package com.lazify;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

final class LazifyScreenStyle {
    static final int WINDOW = -267119584;
    static final int SIDEBAR = -267382760;
    static final int HEADER = -266856408;
    static final int ACCENT = -12877066;
    static final int ACCENT_DIM = -14796193;
    static final int TEXT = -2039584;
    static final int TEXT_DIM = -9408384;
    static final int DIVIDER = -14540237;

    private LazifyScreenStyle() { }

    static int windowWidth(int screenWidth) {
        return Math.max(180, Math.min(420, screenWidth - 40));
    }

    static int windowHeight(int screenHeight) {
        return Math.max(140, Math.min(320, screenHeight - 30));
    }

    static void drawWindow(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                           String title) {
        graphics.fill(x, y, x + width, y + height, WINDOW);
        graphics.fill(x, y, x + 70, y + height, SIDEBAR);
        graphics.fill(x + 69, y, x + 70, y + height, DIVIDER);
        graphics.fill(x + 70, y, x + width, y + 24, HEADER);
        graphics.fill(x + 70, y + 23, x + width, y + 24, DIVIDER);
        graphics.fill(x, y, x + width, y + 1, ACCENT);
        graphics.fill(x, y + height - 1, x + width, y + height, DIVIDER);
        graphics.fill(x, y, x + 1, y + height, DIVIDER);
        graphics.fill(x + width - 1, y, x + width, y + height, DIVIDER);
        graphics.text(font, "§bLazify", x + 8, y + 8, 0xFFFFFFFF, true);
        graphics.text(font, title, x + 80, y + 8, TEXT, false);
    }
    static void drawDialog(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                           String title) {
        graphics.fill(x, y, x + width, y + height, WINDOW);
        graphics.fill(x, y, x + width, y + 24, HEADER);
        graphics.fill(x, y + 23, x + width, y + 24, DIVIDER);
        graphics.fill(x, y, x + width, y + 1, ACCENT);
        graphics.fill(x, y + height - 1, x + width, y + height, DIVIDER);
        graphics.fill(x, y, x + 1, y + height, DIVIDER);
        graphics.fill(x + width - 1, y, x + width, y + height, DIVIDER);
        graphics.text(font, "§bLazify", x + 8, y + 8, 0xFFFFFFFF, true);
        graphics.text(font, title, x + 80, y + 8, TEXT, false);
    }

    static void drawTab(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                        String label, boolean selected, boolean hovered) {
        if (selected || hovered) {
            graphics.fill(x, y, x + width, y + height, 0x15FFFFFF);
        }
        if (selected) graphics.fill(x, y + 2, x + 2, y + height - 2, ACCENT);
        graphics.text(font, label, x + 10, y + (height - font.lineHeight) / 2,
                selected ? 0xFFFFFFFF : hovered ? -4473908 : TEXT_DIM, false);
    }

    static void drawButton(GuiGraphicsExtractor graphics, Font font, int x, int y, int width, int height,
                           String label, int mouseX, int mouseY, boolean active) {
        boolean hovered = active && contains(mouseX, mouseY, x, y, width, height);
        int fill = !active ? 0xA018181C : hovered ? 0xD02F3D48 : 0xB01C2229;
        graphics.fill(x, y, x + width, y + height, fill);
        graphics.fill(x, y, x + width, y + 1, hovered ? ACCENT : DIVIDER);
        graphics.fill(x, y + height - 1, x + width, y + height, DIVIDER);
        graphics.fill(x, y, x + 1, y + height, DIVIDER);
        graphics.fill(x + width - 1, y, x + width, y + height, DIVIDER);
        int textWidth = font.width(label);
        graphics.text(font, label, x + Math.max(2, (width - textWidth) / 2),
                y + (height - font.lineHeight) / 2, active ? TEXT : TEXT_DIM, false);
    }

    static void drawTextCentered(GuiGraphicsExtractor graphics, Font font, String text, int centerX, int y,
                                 int color, boolean shadow) {
        graphics.text(font, text, centerX - font.width(text) / 2, y, color, shadow);
    }

    static boolean contains(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}

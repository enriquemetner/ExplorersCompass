package com.chaosthedude.explorerscompass.gui;

import java.util.ArrayList;
import java.util.List;
import com.chaosthedude.explorerscompass.unlocking.RequirementNode;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.HoveredTooltipPositioner;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

/** Modal requirements panel using the same frame and dim backdrop as the search dialog. */
final class LockedRequirementsView {
    static final int WINDOW_WIDTH = 260;
    private static final int BUTTON_BACKGROUND = 0xff242424;
    private static final int BUTTON_BORDER = 0xff909090;
    private final TextRenderer renderer;
    private final RequirementNode requirements;
    private record Row(OrderedText text, int indent, Boolean ticked, boolean bulletless,
                       Text tooltipTitle, Text tooltipDescription) {}
    private List<Row> lines = List.of();
    private int screenWidth = -1, screenHeight = -1;
    private int x, y, width, bodyHeight, lineHeight;
    private double scroll, grabOffset;
    private boolean dragging;

    LockedRequirementsView(TextRenderer renderer, RequirementNode requirements) {
        this.renderer = renderer;
        this.requirements = requirements;
        this.lineHeight = renderer.fontHeight + 3;
    }

    private void layout(int screenWidth, int screenHeight) {
        if (this.screenWidth == screenWidth && this.screenHeight == screenHeight) return;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        width = Math.max(40, Math.min(WINDOW_WIDTH, screenWidth - 24));
        lineHeight = renderer.fontHeight + 3;
        lines = new ArrayList<>();
        addNode(requirements, 0, false);
        // Include the title, Back button, spacing, and border in the half-screen limit.
        int maxBodyHeight = Math.max(1, screenHeight / 2 - 67);
        bodyHeight = Math.max(1, Math.min(maxBodyHeight, lines.size() * lineHeight));
        x = (screenWidth - width) / 2;
        y = (screenHeight - bodyHeight - 65) / 2;
        scroll = MathHelper.clamp(scroll, 0, maxScroll());
    }

    private void addNode(RequirementNode node, int depth, boolean inLogicalGroup) {
        boolean grouped = inLogicalGroup || node.logicalGroup();
        boolean bulletless = node.description();
        int indent = Math.min(depth * 14, Math.max(0, width - 80));
        String[] paragraphs = node.label().getString().split("\n", -1);
        boolean first = true;
        for (String paragraph : paragraphs) {
            Text text = paragraphs.length == 1 ? node.label() : Text.literal(paragraph).setStyle(node.label().getStyle());
            List<OrderedText> wrapped = renderer.wrapLines(text,
                    Math.max(1, width - 44 - indent));
            if (wrapped.isEmpty()) wrapped = List.of(Text.empty().asOrderedText());
            for (OrderedText line : wrapped) {
                lines.add(new Row(line, indent, first && !bulletless ? grouped && node.checked() : null,
                        bulletless, node.tooltipDescription() == null ? null : node.label(), node.tooltipDescription()));
                first = false;
            }
        }
        for (RequirementNode child : node.children()) addNode(child, depth + 1, grouped);
    }

    private int bodyTop() { return y + 30; }
    private int backTop() { return bodyTop() + bodyHeight + 10; }
    private int maxScroll() { return Math.max(0, lines.size() * lineHeight - bodyHeight); }
    private Scrollbar scrollbar() {
        return new Scrollbar(x + width - 12, bodyTop(), bodyTop() + bodyHeight,
                Scrollbar.pixelThumbHeight(bodyHeight, maxScroll(), 12, 0), scroll, maxScroll());
    }

    void render(DrawContext context, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        layout(screenWidth, screenHeight);
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 300);
        context.fill(0, 0, screenWidth, screenHeight, 0x88000000);
        context.fill(x - 1, y - 1, x + width + 1, backTop() + 26, 0xff909090);
        context.fill(x, y, x + width, backTop() + 25, 0xff000000);
        context.drawCenteredTextWithShadow(renderer, Text.literal("Locked"), x + width / 2, y + 10, 0xffff5555);
        context.enableScissor(x + 10, bodyTop(), x + width - 18, bodyTop() + bodyHeight);
        int first = Math.max(0, (int) scroll / lineHeight);
        int last = Math.min(lines.size(), first + bodyHeight / lineHeight + 2);
        Row hoveredAdvancement = null;
        for (int i = first; i < last; i++) {
            Row row = lines.get(i);
            int rowY = bodyTop() + i * lineHeight - (int) scroll;
            if (row.ticked != null) context.drawText(renderer, row.ticked ? "✔" : "•",
                    x + 12 + row.indent, rowY, row.ticked ? 0xffffffff : 0xffc0c0c0, true);
            int textX = x + (row.bulletless ? 10 : 23) + row.indent;
            context.drawText(renderer, row.text, textX, rowY, 0xffffff, true);
            if (row.tooltipDescription != null && mouseX >= textX && mouseX < textX + renderer.getWidth(row.text)
                    && mouseY >= Math.max(rowY, bodyTop())
                    && mouseY < Math.min(rowY + renderer.fontHeight, bodyTop() + bodyHeight)) hoveredAdvancement = row;
        }
        context.disableScissor();
        // The thumb's transparent grip dots reveal the track color.
        scrollbar().render(context, BUTTON_BORDER, BUTTON_BACKGROUND);
        int buttonX = x + (width - 70) / 2;
        boolean hovered = mouseX >= buttonX && mouseX < buttonX + 70 && mouseY >= backTop() && mouseY < backTop() + 20;
        context.fill(buttonX, backTop(), buttonX + 70, backTop() + 20, BUTTON_BORDER);
        context.fill(buttonX + 1, backTop() + 1, buttonX + 69, backTop() + 19, hovered ? 0xff424242 : BUTTON_BACKGROUND);
        context.drawCenteredTextWithShadow(renderer, Text.literal("Back"), x + width / 2, backTop() + 6, 0xffffff);
        context.getMatrices().pop();
        if (hoveredAdvancement != null) {
            int tooltipWidth = Math.max(20, Math.min(240, screenWidth - 40));
            List<OrderedText> tooltip = new ArrayList<>(renderer.wrapLines(hoveredAdvancement.tooltipTitle, tooltipWidth));
            tooltip.addAll(renderer.wrapLines(hoveredAdvancement.tooltipDescription, tooltipWidth));
            context.drawTooltip(renderer, tooltip, HoveredTooltipPositioner.INSTANCE, mouseX, mouseY);
        }
    }

    /** Returns true when Back or the area outside the panel was clicked. */
    boolean click(double mouseX, double mouseY, int button, int width, int height) {
        layout(width, height);
        if (mouseX < x - 1 || mouseX >= x + this.width + 1
                || mouseY < y - 1 || mouseY >= backTop() + 26) return true;
        if (button != 0) return false;
        Scrollbar bar = scrollbar();
        if (bar.contains(mouseX, mouseY)) {
            grabOffset = bar.grabOffset(mouseY, 0);
            scroll = bar.offsetForMouse(mouseY, grabOffset);
            dragging = true;
            return false;
        }
        return mouseX >= x + (this.width - 70) / 2 && mouseX < x + (this.width + 70) / 2
                && mouseY >= backTop() && mouseY < backTop() + 20;
    }

    void scroll(double amount) { scroll = Scrollbar.scrollPixels(scroll, amount, lineHeight * 3, maxScroll()); }
    void drag(double mouseY) { if (dragging) scroll = scrollbar().offsetForMouse(mouseY, grabOffset); }
    void release() { dragging = false; }
    void keyPressed(int key) {
        switch (key) {
            case GLFW.GLFW_KEY_UP -> scroll(1);
            case GLFW.GLFW_KEY_DOWN -> scroll(-1);
            case GLFW.GLFW_KEY_PAGE_UP -> scroll = Math.max(0, scroll - bodyHeight);
            case GLFW.GLFW_KEY_PAGE_DOWN -> scroll = Math.min(maxScroll(), scroll + bodyHeight);
            case GLFW.GLFW_KEY_HOME -> scroll = 0;
            case GLFW.GLFW_KEY_END -> scroll = maxScroll();
        }
    }
}

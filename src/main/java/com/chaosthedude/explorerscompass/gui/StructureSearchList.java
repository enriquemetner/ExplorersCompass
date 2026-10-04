package com.chaosthedude.explorerscompass.gui;

import java.util.Objects;

import com.chaosthedude.explorerscompass.util.RenderUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class StructureSearchList extends AlwaysSelectedEntryListWidget<StructureSearchEntry> {
	private static final int SCROLLBAR_WIDTH = 6;
	private static final int SCROLLBAR_GRAB_PADDING = 2;

	private final ExplorersCompassScreen parentScreen;
	private final PlayerEntity player;

	public StructureSearchList(ExplorersCompassScreen parentScreen, MinecraftClient client, PlayerEntity player, Identifier structureIdToSelect, int x, int y, int width, int height, int slotHeight) {
		super(client, width, height, y, y + height, slotHeight);
		this.parentScreen = parentScreen;
		this.player = player;
		// Cancel the list widget's default 4px top padding so rows meet the scrollbar track.
		headerHeight = -4;
        setLeftPos(x);
		refreshList(structureIdToSelect);
	}

	@Override
	protected int getScrollbarPositionX() {
		return left + width;
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return super.isMouseOver(mouseX, mouseY)
				|| getMaxScroll() > 0 && mouseX >= getScrollbarPositionX() && mouseX < getScrollbarPositionX() + SCROLLBAR_WIDTH
						&& mouseY >= top && mouseY <= bottom;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		setScrollAmount(getScrollAmount() - amount * itemHeight);
		return true;
	}

	private int getScrollbarThumbHeight() {
		int viewportHeight = bottom - top;
		return MathHelper.clamp((int) ((float) (viewportHeight * viewportHeight) / getMaxPosition()), 32, viewportHeight - 8);
	}

	private int getScrollbarThumbTop() {
		int travel = bottom - top - getScrollbarThumbHeight();
		return top + (int) (getScrollAmount() * travel / getMaxScroll());
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		boolean handled = super.mouseClicked(mouseX, mouseY, button);
		if (handled && button == 0 && getMaxScroll() > 0
				&& mouseX >= getScrollbarPositionX() && mouseX < getScrollbarPositionX() + SCROLLBAR_WIDTH) {
			int thumbTop = getScrollbarThumbTop();
			int thumbHeight = getScrollbarThumbHeight();
			if (mouseY < thumbTop - SCROLLBAR_GRAB_PADDING || mouseY >= thumbTop + thumbHeight + SCROLLBAR_GRAB_PADDING) {
				int travel = bottom - top - thumbHeight;
				setScrollAmount((mouseY - top - thumbHeight / 2.0) * getMaxScroll() / travel);
			}
		}
		return handled;
	}

	@Override
	public int getRowWidth() {
		return width;
	}

	@Override
	protected boolean isSelectedEntry(int slotIndex) {
		return slotIndex >= 0 && slotIndex < children().size() ? children().get(slotIndex).equals(getSelectedOrNull()) : false;
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float partialTicks) {
        enableScissor(context);
        // Render backgrounds
        for (int i = 0; i < getEntryCount(); ++i) {
            if (getRowBottom(i) >= top && getRowTop(i) <= bottom) {
                StructureSearchEntry entry = getEntry(i);
                int fillColor = RenderUtils.getBackgroundColor(entry.isEnabled(), entry == getSelectedOrNull());
                context.fill(getRowLeft(), getRowTop(i), right, getRowBottom(i), fillColor);
            }
        }
        // Render entries
        for (int i = 0; i < getEntryCount(); ++i) {
            int entryTop = getRowTop(i);
            int entryBottom = getRowBottom(i);
            if (entryBottom >= top && entryTop <= bottom) {
                StructureSearchEntry entry = getEntry(i);
                boolean isHovering = isMouseOver(mouseX, mouseY) && Objects.equals(getEntryAtPosition(mouseX, mouseY), entry);
                entry.render(context, i, entryTop, getRowLeft(), getRowWidth(), itemHeight, mouseX, mouseY, isHovering, partialTicks);
            }
        }
        context.disableScissor();
        // Render scrollbar
        if (getMaxScroll() > 0) {
            int scrollbarLeft = getScrollbarPositionX();
            int scrollbarRight = scrollbarLeft + SCROLLBAR_WIDTH;
            int scrollbarHeight = getScrollbarThumbHeight();
            int scrollbarTop = getScrollbarThumbTop();
            context.fill(scrollbarLeft, top, scrollbarRight, bottom, RenderUtils.getBackgroundColor(false, false));
            context.fill(scrollbarLeft, scrollbarTop, scrollbarRight, scrollbarTop + scrollbarHeight, RenderUtils.getBackgroundColor(true, true));
        }
	}

    @Override
    public void setSelected(StructureSearchEntry entry) {
        if (entry == null || entry.isEnabled()) {
            super.setSelected(entry);
        }
    }

    public void refreshList(Identifier structureIdToSelect) {
		clearEntries();
		for (Identifier id : parentScreen.sortStructures()) {
			StructureSearchEntry entry = new StructureSearchEntry(this, id, player);
			addEntry(entry);
			if (id.equals(structureIdToSelect)) {
				setSelected(entry);
			}
		}
		setScrollAmount(0);
	}

	public void refreshList(boolean maintainSelection) {
		Identifier select = maintainSelection && hasSelection() ? getSelectedOrNull().getStructureID() : null;
		refreshList(select);
	}

	public void refreshList() {
		refreshList(null);
	}

	public boolean hasSelection() {
		return getSelectedOrNull() != null;
	}

	public ExplorersCompassScreen getParentScreen() {
		return parentScreen;
	}

}

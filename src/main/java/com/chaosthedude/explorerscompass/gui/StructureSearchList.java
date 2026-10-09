package com.chaosthedude.explorerscompass.gui;

import java.util.Objects;

import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
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
	private static final int SCROLLBAR_WIDTH = Scrollbar.WIDTH;

	private final ExplorersCompassScreen parentScreen;
	private final PlayerEntity player;

	public StructureSearchList(ExplorersCompassScreen parentScreen, MinecraftClient client, PlayerEntity player, Identifier structureIdToSelect, int x, int y, int width, int height, int slotHeight) {
		super(client, width, height, y, y + height, ExplorersCompassConfig.adjustRowsToFitView ? fittedRowHeight(height, slotHeight) : slotHeight);
		this.parentScreen = parentScreen;
		this.player = player;
		// Cancel the list widget's default 4px top padding so rows meet the scrollbar track.
		headerHeight = -4;
        setLeftPos(x);
		refreshList(structureIdToSelect);
	}

	private static int fittedRowHeight(int viewportHeight, int preferredRowHeight) {
		int visibleRows = Math.max(1, viewportHeight / preferredRowHeight);
		return Math.max(preferredRowHeight, viewportHeight / visibleRows);
	}

	private int fullRowsBottom() {
		if (!ExplorersCompassConfig.adjustRowsToFitView) {
			return bottom;
		}
		return Math.min(bottom, top + Math.max(1, (bottom - top) / itemHeight) * itemHeight);
	}

	@Override
	public int getMaxScroll() {
		if (!ExplorersCompassConfig.adjustRowsToFitView) {
			return super.getMaxScroll();
		}
		return Math.max(0, getEntryCount() * itemHeight - (fullRowsBottom() - top));
	}

	@Override
	protected int getScrollbarPositionX() {
		return left + width;
	}

	@Override
	public boolean isMouseOver(double mouseX, double mouseY) {
		return super.isMouseOver(mouseX, mouseY) && (mouseY < fullRowsBottom() || mouseX >= getScrollbarPositionX())
				|| getMaxScroll() > 0 && mouseX >= getScrollbarPositionX() && mouseX < getScrollbarPositionX() + SCROLLBAR_WIDTH
						&& mouseY >= top && mouseY <= bottom;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		setScrollAmount(Scrollbar.scrollPixels(getScrollAmount(), amount, itemHeight, getMaxScroll()));
		return true;
	}

	private Scrollbar scrollbar() {
		int viewportHeight = bottom - top;
		int thumbHeight = Scrollbar.pixelThumbHeight(viewportHeight, getMaxPosition() - viewportHeight, 32, 8);
		return new Scrollbar(getScrollbarPositionX(), top, bottom, thumbHeight, getScrollAmount(), getMaxScroll());
	}

	public boolean isScrollbarAt(double mouseX, double mouseY) {
		return scrollbar().contains(mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (mouseX < getScrollbarPositionX() && mouseY >= fullRowsBottom() && mouseY < bottom) {
			return false;
		}
		boolean handled = super.mouseClicked(mouseX, mouseY, button);
		Scrollbar scrollbar = scrollbar();
		if (handled && button == 0 && scrollbar.contains(mouseX, mouseY)
				&& scrollbar.outsideThumb(mouseY, Scrollbar.GRAB_PADDING)) {
			setScrollAmount(scrollbar.offsetForMouse(mouseY, scrollbar.grabOffset(mouseY, Scrollbar.GRAB_PADDING)));
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
		int rowsBottom = fullRowsBottom();
		context.enableScissor(left, top, right, rowsBottom);
        // Render backgrounds
        for (int i = 0; i < getEntryCount(); ++i) {
            if (getRowBottom(i) >= top && getRowTop(i) <= rowsBottom) {
                StructureSearchEntry entry = getEntry(i);
                int fillColor = RenderUtils.getBackgroundColor(entry.isEnabled(), entry == getSelectedOrNull());
                context.fill(getRowLeft(), getRowTop(i), right, getRowBottom(i), fillColor);
            }
        }
        // Render entries
        for (int i = 0; i < getEntryCount(); ++i) {
            int entryTop = getRowTop(i);
            int entryBottom = getRowBottom(i);
            if (entryBottom >= top && entryTop <= rowsBottom) {
                StructureSearchEntry entry = getEntry(i);
                boolean isHovering = isMouseOver(mouseX, mouseY) && Objects.equals(getEntryAtPosition(mouseX, mouseY), entry);
                entry.render(context, i, entryTop, getRowLeft(), getRowWidth(), itemHeight, mouseX, mouseY, isHovering, partialTicks);
            }
        }
        context.disableScissor();
		if (rowsBottom < bottom) {
			context.fill(getRowLeft(), rowsBottom, right, bottom, RenderUtils.getBackgroundColor(false, false));
		}
        // Render scrollbar
		scrollbar().render(context);
	}

    @Override
    public void setSelected(StructureSearchEntry entry) {
        if (entry == null || entry.isEnabled()) {
            super.setSelected(entry);
        }
    }

	public void refreshList(Identifier structureIdToSelect) {
		clearEntries();
		setSelected(null);
		for (Identifier id : parentScreen.sortStructures()) {
			StructureSearchEntry entry = new StructureSearchEntry(this, id);
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

	public void selectRelative(int direction) {
		if (getEntryCount() == 0) return;
		int selectedIndex = hasSelection() ? children().indexOf(getSelectedOrNull()) : -1;
		int nextIndex = selectedIndex < 0 ? 0 : MathHelper.clamp(selectedIndex + direction, 0, getEntryCount() - 1);
		setSelected(getEntry(nextIndex));
		if (getRowTop(nextIndex) < top) {
			setScrollAmount(getScrollAmount() - (top - getRowTop(nextIndex)));
		} else if (getRowBottom(nextIndex) > fullRowsBottom()) {
			setScrollAmount(getScrollAmount() + getRowBottom(nextIndex) - fullRowsBottom());
		}
	}

	public StructureSearchEntry soleSearchableEntry() {
		StructureSearchEntry only = null;
		for (StructureSearchEntry entry : children()) {
			if (parentScreen.isStructureHidden(entry.getStructureID())) continue;
			if (only != null) return null;
			only = entry;
		}
		return only;
	}

	public boolean isActionButtonClick(StructureSearchEntry entry, double mouseX, double mouseY) {
		int index = children().indexOf(entry);
		if (index < 0) return false;
		int buttonX = getRowLeft() + getRowWidth() - parentScreen.rowActionButtonWidth() - 8;
		int buttonY = getRowTop(index) + (itemHeight - 20) / 2;
		return mouseX >= buttonX && mouseX < buttonX + parentScreen.rowActionButtonWidth()
				&& mouseY >= Math.max(buttonY, top) && mouseY < Math.min(buttonY + 20, fullRowsBottom());
	}

	public boolean isHideButtonHovered(double mouseX, double mouseY) {
		if (!isMouseOver(mouseX, mouseY)) return false;
		StructureSearchEntry entry = getEntryAtPosition(mouseX, mouseY);
		return entry != null && !parentScreen.isStructureHidden(entry.getStructureID())
				&& isActionButtonClick(entry, mouseX, mouseY);
	}

	public ExplorersCompassScreen getParentScreen() {
		return parentScreen;
	}

}

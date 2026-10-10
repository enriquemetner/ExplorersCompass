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
	static final int SCROLLBAR_WIDTH = Scrollbar.WIDTH;
	static final int ACTION_BUTTON_HEIGHT = 16;
	static final int ACTION_BUTTON_GAP = 2;

	private final ExplorersCompassScreen parentScreen;
	private final PlayerEntity player;
	private boolean draggingScrollbar;
	private double scrollbarGrabOffset;

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
		return new Scrollbar(getScrollbarPositionX(), SCROLLBAR_WIDTH, top, bottom, thumbHeight, getScrollAmount(), getMaxScroll());
	}

	public boolean isScrollbarAt(double mouseX, double mouseY) {
		return scrollbar().contains(mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (mouseX < getScrollbarPositionX() && mouseY >= fullRowsBottom() && mouseY < bottom) {
			return false;
		}
		Scrollbar scrollbar = scrollbar();
		if (button == 0 && scrollbar.contains(mouseX, mouseY)) {
			draggingScrollbar = true;
			scrollbarGrabOffset = scrollbar.grabOffset(mouseY, Scrollbar.GRAB_PADDING);
			if (scrollbar.outsideThumb(mouseY, Scrollbar.GRAB_PADDING)) {
				setScrollAmount(scrollbar.offsetForMouse(mouseY, scrollbarGrabOffset));
			}
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (draggingScrollbar && button == 0) {
			setScrollAmount(scrollbar().offsetForMouse(mouseY, scrollbarGrabOffset));
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (draggingScrollbar && button == 0) {
			draggingScrollbar = false;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
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
				int opacity = parentScreen.isStructureLocked(entry.getStructureID())
						? RenderUtils.LOCKED_ENTRY_BACKGROUND_OPACITY_PERCENT
						: RenderUtils.NORMAL_BACKGROUND_OPACITY_PERCENT;
                int fillColor = RenderUtils.blackWithOpacity(opacity);
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
				if (entry == getSelectedOrNull()) {
					context.fill(getRowLeft(), entryTop, getRowLeft() + 1, entryBottom, 0xffffffff);
				}
            }
        }
        context.disableScissor();
		if (rowsBottom < bottom) {
			context.fill(getRowLeft(), rowsBottom, right, bottom,
					RenderUtils.blackWithOpacity(RenderUtils.NORMAL_BACKGROUND_OPACITY_PERCENT));
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

	public void focusStructure(Identifier id) {
		if (id == null) return;
		for (int index = 0; index < getEntryCount(); index++) {
			StructureSearchEntry entry = getEntry(index);
			if (!entry.getStructureID().equals(id)) continue;
			setSelected(entry);
			setScrollAmount(getScrollAmount() + getRowTop(index) - top);
			return;
		}
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
		return isRowButtonClick(entry, mouseX, mouseY, false);
	}

	public boolean isFilterButtonClick(StructureSearchEntry entry, double mouseX, double mouseY) {
		return isRowButtonClick(entry, mouseX, mouseY, true);
	}

	private boolean isRowButtonClick(StructureSearchEntry entry, double mouseX, double mouseY, boolean filter) {
		int index = children().indexOf(entry);
		if (index < 0) return false;
		int buttonX = getRowLeft() + getRowWidth() - parentScreen.rowActionButtonWidth() - 7;
		int buttonY = getRowTop(index) + (itemHeight - (ACTION_BUTTON_HEIGHT * 2 + ACTION_BUTTON_GAP)) / 2
				+ (filter ? 0 : ACTION_BUTTON_HEIGHT + ACTION_BUTTON_GAP);
		return mouseX >= buttonX && mouseX < buttonX + parentScreen.rowActionButtonWidth()
				&& mouseY >= Math.max(buttonY, top) && mouseY < Math.min(buttonY + ACTION_BUTTON_HEIGHT, fullRowsBottom());
	}

	public boolean containsStructure(Identifier id) {
		for (StructureSearchEntry entry : children()) {
			if (entry.getStructureID().equals(id)) return true;
		}
		return false;
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

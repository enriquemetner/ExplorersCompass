package com.chaosthedude.explorerscompass.gui;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import com.chaosthedude.explorerscompass.items.ExplorersCompassItem;
import com.chaosthedude.explorerscompass.network.SearchPacket;
import com.chaosthedude.explorerscompass.network.SearchListedPacket;
import com.chaosthedude.explorerscompass.network.CancelSearchPacket;
import com.chaosthedude.explorerscompass.network.ContinueSearchPacket;
import com.chaosthedude.explorerscompass.network.SearchResultPacket;
import com.chaosthedude.explorerscompass.network.ResetCompassPacket;
import com.chaosthedude.explorerscompass.network.TeleportPacket;
import com.chaosthedude.explorerscompass.sorting.SortCriterion;
import com.chaosthedude.explorerscompass.util.CompassState;
import com.chaosthedude.explorerscompass.util.ItemUtils;
import com.chaosthedude.explorerscompass.util.OverlaySide;
import com.chaosthedude.explorerscompass.util.RenderUtils;
import com.chaosthedude.explorerscompass.util.StructureUtils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
public class ExplorersCompassScreen extends Screen {
	private static final int SORT_ROW_HEIGHT = 20;
	private static final int SORT_LABEL_GAP = 11;
	private static final int SORT_HIGHLIGHT_PADDING = 4;
	private static final int SEARCH_FIELD_WIDTH = 210;
	private static final int SEARCH_TELEPORT_GAP = 10;
	private static final int PREFERRED_FILTER_ROW_HEIGHT = 20;
	private static final int FILTER_HEADER_HEIGHT = 20;
	private static final int FILTER_HEADER_GAP = 1;
	private static final int FILTER_COLUMN_GAP = 1;
	private static final int FILTER_SECTION_GAP = 1;
	private static final int FILTER_CHECKBOX_SIZE = 9;
	private static final int FILTER_TITLE_X = 16;
	private static final int FILTER_TITLE_GAP = 16;
	private static final int BUTTON_CROSS_SIZE = 7;
	private static final int HEADER_BUTTON_TEXT_PADDING = 32;
	private static final int SCROLLBAR_WIDTH = Scrollbar.WIDTH;
	private static final int SETTINGS_ROW_HEIGHT = 30;
	private static final int SETTINGS_CONTROL_WIDTH = 90;
	private static String sessionSearchText = "";
	private static final int HIDE_BUTTON_WIDTH = 100;
	private static final int KEEP_SEARCHING_BUTTON_WIDTH = 100;
	private static final int NOT_FOUND_BUTTON_GAP = 5;
	private static final String[] SETTINGS_LABELS = {
		"HUD Side",
		"HUD Line Offset",
		"Clear Search Bar on Close",
		"Clear Filters on Close",
		"Checked Filters on Top",
		"Adjust Rows to Fit View",
		"Close UI when located",
		"Show While Chat Is Open",
		"Show Technical Names",
		"Pause Game When Open (Singleplayer)",
		"Hidden Structures",
	};
	private static final String[] SETTINGS_TOOLTIPS = {
		"Choose whether compass information appears on the left or right side of the HUD.",
		"Move compass information vertically. Higher values move it down; lower values move it up.",
		"Clears the search bar when you close the compass screen.",
		"Clear selected structure filters when you close the compass screen.",
		"Show checked options above unchecked options in each filter column. The selection itself does not change.",
		"Adjusts row spacing, showing less content but only fully visible list entries. Smooth scrolling may still show partial entries.",
		"Close the compass screen when a structure is located.",
		"Keep compass information visible while the chat screen is open.",
		"Show structure IDs beside their names and include them in search results.",
		"Pauses singleplayer while the compass screen is open, except while locating a structure.",
		"View and restore structures hidden on this client.",
	};

	private World world;
	private PlayerEntity player;
	private List<Identifier> allowedStructureIDs;
	private List<Identifier> structureIDsMatchingSearch;
	private Identifier foundStructureId;
	private ItemStack stack;
	private ExplorersCompassItem explorersCompass;
	private ButtonWidget searchButton;
	private ButtonWidget searchListedButton;
	private ButtonWidget sortOrderButton;
	private ButtonWidget filtersButton;
	private ButtonWidget settingsButton;
	private ButtonWidget chatOverlayButton;
	private ButtonWidget showTechnicalNamesButton;
	private ButtonWidget overlaySideButton;
	private ButtonWidget clearOnCloseButton;
	private ButtonWidget clearSearchBarOnCloseButton;
	private ButtonWidget pauseGameWhenOpenButton;
	private ButtonWidget closeOnFoundButton;
	private ButtonWidget checkedFiltersFirstButton;
	private ButtonWidget adjustRowsToFitViewButton;
	private ButtonWidget offsetDecreaseButton;
	private ButtonWidget offsetIncreaseButton;
	private ButtonWidget clearFiltersButton;
	private ButtonWidget clearFiltersTopButton;
	private ButtonWidget excludeVisitedButton;
	private ButtonWidget backButton;
	private ButtonWidget teleportButton;
	private ButtonWidget cancelButton;
	private TransparentTextField searchTextField;
	private TransparentTextField filterSearchTextField;
	private TransparentTextField hiddenSearchTextField;
	private StructureSearchList selectionList;
	private final Set<Identifier> pendingHiddenStructures = new HashSet<>();
	private String hiddenSearchText = "";
	private boolean filtersOpen;
	private boolean settingsOpen;
    private boolean hiddenStructuresOpen;
    private int hiddenScroll;
    private ButtonWidget hiddenStructuresButton;
    private enum Dialog { NONE, LOCKED, WRONG_DIMENSION, INSUFFICIENT_XP, SEARCHING, FOUND, NOT_FOUND, REJECTED }
    private Dialog dialog = Dialog.NONE;
    private LockedRequirementsView lockedRequirementsView;
    private int insufficientXpCost;
    private long dialogStarted;
    private long notFoundCompassUpdated;
    private double notFoundCompassAngle;
    private double notFoundCompassSpeed;
    private int activeRequestId;
    private int failedRadius;
    private boolean failedCanContinue;
    private Identifier searchedId;
    private boolean searchedGroup;
    private boolean searchedListed;
    private List<Identifier> listedSearchIds = List.of();
    private int listedSelectedCount;
    private int listedSkippedCount;
    private boolean successfulClose;
    private int foundX;
    private int foundZ;
    private boolean showFoundCoordinates;
	private double filterScroll;
	private int settingsFirstRow;
	private enum ScrollPanel { FILTERS, SETTINGS, HIDDEN }
	private ScrollPanel draggedScrollbar;
	private boolean draggingStructureScrollbar;
	private double scrollbarGrabOffset;
	private final List<List<FilterOption>> filterOptions = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
	private boolean showSourceColumn = true;
	private final List<SortCriterion> sortOrder = new ArrayList<SortCriterion>();
	private boolean sortMenuOpen;
	private int draggedSortRow = -1;
	private boolean sortOrderChangedDuringDrag;
	private Identifier entryFiltersId;
	private int entryFilterFirstRow;
	private static final int ENTRY_FILTER_ROW_HEIGHT = 20;
	private record EntryFilterRow(String category, String value, int column, FilterOption option) {}

	private static class FilterOption {
		private final String label;
		private final String source;
		private final Identifier id;
		private final boolean current;

		private FilterOption(String label, String source, Identifier id, boolean current) {
			this.label = label;
			this.source = source;
			this.id = id;
			this.current = current;
		}
	}

	public ExplorersCompassScreen(World world, PlayerEntity player, ItemStack stack, ExplorersCompassItem explorersCompass, List<Identifier> allowedStructureIDs) {
		super(Text.literal("Select Structure"));
		this.world = world;
		this.player = player;
		this.stack = stack;
		this.explorersCompass = explorersCompass;
		FilterSelections.openedInDimension(world.getRegistryKey().getValue());

		this.allowedStructureIDs = new ArrayList<Identifier>(allowedStructureIDs);
		structureIDsMatchingSearch = new ArrayList<Identifier>(allowedStructureIDs);
		for (String name : ExplorersCompassConfig.sortOrder) {
			sortOrder.add(SortCriterion.valueOf(name));
		}

		if (explorersCompass.getState(stack) == CompassState.FOUND) {
			foundStructureId = explorersCompass.getStructureID(stack);
		}
	}

	@Override
	public boolean shouldPause() {
		return ExplorersCompassConfig.pauseGameWhenOpen && dialog != Dialog.SEARCHING;
	}

	@Override
	public void removed() {
        if ((dialog == Dialog.SEARCHING || (dialog == Dialog.NOT_FOUND && failedCanContinue)) && !successfulClose && client.getNetworkHandler() != null) {
            ClientPlayNetworking.send(CancelSearchPacket.ID, new CancelSearchPacket(activeRequestId));
        }
		if (ExplorersCompassConfig.clearFiltersOnClose) {
			FilterSelections.clear();
		}
		sessionSearchText = ExplorersCompassConfig.clearSearchBarOnClose ? "" : searchTextField.getText();
		pendingHiddenStructures.clear();
		hiddenSearchText = "";
		super.removed();
	}

	@Override
	public boolean mouseScrolled(double scroll1, double scroll2, double scroll3) {
		if (entryFiltersId != null) {
			List<EntryFilterRow> rows = entryFilterRows();
			entryFilterFirstRow = MathHelper.clamp(entryFilterFirstRow - (int) Math.signum(scroll3), 0,
					Math.max(0, rows.size() - entryFilterVisibleRows(rows.size())));
			return true;
		}
        if (dialog == Dialog.LOCKED) { lockedRequirementsView.scroll(scroll3); return true; }
        if (dialog != Dialog.NONE) return true;
        if (hiddenStructuresOpen) {
            hiddenScroll = Scrollbar.scrollRows(hiddenScroll, scroll3, 2, hiddenMaxScroll());
            return true;
        }
		if (settingsOpen) {
			settingsFirstRow = Scrollbar.scrollRows(settingsFirstRow, scroll3, 1, settingsMaxFirstRow());
			updateViewButtons();
			return true;
		}
		if (filtersOpen) {
			if (scroll1 >= filterLeft() && scroll1 < filterScrollbarRight() && scroll2 >= filterTop() && scroll2 < filterBottom()) {
				filterScroll = Scrollbar.scrollPixels(filterScroll, scroll3, filterRowHeight(), filterMaxScroll());
				return true;
			}
			return super.mouseScrolled(scroll1, scroll2, scroll3);
		}
		if (sortMenuOpen) return true;
		return selectionList.mouseScrolled(scroll1, scroll2, scroll3);
	}

	@Override
	protected void init() {
		if (searchTextField != null) sessionSearchText = searchTextField.getText();
		clearChildren();
		setupButtons();
		selectionList = new StructureSearchList(this, client, player, null, 130, 40, width - 130 - SCROLLBAR_WIDTH, height - 50, 50);
		if (isStructureView()) {
			addDrawableChild(selectionList);
		}
		entryFiltersId = null;
		setupTextFields();
		rebuildFilterOptions();
		processSearchTerm();
		focusActiveSearchField();
	}

	@Override
	public void tick() {
        if ((dialog == Dialog.WRONG_DIMENSION || dialog == Dialog.INSUFFICIENT_XP) && System.currentTimeMillis() - dialogStarted >= 3000) {
			dialog = Dialog.NONE;
			focusActiveSearchField();
		}
		searchTextField.tick();
		filterSearchTextField.tick();
		hiddenSearchTextField.tick();

		boolean hasSelection = selectionList.hasSelection();
		boolean selectionMatchesFound = hasSelection && foundStructureId != null && selectionList.getSelectedOrNull().getStructureID().equals(foundStructureId);

		searchButton.active = isStructureView() && hasSelection && !isStructureHidden(selectionList.getSelectedOrNull().getStructureID());
		clearFiltersButton.active = !FilterSelections.isEmpty();
		clearFiltersTopButton.active = !FilterSelections.isEmpty();
		searchListedButton.active = isStructureView() && structureIDsMatchingSearch.stream().anyMatch(id -> !isStructureHidden(id));
		teleportButton.active = isStructureView() && selectionMatchesFound;
		sortOrderButton.active = isStructureView();

		// Check if the allowed structure list has synced
		if (ExplorersCompass.synced) {
			entryFiltersId = null;
			remove(selectionList);
			allowedStructureIDs = new ArrayList<Identifier>(ExplorersCompass.allowedStructureIDs);
			structureIDsMatchingSearch = new ArrayList<Identifier>(allowedStructureIDs);
			selectionList = new StructureSearchList(this, client, player, null, 130, 40, width - 130 - SCROLLBAR_WIDTH, height - 50, 50);
			if (isStructureView()) {
				addDrawableChild(selectionList);
			}

			teleportButton.visible = isStructureView() && ExplorersCompass.canTeleport;
			positionFilterButtons();
			positionTopButtons();
			positionFilterSearchField();
			updateViewButtons();
			rebuildFilterOptions();
			processSearchTerm();
			focusActiveSearchField();

			ExplorersCompass.synced = false;
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float partialTicks) {
        renderBackground(context);
		if (filtersOpen) {
			renderFilterPanel(context, mouseX, mouseY);
        } else if (hiddenStructuresOpen) {
            renderHiddenStructures(context, mouseX, mouseY);
		} else if (settingsOpen) {
			renderSettingsPanel(context);
		}
		super.render(context, mouseX, mouseY, partialTicks);
		if (excludeVisitedButton.visible) {
			drawFilterCheckbox(context, excludeVisitedButton.getX() + excludeVisitedButton.getWidth() - FILTER_CHECKBOX_SIZE - 7,
				excludeVisitedButton.getY() + (excludeVisitedButton.getHeight() - FILTER_CHECKBOX_SIZE) / 2, FilterSelections.excludeVisited());
			if (textRenderer.getWidth("Exclude Visited") > excludeVisitedButton.getWidth() - FILTER_CHECKBOX_SIZE - 18
					&& excludeVisitedButton.isHovered()) {
				context.drawTooltip(textRenderer, Text.literal("Exclude Visited"), mouseX, mouseY);
			}
		}
		if (searchListedButton.visible) {
			int iconX = searchListedButton.getX() + searchListedButton.getWidth() - 12;
			int iconY = searchListedButton.getY() + searchListedButton.getHeight() / 2;
			drawQuestionIcon(context, iconX, iconY);
			if (insideQuestionIcon(mouseX, mouseY, iconX, iconY)) drawQuestionTooltip(context, "Searches for any structure from the current list after applied filters and search bar results.", mouseX, mouseY);
		}
		renderCrossIcon(context, cancelButton);
		if (clearFiltersButton.visible) {
			renderCrossIcon(context, clearFiltersButton);
		}
		if (filtersOpen) {
			renderFilterTooltip(context, mouseX, mouseY);
		}
		if (filtersOpen) {
			context.drawTextWithShadow(textRenderer, Text.literal("Filters"), FILTER_TITLE_X, wrapsFilterSearch() ? 40 : 15, 0xffffff);
        } else if (hiddenStructuresOpen) {
            String breadcrumb = Text.literal("Settings > Hidden Structures").getString();
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(breadcrumb,
                    Math.max(1, hiddenSearchTextField.getX() - 20)), 10, 15, 0xffffff);
		} else if (settingsOpen) {
			context.drawTextWithShadow(textRenderer, Text.literal("Settings"), FILTER_TITLE_X, 15, 0xffffff);
		} else {
			context.drawCenteredTextWithShadow(textRenderer, title, 65, 15, 0xffffff);
		}
		if (settingsOpen) {
			renderSettingsTooltip(context, mouseX, mouseY);
		}
		if (isStructureView() && dialog == Dialog.NONE && entryFiltersId == null && !sortMenuOpen && selectionList.isHideButtonHovered(mouseX, mouseY)) {
			drawQuestionTooltip(context, "Removes the structure from the list on this client, across all worlds and servers. You can undo this in Settings.", mouseX, mouseY);
		}
		if (sortMenuOpen) renderSortMenu(context, mouseX, mouseY);
		if (entryFiltersId != null) renderEntryFilters(context, mouseX, mouseY);
        if (dialog != Dialog.NONE) renderSearchDialog(context, mouseX, mouseY);
	}

	private int sortMenuWidth() {
		return Math.max(40, Math.min(LockedRequirementsView.WINDOW_WIDTH, width - 24));
	}

	private int sortMenuX() {
		return (width - sortMenuWidth()) / 2;
	}

	private int sortMenuY() {
		return (height - sortMenuHeight()) / 2;
	}

	private int sortMenuHeight() { return 65 + sortOrder.size() * SORT_ROW_HEIGHT; }
	private int sortRowsY() { return sortMenuY() + 30; }
	private int sortBackY() { return sortRowsY() + sortOrder.size() * SORT_ROW_HEIGHT + 10; }
	private int sortLabelWidth() {
		int width = 0;
		for (SortCriterion criterion : sortOrder) width = Math.max(width, textRenderer.getWidth(criterion.getLabel()));
		return width;
	}
	private int sortNumberRightX() { return sortMenuX() + sortMenuWidth() / 2 - sortLabelWidth() / 2 - SORT_LABEL_GAP; }
	private int sortArrowsX() { return sortMenuX() + sortMenuWidth() / 2 + (sortLabelWidth() + 1) / 2 + SORT_LABEL_GAP; }
	private int sortContentLeft() {
		return sortNumberRightX() - textRenderer.getWidth(sortOrder.size() + ".") - SORT_HIGHLIGHT_PADDING;
	}
	private int sortContentRight() { return sortArrowsX() + 5 + SORT_HIGHLIGHT_PADDING; }

	private void closeSortMenu() {
		commitSortOrder();
		sortMenuOpen = false;
		draggedSortRow = -1;
	}

	private void commitSortOrder() {
		if (sortOrderChangedDuringDrag) {
			ExplorersCompassConfig.sortOrder = sortOrder.stream().map(Enum::name).collect(Collectors.toList());
			ExplorersCompassConfig.saveClient();
			selectionList.refreshList(true);
			sortOrderChangedDuringDrag = false;
		}
	}

	private int sortRowAt(double mouseX, double mouseY) {
		int y = sortRowsY();
		if (mouseX < sortContentLeft() || mouseX >= sortContentRight() || mouseY < y || mouseY >= y + sortOrder.size() * SORT_ROW_HEIGHT) {
			return -1;
		}
		return (int) (mouseY - y) / SORT_ROW_HEIGHT;
	}

	private void renderSortMenu(DrawContext context, int mouseX, int mouseY) {
		int x = sortMenuX();
		int y = sortMenuY();
		int menuWidth = sortMenuWidth();
		context.getMatrices().push();
		context.getMatrices().translate(0, 0, 300);
		context.fill(0, 0, width, height, 0x88000000);
		context.fill(x - 1, y - 1, x + menuWidth + 1, y + sortMenuHeight() + 1, 0xff909090);
		context.fill(x, y, x + menuWidth, y + sortMenuHeight(), 0xff000000);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal("Sort Priority"), x + menuWidth / 2, y + 10, 0xffffff);
		int hoveredRow = sortRowAt(mouseX, mouseY);
		int centerX = x + menuWidth / 2;
		int numberRightX = sortNumberRightX();
		int arrowsX = sortArrowsX();
		for (int i = 0; i < sortOrder.size(); i++) {
			int rowY = sortRowsY() + i * SORT_ROW_HEIGHT;
			if (i == draggedSortRow || i == hoveredRow)
				context.fill(sortContentLeft(), rowY, sortContentRight(), rowY + SORT_ROW_HEIGHT, 0xff242424);
			String number = (i + 1) + ".";
			context.drawTextWithShadow(textRenderer, number, numberRightX - textRenderer.getWidth(number), rowY + 6, 0xffaaaaaa);
			context.drawCenteredTextWithShadow(textRenderer, sortOrder.get(i).getLabel(), centerX, rowY + 6, 0xffffff);
			renderSortArrows(context, arrowsX, rowY + 6);
		}
		int backX = x + (menuWidth - 70) / 2;
		int backY = sortBackY();
		boolean backHovered = mouseX >= backX && mouseX < backX + 70 && mouseY >= backY && mouseY < backY + 20;
		context.fill(backX, backY, backX + 70, backY + 20, 0xff909090);
		context.fill(backX + 1, backY + 1, backX + 69, backY + 19, backHovered ? 0xff424242 : 0xff242424);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal("Back"), x + menuWidth / 2, backY + 6, 0xffffff);
		context.getMatrices().pop();
	}

	private void renderSortArrows(DrawContext context, int x, int y) {
		int color = 0xffaaaaaa;
		context.fill(x + 2, y, x + 3, y + 1, color);
		context.fill(x + 1, y + 1, x + 4, y + 2, color);
		context.fill(x, y + 2, x + 5, y + 3, color);
		context.fill(x, y + 4, x + 5, y + 5, color);
		context.fill(x + 1, y + 5, x + 4, y + 6, color);
		context.fill(x + 2, y + 6, x + 3, y + 7, color);
	}

	private int filterLeft() {
		return SCROLLBAR_WIDTH;
	}

	private int filterRight() {
		return width - SCROLLBAR_WIDTH;
	}

	private int filterScrollbarRight() {
		return filterRight() + SCROLLBAR_WIDTH;
	}

	private int filterTop() {
		return filtersOpen && wrapsFilterSearch() ? 65 : 40;
	}

	private int filterHeaderTop() {
		return filterTop();
	}

	private int filterRowsTop() {
		return filterHeaderTop() + FILTER_HEADER_HEIGHT + FILTER_HEADER_GAP;
	}

	private int filterBottom() {
		return height - 10;
	}

	private int filterVisibleRows() {
		int availableHeight = Math.max(1, filterBottom() - filterRowsTop() - FILTER_SECTION_GAP);
		return Math.max(1, availableHeight / PREFERRED_FILTER_ROW_HEIGHT);
	}

	private int filterRowHeight() {
		if (!ExplorersCompassConfig.adjustRowsToFitView) {
			return PREFERRED_FILTER_ROW_HEIGHT;
		}
		int availableHeight = Math.max(1, filterBottom() - filterRowsTop() - FILTER_SECTION_GAP);
		return Math.max(PREFERRED_FILTER_ROW_HEIGHT, availableHeight / filterVisibleRows());
	}

	private int filterFullRowsBottom(int sectionStart) {
		if (!ExplorersCompassConfig.adjustRowsToFitView) {
			return filterBottom();
		}
		int rowsBottom = filterRowsTop() + filterVisibleRows() * filterRowHeight();
		if (sectionStart >= 0 && filterRowsTop() + sectionStart * filterRowHeight() - visibleFilterScroll() < rowsBottom) {
			rowsBottom += FILTER_SECTION_GAP;
		}
		return Math.min(filterBottom(), rowsBottom);
	}

	private int filterColumnLeft(int column) {
		int firstColumn = showSourceColumn ? 0 : 1;
		int columnCount = showSourceColumn ? 3 : 2;
		return filterLeft() + (filterRight() - filterLeft()) * (column - firstColumn) / columnCount;
	}

	private int filterColumnRight(int column) {
		int firstColumn = showSourceColumn ? 0 : 1;
		int columnCount = showSourceColumn ? 3 : 2;
		int edge = filterLeft() + (filterRight() - filterLeft()) * (column - firstColumn + 1) / columnCount;
		return column == 2 ? edge : edge - FILTER_COLUMN_GAP;
	}

	private int filterColumnAt(double mouseX, double mouseY) {
		if (mouseX < filterLeft() || mouseX >= filterRight() || mouseY < filterHeaderTop() || mouseY >= filterBottom()) {
			return -1;
		}
		for (int column = showSourceColumn ? 0 : 1; column < 3; column++) {
			if (mouseX >= filterColumnLeft(column) && mouseX < filterColumnRight(column)) {
				return column;
			}
		}
		return -1;
	}

	private List<FilterOption> visibleFilterOptions(int column) {
		String query = filterSearchTextField.getText().toLowerCase(Locale.ROOT);
		List<FilterOption> visible = query.isEmpty() ? filterOptions.get(column) : filterOptions.get(column).stream()
				.filter(option -> option.label.toLowerCase(Locale.ROOT).contains(query)).collect(Collectors.toList());
		if (!ExplorersCompassConfig.checkedFiltersFirst) {
			return visible;
		}
		List<FilterOption> ordered = new ArrayList<>(visible.size());
		for (FilterOption option : visible) {
			if (filterChecked(column, option)) {
				ordered.add(option);
			}
		}
		for (FilterOption option : visible) {
			if (!filterChecked(column, option)) {
				ordered.add(option);
			}
		}
		return ordered;
	}

	private int filterSectionStart(int column, List<FilterOption> options) {
		if (!ExplorersCompassConfig.checkedFiltersFirst) {
			return -1;
		}
		int checkedCount = 0;
		for (FilterOption option : options) {
			if (filterChecked(column, option)) {
				checkedCount++;
			}
		}
		return checkedCount > 0 && checkedCount < options.size() ? checkedCount : -1;
	}

	private int filterRowY(int index, int sectionStart) {
		return filterRowsTop() + index * filterRowHeight() - visibleFilterScroll()
				+ (sectionStart >= 0 && index >= sectionStart ? FILTER_SECTION_GAP : 0);
	}

	private int visibleFilterScroll() {
		return (int) Math.floor(filterScroll);
	}

	private int filterOptionIndexAt(double mouseY, List<FilterOption> options, int sectionStart) {
		if (mouseY >= filterFullRowsBottom(sectionStart)) {
			return -1;
		}
		double offset = mouseY - filterRowsTop() + visibleFilterScroll();
		if (sectionStart >= 0) {
			int gapStart = sectionStart * filterRowHeight();
			if (offset >= gapStart && offset < gapStart + FILTER_SECTION_GAP) {
				return -1;
			}
			if (offset >= gapStart + FILTER_SECTION_GAP) {
				offset -= FILTER_SECTION_GAP;
			}
		}
		int index = (int) Math.floor(offset / filterRowHeight());
		return index >= 0 && index < options.size() ? index : -1;
	}

	private int filterMaxScroll() {
		int maxScroll = 0;
		for (int column = showSourceColumn ? 0 : 1; column < 3; column++) {
			List<FilterOption> options = visibleFilterOptions(column);
			if (ExplorersCompassConfig.adjustRowsToFitView) {
				maxScroll = Math.max(maxScroll, (options.size() - filterVisibleRows()) * filterRowHeight());
			} else {
				int columnHeight = options.size() * filterRowHeight()
						+ (filterSectionStart(column, options) >= 0 ? FILTER_SECTION_GAP : 0);
				maxScroll = Math.max(maxScroll, columnHeight - (filterBottom() - filterRowsTop()));
			}
		}
		return Math.max(0, maxScroll);
	}

	private Scrollbar scrollbarFor(ScrollPanel panel) {
		int trackHeight = filterBottom() - filterTop();
		return switch (panel) {
			case FILTERS -> new Scrollbar(filterRight(), filterTop(), filterBottom(),
					Scrollbar.pixelThumbHeight(trackHeight, filterMaxScroll(), 32, 8), filterScroll, filterMaxScroll());
			case SETTINGS -> new Scrollbar(filterRight(), filterTop(), filterBottom(),
					Scrollbar.rowThumbHeight(trackHeight, settingsVisibleRows(), SETTINGS_LABELS.length), settingsFirstRow, settingsMaxFirstRow());
			case HIDDEN -> new Scrollbar(filterRight(), filterTop(), filterBottom(),
					Scrollbar.rowThumbHeight(trackHeight, hiddenVisibleRows(), filteredHiddenStructures().size()), hiddenScroll, hiddenMaxScroll());
		};
	}

	private void rebuildFilterOptions() {
		for (List<FilterOption> options : filterOptions) {
			options.clear();
		}
		Map<String, FilterOption> sources = new HashMap<>();
		Set<Identifier> dimensions = new TreeSet<>();
		Set<Identifier> groups = new TreeSet<>();
		for (Identifier structure : allowedStructureIDs) {
			String namespace = structure.getNamespace();
			sources.putIfAbsent(namespace, new FilterOption(StructureUtils.getStructureSource(structure), namespace, null, false));
			dimensions.addAll(ExplorersCompass.allowedStructureIDsToDimensionIDs.get(structure));
			Identifier group = ExplorersCompass.structureIDsToGroupIDs.get(structure);
			if (group != null) {
				groups.add(group);
			}
		}
		showSourceColumn = sources.size() != 1 || !sources.containsKey("minecraft");
		if (!showSourceColumn) {
			FilterSelections.sources.clear();
		}
		dimensions.add(world.getRegistryKey().getValue());
		filterOptions.get(0).addAll(sources.values());
		for (Identifier dimension : dimensions) {
			filterOptions.get(1).add(new FilterOption(StructureUtils.getDimensionName(dimension), null, dimension, false));
		}
		for (Identifier group : groups) {
			filterOptions.get(2).add(new FilterOption(StructureUtils.getStructureName(group), null, group, false));
		}
		Collator collator = englishCollator();
		Comparator<FilterOption> alphabetical = Comparator.comparing((FilterOption option) -> option.label, collator)
				.thenComparing(option -> option.source != null ? option.source : option.id.toString());
		for (List<FilterOption> options : filterOptions) {
			options.sort(alphabetical);
		}
		filterOptions.get(0).sort(Comparator.comparing((FilterOption option) -> !"minecraft".equals(option.source)).thenComparing(alphabetical));
		filterOptions.get(1).add(0, new FilterOption(Text.literal("Current").getString(), null, world.getRegistryKey().getValue(), true));
		filterScroll = MathHelper.clamp(filterScroll, 0, filterMaxScroll());
	}

	private boolean filterChecked(int column, FilterOption option) {
		if (option.current) {
			return FilterSelections.isCurrentSelected();
		}
		return switch (column) {
			case 0 -> FilterSelections.sources.contains(option.source);
			case 1 -> FilterSelections.dimensions.contains(option.id);
			default -> FilterSelections.groups.contains(option.id);
		};
	}

	private void toggleFilter(int column, FilterOption option) {
		if (column == 0) {
			toggleSet(FilterSelections.sources, option.source);
		} else if (column == 1) {
			if (option.current) {
				FilterSelections.toggleCurrent();
			} else {
				FilterSelections.toggleDimension(option.id);
			}
		} else {
			toggleSet(FilterSelections.groups, option.id);
		}
		processSearchTerm();
		filterScroll = MathHelper.clamp(filterScroll, 0, filterMaxScroll());
	}

	private static <T> void toggleSet(Set<T> values, T value) {
		if (!values.add(value)) {
			values.remove(value);
		}
	}

	public void openEntryFilters(Identifier id) {
		if (sortMenuOpen) closeSortMenu();
		entryFiltersId = id;
		entryFilterFirstRow = 0;
	}

	private List<EntryFilterRow> entryFilterRows() {
		if (entryFiltersId == null) return List.of();
		List<EntryFilterRow> rows = new ArrayList<>();
		if (showSourceColumn) {
			rows.add(new EntryFilterRow("Source:", StructureUtils.getStructureSource(entryFiltersId), 0,
					new FilterOption("", entryFiltersId.getNamespace(), null, false)));
		}
		for (Identifier dimension : ExplorersCompass.allowedStructureIDsToDimensionIDs.get(entryFiltersId)) {
			rows.add(new EntryFilterRow("Dimension:", StructureUtils.getDimensionName(dimension), 1,
					new FilterOption("", null, dimension, false)));
		}
		Identifier group = ExplorersCompass.structureIDsToGroupIDs.get(entryFiltersId);
		if (group != null) {
			rows.add(new EntryFilterRow("Group:", StructureUtils.getStructureName(group), 2,
					new FilterOption("", null, group, false)));
		}
		return rows;
	}

	private int entryFilterVisibleRows(int count) {
		return Math.min(count, Math.max(1, (height - 70) / ENTRY_FILTER_ROW_HEIGHT));
	}

	private int entryFilterWidth() { return Math.max(40, Math.min(LockedRequirementsView.WINDOW_WIDTH, width - 24)); }
	private int entryFilterX() { return (width - entryFilterWidth()) / 2; }
	private int entryFilterHeight(int count) { return 65 + entryFilterVisibleRows(count) * ENTRY_FILTER_ROW_HEIGHT; }
	private int entryFilterY(int count) { return (height - entryFilterHeight(count)) / 2; }

	private void renderEntryFilters(DrawContext context, int mouseX, int mouseY) {
		List<EntryFilterRow> rows = entryFilterRows();
		int x = entryFilterX(), w = entryFilterWidth(), h = entryFilterHeight(rows.size());
		int y = entryFilterY(rows.size()), bodyY = y + 30;
		int visible = entryFilterVisibleRows(rows.size());
		int backY = bodyY + visible * ENTRY_FILTER_ROW_HEIGHT + 10;
		int checkboxX = x + w - 19;
		int valueX = x + 12 + textRenderer.getWidth("Dimension:") + 8;
		String hoveredValue = null;
		context.getMatrices().push();
		context.getMatrices().translate(0, 0, 300);
		context.fill(0, 0, width, height, 0x88000000);
		context.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xff909090);
		context.fill(x, y, x + w, y + h, 0xff000000);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal("Filters"), x + w / 2, y + 10, 0xffffff);
		context.enableScissor(x + 8, bodyY, x + w - 8, bodyY + visible * ENTRY_FILTER_ROW_HEIGHT);
		for (int i = 0; i < visible; i++) {
			EntryFilterRow row = rows.get(entryFilterFirstRow + i);
			int rowY = bodyY + i * ENTRY_FILTER_ROW_HEIGHT;
			if (mouseX >= x + 8 && mouseX < x + w - 8 && mouseY >= rowY && mouseY < rowY + ENTRY_FILTER_ROW_HEIGHT)
				context.fill(x + 8, rowY, x + w - 8, rowY + ENTRY_FILTER_ROW_HEIGHT, 0xff242424);
			context.drawTextWithShadow(textRenderer, row.category(), x + 12, rowY + 6, 0xffffff);
			context.drawTextWithShadow(textRenderer,
					textRenderer.trimToWidth(row.value(), Math.max(1, checkboxX - valueX - 8)), valueX, rowY + 6, 0xffffff);
			if (textRenderer.getWidth(row.value()) > checkboxX - valueX - 8
					&& mouseX >= valueX && mouseX < checkboxX && mouseY >= rowY && mouseY < rowY + ENTRY_FILTER_ROW_HEIGHT)
				hoveredValue = row.value();
			drawFilterCheckbox(context, checkboxX, rowY + 5, filterChecked(row.column(), row.option()));
		}
		context.disableScissor();
		int buttonX = x + (w - 70) / 2;
		boolean backHovered = mouseX >= buttonX && mouseX < buttonX + 70 && mouseY >= backY && mouseY < backY + 20;
		context.fill(buttonX, backY, buttonX + 70, backY + 20, 0xff909090);
		context.fill(buttonX + 1, backY + 1, buttonX + 69, backY + 19, backHovered ? 0xff424242 : 0xff242424);
		context.drawCenteredTextWithShadow(textRenderer, Text.literal("Back"), x + w / 2, backY + 6, 0xffffff);
		context.getMatrices().pop();
		if (hoveredValue != null) context.drawTooltip(textRenderer, Text.literal(hoveredValue), mouseX, mouseY);
	}

	private boolean clickEntryFilters(double mouseX, double mouseY, int button) {
		List<EntryFilterRow> rows = entryFilterRows();
		int x = entryFilterX(), y = entryFilterY(rows.size()), w = entryFilterWidth();
		if (mouseX < x - 1 || mouseX >= x + w + 1 || mouseY < y - 1 || mouseY >= y + entryFilterHeight(rows.size()) + 1) {
			entryFiltersId = null;
			return true;
		}
		if (button != 0) return true;
		int backY = y + 30 + entryFilterVisibleRows(rows.size()) * ENTRY_FILTER_ROW_HEIGHT + 10;
		int buttonX = x + (w - 70) / 2;
		if (mouseX >= buttonX && mouseX < buttonX + 70 && mouseY >= backY && mouseY < backY + 20) {
			entryFiltersId = null;
			return true;
		}
		int index = (int) (mouseY - y - 30) / ENTRY_FILTER_ROW_HEIGHT;
		if (mouseY >= y + 30 && index >= 0 && index < entryFilterVisibleRows(rows.size())) {
			EntryFilterRow row = rows.get(entryFilterFirstRow + index);
			double scroll = selectionList.getScrollAmount();
			toggleFilter(row.column(), row.option());
			selectionList.setScrollAmount(scroll);
			if (!selectionList.containsStructure(entryFiltersId)) entryFiltersId = null;
		}
		return true;
	}

	private void drawFilterCheckbox(DrawContext context, int x, int y, boolean checked) {
		context.fill(x, y, x + FILTER_CHECKBOX_SIZE, y + FILTER_CHECKBOX_SIZE, 0xffb0b0b0);
		context.fill(x + 1, y + 1, x + FILTER_CHECKBOX_SIZE - 1, y + FILTER_CHECKBOX_SIZE - 1, 0xff161616);
		if (checked) {
			for (int pixel = 0; pixel < 5; pixel++) {
				context.fill(x + 2 + pixel, y + 2 + pixel, x + 3 + pixel, y + 3 + pixel, 0xffffffff);
				context.fill(x + 6 - pixel, y + 2 + pixel, x + 7 - pixel, y + 3 + pixel, 0xffffffff);
			}
		}
	}

	private void renderCrossIcon(DrawContext context, ButtonWidget button) {
		int color = button.active ? 0xffffffff : 0xff909090;
		context.getMatrices().push();
		context.getMatrices().translate(
				button.getX() + (button.getWidth() - BUTTON_CROSS_SIZE) / 2.0,
				button.getY() + (button.getHeight() - BUTTON_CROSS_SIZE) / 2.0,
				0.0);
		for (int pixel = 0; pixel < BUTTON_CROSS_SIZE; pixel++) {
			context.fill(pixel, pixel, pixel + 1, pixel + 1, color);
			context.fill(BUTTON_CROSS_SIZE - 1 - pixel, pixel, BUTTON_CROSS_SIZE - pixel, pixel + 1, color);
		}
		context.getMatrices().pop();
	}

	private int settingsRowY(int row) {
		return filterTop() + 10 + (row - settingsFirstRow) * SETTINGS_ROW_HEIGHT;
	}

	private int settingsVisibleRows() {
		return Math.max(1, (filterBottom() - filterTop()) / SETTINGS_ROW_HEIGHT);
	}

	private int settingsMaxFirstRow() {
		return Math.max(0, SETTINGS_LABELS.length - settingsVisibleRows());
	}

	private int settingsControlHeight() {
		return 20;
	}

	private int settingsControlX() {
		return filterRight() - SETTINGS_CONTROL_WIDTH - 10;
	}

	private void renderSettingsPanel(DrawContext context) {
		context.fill(filterLeft(), filterTop(), filterRight(), filterBottom(), 0xb0000000);
		int labelX = filterLeft() + 10;
		int labelWidth = Math.max(1, settingsControlX() - labelX - 10);
		for (int row = settingsFirstRow; row < Math.min(SETTINGS_LABELS.length, settingsFirstRow + settingsVisibleRows()); row++) {
			String label = SETTINGS_LABELS[row];
			int labelY = settingsRowY(row) + (settingsControlHeight() - textRenderer.fontHeight) / 2;
			int textWidth = textRenderer.getWidth(label);
			if (textWidth <= labelWidth) {
				context.drawTextWithShadow(textRenderer, label, labelX, labelY, 0xffffff);
			} else {
				float scale = (float) labelWidth / textWidth;
				context.getMatrices().push();
				context.getMatrices().translate(labelX, labelY + (textRenderer.fontHeight * (1.0f - scale)) / 2.0f, 0);
				context.getMatrices().scale(scale, scale, 1.0f);
				context.drawTextWithShadow(textRenderer, label, 0, 0, 0xffffff);
				context.getMatrices().pop();
			}
		}
		String offset = Integer.toString(ExplorersCompassConfig.overlayLineOffset);
		float offsetScale = Math.min(1.0f, 46.0f / Math.max(1, textRenderer.getWidth(offset)));
		if (settingsFirstRow <= 1 && 1 < settingsFirstRow + settingsVisibleRows()) {
			context.getMatrices().push();
			context.getMatrices().translate(settingsControlX() + SETTINGS_CONTROL_WIDTH / 2.0,
					settingsRowY(1) + (settingsControlHeight() - textRenderer.fontHeight * offsetScale) / 2.0, 0);
			context.getMatrices().scale(offsetScale, offsetScale, 1.0f);
			context.drawCenteredTextWithShadow(textRenderer, offset, 0, 0, 0xffffff);
			context.getMatrices().pop();
		}
		scrollbarFor(ScrollPanel.SETTINGS).render(context);
	}

	private void renderSettingsTooltip(DrawContext context, int mouseX, int mouseY) {
		if (mouseY < settingsRowY(settingsFirstRow) || mouseY >= filterBottom()) {
			return;
		}
		int row = settingsFirstRow + (mouseY - settingsRowY(settingsFirstRow)) / SETTINGS_ROW_HEIGHT;
		if (row >= SETTINGS_LABELS.length || row >= settingsFirstRow + settingsVisibleRows()) {
			return;
		}
		int labelX = filterLeft() + 10;
		int labelWidth = Math.max(1, settingsControlX() - labelX - 10);
		String label = SETTINGS_LABELS[row];
		int textWidth = textRenderer.getWidth(label);
		float scale = Math.min(1.0f, (float) labelWidth / Math.max(1, textWidth));
		double textTop = settingsRowY(row) + (settingsControlHeight() - textRenderer.fontHeight) / 2.0f
				+ textRenderer.fontHeight * (1.0f - scale) / 2.0f;
		if (mouseX < labelX || mouseX >= labelX + textWidth * scale
				|| mouseY < textTop || mouseY >= textTop + textRenderer.fontHeight * scale) {
			return;
		}
		List<Text> lines = new ArrayList<>();
		for (OrderedText line : textRenderer.wrapLines(Text.literal(SETTINGS_TOOLTIPS[row]), Math.min(240, width - 30))) {
			StringBuilder content = new StringBuilder();
			line.accept((index, style, codePoint) -> {
				content.appendCodePoint(codePoint);
				return true;
			});
			lines.add(Text.literal(content.toString()));
		}
		context.drawTooltip(textRenderer, lines, mouseX, mouseY);
	}

	private void updateSettingsButtonLabels() {
		chatOverlayButton.setMessage(onOff(ExplorersCompassConfig.displayWithChatOpen));
		showTechnicalNamesButton.setMessage(onOff(ExplorersCompassConfig.showTechnicalNames));
		clearOnCloseButton.setMessage(onOff(ExplorersCompassConfig.clearFiltersOnClose));
		clearSearchBarOnCloseButton.setMessage(onOff(ExplorersCompassConfig.clearSearchBarOnClose));
		pauseGameWhenOpenButton.setMessage(onOff(ExplorersCompassConfig.pauseGameWhenOpen));
		checkedFiltersFirstButton.setMessage(onOff(ExplorersCompassConfig.checkedFiltersFirst));
		closeOnFoundButton.setMessage(onOff(ExplorersCompassConfig.closeOnFound));
		adjustRowsToFitViewButton.setMessage(onOff(ExplorersCompassConfig.adjustRowsToFitView));
		overlaySideButton.setMessage(Text.literal(ExplorersCompassConfig.overlaySide == OverlaySide.LEFT ? "Left" : "Right"));
	}

	private static Text onOff(boolean enabled) {
		return Text.literal(enabled ? "On" : "Off");
	}

	private void renderFilterPanel(DrawContext context, int mouseX, int mouseY) {
		filterScroll = MathHelper.clamp(filterScroll, 0, filterMaxScroll());
		int right = filterRight();
		int bottom = filterBottom();
		for (int column = showSourceColumn ? 0 : 1; column < 3; column++) {
			int columnLeft = filterColumnLeft(column);
			int columnRight = filterColumnRight(column);
			context.fill(columnLeft, filterTop(), columnRight, filterRowsTop() - FILTER_HEADER_GAP, 0xb0000000);
			List<FilterOption> options = visibleFilterOptions(column);
			int sectionStart = filterSectionStart(column, options);
			if (sectionStart >= 0) {
				int gapY = filterRowsTop() + sectionStart * filterRowHeight() - visibleFilterScroll();
				context.fill(columnLeft, filterRowsTop(), columnRight, MathHelper.clamp(gapY, filterRowsTop(), bottom), 0xb0000000);
				context.fill(columnLeft, MathHelper.clamp(gapY + FILTER_SECTION_GAP, filterRowsTop(), bottom), columnRight, bottom, 0xb0000000);
			} else {
				context.fill(columnLeft, filterRowsTop(), columnRight, bottom, 0xb0000000);
			}
		}
		String[] headings = {
			Text.literal("Source").getString(),
			Text.literal("Dimension").getString(),
			Text.literal("Group").getString()
		};
		for (int column = showSourceColumn ? 0 : 1; column < 3; column++) {
			int columnLeft = filterColumnLeft(column);
			int columnRight = filterColumnRight(column);
			context.enableScissor(columnLeft + 2, filterHeaderTop() + 1, columnRight - 2, filterRowsTop() - FILTER_HEADER_GAP);
			String heading = textRenderer.trimToWidth(headings[column], columnRight - columnLeft - 6);
			context.drawCenteredTextWithShadow(textRenderer, heading, (columnLeft + columnRight) / 2, filterHeaderTop() + 6, 0xffffff);
			context.disableScissor();
			List<FilterOption> options = visibleFilterOptions(column);
			int sectionStart = filterSectionStart(column, options);
			int rowsBottom = filterFullRowsBottom(sectionStart);
			int rowHeight = filterRowHeight();
			context.enableScissor(columnLeft + 1, filterRowsTop(), columnRight - 1, rowsBottom);
			int checkboxX = columnRight - FILTER_CHECKBOX_SIZE - 7;
			for (int index = 0; index < options.size(); index++) {
				int rowY = filterRowY(index, sectionStart);
				if (rowY + rowHeight <= filterRowsTop() || rowY >= rowsBottom) {
					continue;
				}
				if (mouseX >= columnLeft && mouseX < columnRight && mouseY >= rowY && mouseY < rowY + rowHeight) {
					context.fill(columnLeft + 1, rowY, columnRight - 1, rowY + rowHeight, 0x55242424);
				}
				FilterOption option = options.get(index);
				drawFilterOptionLabel(context, option, columnLeft + 5, rowY, Math.max(0, checkboxX - columnLeft - 9));
				drawFilterCheckbox(context, checkboxX, rowY + (rowHeight - FILTER_CHECKBOX_SIZE) / 2, filterChecked(column, option));
			}
			context.disableScissor();
		}
		scrollbarFor(ScrollPanel.FILTERS).render(context);
	}

	private Text filterOptionText(FilterOption option, String label) {
		Text text = Text.literal(label);
		return option.current || "minecraft".equals(option.source) ? text.copy().formatted(Formatting.ITALIC) : text;
	}

	private void drawFilterOptionLabel(DrawContext context, FilterOption option, int x, int rowY, int availableWidth) {
		if (availableWidth <= 1) {
			return;
		}
		Text label = filterOptionText(option, option.label);
		int labelWidth = textRenderer.getWidth(label);
		if (labelWidth <= availableWidth) {
			context.drawTextWithShadow(textRenderer, label, x, rowY + (filterRowHeight() - textRenderer.fontHeight) / 2, 0xffffff);
			return;
		}
		float scale = (availableWidth - 1.0f) / labelWidth;
		context.getMatrices().push();
		context.getMatrices().translate(x, rowY + (filterRowHeight() - textRenderer.fontHeight * scale) / 2.0f, 0);
		context.getMatrices().scale(scale, scale, 1.0f);
		context.drawTextWithShadow(textRenderer, label, 0, 0, 0xffffff);
		context.getMatrices().pop();
	}

	private void renderFilterTooltip(DrawContext context, int mouseX, int mouseY) {
		int column = filterColumnAt(mouseX, mouseY);
		if (column < 0 || mouseY < filterRowsTop()) {
			return;
		}
		List<FilterOption> options = visibleFilterOptions(column);
		int index = filterOptionIndexAt(mouseY, options, filterSectionStart(column, options));
		if (index < 0) {
			return;
		}
		int availableWidth = Math.max(0, filterColumnRight(column) - FILTER_CHECKBOX_SIZE - 7 - filterColumnLeft(column) - 9);
		FilterOption option = options.get(index);
		if (textRenderer.getWidth(filterOptionText(option, option.label)) > availableWidth) {
			context.drawTooltip(textRenderer, filterOptionText(option, option.label), mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		draggingStructureScrollbar = button == 0 && dialog == Dialog.NONE
				&& entryFiltersId == null && !filtersOpen && !settingsOpen && !hiddenStructuresOpen && !sortMenuOpen
				&& selectionList != null && selectionList.isScrollbarAt(mouseX, mouseY);
		try {
			return handleMouseClicked(mouseX, mouseY, button);
		} finally {
			// Minecraft routes drag events to the focused child. Keep the list focused
			// after grabbing its scrollbar instead of returning focus to search.
			if (client.currentScreen == this && dialog == Dialog.NONE && entryFiltersId == null && !sortMenuOpen && !draggingStructureScrollbar) focusActiveSearchField();
		}
	}

	private boolean handleMouseClicked(double mouseX, double mouseY, int button) {
		if (entryFiltersId != null) return clickEntryFilters(mouseX, mouseY, button);
		if (sortMenuOpen) return clickSortMenu(mouseX, mouseY, button);
        if (dialog == Dialog.LOCKED) {
            if (lockedRequirementsView.click(mouseX, mouseY, button, width, height)) dismissDialog();
            return true;
        }
        if (dialog != Dialog.NONE) {
            if (mouseX < dialogX() - 1 || mouseX >= dialogX() + dialogWidth() + 1
                    || mouseY < dialogY() - 1 || mouseY >= dialogY() + dialogHeight() + 1) {
                dismissDialog();
                return true;
            }
            if (dialog == Dialog.WRONG_DIMENSION || dialog == Dialog.INSUFFICIENT_XP) { dismissDialog(); return true; }
            if (button == 0) {
                int x = dialogX(), y = dialogY(), w = dialogWidth();
                if (dialog == Dialog.SEARCHING && mouseX >= x + (w - 70) / 2 && mouseX < x + (w + 70) / 2 && mouseY >= y + searchingBackY() && mouseY < y + searchingBackY() + 20) dismissDialog();
                else if (dialog == Dialog.FOUND && ExplorersCompass.canTeleport && mouseX >= x + (w - 100) / 2 && mouseX < x + (w + 100) / 2 && mouseY >= y + 94 && mouseY < y + 114) teleport();
                else if (dialog == Dialog.FOUND && mouseX >= x + (w - 70) / 2 && mouseX < x + (w + 70) / 2 && mouseY >= y + (ExplorersCompass.canTeleport ? 117 : 94) && mouseY < y + (ExplorersCompass.canTeleport ? 137 : 114)) dismissDialog();
                else if (dialog == Dialog.NOT_FOUND && failedCanContinue && mouseX >= notFoundKeepX() && mouseX < notFoundKeepX() + KEEP_SEARCHING_BUTTON_WIDTH && mouseY >= y + notFoundHideY() && mouseY < y + notFoundHideY() + 20) keepSearching();
                else if (dialog == Dialog.NOT_FOUND && mouseX >= notFoundHideX() && mouseX < notFoundHideX() + HIDE_BUTTON_WIDTH && mouseY >= y + notFoundHideY() && mouseY < y + notFoundHideY() + 20) hideFailedSearch();
                else if ((dialog == Dialog.NOT_FOUND || dialog == Dialog.REJECTED) && mouseX >= x + (w - 70) / 2 && mouseX < x + (w + 70) / 2 && mouseY >= y + (dialog == Dialog.NOT_FOUND ? notFoundBackY() : 57) && mouseY < y + (dialog == Dialog.NOT_FOUND ? notFoundBackY() : 57) + 20) dismissDialog();
            }
            return true;
        }
		if (button == 1) {
			TransparentTextField field = searchFieldAt(mouseX, mouseY);
			if (field != null) {
				super.mouseClicked(mouseX, mouseY, 0);
				field.setText("");
				field.setFocused(true);
				return true;
			}
		}
        if (hiddenStructuresOpen) {
            if (button == 0 && beginScrollbarDrag(ScrollPanel.HIDDEN, mouseX, mouseY)) return true;
            if (button == 0 && mouseX >= width - 95 && mouseX < width - 17 && mouseY >= filterTop() + 6) {
                int row = (int)(mouseY - filterTop() - 6) / 25;
                int index = hiddenScroll + row;
                List<String> visible = filteredHiddenStructures();
                if (row >= 0 && row < hiddenVisibleRows() && index < visible.size()
                        && mouseY < filterTop() + 6 + row * 25 + 20) {
                    ExplorersCompassConfig.hiddenStructures.remove(visible.get(index));
                    ExplorersCompassConfig.saveHiddenStructures();
                    hiddenScroll = MathHelper.clamp(hiddenScroll, 0, Math.max(0, filteredHiddenStructures().size() - hiddenVisibleRows()));
                    processSearchTerm();
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
		if (settingsOpen && button == 0 && beginScrollbarDrag(ScrollPanel.SETTINGS, mouseX, mouseY)) return true;
		if (filtersOpen && button == 0) {
			if (beginScrollbarDrag(ScrollPanel.FILTERS, mouseX, mouseY)) return true;
			int column = filterColumnAt(mouseX, mouseY);
			if (column >= 0 && mouseY >= filterRowsTop()) {
				List<FilterOption> options = visibleFilterOptions(column);
				int index = filterOptionIndexAt(mouseY, options, filterSectionStart(column, options));
				if (index >= 0) {
					toggleFilter(column, options.get(index));
				}
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private boolean clickSortMenu(double mouseX, double mouseY, int button) {
		int x = sortMenuX(), y = sortMenuY(), w = sortMenuWidth();
		if (mouseX < x - 1 || mouseX >= x + w + 1 || mouseY < y - 1 || mouseY >= y + sortMenuHeight() + 1) {
			closeSortMenu();
			return true;
		}
		if (button != 0) return true;
		int backX = x + (w - 70) / 2;
		if (mouseX >= backX && mouseX < backX + 70 && mouseY >= sortBackY() && mouseY < sortBackY() + 20) {
			closeSortMenu();
			return true;
		}
		int row = sortRowAt(mouseX, mouseY);
		if (row >= 0) draggedSortRow = row;
		return true;
	}

	private TransparentTextField searchFieldAt(double mouseX, double mouseY) {
		for (TransparentTextField field : new TransparentTextField[] {searchTextField, filterSearchTextField, hiddenSearchTextField}) {
			if (field != null && field.isVisible() && mouseX >= field.getX() && mouseX < field.getX() + field.getWidth()
					&& mouseY >= field.getY() && mouseY < field.getY() + field.getHeight()) return field;
		}
		return null;
	}

	private TransparentTextField activeSearchField() {
		if (hiddenStructuresOpen) return hiddenSearchTextField;
		if (filtersOpen) return filterSearchTextField;
		return settingsOpen ? null : searchTextField;
	}

	private void focusActiveSearchField() {
		TransparentTextField active = activeSearchField();
		for (TransparentTextField field : new TransparentTextField[] {searchTextField, filterSearchTextField, hiddenSearchTextField}) {
			if (field != null && field != active) field.setFocused(false);
		}
		if (active == null) {
			setFocused(null);
		} else if (getFocused() != active || !active.isFocused()) {
			setFocused(active);
			active.setFocused(true);
		}
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (entryFiltersId != null) return true;
        if (dialog == Dialog.LOCKED) { lockedRequirementsView.drag(mouseY); return true; }
		if (draggedScrollbar != null && button == 0) {
			moveScrollbar(mouseY);
			return true;
		}
		if (draggedSortRow >= 0 && button == 0) {
			if (mouseX >= sortMenuX() && mouseX < sortMenuX() + sortMenuWidth()) {
				int targetRow = MathHelper.clamp((int) Math.floor((mouseY - sortRowsY()) / SORT_ROW_HEIGHT), 0, sortOrder.size() - 1);
				if (targetRow != draggedSortRow) {
					SortCriterion criterion = sortOrder.remove(draggedSortRow);
					sortOrder.add(targetRow, criterion);
					draggedSortRow = targetRow;
					sortOrderChangedDuringDrag = true;
				}
			}
			return true;
		}
		if (sortMenuOpen) return true;
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (entryFiltersId != null) return true;
        if (draggingStructureScrollbar && button == 0) {
            draggingStructureScrollbar = false;
            super.mouseReleased(mouseX, mouseY, button);
            if (client.currentScreen == this && dialog == Dialog.NONE) focusActiveSearchField();
            return true;
        }
        if (dialog == Dialog.LOCKED) { lockedRequirementsView.release(); return true; }
		if (draggedScrollbar != null && button == 0) {
			draggedScrollbar = null;
			return true;
		}
		if (draggedSortRow >= 0 && button == 0) {
			commitSortOrder();
			draggedSortRow = -1;
			return true;
		}
		if (sortMenuOpen) return true;
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int par1, int par2, int par3) {
		if (entryFiltersId != null) {
			if (par1 == 256) entryFiltersId = null;
			return true;
		}
		if (sortMenuOpen) {
			if (par1 == 256) closeSortMenu();
			return true;
		}
        if (dialog != Dialog.NONE) {
            if (par1 == 256) dismissDialog();
            else if (dialog == Dialog.LOCKED) lockedRequirementsView.keyPressed(par1);
            return true;
        }
		if (isStructureView() && !sortMenuOpen) {
			if (par1 == 264 || par1 == 265) {
				selectionList.selectRelative(par1 == 264 ? 1 : -1);
				focusActiveSearchField();
				return true;
			}
			if (par1 == 257 || par1 == 335) {
				StructureSearchEntry entry = selectionList.hasSelection()
						? selectionList.getSelectedOrNull() : selectionList.soleSearchableEntry();
				if (entry != null && !isStructureHidden(entry.getStructureID())) entry.searchForStructure();
				if (dialog == Dialog.NONE) focusActiveSearchField();
				return true;
			}
		}
        if (hiddenStructuresOpen && par1 == 256) {
            leaveHiddenStructures();
            return true;
        }
		if (settingsOpen && par1 == 256) {
			setSettingsOpen(false);
			return true;
		}
		if (filtersOpen && par1 == 256) {
			setFiltersOpen(false);
			return true;
		}
		boolean ret = super.keyPressed(par1, par2, par3);
		focusActiveSearchField();
		return ret;
	}

	@Override
	public boolean charTyped(char typedChar, int keyCode) {
		if (dialog != Dialog.NONE || entryFiltersId != null || sortMenuOpen) return true;
		boolean ret = super.charTyped(typedChar, keyCode);
		focusActiveSearchField();
		return ret;
	}

	private boolean beginScrollbarDrag(ScrollPanel panel, double mouseX, double mouseY) {
		Scrollbar scrollbar = scrollbarFor(panel);
		if (!scrollbar.contains(mouseX, mouseY)) return false;
		int padding = panel == ScrollPanel.SETTINGS ? 0 : Scrollbar.GRAB_PADDING;
		boolean jump = scrollbar.outsideThumb(mouseY, padding);
		scrollbarGrabOffset = scrollbar.grabOffset(mouseY, padding);
		draggedScrollbar = panel;
		if (jump) moveScrollbar(mouseY);
		return true;
	}

	private void moveScrollbar(double mouseY) {
		if (draggedScrollbar == null) return;
		double offset = scrollbarFor(draggedScrollbar).offsetForMouse(mouseY, scrollbarGrabOffset);
		switch (draggedScrollbar) {
			case FILTERS -> filterScroll = offset;
			case SETTINGS -> {
				settingsFirstRow = (int)Math.round(offset);
				updateViewButtons();
			}
			case HIDDEN -> hiddenScroll = (int)Math.round(offset);
		}
	}

	private void setFiltersOpen(boolean open) {
		if (filtersOpen == open) {
			return;
		}
		if (sortMenuOpen) {
			closeSortMenu();
		}
		entryFiltersId = null;
		filtersOpen = open;
		if (open) {
			pendingHiddenStructures.clear();
			selectionList.setSelected(null);
		}
		draggedScrollbar = null;
		searchTextField.setVisible(isStructureView());
		searchTextField.setFocused(false);
		filterSearchTextField.setVisible(open);
		filterSearchTextField.setFocused(false);
		if (open) {
			remove(selectionList);
		} else {
			addDrawableChild(selectionList);
			processSearchTerm();
		}
		updateViewButtons();
		focusActiveSearchField();
	}

	private void setSettingsOpen(boolean open) {
		if (settingsOpen == open) {
			return;
		}
		if (sortMenuOpen) {
			closeSortMenu();
		}
		entryFiltersId = null;
		settingsOpen = open;
		if (open) {
			pendingHiddenStructures.clear();
			selectionList.setSelected(null);
		}
		draggedScrollbar = null;
		searchTextField.setVisible(isStructureView());
		searchTextField.setFocused(false);
		if (open) {
			remove(selectionList);
		} else {
			addDrawableChild(selectionList);
			processSearchTerm();
		}
		updateViewButtons();
		focusActiveSearchField();
	}

	private boolean isStructureView() {
		return !filtersOpen && !settingsOpen && !hiddenStructuresOpen;
	}

    private boolean validDimension(Identifier id, boolean group) {
        Identifier current = world.getRegistryKey().getValue();
        if (!group) return ExplorersCompass.allowedStructureIDsToDimensionIDs.get(id).contains(current);
        for (Identifier member : allowedStructureIDs) {
            if (id.equals(ExplorersCompass.structureIDsToGroupIDs.get(member))
                    && ExplorersCompass.allowedStructureIDsToDimensionIDs.get(member).contains(current)) return true;
        }
        return false;
    }

    private boolean beginSearch(Identifier id, boolean group) {
        if (dialog != Dialog.NONE || id == null) return false;
        if (isStructureLocked(id)) {
            showLockedRequirements(ExplorersCompass.lockedRequirements.get(id));
            return false;
        }
        if (!validDimension(id, group)) {
            showWrongDimension(true);
            return false;
        }
        if (!canAffordSearch()) {
            showInsufficientXp(ExplorersCompass.defaultXpCost);
            return false;
        }
        searchedId = id;
        searchedGroup = group;
        searchedListed = false;
        listedSearchIds = List.of();
        activeRequestId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        dialog = Dialog.SEARCHING;
        dialogStarted = System.currentTimeMillis();
        return true;
    }

    public void searchForStructure(Identifier structureID) {
		if (isStructureHidden(structureID)) return;
        boolean started = beginSearch(structureID, false);
		finalizePendingHides();
        if (started) ClientPlayNetworking.send(SearchPacket.ID, new SearchPacket(structureID, false, FilterSelections.excludeVisited(), activeRequestId));
    }

    private void searchListed() {
        if (dialog != Dialog.NONE) return;
        List<Identifier> selected = structureIDsMatchingSearch.stream().filter(id -> !isStructureHidden(id)).toList();
		finalizePendingHides();
        if (selected.isEmpty()) return;
        selected = selected.stream().filter(id -> !isStructureLocked(id)).toList();
        if (selected.isEmpty()) {
            showLockedRequirements(new com.chaosthedude.explorerscompass.unlocking.RequirementNode(
                    Text.literal("There are no unlocked structures in the current list."), false, java.util.List.of()));
            return;
        }
        List<Identifier> eligible = new ArrayList<>();
        for (Identifier id : selected) if (validDimension(id, false)) eligible.add(id);
        if (eligible.isEmpty()) {
            showWrongDimension(true);
            return;
        }
        if (!canAffordSearch()) {
            showInsufficientXp(ExplorersCompass.defaultXpCost);
            return;
        }
        searchedListed = true;
        searchedGroup = false;
        searchedId = null;
        listedSearchIds = List.copyOf(eligible);
        listedSelectedCount = selected.size();
        listedSkippedCount = selected.size() - eligible.size();
        activeRequestId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        dialog = Dialog.SEARCHING;
        dialogStarted = System.currentTimeMillis();
        ClientPlayNetworking.send(SearchListedPacket.ID, new SearchListedPacket(eligible, FilterSelections.excludeVisited(), activeRequestId));
    }

    public void onSearchResult(int requestId, int result, int radius, boolean canContinue, Identifier resultId, int foundX, int foundZ, boolean displayCoordinates) {
        if (dialog != Dialog.SEARCHING || activeRequestId != requestId) return;
        if (result == SearchResultPacket.FOUND) {
            if (ExplorersCompassConfig.closeOnFound) {
                successfulClose = true;
                client.setScreen(null);
            } else {
                foundStructureId = resultId;
                this.foundX = foundX;
                this.foundZ = foundZ;
                showFoundCoordinates = displayCoordinates;
				selectionList.refreshList(true);
                dialog = Dialog.FOUND;
            }
        } else if (result == SearchResultPacket.NOT_FOUND) {
            failedRadius = radius;
            failedCanContinue = canContinue;
            notFoundCompassUpdated = System.nanoTime();
            notFoundCompassAngle = 0.0D;
            notFoundCompassSpeed = 0.0D;
            dialog = Dialog.NOT_FOUND;
        } else if (result == SearchResultPacket.WRONG_DIMENSION) {
            showWrongDimension(false);
        } else if (result == SearchResultPacket.INSUFFICIENT_XP) {
            showInsufficientXp(radius);
        } else {
            dialog = Dialog.REJECTED;
        }
    }

    private boolean canAffordSearch() {
        return ExplorersCompass.infiniteXp || ExplorersCompass.allowSearchWithoutEnoughXp
                || client.player == null || client.player.experienceLevel >= ExplorersCompass.defaultXpCost;
    }

    private void showInsufficientXp(int cost) {
        insufficientXpCost = cost;
        dialog = Dialog.INSUFFICIENT_XP;
        dialogStarted = System.currentTimeMillis();
    }

    private void showWrongDimension(boolean notifyServer) {
        ItemStack heldCompass = client.player == null ? ItemStack.EMPTY : ItemUtils.getHeldItem(client.player, ExplorersCompass.EXPLORERS_COMPASS_ITEM);
        if (!heldCompass.isEmpty()) explorersCompass.clearCompassTarget(heldCompass);
        foundStructureId = null;
        foundX = 0;
        foundZ = 0;
        showFoundCoordinates = false;
        if (notifyServer && client.getNetworkHandler() != null) {
            ClientPlayNetworking.send(ResetCompassPacket.ID, new ResetCompassPacket());
        }
        dialog = Dialog.WRONG_DIMENSION;
        dialogStarted = System.currentTimeMillis();
    }

	private void dismissDialog() {
        if ((dialog == Dialog.SEARCHING || (dialog == Dialog.NOT_FOUND && failedCanContinue)) && client.getNetworkHandler() != null) {
            ClientPlayNetworking.send(CancelSearchPacket.ID, new CancelSearchPacket(activeRequestId));
        }
		dialog = Dialog.NONE;
		focusActiveSearchField();
    }

    private void keepSearching() {
        if (!failedCanContinue || dialog != Dialog.NOT_FOUND || client.getNetworkHandler() == null) return;
        failedCanContinue = false;
        dialog = Dialog.SEARCHING;
        dialogStarted = System.currentTimeMillis();
        ClientPlayNetworking.send(ContinueSearchPacket.ID, new ContinueSearchPacket(activeRequestId));
    }

    private void hideFailedSearch() {
        if (searchedListed) {
            for (Identifier id : listedSearchIds) {
                if (!ExplorersCompassConfig.hiddenStructures.contains(id.toString())) ExplorersCompassConfig.hiddenStructures.add(id.toString());
            }
        } else if (searchedId == null) return;
        else if (searchedGroup) {
            for (Identifier id : allowedStructureIDs) {
                if (searchedId.equals(ExplorersCompass.structureIDsToGroupIDs.get(id)) && !ExplorersCompassConfig.hiddenStructures.contains(id.toString())) {
                    ExplorersCompassConfig.hiddenStructures.add(id.toString());
                }
            }
        } else if (!ExplorersCompassConfig.hiddenStructures.contains(searchedId.toString())) {
            ExplorersCompassConfig.hiddenStructures.add(searchedId.toString());
        }
        ExplorersCompassConfig.saveHiddenStructures();
        dismissDialog();
        processSearchTerm();
    }

    private int dialogWidth() {
        if (dialog == Dialog.WRONG_DIMENSION) {
            return Math.min(width - 20, textRenderer.getWidth(Text.literal("You're in the wrong dimension, buddy!")) + 12);
        }
        if (dialog == Dialog.INSUFFICIENT_XP) {
            return Math.min(width - 20, Math.max(textRenderer.getWidth(Text.literal("You don't have enough levels, buddy!")), xpCostLineWidth()) + 12);
        }
        if (dialog == Dialog.FOUND) {
            int leftWidth = 0;
            for (Text line : foundInfoLines()) leftWidth = Math.max(leftWidth, textRenderer.getWidth(line));
            int contentWidth = leftWidth + 24;
            if (showFoundCoordinates) {
                int rightWidth = 0;
                for (Text line : foundLocationLines()) rightWidth = Math.max(rightWidth, textRenderer.getWidth(line));
                contentWidth = leftWidth + rightWidth + 36;
            }
            contentWidth = Math.max(245, contentWidth);
            return Math.min(width - 20, contentWidth);
        }
        if (dialog == Dialog.NOT_FOUND) return Math.min(width - 20, Math.max(245, textRenderer.getWidth(Text.literal("Couldn't locate structure within " + failedRadius + " blocks")) + 16));
        return Math.min(width - 20, 245);
    }
    private int searchingBackY() { return searchedListed ? (listedSkippedCount > 0 ? 89 : 77) : 65; }
    private int notFoundHideY() { return searchedListed ? (listedSkippedCount > 0 ? 78 : 66) : 52; }
    private int notFoundKeepX() { return dialogX() + (dialogWidth() - KEEP_SEARCHING_BUTTON_WIDTH - NOT_FOUND_BUTTON_GAP - HIDE_BUTTON_WIDTH) / 2; }
    private int notFoundHideX() { return notFoundKeepX() + KEEP_SEARCHING_BUTTON_WIDTH + NOT_FOUND_BUTTON_GAP; }
    private int notFoundBackY() { return notFoundHideY() + 23; }
    private Text wrongDimensionCountText() { return Text.literal("Wrong dimension for " + listedSkippedCount + (listedSkippedCount == 1 ? " structure" : " structures")); }
    private int dialogHeight() { return dialog == Dialog.WRONG_DIMENSION ? textRenderer.fontHeight + 12 : dialog == Dialog.INSUFFICIENT_XP ? textRenderer.fontHeight * 2 + 16 : dialog == Dialog.SEARCHING ? searchingBackY() + 25 : dialog == Dialog.FOUND ? (ExplorersCompass.canTeleport ? 142 : 119) : dialog == Dialog.NOT_FOUND ? notFoundBackY() + 25 : 80; }
    private int dialogX() { return (width - dialogWidth()) / 2; }
    private int dialogY() { return (height - dialogHeight()) / 2; }

    private int xpCostLineWidth() {
        return textRenderer.getWidth(Text.literal("XP Cost:")) + 3
                + 5 + 3 + textRenderer.getWidth(Integer.toString(insufficientXpCost));
    }

    private Text[] foundInfoLines() {
        Identifier groupId = foundStructureId == null ? null : ExplorersCompass.structureIDsToGroupIDs.get(foundStructureId);
        return new Text[] {
            Text.literal("Name").append(": ").append(Text.literal(StructureUtils.getStructureName(foundStructureId)).formatted(Formatting.GRAY)),
            Text.literal("Source").append(": ").append(Text.literal(StructureUtils.getStructureSource(foundStructureId)).formatted(Formatting.GRAY)),
            Text.literal("Group").append(": ").append(Text.literal(StructureUtils.getStructureName(groupId)).formatted(Formatting.GRAY))
        };
    }

    private Text[] foundLocationLines() {
        int distance = client.player == null ? 0 : StructureUtils.getHorizontalDistanceToLocation(client.player, foundX, foundZ);
        return new Text[] {
            Text.literal("Distance").append(": ").append(Text.literal(Integer.toString(distance)).formatted(Formatting.GRAY)),
            Text.literal("Coordinates").append(":"),
            Text.literal(foundX + ", " + foundZ).formatted(Formatting.GRAY)
        };
    }

    private void drawDialogButton(DrawContext context, String label, int x, int y, int w, int mouseX, int mouseY) {
        drawDialogButton(context, label, x, y, w, mouseX, mouseY, true);
    }

    private void drawDialogButton(DrawContext context, String label, int x, int y, int w, int mouseX, int mouseY, boolean enabled) {
        context.fill(x, y, x + w, y + 20, enabled ? 0xff909090 : 0xff505050);
        context.fill(x + 1, y + 1, x + w - 1, y + 19, enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 20 ? 0xff424242 : 0xff242424);
        context.drawCenteredTextWithShadow(textRenderer, label, x + w / 2, y + 6, enabled ? 0xffffff : 0xff808080);
    }

    private boolean insideQuestionIcon(int mouseX, int mouseY, int iconX, int iconY) {
        return (mouseX - iconX) * (mouseX - iconX) + (mouseY - iconY) * (mouseY - iconY) <= 49;
    }

    private void drawQuestionIcon(DrawContext context, int iconX, int iconY) {
        int color = 0xff909090;
        context.fill(iconX - 2, iconY - 6, iconX + 3, iconY - 5, color);
        context.fill(iconX - 2, iconY + 6, iconX + 3, iconY + 7, color);
        for (int dy = -5; dy <= 5; dy += 10) {
            context.fill(iconX - 4, iconY + dy, iconX - 2, iconY + dy + 1, color);
            context.fill(iconX + 3, iconY + dy, iconX + 5, iconY + dy + 1, color);
        }
        for (int dy = -4; dy <= 4; dy++) {
            if (Math.abs(dy) < 3) continue;
            context.fill(iconX - 5, iconY + dy, iconX - 4, iconY + dy + 1, color);
            context.fill(iconX + 5, iconY + dy, iconX + 6, iconY + dy + 1, color);
        }
        for (int dy = -2; dy <= 2; dy++) {
            context.fill(iconX - 6, iconY + dy, iconX - 5, iconY + dy + 1, color);
            context.fill(iconX + 6, iconY + dy, iconX + 7, iconY + dy + 1, color);
        }
        context.drawText(textRenderer, "?", iconX - textRenderer.getWidth("?") / 2 + 1, iconY - textRenderer.fontHeight / 2 + 1, color, false);
    }

    private void drawQuestionTooltip(DrawContext context, String message, int mouseX, int mouseY) {
        List<Text> lines = new ArrayList<>();
        for (OrderedText line : textRenderer.wrapLines(Text.literal(message), Math.min(220, width - 30))) {
            StringBuilder content = new StringBuilder();
            line.accept((index, style, codePoint) -> { content.appendCodePoint(codePoint); return true; });
            lines.add(Text.literal(content.toString()));
        }
        context.drawTooltip(textRenderer, lines, mouseX, mouseY);
    }

    public boolean isStructureLocked(Identifier id) {
        return ExplorersCompass.lockedRequirements.containsKey(id);
    }

    private void showLockedRequirements(com.chaosthedude.explorerscompass.unlocking.RequirementNode requirements) {
        lockedRequirementsView = new LockedRequirementsView(textRenderer, requirements);
        dialog = Dialog.LOCKED;
    }

    private void renderSearchDialog(DrawContext context, int mouseX, int mouseY) {
        if (dialog == Dialog.LOCKED) {
            lockedRequirementsView.render(context, width, height, mouseX, mouseY);
            return;
        }
        int w = dialogWidth(), x = dialogX(), y = dialogY(), h = dialogHeight();
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 300);
        context.fill(0, 0, width, height, 0x88000000);
        context.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xff909090);
        context.fill(x, y, x + w, y + h, 0xff000000);
        if (dialog == Dialog.SEARCHING) {
            int frame = (int)((System.currentTimeMillis() - dialogStarted) / 31L % 32L);
            Identifier texture = new Identifier(ExplorersCompass.MODID, String.format(Locale.ROOT, "textures/item/explorerscompass_%02d.png", frame));
            context.drawTexture(texture, x + (w - 16) / 2, y + 10, 16, 16, 0, 0, 16, 16, 16, 16);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Locating..."), x + w / 2, y + 32, 0xffffff);
            if (searchedListed) {
                context.drawCenteredTextWithShadow(textRenderer, Text.literal(listedSelectedCount + " structures selected"), x + w / 2, y + 47, 0xffffff);
                if (listedSkippedCount > 0) context.drawCenteredTextWithShadow(textRenderer, wrongDimensionCountText(), x + w / 2, y + 59, 0xffffff);
            }
            ItemStack heldCompass = client.player == null ? ItemStack.EMPTY : ItemUtils.getHeldItem(client.player, ExplorersCompass.EXPLORERS_COMPASS_ITEM);
            int distance = heldCompass.getItem() instanceof ExplorersCompassItem
                ? ((ExplorersCompassItem) heldCompass.getItem()).getSearchRadius(heldCompass) : 0;
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Distance").getString() + ": " + distance, x + w / 2, y + (searchedListed ? (listedSkippedCount > 0 ? 74 : 59) : 47), 0xffffff);
            drawDialogButton(context, Text.literal("Back").getString(), x + (w - 70) / 2, y + searchingBackY(), 70, mouseX, mouseY);
        } else if (dialog == Dialog.FOUND) {
            double yaw = client.player == null ? 0.0D : client.player.getYaw() % 360.0D;
            double targetAngle = client.player == null ? 0.0D : Math.atan2(foundZ - client.player.getZ(), foundX - client.player.getX());
            double angle = MathHelper.floorMod((Math.PI - ((yaw - 90.0D) * (Math.PI / 180.0D) - targetAngle)) / (Math.PI * 2.0D), 1.0D);
            int frame = (16 + (int)Math.floor(angle * 32.0D + 0.5D)) % 32;
            Identifier texture = new Identifier(ExplorersCompass.MODID, String.format(Locale.ROOT, "textures/item/explorerscompass_%02d.png", frame));
            context.drawTexture(texture, x + (w - 16) / 2, y + 10, 16, 16, 0, 0, 16, 16, 16, 16);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Structure Located"), x + w / 2, y + 32, 0xffffff);
            Text[] lines = foundInfoLines();
            Text[] locationLines = showFoundCoordinates ? foundLocationLines() : new Text[0];
            int leftWidth = w - 24;
            int rightWidth = 0;
            for (Text line : locationLines) rightWidth = Math.max(rightWidth, textRenderer.getWidth(line));
            if (showFoundCoordinates) {
                int leftWanted = 0;
                for (Text line : lines) leftWanted = Math.max(leftWanted, textRenderer.getWidth(line));
                int available = Math.max(2, w - 36);
                leftWidth = Math.min(leftWanted, Math.max(1, available - Math.min(rightWidth, available / 2)));
                rightWidth = Math.min(rightWidth, Math.max(1, available - leftWidth));
            }
            int rightX = x + w - 12 - rightWidth;
            for (int i = 0; i < lines.length; i++) {
                context.drawTextWithShadow(textRenderer, Language.getInstance().reorder(textRenderer.trimToWidth(lines[i], leftWidth)), x + 12, y + 50 + i * 12, 0xffffff);
            }
            for (int i = 0; i < locationLines.length; i++) {
                context.drawTextWithShadow(textRenderer, Language.getInstance().reorder(textRenderer.trimToWidth(locationLines[i], rightWidth)), rightX, y + 50 + i * 12, 0xffffff);
            }
            if (ExplorersCompass.canTeleport) {
                drawDialogButton(context, Text.literal("Teleport").getString(), x + (w - 100) / 2, y + 94, 100, mouseX, mouseY);
            }
            drawDialogButton(context, Text.literal("Back").getString(), x + (w - 70) / 2, y + (ExplorersCompass.canTeleport ? 117 : 94), 70, mouseX, mouseY);
        } else if (dialog == Dialog.INSUFFICIENT_XP) {
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("You don't have enough levels, buddy!"), x + w / 2, y + 6, 0xffff4444);
            int lineX = x + (w - xpCostLineWidth()) / 2;
            int lineY = y + 10 + textRenderer.fontHeight;
            Text costLabel = Text.literal("XP Cost:");
            context.drawTextWithShadow(textRenderer, costLabel, lineX, lineY, 0xffffff);
            int orbX = lineX + textRenderer.getWidth(costLabel) + 3;
            int orbSize = 5;
            int orbFrame = (int)((System.currentTimeMillis() / 40L) % 20L);
            Identifier orbTexture = new Identifier(ExplorersCompass.MODID, String.format(Locale.ROOT, "textures/gui/experience_orb_%02d.png", orbFrame));
            context.drawTexture(orbTexture, orbX, lineY + (textRenderer.fontHeight - orbSize) / 2, orbSize, orbSize, 0, 0, 16, 16, 16, 16);
            context.drawTextWithShadow(textRenderer, Integer.toString(insufficientXpCost), orbX + orbSize + 3, lineY, 0x80ff20);
        } else {
            Text message = dialog == Dialog.WRONG_DIMENSION ? Text.literal("You're in the wrong dimension, buddy!")
                : dialog == Dialog.NOT_FOUND ? Text.literal("Couldn't locate structure within " + failedRadius + " blocks")
                : Text.literal("Search could not start");
            if (dialog == Dialog.NOT_FOUND) {
                long now = System.nanoTime();
                long elapsed = now - notFoundCompassUpdated;
                int ticks = (int)Math.min(5L, elapsed / 50_000_000L);
                if (ticks > 0) {
                    notFoundCompassUpdated = now - elapsed % 50_000_000L;
                }
                for (int tick = 0; tick < ticks; tick++) {
                    double delta = MathHelper.floorMod(Math.random() - notFoundCompassAngle + 0.5D, 1.0D) - 0.5D;
                    notFoundCompassSpeed = (notFoundCompassSpeed + delta * 0.1D) * 0.8D;
                    notFoundCompassAngle = MathHelper.floorMod(notFoundCompassAngle + notFoundCompassSpeed, 1.0D);
                }
                int frame = (16 + (int)Math.floor(notFoundCompassAngle * 32.0D + 0.5D)) % 32;
                Identifier texture = new Identifier(ExplorersCompass.MODID, String.format(Locale.ROOT, "textures/item/explorerscompass_%02d.png", frame));
                context.drawTexture(texture, x + (w - 16) / 2, y + 10, 16, 16, 0, 0, 16, 16, 16, 16);
            }
            context.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(message.getString(), w - 12), x + w / 2, dialog == Dialog.WRONG_DIMENSION ? y + (h - textRenderer.fontHeight) / 2 : dialog == Dialog.NOT_FOUND ? y + 32 : y + 14, dialog == Dialog.WRONG_DIMENSION ? 0xffff4444 : 0xffffff);
            if (dialog == Dialog.NOT_FOUND) {
                if (searchedListed) {
                    context.drawCenteredTextWithShadow(textRenderer, Text.literal(listedSelectedCount + " structures selected"), x + w / 2, y + 47, 0xffffff);
                    if (listedSkippedCount > 0) context.drawCenteredTextWithShadow(textRenderer, wrongDimensionCountText(), x + w / 2, y + 59, 0xffffff);
                }
                int hideX = notFoundHideX();
                int iconX = hideX + HIDE_BUTTON_WIDTH - 12;
                int iconY = y + notFoundHideY() + 10;
                drawDialogButton(context, Text.literal("Keep Searching").getString(), notFoundKeepX(), y + notFoundHideY(), KEEP_SEARCHING_BUTTON_WIDTH, mouseX, mouseY, failedCanContinue);
                drawDialogButton(context, Text.literal(searchedListed ? "Hide All" : "Hide").getString(), hideX, y + notFoundHideY(), HIDE_BUTTON_WIDTH, mouseX, mouseY);
                drawQuestionIcon(context, iconX, iconY);
                if (insideQuestionIcon(mouseX, mouseY, iconX, iconY)) drawQuestionTooltip(context, searchedListed ? "Removes all searched structures from the list on this client, across all worlds and servers. You can undo this in Settings." : "Removes the structure from the list on this client, across all worlds and servers. You can undo this in Settings.", mouseX, mouseY);
            }
            if (dialog == Dialog.NOT_FOUND || dialog == Dialog.REJECTED) {
                drawDialogButton(context, Text.literal("Back").getString(), x + (w - 70) / 2, y + (dialog == Dialog.NOT_FOUND ? notFoundBackY() : 57), 70, mouseX, mouseY);
            }
        }
        context.getMatrices().pop();
    }

    private int hiddenVisibleRows() { return Math.max(1, (filterBottom() - filterTop() - 12) / 25); }

	private int hiddenMaxScroll() {
		return Math.max(0, filteredHiddenStructures().size() - hiddenVisibleRows());
	}

    private void renderHiddenStructures(DrawContext context, int mouseX, int mouseY) {
        context.fill(filterLeft(), filterTop(), filterRight(), filterBottom(), 0xb0000000);
        List<String> ids = filteredHiddenStructures();
        hiddenScroll = MathHelper.clamp(hiddenScroll, 0, Math.max(0, ids.size() - hiddenVisibleRows()));
        context.enableScissor(filterLeft(), filterTop(), filterRight(), filterBottom());
        if (ids.isEmpty()) context.drawCenteredTextWithShadow(textRenderer,
                Text.literal(ExplorersCompassConfig.hiddenStructures.isEmpty()
                        ? "No hidden structures" : "No matching hidden structures"),
                width / 2, filterTop() + 20, 0xffffff);
        for (int row = 0; row < hiddenVisibleRows() && hiddenScroll + row < ids.size(); row++) {
            int y = filterTop() + 6 + row * 25;
            String raw = ids.get(hiddenScroll + row);
            Identifier id = Identifier.tryParse(raw);
            String label = id == null || !allowedStructureIDs.contains(id) ? raw : StructureUtils.getStructureName(id) + " (" + raw + ")";
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(label, Math.max(1, width - 145)), 16, y + 6, 0xffffff);
            drawDialogButton(context, Text.literal("Remove").getString(), width - 95, y, 78, mouseX, mouseY);
        }
        context.disableScissor();
        scrollbarFor(ScrollPanel.HIDDEN).render(context);
    }

	private List<String> filteredHiddenStructures() {
		String query = hiddenSearchText.toLowerCase(Locale.ROOT);
		if (query.isEmpty()) return ExplorersCompassConfig.hiddenStructures;
		List<String> matches = new ArrayList<>();
		for (String raw : ExplorersCompassConfig.hiddenStructures) {
			Identifier id = Identifier.tryParse(raw);
			String name = id == null || !allowedStructureIDs.contains(id) ? raw : StructureUtils.getStructureName(id);
			if (raw.toLowerCase(Locale.ROOT).contains(query) || name.toLowerCase(Locale.ROOT).contains(query)) matches.add(raw);
		}
		return matches;
	}

	public boolean isStructureHidden(Identifier id) {
		return ExplorersCompassConfig.hiddenStructures.contains(id.toString());
	}

	public int rowActionButtonWidth() {
		return textRenderer.getWidth(Text.literal("filters")) + 6;
	}

	public void toggleStructureHidden(Identifier id) {
		String raw = id.toString();
		if (ExplorersCompassConfig.hiddenStructures.remove(raw)) pendingHiddenStructures.remove(id);
		else {
			ExplorersCompassConfig.hiddenStructures.add(raw);
			pendingHiddenStructures.add(id);
		}
		ExplorersCompassConfig.saveHiddenStructures();
	}

	private void finalizePendingHides() {
		if (pendingHiddenStructures.isEmpty()) return;
		pendingHiddenStructures.clear();
		processSearchTerm();
	}

	public void teleport() {
		ClientPlayNetworking.send(TeleportPacket.ID, new TeleportPacket());
		client.setScreen(null);
	}

	public void processSearchTerm() {
		structureIDsMatchingSearch = new ArrayList<Identifier>();
		String searchTerm = searchTextField.getText().toLowerCase(Locale.ROOT);
		for (Identifier id : allowedStructureIDs) {
			if ((isStructureHidden(id) && !pendingHiddenStructures.contains(id)) || !FilterSelections.matches(id)) {
				continue;
			}
			if (searchTerm.startsWith("@")) {
				if (StructureUtils.getStructureSource(id).toLowerCase(Locale.ROOT).contains(searchTerm.substring(1))) {
					structureIDsMatchingSearch.add(id);
				}
			} else if (StructureUtils.getStructureName(id).toLowerCase(Locale.ROOT).contains(searchTerm)
					|| (ExplorersCompassConfig.showTechnicalNames && id.toString().toLowerCase(Locale.ROOT).contains(searchTerm))) {
				structureIDsMatchingSearch.add(id);
			}
		}
		selectionList.refreshList(true);
	}

	public List<Identifier> sortStructures() {
		final List<Identifier> structures = new ArrayList<Identifier>(structureIDsMatchingSearch);
		Collator collator = englishCollator();
		structures.sort((first, second) -> {
			for (SortCriterion criterion : sortOrder) {
				int comparison = criterion.compare(first, second, collator);
				if (comparison != 0) {
					return comparison;
				}
			}
			return first.compareTo(second);
		});
		return structures;
	}

	private Collator englishCollator() {
		return Collator.getInstance(Locale.US);
	}

	private void setupButtons() {
		searchButton = addDrawableChild(new TransparentButton(10, 40, 110, 20, Text.literal("Search"), (onPress) -> {
			if (selectionList.hasSelection()) {
				selectionList.getSelectedOrNull().searchForStructure();
			}
		}));
		searchButton.active = false;

		searchListedButton = addDrawableChild(new TransparentButton(10, 65, 110, 20, Text.literal("Search Listed"), (onPress) -> searchListed()));
		searchListedButton.active = false;

		sortOrderButton = addDrawableChild(new TransparentButton(10, 90, 110, 20, Text.literal("Sort Priority"), (onPress) -> {
			if (sortMenuOpen) {
				closeSortMenu();
			} else {
				sortMenuOpen = true;
			}
		}));
		filtersButton = addDrawableChild(new TransparentButton(10, sortOrderButton.getY() + 25, 85, 20, Text.literal("Filters"), (onPress) -> {
			setFiltersOpen(!filtersOpen);
		}));
		clearFiltersButton = addDrawableChild(new TransparentButton(100, sortOrderButton.getY() + 25, 20, 20, Text.literal(""), (onPress) -> {
			FilterSelections.clear();
			processSearchTerm();
		}));
		settingsButton = addDrawableChild(new TransparentButton(10, height - 30, 110, 20, Text.literal("Settings"), (onPress) -> {
			setSettingsOpen(true);
		}));
		int controlX = settingsControlX();
		overlaySideButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(0), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), (onPress) -> {
			ExplorersCompassConfig.overlaySide = ExplorersCompassConfig.overlaySide == OverlaySide.LEFT ? OverlaySide.RIGHT : OverlaySide.LEFT;
			ExplorersCompassConfig.saveClient();
			updateSettingsButtonLabels();
		}));
		offsetDecreaseButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(1), 20, settingsControlHeight(), Text.literal("−"), (onPress) -> {
			if (ExplorersCompassConfig.overlayLineOffset > Integer.MIN_VALUE) {
				ExplorersCompassConfig.overlayLineOffset--;
				ExplorersCompassConfig.saveClient();
			}
		}));
		offsetIncreaseButton = addDrawableChild(new TransparentButton(controlX + SETTINGS_CONTROL_WIDTH - 20, settingsRowY(1), 20, settingsControlHeight(), Text.literal("+"), (onPress) -> {
			if (ExplorersCompassConfig.overlayLineOffset < Integer.MAX_VALUE) {
				ExplorersCompassConfig.overlayLineOffset++;
				ExplorersCompassConfig.saveClient();
			}
		}));
		chatOverlayButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(7), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), (onPress) -> {
			ExplorersCompassConfig.displayWithChatOpen = !ExplorersCompassConfig.displayWithChatOpen;
			ExplorersCompassConfig.saveClient();
			updateSettingsButtonLabels();
		}));
		clearSearchBarOnCloseButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(2), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), button -> {
			ExplorersCompassConfig.clearSearchBarOnClose = !ExplorersCompassConfig.clearSearchBarOnClose;
			ExplorersCompassConfig.saveClient();
			updateSettingsButtonLabels();
		}));
		clearOnCloseButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(3), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), (onPress) -> {
			ExplorersCompassConfig.clearFiltersOnClose = !ExplorersCompassConfig.clearFiltersOnClose;
			ExplorersCompassConfig.saveClient();
			updateSettingsButtonLabels();
		}));
		checkedFiltersFirstButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(4), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), (onPress) -> {
			ExplorersCompassConfig.checkedFiltersFirst = !ExplorersCompassConfig.checkedFiltersFirst;
			ExplorersCompassConfig.saveClient();
			updateSettingsButtonLabels();
			filterScroll = MathHelper.clamp(filterScroll, 0, filterMaxScroll());
		}));
		showTechnicalNamesButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(8), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), button -> {
			ExplorersCompassConfig.showTechnicalNames = !ExplorersCompassConfig.showTechnicalNames;
			ExplorersCompassConfig.saveClient();
			updateSettingsButtonLabels();
			processSearchTerm();
		}));
		adjustRowsToFitViewButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(5), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), (onPress) -> {
			Identifier selectedId = selectionList.hasSelection() ? selectionList.getSelectedOrNull().getStructureID() : null;
			ExplorersCompassConfig.adjustRowsToFitView = !ExplorersCompassConfig.adjustRowsToFitView;
			ExplorersCompassConfig.saveClient();
			remove(selectionList);
			selectionList = new StructureSearchList(this, client, player, selectedId, 130, 40, width - 130 - SCROLLBAR_WIDTH, height - 50, 50);
			filterScroll = MathHelper.clamp(filterScroll, 0, filterMaxScroll());
			updateSettingsButtonLabels();
		}));
		closeOnFoundButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(6), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), button -> {
			ExplorersCompassConfig.closeOnFound = !ExplorersCompassConfig.closeOnFound;
			ExplorersCompassConfig.saveClient();
			updateSettingsButtonLabels();
		}));
		pauseGameWhenOpenButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(9), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), button -> {
			ExplorersCompassConfig.pauseGameWhenOpen = !ExplorersCompassConfig.pauseGameWhenOpen;
			ExplorersCompassConfig.saveClient();
			updateSettingsButtonLabels();
		}));
		updateSettingsButtonLabels();
        hiddenStructuresButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(10), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.literal("Open"), button -> {
            settingsOpen = false;
            hiddenStructuresOpen = true;
            hiddenScroll = 0;
            updateViewButtons();
			focusActiveSearchField();
        }));

		cancelButton = addDrawableChild(new TransparentButton(width - 30, 10, 20, 20, Text.literal(""), (onPress) -> {
			client.setScreen(null);
		}));

		teleportButton = addDrawableChild(new TransparentButton(10, 10, buttonWidthForLabel("Teleport"), 20, Text.literal("Teleport"), (onPress) -> {
			teleport();
		}));
		teleportButton.active = false;
		clearFiltersTopButton = addDrawableChild(new TransparentButton(10, 10, buttonWidthForLabel("Clear Filters"), 20, Text.literal("Clear Filters"), (onPress) -> {
			FilterSelections.clear();
			processSearchTerm();
			filterScroll = MathHelper.clamp(filterScroll, 0, filterMaxScroll());
		}));
		TransparentButton excludeButton = addDrawableChild(new TransparentButton(10, 10,
				buttonWidthForLabel("Exclude Visited") + FILTER_CHECKBOX_SIZE + 10, 20, Text.literal("Exclude Visited"), button -> {
			FilterSelections.toggleExcludeVisited();
		}));
		excludeButton.setRightInset(FILTER_CHECKBOX_SIZE + 10);
		excludeVisitedButton = excludeButton;
		backButton = addDrawableChild(new TransparentButton(10, 10, buttonWidthForLabel("Back"), 20, Text.literal("Back"), (onPress) -> {
            if (hiddenStructuresOpen) {
                leaveHiddenStructures();
            } else if (settingsOpen) {
				setSettingsOpen(false);
			} else {
				setFiltersOpen(false);
			}
		}));
		positionTopButtons();
		updateViewButtons();
	}

	private void setupTextFields() {
		int searchX = selectionList.getRowLeft();
		int rightButtonX = teleportButton.getY() == 10 ? teleportButton.getX() : cancelButton.getX();
		int availableWidth = Math.max(0, rightButtonX - searchX - SEARCH_TELEPORT_GAP);
		searchTextField = new TransparentTextField(textRenderer, searchX, 10, Math.min(SEARCH_FIELD_WIDTH, availableWidth), 20, Text.literal("Search"));
		searchTextField.setText(sessionSearchText);
		searchTextField.setChangedListener(value -> processSearchTerm());
		addDrawableChild(searchTextField);
		searchTextField.setVisible(isStructureView());
		int filterSearchX = filterSearchX();
		filterSearchTextField = new TransparentTextField(textRenderer, filterSearchX, wrapsFilterSearch() ? 35 : 10,
				filterSearchWidth(filterSearchX), 20, Text.literal("Search Filters"));
		filterSearchTextField.setChangedListener(value -> {
			filterScroll = 0;
		});
		addDrawableChild(filterSearchTextField);
		filterSearchTextField.setVisible(filtersOpen);
		int headerEnd = backButton.getX() - SEARCH_TELEPORT_GAP;
		int hiddenSearchX = Math.min(10 + textRenderer.getWidth(Text.literal("Settings > Hidden Structures")) + 10,
				Math.max(10, headerEnd - 80));
		hiddenSearchTextField = new TransparentTextField(textRenderer, hiddenSearchX, 10,
				Math.max(1, Math.min(SEARCH_FIELD_WIDTH, headerEnd - hiddenSearchX)),
				20, Text.literal("Search hidden structures"));
		hiddenSearchTextField.setMaxLength(256);
		hiddenSearchTextField.setText(hiddenSearchText);
		hiddenSearchTextField.setChangedListener(value -> {
			hiddenSearchText = value;
			hiddenScroll = 0;
		});
		addDrawableChild(hiddenSearchTextField);
		hiddenSearchTextField.setVisible(hiddenStructuresOpen);
	}

	private void leaveHiddenStructures() {
		hiddenStructuresOpen = false;
		draggedScrollbar = null;
		settingsOpen = true;
		hiddenSearchText = "";
		hiddenSearchTextField.setText("");
		hiddenSearchTextField.setFocused(false);
		hiddenScroll = 0;
		updateViewButtons();
		focusActiveSearchField();
	}

	private void positionFilterButtons() {
		filtersButton.setY(sortOrderButton.getY() + 25);
		clearFiltersButton.setY(sortOrderButton.getY() + 25);
	}

	private int buttonWidthForLabel(String label) {
		return textRenderer.getWidth(label) + HEADER_BUTTON_TEXT_PADDING;
	}

	private int filterSearchX() {
		return FILTER_TITLE_X + textRenderer.getWidth(Text.literal("Filters")) + FILTER_TITLE_GAP;
	}

	private boolean wrapsFilterSearch() {
		if (!ExplorersCompass.canExcludeVisited) return false;
		int buttonsStart = width - 45 - buttonWidthForLabel("Back") - buttonWidthForLabel("Clear Filters")
				- buttonWidthForLabel("Exclude Visited") - FILTER_CHECKBOX_SIZE - 10;
		int searchX = filterSearchX();
		return buttonsStart - searchX - SEARCH_TELEPORT_GAP < 50;
	}

	private int filterSearchWidth(int searchX) {
		if (wrapsFilterSearch()) return Math.max(1, Math.min(SEARCH_FIELD_WIDTH, width - searchX - 10));
		int rightEdge = ExplorersCompass.canExcludeVisited ? excludeVisitedButton.getX() : clearFiltersTopButton.getX();
		return Math.max(1, Math.min(SEARCH_FIELD_WIDTH, rightEdge - searchX - SEARCH_TELEPORT_GAP));
	}

	private void positionFilterSearchField() {
		int searchX = filterSearchX();
		filterSearchTextField.setX(searchX);
		filterSearchTextField.setY(wrapsFilterSearch() ? 35 : 10);
		filterSearchTextField.setWidth(filterSearchWidth(searchX));
	}

	private void positionTopButtons() {
		cancelButton.setX(width - 30);
		settingsButton.setY(height - 30);
        hiddenStructuresButton.setX(settingsControlX());
        hiddenStructuresButton.setY(settingsRowY(10));
		if (width >= 480) {
			teleportButton.setX(cancelButton.getX() - teleportButton.getWidth() - 5);
			teleportButton.setY(10);
		} else {
			teleportButton.setX(10);
			teleportButton.setY(height - 55);
		}
		backButton.setX(cancelButton.getX() - backButton.getWidth() - 5);
		clearFiltersTopButton.setX(backButton.getX() - clearFiltersTopButton.getWidth() - 5);
		excludeVisitedButton.setX(clearFiltersTopButton.getX() - excludeVisitedButton.getWidth() - 5);
	}

	private void updateViewButtons() {
		settingsFirstRow = MathHelper.clamp(settingsFirstRow, 0, settingsMaxFirstRow());
		searchButton.visible = isStructureView();
		searchListedButton.visible = isStructureView();
		sortOrderButton.visible = isStructureView();
		filtersButton.visible = isStructureView();
		clearFiltersButton.visible = isStructureView();
		settingsButton.visible = isStructureView();
        hiddenStructuresButton.visible = settingsOpen;
		closeOnFoundButton.visible = settingsOpen;
		chatOverlayButton.visible = settingsOpen;
		showTechnicalNamesButton.visible = settingsOpen;
		offsetDecreaseButton.visible = settingsOpen;
		offsetIncreaseButton.visible = settingsOpen;
		overlaySideButton.visible = settingsOpen;
		clearOnCloseButton.visible = settingsOpen;
		checkedFiltersFirstButton.visible = settingsOpen;
		adjustRowsToFitViewButton.visible = settingsOpen;
		cancelButton.visible = true;
		teleportButton.visible = isStructureView() && ExplorersCompass.canTeleport;
		clearFiltersTopButton.visible = filtersOpen;
		excludeVisitedButton.visible = filtersOpen && ExplorersCompass.canExcludeVisited;
		backButton.visible = filtersOpen || settingsOpen || hiddenStructuresOpen;
		if (hiddenSearchTextField != null) hiddenSearchTextField.setVisible(hiddenStructuresOpen);
		placeSetting(overlaySideButton, 0);
		placeSetting(offsetDecreaseButton, 1);
		placeSetting(offsetIncreaseButton, 1);
		placeSetting(clearSearchBarOnCloseButton, 2);
		placeSetting(clearOnCloseButton, 3);
		placeSetting(checkedFiltersFirstButton, 4);
		placeSetting(adjustRowsToFitViewButton, 5);
		placeSetting(closeOnFoundButton, 6);
		placeSetting(chatOverlayButton, 7);
		placeSetting(showTechnicalNamesButton, 8);
		placeSetting(pauseGameWhenOpenButton, 9);
		placeSetting(hiddenStructuresButton, 10);
	}

	private void placeSetting(ButtonWidget button, int row) {
		button.setY(settingsRowY(row));
		button.visible = settingsOpen && row >= settingsFirstRow && row < settingsFirstRow + settingsVisibleRows();
	}

}

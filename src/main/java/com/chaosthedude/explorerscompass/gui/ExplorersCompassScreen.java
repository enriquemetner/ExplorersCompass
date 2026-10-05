package com.chaosthedude.explorerscompass.gui;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
import com.chaosthedude.explorerscompass.network.SearchForNextPacket;
import com.chaosthedude.explorerscompass.network.SearchPacket;
import com.chaosthedude.explorerscompass.network.SearchListedPacket;
import com.chaosthedude.explorerscompass.network.CancelSearchPacket;
import com.chaosthedude.explorerscompass.network.SearchResultPacket;
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
	private static final int SORT_MENU_WIDTH = 110;
	private static final int SORT_ROW_HEIGHT = 20;
	private static final int SEARCH_FIELD_WIDTH = 210;
	private static final int SEARCH_TELEPORT_GAP = 10;
	private static final int PREFERRED_FILTER_ROW_HEIGHT = 20;
	private static final int FILTER_HEADER_HEIGHT = 20;
	private static final int FILTER_HEADER_GAP = 1;
	private static final int FILTER_COLUMN_GAP = 1;
	private static final int FILTER_SECTION_GAP = 1;
	private static final int FILTER_CHECKBOX_SIZE = 9;
	private static final int BUTTON_CROSS_SIZE = 7;
	private static final int SCROLLBAR_WIDTH = 6;
	private static final int FILTER_SCROLLBAR_GRAB_PADDING = 2;
	private static final int SETTINGS_ROW_HEIGHT = 30;
	private static final int SETTINGS_CONTROL_WIDTH = 90;
	private static final int HIDE_BUTTON_WIDTH = 100;
	private static final String[] SETTINGS_LABEL_KEYS = {
		"string.explorerscompass.overlaySide",
		"string.explorerscompass.overlayLineOffset",
		"string.explorerscompass.displayWithChatOpen",
		"string.explorerscompass.clearFiltersOnClose",
		"string.explorerscompass.checkedFiltersFirst",
		"string.explorerscompass.adjustRowsToFitView",
        "string.explorerscompass.closeOnFound",
		"string.explorerscompass.translateStructureNames",
        "string.explorerscompass.hiddenStructures"
	};

	private World world;
	private PlayerEntity player;
	private List<Identifier> allowedStructureIDs;
	private List<Identifier> structureIDsMatchingSearch;
	private Identifier foundStructureId;
	private ItemStack stack;
	private ExplorersCompassItem explorersCompass;
	private ButtonWidget searchButton;
	private ButtonWidget searchForNextButton;
	private ButtonWidget searchListedButton;
	private ButtonWidget sortOrderButton;
	private ButtonWidget filtersButton;
	private ButtonWidget settingsButton;
	private ButtonWidget chatOverlayButton;
	private ButtonWidget translateNamesButton;
	private ButtonWidget overlaySideButton;
	private ButtonWidget clearOnCloseButton;
	private ButtonWidget closeOnFoundButton;
	private ButtonWidget checkedFiltersFirstButton;
	private ButtonWidget adjustRowsToFitViewButton;
	private ButtonWidget offsetDecreaseButton;
	private ButtonWidget offsetIncreaseButton;
	private ButtonWidget clearFiltersButton;
	private ButtonWidget clearFiltersTopButton;
	private ButtonWidget backButton;
	private ButtonWidget teleportButton;
	private ButtonWidget cancelButton;
	private TransparentTextField searchTextField;
	private TransparentTextField filterSearchTextField;
	private StructureSearchList selectionList;
	private boolean filtersOpen;
	private boolean settingsOpen;
    private boolean hiddenStructuresOpen;
    private int hiddenScroll;
    private ButtonWidget hiddenStructuresButton;
    private enum Dialog { NONE, WRONG_DIMENSION, SEARCHING, FOUND, NOT_FOUND, REJECTED }
    private Dialog dialog = Dialog.NONE;
    private long dialogStarted;
    private long notFoundCompassUpdated;
    private double notFoundCompassAngle;
    private double notFoundCompassSpeed;
    private int activeRequestId;
    private int failedRadius;
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
	private boolean draggingFilterScrollbar;
	private double filterScrollbarGrabOffset;
	private final List<List<FilterOption>> filterOptions = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
	private boolean showSourceColumn = true;
	private final List<SortCriterion> sortOrder = new ArrayList<SortCriterion>();
	private boolean sortMenuOpen;
	private int draggedSortRow = -1;
	private boolean sortOrderChangedDuringDrag;

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
		super(Text.translatable("string.explorerscompass.selectStructure"));
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
        if (dialog == Dialog.SEARCHING && !successfulClose && client.getNetworkHandler() != null) {
            ClientPlayNetworking.send(CancelSearchPacket.ID, new CancelSearchPacket(activeRequestId));
        }
		if (ExplorersCompassConfig.clearFiltersOnClose) {
			FilterSelections.clear();
		}
		super.removed();
	}

	@Override
	public boolean mouseScrolled(double scroll1, double scroll2, double scroll3) {
        if (dialog != Dialog.NONE) return true;
        if (hiddenStructuresOpen) {
            hiddenScroll = MathHelper.clamp(hiddenScroll - (int)Math.signum(scroll3) * 2, 0, Math.max(0, ExplorersCompassConfig.hiddenStructures.size() - hiddenVisibleRows()));
            return true;
        }
		if (settingsOpen) {
			return super.mouseScrolled(scroll1, scroll2, scroll3);
		}
		if (filtersOpen) {
			if (scroll1 >= filterLeft() && scroll1 < filterScrollbarRight() && scroll2 >= filterTop() && scroll2 < filterBottom()) {
				int rowHeight = filterRowHeight();
				double target = filterScroll - scroll3 * rowHeight;
				if (scroll3 != 0 && Math.rint(scroll3) == scroll3) {
					double row = filterScroll / rowHeight - scroll3;
					target = (scroll3 < 0 ? Math.floor(row) : Math.ceil(row)) * rowHeight;
				}
				filterScroll = MathHelper.clamp(target, 0, filterMaxScroll());
				return true;
			}
			return super.mouseScrolled(scroll1, scroll2, scroll3);
		}
		if (sortMenuOpen && sortRowAt(scroll1, scroll2) >= 0) {
			return true;
		}
		return selectionList.mouseScrolled(scroll1, scroll2, scroll3);
	}

	@Override
	protected void init() {
		clearChildren();
		setupButtons();
		selectionList = new StructureSearchList(this, client, player, foundStructureId, 130, 40, width - 130 - SCROLLBAR_WIDTH, height - 50, 50);
		if (isStructureView()) {
			addDrawableChild(selectionList);
		}
		setupTextFields();
		rebuildFilterOptions();
		processSearchTerm();
	}

	@Override
	public void tick() {
        if (dialog == Dialog.WRONG_DIMENSION && System.currentTimeMillis() - dialogStarted >= 3000) dialog = Dialog.NONE;
		searchTextField.tick();
		filterSearchTextField.tick();

		boolean hasSelection = selectionList.hasSelection();
		boolean selectionMatchesFound = hasSelection && foundStructureId != null && selectionList.getSelectedOrNull().getStructureID().equals(foundStructureId);

		searchButton.active = isStructureView() && hasSelection;
		searchListedButton.active = isStructureView() && !structureIDsMatchingSearch.isEmpty();
		ItemStack heldCompass = client.player == null ? ItemStack.EMPTY : ItemUtils.getHeldItem(client.player, ExplorersCompass.EXPLORERS_COMPASS_ITEM);
		searchForNextButton.active = isStructureView() && selectionMatchesFound && !(heldCompass.hasNbt() && heldCompass.getNbt().getBoolean("IsListed"));
		teleportButton.active = isStructureView() && selectionMatchesFound;
		sortOrderButton.active = isStructureView();

		// Check if the allowed structure list has synced
		if (ExplorersCompass.synced) {
			remove(selectionList);
			allowedStructureIDs = new ArrayList<Identifier>(ExplorersCompass.allowedStructureIDs);
			structureIDsMatchingSearch = new ArrayList<Identifier>(allowedStructureIDs);
			selectionList = new StructureSearchList(this, client, player, foundStructureId, 130, 40, width - 130 - SCROLLBAR_WIDTH, height - 50, 50);
			if (isStructureView()) {
				addDrawableChild(selectionList);
			}

			teleportButton.visible = isStructureView() && ExplorersCompass.canTeleport;
			searchForNextButton.visible = ExplorersCompass.maxNextSearches > 0;
			if (searchForNextButton.visible) {
				sortOrderButton.setX(10);
				sortOrderButton.setY(115);
			} else {
				sortOrderButton.setX(10);
				sortOrderButton.setY(90);
			}
			positionFilterButtons();
			positionTopButtons();
			updateViewButtons();
			rebuildFilterOptions();
			processSearchTerm();

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
		if (searchListedButton.visible) {
			int iconX = searchListedButton.getX() + searchListedButton.getWidth() - 12;
			int iconY = searchListedButton.getY() + searchListedButton.getHeight() / 2;
			drawQuestionIcon(context, iconX, iconY);
			if (insideQuestionIcon(mouseX, mouseY, iconX, iconY)) drawQuestionTooltip(context, "string.explorerscompass.searchListed.tooltip", mouseX, mouseY);
		}
		renderCrossIcon(context, cancelButton);
		if (clearFiltersButton.visible) {
			renderCrossIcon(context, clearFiltersButton);
		}
		if (filtersOpen) {
			renderFilterTooltip(context, mouseX, mouseY);
		}
		if (sortMenuOpen) {
			context.getMatrices().push();
			context.getMatrices().translate(0, 0, 200);
			renderSortMenu(context, mouseX, mouseY);
			context.getMatrices().pop();
		}
		if (filtersOpen) {
			context.drawTextWithShadow(textRenderer, Text.translatable("string.explorerscompass.filters"), 10, 15, 0xffffff);
        } else if (hiddenStructuresOpen) {
            String breadcrumb = Text.translatable("string.explorerscompass.hiddenBreadcrumb").getString();
            context.drawTextWithShadow(textRenderer, breadcrumb, 10, 15, 0xffffff);
		} else if (settingsOpen) {
			context.drawTextWithShadow(textRenderer, Text.translatable("string.explorerscompass.settings"), 10, 15, 0xffffff);
		} else {
			context.drawCenteredTextWithShadow(textRenderer, title, 65, 15, 0xffffff);
		}
		if (settingsOpen) {
			renderSettingsTooltip(context, mouseX, mouseY);
		}
        if (dialog != Dialog.NONE) renderSearchDialog(context, mouseX, mouseY);
	}

	private int sortMenuWidth() {
		int desiredWidth = SORT_MENU_WIDTH;
		for (int i = 0; i < sortOrder.size(); i++) {
			desiredWidth = Math.max(desiredWidth, textRenderer.getWidth((i + 1) + ". " + sortOrder.get(i).getLabel().getString()) + 28);
		}
		return Math.min(desiredWidth, Math.max(SORT_MENU_WIDTH, width - sortOrderButton.getX() - sortOrderButton.getWidth() - 10));
	}

	private int sortMenuX() {
		return MathHelper.clamp(sortOrderButton.getX() + sortOrderButton.getWidth() + 5, 0, Math.max(0, width - sortMenuWidth() - 5));
	}

	private int sortMenuY() {
		return sortOrderButton.getY();
	}

	private void closeSortMenu() {
		commitSortOrder();
		sortMenuOpen = false;
		draggedSortRow = -1;
	}

	private void commitSortOrder() {
		if (sortOrderChangedDuringDrag) {
			ExplorersCompassConfig.sortOrder = sortOrder.stream().map(Enum::name).collect(Collectors.toList());
			ExplorersCompassConfig.save();
			selectionList.refreshList(true);
			sortOrderChangedDuringDrag = false;
		}
	}

	private int sortRowAt(double mouseX, double mouseY) {
		int x = sortMenuX();
		int y = sortMenuY();
		if (mouseX < x || mouseX >= x + sortMenuWidth() || mouseY < y || mouseY >= y + sortOrder.size() * SORT_ROW_HEIGHT) {
			return -1;
		}
		return (int) (mouseY - y) / SORT_ROW_HEIGHT;
	}

	private void renderSortMenu(DrawContext context, int mouseX, int mouseY) {
		int x = sortMenuX();
		int y = sortMenuY();
		int menuWidth = sortMenuWidth();
		context.fill(x - 1, y - 1, x + menuWidth + 1, y + sortOrder.size() * SORT_ROW_HEIGHT + 1, 0xff909090);
		context.fill(x, y, x + menuWidth, y + sortOrder.size() * SORT_ROW_HEIGHT, 0xff000000);
		int hoveredRow = sortRowAt(mouseX, mouseY);
		for (int i = 0; i < sortOrder.size(); i++) {
			int rowY = y + i * SORT_ROW_HEIGHT;
			int background = i == draggedSortRow || i == hoveredRow ? 0xff000000 : 0xff242424;
			int rowBottom = rowY + SORT_ROW_HEIGHT - (i == sortOrder.size() - 1 ? 1 : 0);
			context.fill(x + 1, rowY + 1, x + menuWidth - 1, rowBottom, background);
			String label = (i + 1) + ". " + sortOrder.get(i).getLabel().getString();
			context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(label, menuWidth - 28), x + 5, rowY + 6, 0xffffff);
			renderSortArrows(context, x + menuWidth - 11, rowY + 6);
		}
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
		return 40;
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

	private int filterThumbHeight() {
		int viewport = filterBottom() - filterTop();
		return MathHelper.clamp(viewport * viewport / (viewport + filterMaxScroll()), 32, viewport - 8);
	}

	private int filterThumbTop() {
		return filterTop() + (int) (filterScroll * (filterBottom() - filterTop() - filterThumbHeight()) / filterMaxScroll());
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
		Collator collator = languageCollator();
		Comparator<FilterOption> alphabetical = Comparator.comparing((FilterOption option) -> option.label, collator)
				.thenComparing(option -> option.source != null ? option.source : option.id.toString());
		for (List<FilterOption> options : filterOptions) {
			options.sort(alphabetical);
		}
		filterOptions.get(0).sort(Comparator.comparing((FilterOption option) -> !"minecraft".equals(option.source)).thenComparing(alphabetical));
		filterOptions.get(1).add(0, new FilterOption(Text.translatable("string.explorerscompass.current").getString(), null, world.getRegistryKey().getValue(), true));
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
		context.getMatrices().push();
		context.getMatrices().translate(
				button.getX() + (button.getWidth() - BUTTON_CROSS_SIZE) / 2.0,
				button.getY() + (button.getHeight() - BUTTON_CROSS_SIZE) / 2.0,
				0.0);
		for (int pixel = 0; pixel < BUTTON_CROSS_SIZE; pixel++) {
			context.fill(pixel, pixel, pixel + 1, pixel + 1, 0xffffffff);
			context.fill(BUTTON_CROSS_SIZE - 1 - pixel, pixel, BUTTON_CROSS_SIZE - pixel, pixel + 1, 0xffffffff);
		}
		context.getMatrices().pop();
	}

	private int settingsRowY(int row) {
		return filterTop() + 10 + row * settingsRowHeight();
	}

	private int settingsRowHeight() {
		return Math.min(SETTINGS_ROW_HEIGHT, Math.max(10, (filterBottom() - filterTop() - 10) / SETTINGS_LABEL_KEYS.length));
	}

	private int settingsControlHeight() {
		return Math.min(20, settingsRowHeight());
	}

	private int settingsControlX() {
		return filterRight() - SETTINGS_CONTROL_WIDTH - 10;
	}

	private void renderSettingsPanel(DrawContext context) {
		context.fill(filterLeft(), filterTop(), filterRight(), filterBottom(), 0xb0000000);
		int labelX = filterLeft() + 10;
		int labelWidth = Math.max(1, settingsControlX() - labelX - 10);
		for (int row = 0; row < SETTINGS_LABEL_KEYS.length; row++) {
			String label = Text.translatable(SETTINGS_LABEL_KEYS[row]).getString();
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
		context.getMatrices().push();
		context.getMatrices().translate(settingsControlX() + SETTINGS_CONTROL_WIDTH / 2.0,
				settingsRowY(1) + (settingsControlHeight() - textRenderer.fontHeight * offsetScale) / 2.0, 0);
		context.getMatrices().scale(offsetScale, offsetScale, 1.0f);
		context.drawCenteredTextWithShadow(textRenderer, offset, 0, 0, 0xffffff);
		context.getMatrices().pop();
	}

	private void renderSettingsTooltip(DrawContext context, int mouseX, int mouseY) {
		if (mouseY < settingsRowY(0)) {
			return;
		}
		int row = (mouseY - settingsRowY(0)) / settingsRowHeight();
		if (row >= SETTINGS_LABEL_KEYS.length) {
			return;
		}
		int labelX = filterLeft() + 10;
		int labelWidth = Math.max(1, settingsControlX() - labelX - 10);
		String label = Text.translatable(SETTINGS_LABEL_KEYS[row]).getString();
		int textWidth = textRenderer.getWidth(label);
		float scale = Math.min(1.0f, (float) labelWidth / Math.max(1, textWidth));
		double textTop = settingsRowY(row) + (settingsControlHeight() - textRenderer.fontHeight) / 2.0f
				+ textRenderer.fontHeight * (1.0f - scale) / 2.0f;
		if (mouseX < labelX || mouseX >= labelX + textWidth * scale
				|| mouseY < textTop || mouseY >= textTop + textRenderer.fontHeight * scale) {
			return;
		}
		List<Text> lines = new ArrayList<>();
		for (OrderedText line : textRenderer.wrapLines(Text.translatable(SETTINGS_LABEL_KEYS[row] + ".tooltip"), Math.min(240, width - 30))) {
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
		chatOverlayButton.setMessage(Text.translatable(ExplorersCompassConfig.displayWithChatOpen ? "options.on" : "options.off"));
		translateNamesButton.setMessage(Text.translatable(ExplorersCompassConfig.translateStructureNames ? "options.on" : "options.off"));
		clearOnCloseButton.setMessage(Text.translatable(ExplorersCompassConfig.clearFiltersOnClose ? "options.on" : "options.off"));
		checkedFiltersFirstButton.setMessage(Text.translatable(ExplorersCompassConfig.checkedFiltersFirst ? "options.on" : "options.off"));
		closeOnFoundButton.setMessage(Text.translatable(ExplorersCompassConfig.closeOnFound ? "options.on" : "options.off"));
		adjustRowsToFitViewButton.setMessage(Text.translatable(ExplorersCompassConfig.adjustRowsToFitView ? "options.on" : "options.off"));
		overlaySideButton.setMessage(Text.translatable(ExplorersCompassConfig.overlaySide == OverlaySide.LEFT
				? "string.explorerscompass.left" : "string.explorerscompass.right"));
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
			Text.translatable("string.explorerscompass.source").getString(),
			Text.translatable("string.explorerscompass.dimension").getString(),
			Text.translatable("string.explorerscompass.group").getString()
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
		if (filterMaxScroll() > 0) {
			int thumbTop = filterThumbTop();
			context.fill(right, filterTop(), filterScrollbarRight(), bottom, RenderUtils.getBackgroundColor(false, false));
			RenderUtils.drawScrollbarThumb(context, right, thumbTop, SCROLLBAR_WIDTH, filterThumbHeight(), RenderUtils.getBackgroundColor(true, true));
		}
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
        if (dialog != Dialog.NONE) {
            if (dialog == Dialog.WRONG_DIMENSION) { dismissDialog(); return true; }
            if (button == 0) {
                int x = dialogX(), y = dialogY(), w = dialogWidth();
                if ((dialog == Dialog.FOUND || dialog == Dialog.NOT_FOUND)
                        && (mouseX < x - 1 || mouseX >= x + w + 1 || mouseY < y - 1 || mouseY >= y + dialogHeight() + 1)) {
                    dismissDialog();
                    return true;
                }
                if (dialog == Dialog.SEARCHING && mouseX >= x + (w - 70) / 2 && mouseX < x + (w + 70) / 2 && mouseY >= y + searchingBackY() && mouseY < y + searchingBackY() + 20) dismissDialog();
                else if (dialog == Dialog.FOUND && ExplorersCompass.canTeleport && mouseX >= x + (w - 100) / 2 && mouseX < x + (w + 100) / 2 && mouseY >= y + 94 && mouseY < y + 114) teleport();
                else if (dialog == Dialog.FOUND && mouseX >= x + (w - 70) / 2 && mouseX < x + (w + 70) / 2 && mouseY >= y + (ExplorersCompass.canTeleport ? 117 : 94) && mouseY < y + (ExplorersCompass.canTeleport ? 137 : 114)) dismissDialog();
                else if (dialog == Dialog.NOT_FOUND && mouseX >= x + (w - HIDE_BUTTON_WIDTH) / 2 && mouseX < x + (w + HIDE_BUTTON_WIDTH) / 2 && mouseY >= y + notFoundHideY() && mouseY < y + notFoundHideY() + 20) hideFailedSearch();
                else if ((dialog == Dialog.NOT_FOUND || dialog == Dialog.REJECTED) && mouseX >= x + (w - 70) / 2 && mouseX < x + (w + 70) / 2 && mouseY >= y + (dialog == Dialog.NOT_FOUND ? notFoundBackY() : 57) && mouseY < y + (dialog == Dialog.NOT_FOUND ? notFoundBackY() : 57) + 20) dismissDialog();
            }
            return true;
        }
        if (hiddenStructuresOpen) {
            if (button == 0 && mouseX >= width - 95 && mouseX < width - 17 && mouseY >= filterTop() + 6) {
                int row = (int)(mouseY - filterTop() - 6) / 25;
                int index = hiddenScroll + row;
                if (row >= 0 && row < hiddenVisibleRows() && index < ExplorersCompassConfig.hiddenStructures.size()) {
                    ExplorersCompassConfig.hiddenStructures.remove(index);
                    ExplorersCompassConfig.save();
                    hiddenScroll = MathHelper.clamp(hiddenScroll, 0, Math.max(0, ExplorersCompassConfig.hiddenStructures.size() - hiddenVisibleRows()));
                    processSearchTerm();
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
		if (filtersOpen && button == 0) {
			if (filterMaxScroll() > 0 && mouseX >= filterRight() && mouseX < filterScrollbarRight()
					&& mouseY >= filterTop() && mouseY < filterBottom()) {
				int thumbTop = filterThumbTop();
				int thumbHeight = filterThumbHeight();
				int thumbBottom = thumbTop + thumbHeight;
				if (mouseY < thumbTop - FILTER_SCROLLBAR_GRAB_PADDING || mouseY >= thumbBottom + FILTER_SCROLLBAR_GRAB_PADDING) {
					filterScrollbarGrabOffset = thumbHeight / 2.0;
					moveFilterScrollbar(mouseY);
				} else {
					filterScrollbarGrabOffset = MathHelper.clamp(mouseY - thumbTop, 0, thumbHeight);
				}
				draggingFilterScrollbar = true;
				return true;
			}
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
		if (sortMenuOpen) {
			int row = sortRowAt(mouseX, mouseY);
			if (row >= 0) {
				if (button == 0) {
					draggedSortRow = row;
				}
				return true;
			}
			if (mouseX < sortOrderButton.getX() || mouseX >= sortOrderButton.getX() + sortOrderButton.getWidth()
					|| mouseY < sortOrderButton.getY() || mouseY >= sortOrderButton.getY() + sortOrderButton.getHeight()) {
				closeSortMenu();
				return true;
			}
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (draggingFilterScrollbar && button == 0) {
			moveFilterScrollbar(mouseY);
			return true;
		}
		if (draggedSortRow >= 0 && button == 0) {
			if (mouseX >= sortMenuX() && mouseX < sortMenuX() + sortMenuWidth()) {
				int targetRow = MathHelper.clamp((int) Math.floor((mouseY - sortMenuY()) / SORT_ROW_HEIGHT), 0, sortOrder.size() - 1);
				if (targetRow != draggedSortRow) {
					SortCriterion criterion = sortOrder.remove(draggedSortRow);
					sortOrder.add(targetRow, criterion);
					draggedSortRow = targetRow;
					sortOrderChangedDuringDrag = true;
				}
			}
			return true;
		}
		return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
	}

	@Override
	public boolean mouseReleased(double mouseX, double mouseY, int button) {
		if (draggingFilterScrollbar && button == 0) {
			draggingFilterScrollbar = false;
			return true;
		}
		if (draggedSortRow >= 0 && button == 0) {
			commitSortOrder();
			draggedSortRow = -1;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int par1, int par2, int par3) {
        if (dialog != Dialog.NONE) {
            if (par1 == 256) dismissDialog();
            return true;
        }
        if (hiddenStructuresOpen && par1 == 256) {
            hiddenStructuresOpen = false;
            settingsOpen = true;
            updateViewButtons();
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
		if (sortMenuOpen && par1 == 256) {
			closeSortMenu();
			return true;
		}
		boolean ret = super.keyPressed(par1, par2, par3);
		if (searchTextField.isFocused()) {
			processSearchTerm();
			return true;
		}
		return ret;
	}

	@Override
	public boolean charTyped(char typedChar, int keyCode) {
        if (dialog != Dialog.NONE || hiddenStructuresOpen) return true;
		boolean ret = super.charTyped(typedChar, keyCode);
		if (searchTextField.isFocused()) {
			processSearchTerm();
			return true;
		}
		return ret;
	}

	private void moveFilterScrollbar(double mouseY) {
		int maxScroll = filterMaxScroll();
		if (maxScroll <= 0) {
			return;
		}
		int travel = filterBottom() - filterTop() - filterThumbHeight();
		filterScroll = MathHelper.clamp((mouseY - filterTop() - filterScrollbarGrabOffset) * maxScroll / travel, 0, maxScroll);
	}

	private void setFiltersOpen(boolean open) {
		if (filtersOpen == open) {
			return;
		}
		if (sortMenuOpen) {
			closeSortMenu();
		}
		filtersOpen = open;
		draggingFilterScrollbar = false;
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
	}

	private void setSettingsOpen(boolean open) {
		if (settingsOpen == open) {
			return;
		}
		if (sortMenuOpen) {
			closeSortMenu();
		}
		settingsOpen = open;
		searchTextField.setVisible(isStructureView());
		searchTextField.setFocused(false);
		if (open) {
			remove(selectionList);
		} else {
			addDrawableChild(selectionList);
			processSearchTerm();
		}
		updateViewButtons();
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
        if (!validDimension(id, group)) {
            dialog = Dialog.WRONG_DIMENSION;
            dialogStarted = System.currentTimeMillis();
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
        if (beginSearch(structureID, false)) ClientPlayNetworking.send(SearchPacket.ID, new SearchPacket(structureID, false, activeRequestId));
    }

    private void searchListed() {
        if (dialog != Dialog.NONE || structureIDsMatchingSearch.isEmpty()) return;
        List<Identifier> selected = List.copyOf(structureIDsMatchingSearch);
        List<Identifier> eligible = new ArrayList<>();
        for (Identifier id : selected) if (validDimension(id, false)) eligible.add(id);
        if (eligible.isEmpty()) {
            dialog = Dialog.WRONG_DIMENSION;
            dialogStarted = System.currentTimeMillis();
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
        ClientPlayNetworking.send(SearchListedPacket.ID, new SearchListedPacket(eligible, activeRequestId));
    }

    public void searchForNext() {
        if (foundStructureId == null) return;
        ItemStack heldCompass = client.player == null ? ItemStack.EMPTY : ItemUtils.getHeldItem(client.player, ExplorersCompass.EXPLORERS_COMPASS_ITEM);
        if (heldCompass.hasNbt() && heldCompass.getNbt().getBoolean("IsListed")) return;
        boolean group = heldCompass.hasNbt() && heldCompass.getNbt().getBoolean("IsGroup");
        Identifier id = group ? ExplorersCompass.structureIDsToGroupIDs.get(foundStructureId) : foundStructureId;
        if (beginSearch(id, group)) ClientPlayNetworking.send(SearchForNextPacket.ID, new SearchForNextPacket(activeRequestId));
    }

    public void onSearchResult(int requestId, int result, int radius, Identifier resultId, int foundX, int foundZ, boolean displayCoordinates) {
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
                selectionList.refreshList(resultId);
                dialog = Dialog.FOUND;
            }
        } else if (result == SearchResultPacket.NOT_FOUND) {
            failedRadius = radius;
            notFoundCompassUpdated = System.nanoTime();
            notFoundCompassAngle = 0.0D;
            notFoundCompassSpeed = 0.0D;
            dialog = Dialog.NOT_FOUND;
        } else if (result == SearchResultPacket.WRONG_DIMENSION) {
            dialog = Dialog.WRONG_DIMENSION;
            dialogStarted = System.currentTimeMillis();
        } else {
            dialog = Dialog.REJECTED;
        }
    }

    private void dismissDialog() {
        if (dialog == Dialog.SEARCHING && client.getNetworkHandler() != null) {
            ClientPlayNetworking.send(CancelSearchPacket.ID, new CancelSearchPacket(activeRequestId));
        }
        dialog = Dialog.NONE;
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
        ExplorersCompassConfig.save();
        dismissDialog();
        processSearchTerm();
    }

    private int dialogWidth() {
        if (dialog == Dialog.WRONG_DIMENSION) {
            return Math.min(width - 20, textRenderer.getWidth(Text.translatable("string.explorerscompass.wrongDimension")) + 12);
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
        if (dialog == Dialog.NOT_FOUND) return Math.min(width - 20, Math.max(245, textRenderer.getWidth(Text.translatable("string.explorerscompass.notFoundWithin", failedRadius)) + 16));
        return Math.min(width - 20, 245);
    }
    private int searchingBackY() { return searchedListed ? (listedSkippedCount > 0 ? 89 : 77) : 65; }
    private int notFoundHideY() { return searchedListed ? (listedSkippedCount > 0 ? 78 : 66) : 52; }
    private int notFoundBackY() { return notFoundHideY() + 23; }
    private Text wrongDimensionCountText() { return Text.translatable(listedSkippedCount == 1 ? "string.explorerscompass.wrongDimensionCountOne" : "string.explorerscompass.wrongDimensionCount", listedSkippedCount); }
    private int dialogHeight() { return dialog == Dialog.WRONG_DIMENSION ? textRenderer.fontHeight + 12 : dialog == Dialog.SEARCHING ? searchingBackY() + 25 : dialog == Dialog.FOUND ? (ExplorersCompass.canTeleport ? 142 : 119) : dialog == Dialog.NOT_FOUND ? notFoundBackY() + 25 : 80; }
    private int dialogX() { return (width - dialogWidth()) / 2; }
    private int dialogY() { return (height - dialogHeight()) / 2; }

    private Text[] foundInfoLines() {
        Identifier groupId = foundStructureId == null ? null : ExplorersCompass.structureIDsToGroupIDs.get(foundStructureId);
        return new Text[] {
            Text.translatable("string.explorerscompass.name").append(": ").append(Text.literal(StructureUtils.getStructureName(foundStructureId)).formatted(Formatting.GRAY)),
            Text.translatable("string.explorerscompass.source").append(": ").append(Text.literal(StructureUtils.getStructureSource(foundStructureId)).formatted(Formatting.GRAY)),
            Text.translatable("string.explorerscompass.group").append(": ").append(Text.literal(StructureUtils.getStructureName(groupId)).formatted(Formatting.GRAY))
        };
    }

    private Text[] foundLocationLines() {
        int distance = client.player == null ? 0 : StructureUtils.getHorizontalDistanceToLocation(client.player, foundX, foundZ);
        return new Text[] {
            Text.translatable("string.explorerscompass.distance").append(": ").append(Text.literal(Integer.toString(distance)).formatted(Formatting.GRAY)),
            Text.translatable("string.explorerscompass.coordinates").append(":"),
            Text.literal(foundX + ", " + foundZ).formatted(Formatting.GRAY)
        };
    }

    private void drawDialogButton(DrawContext context, String label, int x, int y, int w, int mouseX, int mouseY) {
        context.fill(x, y, x + w, y + 20, 0xff909090);
        context.fill(x + 1, y + 1, x + w - 1, y + 19, mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 20 ? 0xff424242 : 0xff242424);
        context.drawCenteredTextWithShadow(textRenderer, label, x + w / 2, y + 6, 0xffffff);
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

    private void drawQuestionTooltip(DrawContext context, String key, int mouseX, int mouseY) {
        List<Text> lines = new ArrayList<>();
        for (OrderedText line : textRenderer.wrapLines(Text.translatable(key), Math.min(220, width - 30))) {
            StringBuilder content = new StringBuilder();
            line.accept((index, style, codePoint) -> { content.appendCodePoint(codePoint); return true; });
            lines.add(Text.literal(content.toString()));
        }
        context.drawTooltip(textRenderer, lines, mouseX, mouseY);
    }

    private void renderSearchDialog(DrawContext context, int mouseX, int mouseY) {
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
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("string.explorerscompass.searchingDialog"), x + w / 2, y + 32, 0xffffff);
            if (searchedListed) {
                context.drawCenteredTextWithShadow(textRenderer, Text.translatable("string.explorerscompass.structuresSelected", listedSelectedCount), x + w / 2, y + 47, 0xffffff);
                if (listedSkippedCount > 0) context.drawCenteredTextWithShadow(textRenderer, wrongDimensionCountText(), x + w / 2, y + 59, 0xffffff);
            }
            ItemStack heldCompass = client.player == null ? ItemStack.EMPTY : ItemUtils.getHeldItem(client.player, ExplorersCompass.EXPLORERS_COMPASS_ITEM);
            int distance = heldCompass.getItem() instanceof ExplorersCompassItem
                ? ((ExplorersCompassItem) heldCompass.getItem()).getSearchRadius(heldCompass) : 0;
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("string.explorerscompass.distance").getString() + ": " + distance, x + w / 2, y + (searchedListed ? (listedSkippedCount > 0 ? 74 : 59) : 47), 0xffffff);
            drawDialogButton(context, Text.translatable("string.explorerscompass.back").getString(), x + (w - 70) / 2, y + searchingBackY(), 70, mouseX, mouseY);
        } else if (dialog == Dialog.FOUND) {
            double yaw = client.player == null ? 0.0D : client.player.getYaw() % 360.0D;
            double targetAngle = client.player == null ? 0.0D : Math.atan2(foundZ - client.player.getZ(), foundX - client.player.getX());
            double angle = MathHelper.floorMod((Math.PI - ((yaw - 90.0D) * (Math.PI / 180.0D) - targetAngle)) / (Math.PI * 2.0D), 1.0D);
            int frame = (16 + (int)Math.floor(angle * 32.0D + 0.5D)) % 32;
            Identifier texture = new Identifier(ExplorersCompass.MODID, String.format(Locale.ROOT, "textures/item/explorerscompass_%02d.png", frame));
            context.drawTexture(texture, x + (w - 16) / 2, y + 10, 16, 16, 0, 0, 16, 16, 16, 16);
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("string.explorerscompass.structureFound"), x + w / 2, y + 32, 0xffffff);
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
                drawDialogButton(context, Text.translatable("string.explorerscompass.teleport").getString(), x + (w - 100) / 2, y + 94, 100, mouseX, mouseY);
            }
            drawDialogButton(context, Text.translatable("string.explorerscompass.back").getString(), x + (w - 70) / 2, y + (ExplorersCompass.canTeleport ? 117 : 94), 70, mouseX, mouseY);
        } else {
            Text message = dialog == Dialog.WRONG_DIMENSION ? Text.translatable("string.explorerscompass.wrongDimension")
                : dialog == Dialog.NOT_FOUND ? Text.translatable("string.explorerscompass.notFoundWithin", failedRadius)
                : Text.translatable("string.explorerscompass.searchRejected");
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
                    context.drawCenteredTextWithShadow(textRenderer, Text.translatable("string.explorerscompass.structuresSelected", listedSelectedCount), x + w / 2, y + 47, 0xffffff);
                    if (listedSkippedCount > 0) context.drawCenteredTextWithShadow(textRenderer, wrongDimensionCountText(), x + w / 2, y + 59, 0xffffff);
                }
                int hideX = x + (w - HIDE_BUTTON_WIDTH) / 2;
                int iconX = hideX + HIDE_BUTTON_WIDTH - 12;
                int iconY = y + notFoundHideY() + 10;
                drawDialogButton(context, Text.translatable(searchedListed ? "string.explorerscompass.hideAll" : "string.explorerscompass.hide").getString(), hideX, y + notFoundHideY(), HIDE_BUTTON_WIDTH, mouseX, mouseY);
                drawQuestionIcon(context, iconX, iconY);
                if (insideQuestionIcon(mouseX, mouseY, iconX, iconY)) drawQuestionTooltip(context, searchedListed ? "string.explorerscompass.hideAll.tooltip" : "string.explorerscompass.hide.tooltip", mouseX, mouseY);
            }
            if (dialog == Dialog.NOT_FOUND || dialog == Dialog.REJECTED) {
                drawDialogButton(context, Text.translatable("string.explorerscompass.back").getString(), x + (w - 70) / 2, y + (dialog == Dialog.NOT_FOUND ? notFoundBackY() : 57), 70, mouseX, mouseY);
            }
        }
        context.getMatrices().pop();
    }

    private int hiddenVisibleRows() { return Math.max(1, (filterBottom() - filterTop() - 12) / 25); }

    private void renderHiddenStructures(DrawContext context, int mouseX, int mouseY) {
        context.fill(filterLeft(), filterTop(), filterRight(), filterBottom(), 0xb0000000);
        if (ExplorersCompassConfig.hiddenStructures.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("string.explorerscompass.noHiddenStructures"), width / 2, filterTop() + 20, 0xffffff);
        }
        List<String> ids = ExplorersCompassConfig.hiddenStructures;
        for (int row = 0; row < hiddenVisibleRows() && hiddenScroll + row < ids.size(); row++) {
            int y = filterTop() + 6 + row * 25;
            String raw = ids.get(hiddenScroll + row);
            Identifier id = Identifier.tryParse(raw);
            String label = id == null || !allowedStructureIDs.contains(id) ? raw : StructureUtils.getStructureName(id) + " (" + raw + ")";
            context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(label, Math.max(1, width - 145)), 16, y + 6, 0xffffff);
            drawDialogButton(context, Text.translatable("string.explorerscompass.unhide").getString(), width - 95, y, 78, mouseX, mouseY);
        }
    }

	public void teleport() {
		ClientPlayNetworking.send(TeleportPacket.ID, new TeleportPacket());
		client.setScreen(null);
	}

	public void processSearchTerm() {
		structureIDsMatchingSearch = new ArrayList<Identifier>();
		String searchTerm = searchTextField.getText().toLowerCase(Locale.ROOT);
		for (Identifier id : allowedStructureIDs) {
			if (ExplorersCompassConfig.hiddenStructures.contains(id.toString()) || !FilterSelections.matches(id)) {
				continue;
			}
			if (searchTerm.startsWith("@")) {
				if (StructureUtils.getStructureSource(id).toLowerCase(Locale.ROOT).contains(searchTerm.substring(1))) {
					structureIDsMatchingSearch.add(id);
				}
			} else if (StructureUtils.getStructureName(id).toLowerCase(Locale.ROOT).contains(searchTerm)) {
				structureIDsMatchingSearch.add(id);
			}
		}
		selectionList.refreshList(true);
	}

	public List<Identifier> sortStructures() {
		final List<Identifier> structures = new ArrayList<Identifier>(structureIDsMatchingSearch);
		Collator collator = languageCollator();
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

	private Collator languageCollator() {
		String language = client.getLanguageManager().getLanguage();
		return Collator.getInstance(Locale.forLanguageTag(language.replace('_', '-')));
	}

	private void setupButtons() {
		searchButton = addDrawableChild(new TransparentButton(10, 40, 110, 20, Text.translatable("string.explorerscompass.search"), (onPress) -> {
			if (selectionList.hasSelection()) {
				selectionList.getSelectedOrNull().searchForStructure();
			}
		}));
		searchButton.active = false;

		searchListedButton = addDrawableChild(new TransparentButton(10, 65, 110, 20, Text.translatable("string.explorerscompass.searchListed"), (onPress) -> searchListed()));
		searchListedButton.active = false;

		searchForNextButton = addDrawableChild(new TransparentButton(10, 90, 110, 20, Text.translatable("string.explorerscompass.searchForNext"), (onPress) -> {
			searchForNext();
		}));
		searchForNextButton.visible = ExplorersCompass.maxNextSearches > 0;
		searchForNextButton.active = false;

		sortOrderButton = addDrawableChild(new TransparentButton(10, searchForNextButton.visible ? 115 : 90, 110, 20, Text.translatable("string.explorerscompass.sortOrder"), (onPress) -> {
			if (sortMenuOpen) {
				closeSortMenu();
			} else {
				sortMenuOpen = true;
			}
		}));
		filtersButton = addDrawableChild(new TransparentButton(10, sortOrderButton.getY() + 25, 85, 20, Text.translatable("string.explorerscompass.filters"), (onPress) -> {
			setFiltersOpen(!filtersOpen);
		}));
		clearFiltersButton = addDrawableChild(new TransparentButton(100, sortOrderButton.getY() + 25, 20, 20, Text.literal(""), (onPress) -> {
			FilterSelections.clear();
			processSearchTerm();
		}));
		settingsButton = addDrawableChild(new TransparentButton(10, height - 30, 110, 20, Text.translatable("string.explorerscompass.settings"), (onPress) -> {
			setSettingsOpen(true);
		}));
		int controlX = settingsControlX();
		overlaySideButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(0), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), (onPress) -> {
			ExplorersCompassConfig.overlaySide = ExplorersCompassConfig.overlaySide == OverlaySide.LEFT ? OverlaySide.RIGHT : OverlaySide.LEFT;
			ExplorersCompassConfig.save();
			updateSettingsButtonLabels();
		}));
		offsetDecreaseButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(1), 20, settingsControlHeight(), Text.literal("−"), (onPress) -> {
			if (ExplorersCompassConfig.overlayLineOffset > Integer.MIN_VALUE) {
				ExplorersCompassConfig.overlayLineOffset--;
				ExplorersCompassConfig.save();
			}
		}));
		offsetIncreaseButton = addDrawableChild(new TransparentButton(controlX + SETTINGS_CONTROL_WIDTH - 20, settingsRowY(1), 20, settingsControlHeight(), Text.literal("+"), (onPress) -> {
			if (ExplorersCompassConfig.overlayLineOffset < Integer.MAX_VALUE) {
				ExplorersCompassConfig.overlayLineOffset++;
				ExplorersCompassConfig.save();
			}
		}));
		chatOverlayButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(2), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), (onPress) -> {
			ExplorersCompassConfig.displayWithChatOpen = !ExplorersCompassConfig.displayWithChatOpen;
			ExplorersCompassConfig.save();
			updateSettingsButtonLabels();
		}));
		clearOnCloseButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(3), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), (onPress) -> {
			ExplorersCompassConfig.clearFiltersOnClose = !ExplorersCompassConfig.clearFiltersOnClose;
			ExplorersCompassConfig.save();
			updateSettingsButtonLabels();
		}));
		checkedFiltersFirstButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(4), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), (onPress) -> {
			ExplorersCompassConfig.checkedFiltersFirst = !ExplorersCompassConfig.checkedFiltersFirst;
			ExplorersCompassConfig.save();
			updateSettingsButtonLabels();
			filterScroll = MathHelper.clamp(filterScroll, 0, filterMaxScroll());
		}));
		translateNamesButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(7), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), (onPress) -> {
			ExplorersCompassConfig.translateStructureNames = !ExplorersCompassConfig.translateStructureNames;
			ExplorersCompassConfig.save();
			updateSettingsButtonLabels();
			rebuildFilterOptions();
			processSearchTerm();
		}));
		adjustRowsToFitViewButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(5), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), (onPress) -> {
			Identifier selectedId = selectionList.hasSelection() ? selectionList.getSelectedOrNull().getStructureID() : null;
			ExplorersCompassConfig.adjustRowsToFitView = !ExplorersCompassConfig.adjustRowsToFitView;
			ExplorersCompassConfig.save();
			remove(selectionList);
			selectionList = new StructureSearchList(this, client, player, selectedId, 130, 40, width - 130 - SCROLLBAR_WIDTH, height - 50, 50);
			filterScroll = MathHelper.clamp(filterScroll, 0, filterMaxScroll());
			updateSettingsButtonLabels();
		}));
		closeOnFoundButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(6), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.empty(), button -> {
			ExplorersCompassConfig.closeOnFound = !ExplorersCompassConfig.closeOnFound;
			ExplorersCompassConfig.save();
			updateSettingsButtonLabels();
		}));
		updateSettingsButtonLabels();
        hiddenStructuresButton = addDrawableChild(new TransparentButton(controlX, settingsRowY(8), SETTINGS_CONTROL_WIDTH, settingsControlHeight(), Text.translatable("string.explorerscompass.open"), button -> {
            settingsOpen = false;
            hiddenStructuresOpen = true;
            hiddenScroll = 0;
            updateViewButtons();
        }));

		cancelButton = addDrawableChild(new TransparentButton(width - 30, 10, 20, 20, Text.literal(""), (onPress) -> {
			client.setScreen(null);
		}));

		teleportButton = addDrawableChild(new TransparentButton(10, 10, 110, 20, Text.translatable("string.explorerscompass.teleport"), (onPress) -> {
			teleport();
		}));
		teleportButton.active = false;
		clearFiltersTopButton = addDrawableChild(new TransparentButton(10, 10, width >= 500 ? 110 : 85, 20, Text.translatable("string.explorerscompass.clearFilters"), (onPress) -> {
			FilterSelections.clear();
			processSearchTerm();
			filterScroll = MathHelper.clamp(filterScroll, 0, filterMaxScroll());
		}));
		backButton = addDrawableChild(new TransparentButton(10, 10, width >= 500 ? 110 : 50, 20, Text.translatable("string.explorerscompass.back"), (onPress) -> {
            if (hiddenStructuresOpen) {
                hiddenStructuresOpen = false;
                settingsOpen = true;
                updateViewButtons();
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
		searchTextField = new TransparentTextField(textRenderer, searchX, 10, Math.min(SEARCH_FIELD_WIDTH, availableWidth), 20, Text.translatable("string.explorerscompass.search"));
		addDrawableChild(searchTextField);
		searchTextField.setVisible(isStructureView());
		int filterSearchX = 20 + textRenderer.getWidth(Text.translatable("string.explorerscompass.filters"));
		filterSearchTextField = new TransparentTextField(textRenderer, filterSearchX, 10, Math.max(1, Math.min(SEARCH_FIELD_WIDTH, clearFiltersTopButton.getX() - filterSearchX - SEARCH_TELEPORT_GAP)), 20, Text.translatable("string.explorerscompass.searchFilters"));
		filterSearchTextField.setChangedListener(value -> {
			filterScroll = 0;
		});
		addDrawableChild(filterSearchTextField);
		filterSearchTextField.setVisible(filtersOpen);
	}

	private void positionFilterButtons() {
		filtersButton.setY(sortOrderButton.getY() + 25);
		clearFiltersButton.setY(sortOrderButton.getY() + 25);
	}

	private void positionTopButtons() {
		cancelButton.setX(width - 30);
		settingsButton.setY(height - 30);
        hiddenStructuresButton.setX(settingsControlX());
        hiddenStructuresButton.setY(settingsRowY(8));
		if (width >= 480) {
			teleportButton.setX(cancelButton.getX() - teleportButton.getWidth() - 5);
			teleportButton.setY(10);
		} else {
			teleportButton.setX(10);
			teleportButton.setY(height - 55);
		}
		backButton.setX(cancelButton.getX() - backButton.getWidth() - 5);
		clearFiltersTopButton.setX(backButton.getX() - clearFiltersTopButton.getWidth() - 5);
	}

	private void updateViewButtons() {
		searchButton.visible = isStructureView();
		searchListedButton.visible = isStructureView();
		searchForNextButton.visible = isStructureView() && ExplorersCompass.maxNextSearches > 0;
		sortOrderButton.visible = isStructureView();
		filtersButton.visible = isStructureView();
		clearFiltersButton.visible = isStructureView();
		settingsButton.visible = isStructureView();
        hiddenStructuresButton.visible = settingsOpen;
		closeOnFoundButton.visible = settingsOpen;
		chatOverlayButton.visible = settingsOpen;
		translateNamesButton.visible = settingsOpen;
		offsetDecreaseButton.visible = settingsOpen;
		offsetIncreaseButton.visible = settingsOpen;
		overlaySideButton.visible = settingsOpen;
		clearOnCloseButton.visible = settingsOpen;
		checkedFiltersFirstButton.visible = settingsOpen;
		adjustRowsToFitViewButton.visible = settingsOpen;
		cancelButton.visible = true;
		teleportButton.visible = isStructureView() && ExplorersCompass.canTeleport;
		clearFiltersTopButton.visible = filtersOpen;
		backButton.visible = filtersOpen || settingsOpen || hiddenStructuresOpen;
	}

}

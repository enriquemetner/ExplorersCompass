package com.chaosthedude.explorerscompass.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import com.chaosthedude.explorerscompass.items.ExplorersCompassItem;
import com.chaosthedude.explorerscompass.network.SearchForNextPacket;
import com.chaosthedude.explorerscompass.network.SearchPacket;
import com.chaosthedude.explorerscompass.network.TeleportPacket;
import com.chaosthedude.explorerscompass.sorting.SortCriterion;
import com.chaosthedude.explorerscompass.util.CompassState;
import com.chaosthedude.explorerscompass.util.StructureUtils;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

@Environment(EnvType.CLIENT)
public class ExplorersCompassScreen extends Screen {
	private static final int SORT_MENU_WIDTH = 110;
	private static final int SORT_ROW_HEIGHT = 20;
	private static final int SEARCH_FIELD_WIDTH = 210;
	private static final int SEARCH_TELEPORT_GAP = 10;

	private World world;
	private PlayerEntity player;
	private List<Identifier> allowedStructureIDs;
	private List<Identifier> structureIDsMatchingSearch;
	private Identifier foundStructureId;
	private ItemStack stack;
	private ExplorersCompassItem explorersCompass;
	private ButtonWidget searchButton;
	private ButtonWidget searchForNextButton;
	private ButtonWidget searchForGroupButton;
	private ButtonWidget sortOrderButton;
	private ButtonWidget teleportButton;
	private ButtonWidget cancelButton;
	private TransparentTextField searchTextField;
	private StructureSearchList selectionList;
	private final List<SortCriterion> sortOrder = new ArrayList<SortCriterion>();
	private boolean sortMenuOpen;
	private int draggedSortRow = -1;
	private boolean sortOrderChangedDuringDrag;

	public ExplorersCompassScreen(World world, PlayerEntity player, ItemStack stack, ExplorersCompassItem explorersCompass, List<Identifier> allowedStructureIDs) {
		super(Text.translatable("string.explorerscompass.selectStructure"));
		this.world = world;
		this.player = player;
		this.stack = stack;
		this.explorersCompass = explorersCompass;

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
	public boolean mouseScrolled(double scroll1, double scroll2, double scroll3) {
		if (sortMenuOpen && sortRowAt(scroll1, scroll2) >= 0) {
			return true;
		}
		return selectionList.mouseScrolled(scroll1, scroll2, scroll3);
	}

	@Override
	protected void init() {
		clearChildren();
		setupButtons();
		selectionList = new StructureSearchList(this, client, player, foundStructureId, 130, 40, width - 140, height - 50, 50);
		addDrawableChild(selectionList);
		setupTextFields();
	}

	@Override
	public void tick() {
		searchTextField.tick();

		boolean hasSelection = selectionList.hasSelection();
		boolean selectionMatchesFound = hasSelection && foundStructureId != null && selectionList.getSelectedOrNull().getStructureID().equals(foundStructureId);

		searchButton.active = hasSelection;
		searchForGroupButton.active = hasSelection;
		searchForNextButton.active = selectionMatchesFound;
		teleportButton.active = selectionMatchesFound;

		// Check if the allowed structure list has synced
		if (ExplorersCompass.synced) {
			remove(selectionList);
			allowedStructureIDs = new ArrayList<Identifier>(ExplorersCompass.allowedStructureIDs);
			structureIDsMatchingSearch = new ArrayList<Identifier>(allowedStructureIDs);
			selectionList = new StructureSearchList(this, client, player, foundStructureId, 130, 40, width - 140, height - 50, 50);
			addDrawableChild(selectionList);

			teleportButton.visible = ExplorersCompass.canTeleport;
			searchForNextButton.visible = ExplorersCompass.maxNextSearches > 0;
			if (searchForNextButton.visible) {
				sortOrderButton.setX(10);
				sortOrderButton.setY(115);
			} else {
				sortOrderButton.setX(10);
				sortOrderButton.setY(90);
			}

			ExplorersCompass.synced = false;
		}
	}

	@Override
	public void render(DrawContext context, int mouseX, int mouseY, float partialTicks) {
        renderBackground(context);
		super.render(context, mouseX, mouseY, partialTicks);
		if (sortMenuOpen) {
			context.getMatrices().push();
			context.getMatrices().translate(0, 0, 200);
			renderSortMenu(context, mouseX, mouseY);
			context.getMatrices().pop();
		}
        context.drawCenteredTextWithShadow(textRenderer, title, 65, 15, 0xffffff);
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

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
		if (draggedSortRow >= 0 && button == 0) {
			commitSortOrder();
			draggedSortRow = -1;
			return true;
		}
		return super.mouseReleased(mouseX, mouseY, button);
	}

	@Override
	public boolean keyPressed(int par1, int par2, int par3) {
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
		boolean ret = super.charTyped(typedChar, keyCode);
		if (searchTextField.isFocused()) {
			processSearchTerm();
			return true;
		}
		return ret;
	}

	public void searchForStructure(Identifier structureID) {
		ClientPlayNetworking.send(SearchPacket.ID, new SearchPacket(structureID, false));
		client.setScreen(null);
	}

	public void searchForStructureGroup(Identifier groupID) {
		ClientPlayNetworking.send(SearchPacket.ID, new SearchPacket(groupID, true));
		client.setScreen(null);
	}

	public void searchForNext() {
		ClientPlayNetworking.send(SearchForNextPacket.ID, new SearchForNextPacket());
		client.setScreen(null);
	}

	public void teleport() {
		ClientPlayNetworking.send(TeleportPacket.ID, new TeleportPacket());
		client.setScreen(null);
	}

	public void processSearchTerm() {
		structureIDsMatchingSearch = new ArrayList<Identifier>();
		String searchTerm = searchTextField.getText().toLowerCase();
		for (Identifier id : allowedStructureIDs) {
			if (searchTerm.startsWith("@")) {
				if (StructureUtils.getStructureSource(id).toLowerCase().contains(searchTerm.substring(1))) {
					structureIDsMatchingSearch.add(id);
				}
			} else if (StructureUtils.getStructureName(id).toLowerCase().contains(searchTerm)) {
				structureIDsMatchingSearch.add(id);
			}
		}
		selectionList.refreshList(true);
	}

	public List<Identifier> sortStructures() {
		final List<Identifier> structures = new ArrayList<Identifier>(structureIDsMatchingSearch);
		structures.sort((first, second) -> {
			for (SortCriterion criterion : sortOrder) {
				int comparison = criterion.compare(first, second);
				if (comparison != 0) {
					return comparison;
				}
			}
			return first.compareTo(second);
		});
		return structures;
	}

	private void setupButtons() {
		searchButton = addDrawableChild(new TransparentButton(10, 40, 110, 20, Text.translatable("string.explorerscompass.search"), (onPress) -> {
			if (selectionList.hasSelection()) {
				selectionList.getSelectedOrNull().searchForStructure();
			}
		}));
		searchButton.active = false;

		searchForGroupButton = addDrawableChild(new TransparentButton(10, 65, 110, 20, Text.translatable("string.explorerscompass.searchForGroup"), (onPress) -> {
			if (selectionList.hasSelection()) {
				selectionList.getSelectedOrNull().searchForStructureGroup();
			}
		}));
		searchForGroupButton.active = false;

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

		cancelButton = addDrawableChild(new TransparentButton(10, height - 30, 110, 20, Text.translatable("gui.cancel"), (onPress) -> {
			client.setScreen(null);
		}));

		teleportButton = addDrawableChild(new TransparentButton(width - 120, 10, 110, 20, Text.translatable("string.explorerscompass.teleport"), (onPress) -> {
			teleport();
		}));
		teleportButton.visible = ExplorersCompass.canTeleport;
		teleportButton.active = false;
	}

	private void setupTextFields() {
		int searchX = selectionList.getRowLeft();
		int availableWidth = Math.max(0, teleportButton.getX() - searchX - SEARCH_TELEPORT_GAP);
		searchTextField = new TransparentTextField(textRenderer, searchX, 10, Math.min(SEARCH_FIELD_WIDTH, availableWidth), 20, Text.translatable("string.explorerscompass.search"));
		addDrawableChild(searchTextField);
	}

}

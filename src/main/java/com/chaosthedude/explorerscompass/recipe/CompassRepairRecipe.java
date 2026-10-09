package com.chaosthedude.explorerscompass.recipe;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/** Combines the remaining searches of two compasses, with vanilla's 5% repair bonus. */
public final class CompassRepairRecipe extends SpecialCraftingRecipe {
    public CompassRepairRecipe(Identifier id, CraftingRecipeCategory category) {
        super(id, category);
    }

    @Override
    public boolean matches(RecipeInputInventory inventory, World world) {
        return craftResult(inventory) != ItemStack.EMPTY;
    }

    @Override
    public ItemStack craft(RecipeInputInventory inventory, DynamicRegistryManager registries) {
        return craftResult(inventory);
    }

    private static ItemStack craftResult(RecipeInputInventory inventory) {
        int max = effectiveDurability();
        if (max <= 0) return ItemStack.EMPTY;
        ItemStack first = ItemStack.EMPTY, second = ItemStack.EMPTY;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) continue;
            if (!stack.isOf(ExplorersCompass.EXPLORERS_COMPASS_ITEM) || stack.getCount() != 1) return ItemStack.EMPTY;
            if (first.isEmpty()) first = stack;
            else if (second.isEmpty()) second = stack;
            else return ItemStack.EMPTY;
        }
        if (second.isEmpty()) return ItemStack.EMPTY;
        int damage = repairedDamage(max, compassDamage(first), compassDamage(second));
        if (damage >= max) return ItemStack.EMPTY;
        ItemStack result = new ItemStack(ExplorersCompass.EXPLORERS_COMPASS_ITEM);
        if (damage > 0) {
            NbtCompound nbt = result.getOrCreateNbt();
            nbt.putInt("CompassDamage", damage);
        }
        return result;
    }

    private static int compassDamage(ItemStack stack) {
        return stack.hasNbt() ? stack.getNbt().getInt("CompassDamage") : 0;
    }

    static int repairedDamage(int max, int firstDamage, int secondDamage) {
        if (max <= 0) return 0;
        long firstRemaining = Math.max(0L, (long) max - Math.max(0, firstDamage));
        long secondRemaining = Math.max(0L, (long) max - Math.max(0, secondDamage));
        long remaining = Math.min(max, firstRemaining + secondRemaining + max / 20L);
        return (int) (max - remaining);
    }

    private static int effectiveDurability() {
        return serverDurability >= 0 ? serverDurability : ExplorersCompassConfig.compassDurability;
    }

    private static volatile int serverDurability = -1;

    /** The client uses the connected server's value when previewing crafting results. */
    public static void setServerDurability(int durability) {
        serverDurability = Math.max(0, durability);
    }

    public static void clearServerDurability() {
        serverDurability = -1;
    }

    @Override
    public boolean fits(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ExplorersCompass.COMPASS_REPAIR_SERIALIZER;
    }
}

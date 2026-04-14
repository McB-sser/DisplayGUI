package de.mcbesser.displaygui.feature.crafting;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.Map;

public record RecipeMatch(Recipe recipe, ItemStack result, Map<Integer, ItemStack> normalizedIngredients) {
}

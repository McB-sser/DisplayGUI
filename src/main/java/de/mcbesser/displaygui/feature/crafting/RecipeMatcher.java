package de.mcbesser.displaygui.feature.crafting;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class RecipeMatcher {
    public RecipeMatch findMatchingCraftingRecipe(ItemStack[] matrix) {
        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            RecipeMatch match = matchRecipe(recipe, matrix);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    public RecipeMatch adaptCookingRecipe(ItemStack ingredient, DisplayPreset preset) {
        return adaptCookingRecipe(ingredient, preset, Material.FURNACE);
    }

    public RecipeMatch adaptCookingRecipe(ItemStack ingredient, DisplayPreset preset, Material cookerType) {
        if (ingredient == null || ingredient.getType() == Material.AIR || preset != DisplayPreset.FURNACE_1X5) {
            return null;
        }

        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (recipe instanceof CookingRecipe<?> cookingRecipe
                    && matchesCookingRecipe(cookingRecipe, cookerType)
                    && matchesChoice(cookingRecipe.getInputChoice(), ingredient)) {
                return new RecipeMatch(recipe, cookingRecipe.getResult().clone(), Map.of(0, single(ingredient)));
            }
            if (recipe instanceof StonecuttingRecipe stonecuttingRecipe && matchesChoice(stonecuttingRecipe.getInputChoice(), ingredient)) {
                return new RecipeMatch(recipe, stonecuttingRecipe.getResult().clone(), Map.of(0, single(ingredient)));
            }
        }
        return null;
    }

    private RecipeMatch matchRecipe(Recipe recipe, ItemStack[] matrix) {
        if (recipe instanceof ShapedRecipe shapedRecipe) {
            return matchShaped(shapedRecipe, matrix);
        }
        if (recipe instanceof ShapelessRecipe shapelessRecipe) {
            return matchShapeless(shapelessRecipe, matrix);
        }
        return null;
    }

    private RecipeMatch matchShaped(ShapedRecipe recipe, ItemStack[] matrix) {
        String[] shape = recipe.getShape();
        if (shape.length == 0) {
            return null;
        }

        int minRow = 3;
        int minCol = 3;
        int maxRow = -1;
        int maxCol = -1;
        for (int i = 0; i < matrix.length; i++) {
            if (isEmpty(matrix[i])) {
                continue;
            }
            int row = i / 3;
            int col = i % 3;
            minRow = Math.min(minRow, row);
            minCol = Math.min(minCol, col);
            maxRow = Math.max(maxRow, row);
            maxCol = Math.max(maxCol, col);
        }
        if (maxRow == -1) {
            return null;
        }

        int usedHeight = maxRow - minRow + 1;
        int usedWidth = maxCol - minCol + 1;
        if (usedHeight != shape.length) {
            return null;
        }
        int shapeWidth = 0;
        for (String row : shape) {
            shapeWidth = Math.max(shapeWidth, row.length());
        }
        if (usedWidth != shapeWidth) {
            return null;
        }

        Map<Character, RecipeChoice> choices = recipe.getChoiceMap();
        Map<Integer, ItemStack> normalized = new HashMap<>();
        for (int row = 0; row < shape.length; row++) {
            for (int col = 0; col < shape[row].length(); col++) {
                char key = shape[row].charAt(col);
                ItemStack provided = matrix[(minRow + row) * 3 + (minCol + col)];
                if (key == ' ') {
                    if (!isEmpty(provided)) {
                        return null;
                    }
                    continue;
                }
                if (!matchesChoice(choices.get(key), provided)) {
                    return null;
                }
                normalized.put((minRow + row) * 3 + (minCol + col), single(provided));
            }
        }

        for (int i = 0; i < matrix.length; i++) {
            int row = i / 3;
            int col = i % 3;
            boolean inside = row >= minRow && row <= maxRow && col >= minCol && col <= maxCol;
            if (!inside && !isEmpty(matrix[i])) {
                return null;
            }
        }

        return new RecipeMatch(recipe, recipe.getResult().clone(), normalized);
    }

    private boolean matchesCookingRecipe(CookingRecipe<?> recipe, Material cookerType) {
        if (cookerType == Material.SMOKER) {
            return recipe instanceof SmokingRecipe;
        }
        if (cookerType == Material.BLAST_FURNACE) {
            return recipe instanceof BlastingRecipe;
        }
        return recipe instanceof FurnaceRecipe;
    }

    private RecipeMatch matchShapeless(ShapelessRecipe recipe, ItemStack[] matrix) {
        List<ItemStack> provided = new ArrayList<>();
        Map<Integer, ItemStack> normalized = new HashMap<>();
        for (int i = 0; i < matrix.length; i++) {
            if (!isEmpty(matrix[i])) {
                provided.add(single(matrix[i]));
                normalized.put(i, single(matrix[i]));
            }
        }

        List<RecipeChoice> choices = recipe.getChoiceList();
        if (provided.size() != choices.size()) {
            return null;
        }

        boolean[] used = new boolean[provided.size()];
        for (RecipeChoice choice : choices) {
            boolean matched = false;
            for (int i = 0; i < provided.size(); i++) {
                if (used[i]) {
                    continue;
                }
                if (matchesChoice(choice, provided.get(i))) {
                    used[i] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return null;
            }
        }

        return new RecipeMatch(recipe, recipe.getResult().clone(), normalized);
    }

    private boolean matchesChoice(RecipeChoice choice, ItemStack stack) {
        return choice != null && stack != null && stack.getType() != Material.AIR && choice.test(single(stack));
    }

    private boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType() == Material.AIR;
    }

    private ItemStack single(ItemStack stack) {
        ItemStack clone = stack.clone();
        clone.setAmount(1);
        return clone;
    }
}

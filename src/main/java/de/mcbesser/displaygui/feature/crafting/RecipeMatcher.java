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
                return new RecipeMatch(
                        recipe,
                        cookingRecipe.getResult().clone(),
                        Map.of(0, single(ingredient)),
                        Map.of(0, cookingRecipe.getInputChoice())
                );
            }
            if (recipe instanceof StonecuttingRecipe stonecuttingRecipe && matchesChoice(stonecuttingRecipe.getInputChoice(), ingredient)) {
                return new RecipeMatch(
                        recipe,
                        stonecuttingRecipe.getResult().clone(),
                        Map.of(0, single(ingredient)),
                        Map.of(0, stonecuttingRecipe.getInputChoice())
                );
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

        int shapeWidth = 0;
        for (String row : shape) {
            shapeWidth = Math.max(shapeWidth, row.length());
        }
        if (shape.length > 3 || shapeWidth > 3) {
            return null;
        }

        Map<Character, RecipeChoice> choices = recipe.getChoiceMap();
        for (int rowOffset = 0; rowOffset <= 3 - shape.length; rowOffset++) {
            for (int colOffset = 0; colOffset <= 3 - shapeWidth; colOffset++) {
                Map<Integer, ItemStack> normalized = new HashMap<>();
                Map<Integer, RecipeChoice> matchedChoices = new HashMap<>();
                if (matchesShapedAtOffset(matrix, shape, choices, rowOffset, colOffset, normalized, matchedChoices)) {
                    return new RecipeMatch(recipe, recipe.getResult().clone(), normalized, matchedChoices);
                }
            }
        }

        return null;
    }

    private boolean matchesShapedAtOffset(ItemStack[] matrix,
                                          String[] shape,
                                          Map<Character, RecipeChoice> choices,
                                          int rowOffset,
                                          int colOffset,
                                          Map<Integer, ItemStack> normalized,
                                          Map<Integer, RecipeChoice> matchedChoices) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                ItemStack provided = matrix[(row * 3) + col];
                boolean withinRows = row >= rowOffset && row < rowOffset + shape.length;
                int relativeCol = col - colOffset;

                if (!withinRows) {
                    if (!isEmpty(provided)) {
                        return false;
                    }
                    continue;
                }

                String shapeRow = shape[row - rowOffset];
                boolean withinCols = relativeCol >= 0 && relativeCol < shapeRow.length();
                if (!withinCols) {
                    if (!isEmpty(provided)) {
                        return false;
                    }
                    continue;
                }

                char key = shapeRow.charAt(relativeCol);
                RecipeChoice choice = choices.get(key);
                if (choice == null) {
                    if (!isEmpty(provided)) {
                        return false;
                    }
                    continue;
                }

                if (!matchesChoice(choice, provided)) {
                    return false;
                }
                int slot = (row * 3) + col;
                normalized.put(slot, single(provided));
                matchedChoices.put(slot, choice);
            }
        }

        return true;
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
        List<Integer> providedSlots = new ArrayList<>();
        for (int i = 0; i < matrix.length; i++) {
            if (!isEmpty(matrix[i])) {
                provided.add(single(matrix[i]));
                providedSlots.add(i);
            }
        }

        List<RecipeChoice> choices = recipe.getChoiceList();
        if (provided.size() != choices.size()) {
            return null;
        }

        boolean[] used = new boolean[provided.size()];
        Map<Integer, ItemStack> normalized = new HashMap<>();
        Map<Integer, RecipeChoice> matchedChoices = new HashMap<>();
        for (RecipeChoice choice : choices) {
            boolean matched = false;
            for (int i = 0; i < provided.size(); i++) {
                if (used[i]) {
                    continue;
                }
                if (matchesChoice(choice, provided.get(i))) {
                    used[i] = true;
                    int slot = providedSlots.get(i);
                    normalized.put(slot, provided.get(i));
                    matchedChoices.put(slot, choice);
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return null;
            }
        }

        return new RecipeMatch(recipe, recipe.getResult().clone(), normalized, matchedChoices);
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

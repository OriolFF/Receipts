package com.uriolus.recipes.feature.recipe_list.data.source

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.uriolus.recipes.core.data.local.RecipesDatabase
// Assuming RecipeEntity and its toDomain mapper exist, e.g.:
// import com.uriolus.recipes.feature.recipe_list.data.source.local.model.RecipeEntity
// import com.uriolus.recipes.feature.recipe_list.data.source.local.model.toDomain
import com.uriolus.recipes.feature.recipe_list.data.source.local.model.toEntity // Already present
import com.uriolus.recipes.core.model.AppError
import com.uriolus.recipes.feature.recipe_list.data.source.local.model.RecipeEntity
import com.uriolus.recipes.feature.recipe_list.data.source.local.model.toDomain
import com.uriolus.recipes.feature.recipe_list.domain.model.Recipe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

// Define a placeholder for RecipeEntity if not available for import check
// typealias RecipeEntity = Any // Placeholder
// fun RecipeEntity.toDomain(): Recipe = TODO("Implement RecipeEntity.toDomain()") // Placeholder

class RoomRecipeDataSource @Inject constructor(
    private val database: RecipesDatabase
) : RecipeDataSource {

    // Assuming DEFAULT_IMAGE_URL is defined elsewhere or remove if not needed
    // private val DEFAULT_IMAGE_URL = ""

    override suspend fun getRecipes(): Either<AppError, List<Recipe>> {
        return try {
            // Assuming recipeDao().getAll() returns Flow<List<RecipeEntity>>
            // And RecipeEntity has a .toDomain() extension function
            database.recipeDao().getAllRecipes()
                .map { it.toDomain() }.right()
        } catch (e: Exception) {
            AppError.DatabaseError("Failed to get recipes from local database: ${e.message}", e)
                .left()
        }
    }

    override suspend fun saveRecipes(recipes: List<Recipe>): Either<AppError, Unit> {
        return try {
            val entities = recipes.map { it.toEntity() } // Uses existing Recipe.toEntity()
            // Assuming recipeDao().deleteAll() and recipeDao().insertAll(entities) exist
            database.recipeDao().deleteAll()
            database.recipeDao().insertAll(entities)
            Unit.right()
        } catch (e: Exception) {
            AppError.DatabaseError("Failed to save recipes to local database: ${e.message}", e)
                .left()
        }
    }

    override suspend fun saveRecipe(recipe: Recipe): Either<AppError, Unit> {
        return try {
            val recipeEntity = recipe.toEntity() // Convert domain Recipe to RecipeEntity
            database.recipeDao().insertRecipe(recipeEntity) // Assuming recipeDao().insert(entity) exists
            Unit.right()
        } catch (e: Exception) {
            AppError.DatabaseError("Failed to save recipe to local database: ${e.message}", e)
                .left()
        }
    }

    override suspend fun extractRecipeFromUrl(url: String): Either<AppError, Recipe?> {
        return AppError.UnsupportedOperation("Extracting from URL is not supported by RoomRecipeDataSource")
            .left()
    }

    // extractIngredientsAndInstructions seems to be unused by the interface methods now, keeping it for now.
    private fun extractIngredientsAndInstructions(description: String): Pair<List<String>, List<String>> {
        val ingredients = mutableListOf<String>()
        val instructions = mutableListOf<String>()

        // Split the description into sections
        val sections = description.split("\n\n")

        // Look for ingredients and instructions sections
        for (section in sections) {
            when {
                section.startsWith("Ingredients:", ignoreCase = true) -> {
                    // Extract ingredients
                    val lines = section.lines().drop(1) // Skip the "Ingredients:" header
                    for (line in lines) {
                        val trimmedLine = line.trim()
                        if (trimmedLine.isNotEmpty()) {
                            // Handle bullet points or numbered lists
                            val ingredient = when {
                                trimmedLine.startsWith("- ") -> trimmedLine.substring(2)
                                trimmedLine.startsWith("• ") -> trimmedLine.substring(2)
                                else -> trimmedLine
                            }
                            ingredients.add(ingredient)
                        }
                    }
                }

                section.startsWith("Instructions:", ignoreCase = true) -> {
                    // Extract instructions
                    val lines = section.lines().drop(1) // Skip the "Instructions:" header
                    for (line in lines) {
                        val trimmedLine = line.trim()
                        if (trimmedLine.isNotEmpty()) {
                            // Handle numbered lists
                            val instruction = when {
                                trimmedLine.matches(Regex("^\\d+\\.\\s+.*$")) -> {
                                    val dotIndex = trimmedLine.indexOf('.')
                                    trimmedLine.substring(dotIndex + 1).trim()
                                }

                                else -> trimmedLine
                            }
                            instructions.add(instruction)
                        }
                    }
                }
            }
        }

        // If no structured sections were found, try the original approach
        if (ingredients.isEmpty() && instructions.isEmpty()) {
            val lines = description.split("\n", "\r\n")

            var currentSection = ""

            for (line in lines) {
                val trimmedLine = line.trim()

                when {
                    trimmedLine.isEmpty() -> continue

                    trimmedLine.contains("ingredient", ignoreCase = true) ||
                            trimmedLine.contains("you need", ignoreCase = true) ||
                            trimmedLine.contains("you'll need", ignoreCase = true) -> {
                        currentSection = "ingredients"
                    }

                    trimmedLine.contains("instruction", ignoreCase = true) ||
                            trimmedLine.contains("direction", ignoreCase = true) ||
                            trimmedLine.contains("step", ignoreCase = true) ||
                            trimmedLine.contains("method", ignoreCase = true) -> {
                        currentSection = "instructions"
                    }

                    trimmedLine.startsWith("-") || trimmedLine.startsWith("•") ||
                            (trimmedLine.length > 2 && trimmedLine[0].isDigit() && trimmedLine[1] == '.') -> {
                        val content =
                            trimmedLine.substring(trimmedLine.indexOfFirst { it == ' ' } + 1)
                        if (currentSection == "ingredients") {
                            ingredients.add(content)
                        } else if (currentSection == "instructions") {
                            instructions.add(content)
                        }
                    }
                }
            }
        }

        // If we still couldn't extract any structured data, provide some default placeholders
        if (ingredients.isEmpty()) {
            ingredients.add("No ingredients information available")
        }

        if (instructions.isEmpty()) {
            instructions.add("No instructions available. Check the original recipe at the link.")
        }

        return Pair(ingredients, instructions)
    }

    companion object {
        private const val DEFAULT_IMAGE_URL =
            "https://images.unsplash.com/photo-1495521821757-a1efb6729352?ixlib=rb-4.0.3&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=800&q=80"
    }
}

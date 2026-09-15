package com.doulabi.app

import org.junit.Assert.*
import org.junit.Test

class RecommendationEngineTest {
    private val items = listOf(
        ClothingItem(1, "White Tee", Category.TOP, Season.SUMMER, Occasion.CASUAL),
        ClothingItem(2, "Black Trousers", Category.BOTTOM, Season.ALL, Occasion.FORMAL),
        ClothingItem(3, "Sneakers", Category.SHOES, Season.ALL, Occasion.CASUAL),
        ClothingItem(4, "Winter Coat", Category.OUTERWEAR, Season.WINTER, Occasion.WORK),
        ClothingItem(5, "Watch", Category.ACCESSORY, Season.ALL, Occasion.CASUAL, favorite = true)
    )

    @Test fun filtersBySeasonOrAllSeason() {
        val result = RecommendationEngine.recommend(items, Season.SUMMER, Occasion.CASUAL)
        assertTrue(result.any { it.id == 1L })
        assertTrue(result.any { it.id == 3L })
        assertTrue(result.any { it.id == 5L })
        assertTrue(result.none { it.id == 4L })
    }

    @Test fun matchingOccasionRanksFirst() {
        val result = RecommendationEngine.recommend(items, Season.WINTER, Occasion.FORMAL)
        assertEquals(2L, result.first().id)
    }

    @Test fun favoriteGetsBonus() {
        val result = RecommendationEngine.recommend(items, Season.SUMMER, Occasion.CASUAL)
        assertEquals(5L, result.first().id)
    }

    @Test fun buildsOutfitAcrossCategories() {
        val outfit = RecommendationEngine.buildOutfit(items, Season.SUMMER, Occasion.CASUAL)
        assertNotNull(outfit.top)
        assertNotNull(outfit.bottom)
        assertNotNull(outfit.shoes)
        assertNotNull(outfit.accessory)
        assertNull(outfit.outerwear)
    }
}

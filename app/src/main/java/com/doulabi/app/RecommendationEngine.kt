package com.doulabi.app

/** Deterministic, offline outfit ranking. No network/API key is required. */
object RecommendationEngine {
    fun recommend(items: List<ClothingItem>, season: Season, occasion: Occasion): List<ClothingItem> =
        items.asSequence()
            .filter { it.season == season || it.season == Season.ALL }
            .sortedByDescending { score(it, occasion) }
            .toList()

    fun buildOutfit(items: List<ClothingItem>, season: Season, occasion: Occasion): Outfit {
        val ranked = recommend(items, season, occasion)
        return Outfit(
            top = ranked.firstOrNull { it.category == Category.TOP },
            bottom = ranked.firstOrNull { it.category == Category.BOTTOM },
            outerwear = ranked.firstOrNull { it.category == Category.OUTERWEAR },
            shoes = ranked.firstOrNull { it.category == Category.SHOES },
            accessory = ranked.firstOrNull { it.category == Category.ACCESSORY }
        )
    }

    private fun score(item: ClothingItem, occasion: Occasion): Int {
        var score = 0
        if (item.occasion == occasion) score += 100
        if (item.occasion == Occasion.CASUAL && occasion == Occasion.WORK) score += 8
        if (item.occasion == Occasion.WORK && occasion == Occasion.FORMAL) score += 15
        if (item.favorite) score += 7
        score += when (item.category) {
            Category.TOP -> 8
            Category.BOTTOM -> 7
            Category.SHOES -> 6
            Category.OUTERWEAR -> 4
            Category.ACCESSORY -> 3
        }
        score += item.wornCount.coerceAtMost(10)
        return score
    }
}

data class Outfit(
    val top: ClothingItem?,
    val bottom: ClothingItem?,
    val outerwear: ClothingItem?,
    val shoes: ClothingItem?,
    val accessory: ClothingItem?
) {
    val pieces: List<ClothingItem> get() = listOfNotNull(top, bottom, outerwear, shoes, accessory)
}

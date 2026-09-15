package com.doulabi.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiVisionServiceTest {
    @Test fun allProvidersHaveDefaults() {
        assertEquals(AiProvider.values().size, defaultAiProviders().size)
        AiProvider.values().forEach { provider ->
            val config = defaultAiProviders().getValue(provider)
            assertEquals(provider.defaultModel, config.model)
            assertEquals(provider.defaultBaseUrl, config.baseUrl)
        }
    }

    @Test fun defaultsStartDisabled() {
        assertTrue(defaultAiProviders().values.all { !it.enabled && it.apiKey.isBlank() })
    }

    @Test fun categoryAndSeasonEnumsAreStable() {
        assertEquals(Category.TOP, Category.valueOf("TOP"))
        assertEquals(Season.SUMMER, Season.valueOf("SUMMER"))
        assertEquals(Occasion.FORMAL, Occasion.valueOf("FORMAL"))
    }
}

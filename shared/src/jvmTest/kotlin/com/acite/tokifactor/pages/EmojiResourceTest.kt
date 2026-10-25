package com.acite.tokifactor.pages

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class EmojiResourceTest {
    @Test
    fun bundledCatalogAndImagesLoad() = runBlocking {
        val catalog = EmojiRepository.catalog()
        assertEquals(27, catalog.assets.size)
        val cheer = assertNotNull(catalog.resolve("aki_cheer_peace"))
        assertTrue((EmojiRepository.image(cheer)?.size ?: 0) > 0)
    }
}

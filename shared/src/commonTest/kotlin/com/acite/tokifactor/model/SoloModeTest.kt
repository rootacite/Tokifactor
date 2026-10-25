package com.acite.tokifactor.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class SoloModeTest {
    @Test
    fun magicMatchesAkiVectorAndIsMemberSpecific() {
        assertEquals(
            "e002defc2c9cb6a7f1f38c5c1120a82fde05a411f803832f9ae99bbfa82bd773",
            deriveSoloMagic("group", "legacy_abc"),
        )
        assertNotEquals(
            deriveSoloMagic("group", "legacy_abc"),
            deriveSoloMagic("group", "legacy_def"),
        )
        assertNotEquals(
            deriveSoloMagic("group", "legacy_abc"),
            deriveSoloMagic("other-group", "legacy_abc"),
        )
    }
}

package com.acite.tokifactor.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SoloCharacterTest {
    @Test
    fun resolvesEnglishAndChineseAliases() {
        assertEquals(SoloCharacter.Aki, SoloCharacter.resolve("Aki"))
        assertEquals(SoloCharacter.Aki, SoloCharacter.resolve("秋"))
        assertEquals(SoloCharacter.Fuyu, SoloCharacter.resolve("冬"))
        assertEquals(SoloCharacter.Haru, SoloCharacter.resolve("haru"))
        assertEquals(SoloCharacter.Natsu, SoloCharacter.resolve("夏"))
    }

    @Test
    fun resolvesLegacyMemberIdsWhenNameIsMissing() {
        for (character in SoloCharacter.entries) {
            assertEquals(
                character,
                SoloCharacter.resolve("", legacyMemberId(character.label)),
            )
        }
    }

    @Test
    fun unknownSoloTargetFallsBackToNoCharacter() {
        assertNull(SoloCharacter.resolve("Unknown", "mem_00000000000000000000000000000000"))
    }
}

package com.acite.tokifactor.pages

import kotlin.test.Test
import kotlin.test.assertEquals

class AttachmentNamingTest {
    @Test
    fun defaultsUseTheFirstUnusedImageNumber() {
        assertEquals("图片1", defaultAttachmentName(emptyList()))
        assertEquals("图片3", defaultAttachmentName(listOf("图片1", "图片2")))
    }

    @Test
    fun duplicateAndBlankNamesAreMadeUniqueInOrder() {
        assertEquals(
            listOf("图片1", "图片2", "草图", "草图 (2)", "草图 (3)"),
            normalizeAttachmentNames(listOf("图片1", "", "草图", "草图", "草图")),
        )
    }
}

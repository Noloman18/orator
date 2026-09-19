package com.noloxtreme.tts.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderScrollPositionTest {

    @Test
    fun `paragraph key resolves its paging index`() {
        assertEquals(42, paragraphIndexFromReaderKey("paragraph-42"))
    }

    @Test
    fun `non paragraph and invalid keys are ignored`() {
        assertNull(paragraphIndexFromReaderKey("header"))
        assertNull(paragraphIndexFromReaderKey("paragraph--1"))
        assertNull(paragraphIndexFromReaderKey("paragraph-next"))
        assertNull(paragraphIndexFromReaderKey(3))
    }
}

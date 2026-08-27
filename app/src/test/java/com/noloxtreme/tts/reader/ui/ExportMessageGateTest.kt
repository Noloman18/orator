package com.noloxtreme.tts.reader.ui

import com.noloxtreme.tts.reader.domain.ExportError
import com.noloxtreme.tts.reader.domain.ExportState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExportMessageGateTest {

    private val gate = ExportMessageGate()

    @Test
    fun `replayed terminal success produces no message`() {
        // A fresh reader screen observes the last WorkInfo state first.
        assertNull(gate.onState(ExportState.Idle))
        assertNull(gate.onState(ExportState.Succeeded("Book.m4a", "content://book")))
    }

    @Test
    fun `replayed terminal failure produces no message`() {
        assertNull(gate.onState(ExportState.Idle))
        assertNull(gate.onState(ExportState.Failed(ExportError.SYNTHESIS_FAILED)))
    }

    @Test
    fun `success transition witnessed live produces one message`() {
        assertNull(gate.onState(ExportState.Idle))
        assertNull(gate.onState(ExportState.Enqueued))
        assertNull(gate.onState(ExportState.Running(40)))
        val message = gate.onState(ExportState.Succeeded("Book.m4a", "content://book"))
        assertEquals(ExportMessage.Succeeded("Book.m4a", "content://book"), message)
        // The state is not re-emitted afterwards.
        assertNull(gate.onState(ExportState.Succeeded("Book.m4a", "content://book")))
    }

    @Test
    fun `failure transition witnessed live produces one message with detail`() {
        assertNull(gate.onState(ExportState.Enqueued))
        val message = gate.onState(ExportState.Failed(ExportError.ENCODING_FAILED, "boom"))
        assertEquals(ExportMessage.Failed(ExportError.ENCODING_FAILED, "boom"), message)
    }

    @Test
    fun `second export after completion emits again`() {
        assertNull(gate.onState(ExportState.Enqueued))
        assertNull(gate.onState(ExportState.Running(90)))
        assertEquals(
            ExportMessage.Succeeded("Book.m4a", "content://book"),
            gate.onState(ExportState.Succeeded("Book.m4a", "content://book"))
        )
        assertNull(gate.onState(ExportState.Enqueued))
        assertEquals(
            ExportMessage.Succeeded("Book.m4a", "content://book"),
            gate.onState(ExportState.Succeeded("Book.m4a", "content://book"))
        )
    }

    @Test
    fun `non-terminal states produce no message`() {
        assertNull(gate.onState(ExportState.Idle))
        assertNull(gate.onState(ExportState.Enqueued))
        assertNull(gate.onState(ExportState.Running(10)))
        assertNull(gate.onState(ExportState.Cancelled))
    }
}

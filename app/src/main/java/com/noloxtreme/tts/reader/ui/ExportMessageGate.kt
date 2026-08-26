package com.noloxtreme.tts.reader.ui

import com.noloxtreme.tts.reader.domain.ExportError
import com.noloxtreme.tts.reader.domain.ExportState

sealed interface ExportMessage {
    data class Succeeded(val displayName: String) : ExportMessage
    data class Failed(val error: ExportError, val detail: String? = null) : ExportMessage
}

/**
 * Produces a one-shot snackbar message only for terminal export transitions
 * witnessed live — Enqueued/Running into Succeeded/Failed.
 *
 * WorkManager replays the last [ExportState] (including a terminal one) to
 * every new observer, so a reader screen that simply reopens after an export
 * finished would otherwise show the completion snackbar again. The gate
 * remembers the previous state and stays silent unless the running job was
 * actually observed, so the message appears exactly once per export run.
 */
internal class ExportMessageGate {
    private var previous: ExportState? = null

    fun onState(state: ExportState): ExportMessage? {
        val transition = previous
        previous = state
        return when (state) {
            is ExportState.Succeeded ->
                if (transition is ExportState.Enqueued || transition is ExportState.Running) {
                    ExportMessage.Succeeded(state.displayName)
                } else {
                    null
                }
            is ExportState.Failed ->
                if (transition is ExportState.Enqueued || transition is ExportState.Running) {
                    ExportMessage.Failed(state.error, state.detail)
                } else {
                    null
                }
            else -> null
        }
    }
}

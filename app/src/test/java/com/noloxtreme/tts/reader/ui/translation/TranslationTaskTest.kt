package com.noloxtreme.tts.reader.ui.translation

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationTaskTest {
    @Test
    fun `an SDK cancelled task becomes a recoverable failure without cancelling its caller`() = runTest {
        val failure = runCatching { Tasks.forCanceled<String>().awaitTranslationTask() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(currentCoroutineContext().isActive)
    }

    @Test
    fun `caller cancellation is preserved and a late SDK callback is harmless`() = runTest {
        val completion = TaskCompletionSource<String>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            completion.task.awaitTranslationTask()
        }

        job.cancel()
        job.join()
        completion.setResult("late result")

        assertTrue(job.isCancelled)
    }
}

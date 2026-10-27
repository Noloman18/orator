package com.noloxtreme.tts.reader.ui.translation

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.Closeable
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

/** Keep the SDK and Android checks behind a small boundary so state transitions can be tested. */
interface TranslationRuntime {
    val supportedLanguageCodes: Set<String>
    suspend fun downloadedLanguageCodes(): Set<String>
    fun preflight(missingModels: Set<String>, allowMobileData: Boolean, characterCount: Long): TranslationError?
    fun session(source: String, target: String): TranslationSession
}

interface TranslationSession : Closeable {
    suspend fun prepare(allowMobileData: Boolean)
    suspend fun translate(text: String): String
}

internal class TranslationFailure(val reason: TranslationError) : Exception(reason.name)

@Singleton
class OnDeviceTranslationRuntime @Inject constructor(
    @ApplicationContext private val context: Context
) : TranslationRuntime {
    override val supportedLanguageCodes: Set<String>
        get() = TranslateLanguage.getAllLanguages().toSet()

    override suspend fun downloadedLanguageCodes(): Set<String> = withTimeout(15_000L) {
        // This read also probes whether the installed SDK's model runtime can initialize on
        // this device. It does not download models or send document text anywhere.
        RemoteModelManager.getInstance()
            .getDownloadedModels(TranslateRemoteModel::class.java)
            .awaitTranslationTask()
            .map { it.language }
            .toSet()
    }

    override fun preflight(
        missingModels: Set<String>,
        allowMobileData: Boolean,
        characterCount: Long
    ): TranslationError? {
        // Allow room for downloaded packs, temporary model files and the translated copy.
        val requiredBytes = 8L * MEBIBYTE + characterCount.coerceAtLeast(0) * 8L +
            missingModels.size * 45L * MEBIBYTE
        if (context.filesDir.usableSpace < requiredBytes) return TranslationError.STORAGE_LOW
        if (missingModels.isEmpty()) return null
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
        if (capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true ||
            !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        ) return TranslationError.NETWORK_REQUIRED
        if (!allowMobileData && !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return TranslationError.WIFI_REQUIRED
        }
        return null
    }

    override fun session(source: String, target: String): TranslationSession {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build()
        return MlKitTranslationSession(Translation.getClient(options))
    }
}

private class MlKitTranslationSession(private val translator: Translator) : TranslationSession {
    override suspend fun prepare(allowMobileData: Boolean) {
        val conditions = DownloadConditions.Builder().apply {
            if (!allowMobileData) requireWifi()
        }.build()
        // A stalled download must leave a route back to retry or cancel.
        withTimeout(5 * 60_000L) {
            translator.downloadModelIfNeeded(conditions).awaitTranslationTask()
        }
    }

    override suspend fun translate(text: String): String = withTimeout(60_000L) {
        translator.translate(text).awaitTranslationTask()
    }

    override fun close() = translator.close()
}

private val directExecutor = Executor { it.run() }

internal suspend fun <T> Task<T>.awaitTranslationTask(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener(directExecutor) { task ->
        if (!continuation.isActive) return@addOnCompleteListener
        when {
            // An SDK operation can be cancelled independently of our coroutine. Report it
            // as an operation failure so the UI exits loading; caller cancellation already
            // makes the continuation inactive and is preserved by the guard above.
            task.isCanceled -> continuation.resumeWith(Result.failure(IllegalStateException("Translation operation was cancelled")))
            task.isSuccessful -> continuation.resumeWith(Result.success(task.result))
            else -> continuation.resumeWith(Result.failure(task.exception ?: IllegalStateException("Translation task failed")))
        }
    }
}

private const val MEBIBYTE = 1_024L * 1_024L

@Module
@InstallIn(SingletonComponent::class)
abstract class TranslationModule {
    @Binds
    abstract fun bindTranslationRuntime(implementation: OnDeviceTranslationRuntime): TranslationRuntime
}

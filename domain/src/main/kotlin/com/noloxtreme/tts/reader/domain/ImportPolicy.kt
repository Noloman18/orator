package com.noloxtreme.tts.reader.domain

/** Product-level limits and formats accepted by the library importer. */
object ImportPolicy {
    const val MAX_SOURCE_BYTES: Long = 4L * 1024L * 1024L

    val pickerMimeTypes: List<String> = listOf(
        "text/plain",
        "text/markdown",
        "text/x-markdown",
        "application/epub+zip",
        "application/pdf"
    )

    private val supportedExtensions = setOf("txt", "md", "markdown", "epub", "pdf")

    fun supports(source: ImportSource): Boolean {
        val extension = source.displayName.substringAfterLast('.', "").lowercase()
        // Prefer a filename extension when the document provider supplies one:
        // providers occasionally label unrelated files as text/plain.
        return if (extension.isNotBlank()) {
            extension in supportedExtensions
        } else {
            source.mimeType.lowercase() in pickerMimeTypes
        }
    }

    fun isWithinSizeLimit(reportedSizeBytes: Long?): Boolean =
        reportedSizeBytes == null || reportedSizeBytes <= MAX_SOURCE_BYTES
}

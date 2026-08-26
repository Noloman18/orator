# Supported formats

Orator imports the following formats through Android's system document picker. Parsing is local and produces normalized text for the Reader and Android TextToSpeech; Orator does not upload documents. Any imported book can optionally be exported to a local AAC audio file (`Music/Orator`, see ADR 0005); nothing leaves the device.

## Plain text (`.txt`)

| Aspect | Support |
|---|---|
| MIME | `text/plain`; `application/octet-stream` is accepted when the filename identifies `.txt` |
| Encodings | UTF-8 (with or without BOM), UTF-16 LE/BE (BOM required) |
| Line endings | LF and CRLF |
| Paragraph split | One or more blank lines |
| Normalization | Whitespace collapsed to single spaces; paragraphs trimmed; blank paragraphs dropped |
| Metadata | Title from the filename; no language tag |

Malformed input that cannot be decoded under these rules is rejected; Orator never falls back to a device-dependent charset.

## Markdown (`.md`, `.markdown`)

| Aspect | Support |
|---|---|
| MIME | `text/markdown` and `text/x-markdown`; weak provider MIME (`text/plain` or `application/octet-stream`) is accepted when the filename has `.md` or `.markdown` |
| Encodings | The same deterministic UTF-8 and BOM-marked UTF-16 support as TXT |
| Content | ATX headings (`#` through `######`), paragraphs, list items, and blockquotes |
| Removed syntax | Link destinations, image destinations, emphasis markers, inline-code delimiters, thematic breaks, and reference definitions |
| Excluded | Fenced code blocks delimited by backticks or tildes |
| Sections | A new section starts at each ATX heading; content before the first heading is section zero |
| Metadata | First ATX heading after inline-markup removal; fallback to filename; no language tag |

Markdown is narrated as readable text, not rendered as an interactive document. Raw HTML may be reduced to its text content, but HTML-specific layout is not supported.

## EPUB (non-DRM EPUB 2 and EPUB 3)

| Aspect | Support |
|---|---|
| MIME | `application/epub+zip`; weak provider MIME is accepted when the filename identifies `.epub` |
| Validation | Requires a valid ZIP header, `mimetype` = `application/epub+zip`, and readable `META-INF/container.xml` plus package OPF |
| Content | XHTML/HTML reading-order items visited strictly in OPF spine order |
| Extraction | Headings (h1–h6), paragraphs, list items, blockquotes, and pre blocks; `br` treated as a line boundary |
| Excluded | Scripts, styles, forms, images, audio, video, SVG-only content, and navigation documents |
| Sections | One section per spine item; title from the first heading, else navigation label, else none |
| Metadata | Dublin Core title (fallback: filename) and BCP-47 language when valid |
| DRM | Rejected when `META-INF/encryption.xml` exists or encrypted reading-order content is declared |

## PDF (`.pdf`)

| Aspect | Support |
|---|---|
| MIME | `application/pdf`; weak provider MIME is accepted when the filename identifies `.pdf` |
| Validation | Requires the `%PDF-` signature and a document that PDFBox-Android can open |
| Content | Embedded, extractable text processed in page order |
| Paragraphs | Page text is split on blank lines; control characters are removed |
| Sections | One zero-based section per PDF page; page sections have no inferred title |
| Metadata | Embedded PDF title when non-blank; fallback to filename; no language tag |
| Encryption | Password-protected or otherwise encrypted PDFs are rejected |

PDF support is text extraction, not visual PDF rendering. Reading order depends on the PDF's text structure, so complex multi-column or heavily positioned layouts may not narrate in the same order in which they appear visually.

Scanned or image-only PDFs do not contain extractable text and are rejected as having no readable text. OCR is not included. PDF forms, annotations, JavaScript, embedded media, and images are not executed or narrated.

## Resolution and safety rules

- A recognized filename extension takes precedence when a document provider reports weak MIME `text/plain` or `application/octet-stream`.
- A contradiction between a recognized extension and a different strong supported MIME is rejected as `UNSUPPORTED_FORMAT`; Orator does not guess.
- Extension matching is case-insensitive.
- Every successful import is copied to app-private storage before parsing and remains usable after the original is moved or deleted.
- Every parsed document must contain at least one non-blank text paragraph.

| Limit | Value |
|---|---|
| Maximum source file | 100 MiB (1 MiB = 1,048,576 bytes) |
| Maximum EPUB ZIP entries | 10,000 |
| Maximum uncompressed EPUB content | 250 MiB |
| Maximum single EPUB entry | 25 MiB |
| EPUB ZIP path traversal | Rejected; resources resolve only inside the ZIP |
| Maximum PDF pages | 10,000 |
| Maximum extracted PDF text | 25,000,000 UTF-16 code units |

## Not supported

DOCX, standalone HTML, RTF, MOBI, Kindle formats, audiobooks, image imports, OCR, DRM removal, and cloud/network voices are not supported. Audio generation is never automatic or cloud-based; the only audio output is the user-initiated AAC export of ADR 0005. Encrypted EPUB and PDF documents are rejected rather than unlocked.

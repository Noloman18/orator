# Supported formats

Orator v1 imports and reads the following formats through Android's system document picker. No other formats are supported in v1.

## Plain text (TXT)

| Aspect | Support |
|---|---|
| Encodings | UTF-8 (with or without BOM), UTF-16 LE/BE (BOM required) |
| Line endings | LF and CRLF |
| Paragraph split | One or more blank lines |
| Normalization | NFC, whitespace collapsed to single spaces, paragraphs trimmed, blank paragraphs dropped |
| Metadata | One section titled with the filename (without extension); no language tag |

Malformed input that cannot be decoded as UTF-8/UTF-16 is rejected with a clear error; Orator never falls back to a device-dependent charset.

## EPUB (non-DRM EPUB 2 and EPUB 3)

| Aspect | Support |
|---|---|
| Validation | Requires a valid ZIP header, `mimetype` = `application/epub+zip`, and a readable `META-INF/container.xml` + package OPF |
| Content | XHTML/HTML reading-order items visited strictly in OPF spine order |
| Extraction | Headings (h1–h6), paragraphs, list items, blockquotes, and pre blocks; `br` treated as a line boundary |
| Excluded | Scripts, styles, forms, images, audio, video, SVG-only content, and navigation documents |
| Sections | One section per spine item; title from the first heading, else the navigation label, else none |
| Metadata | Dublin Core title (fallback: filename) and BCP-47 language when valid |
| DRM | Rejected when `META-INF/encryption.xml` exists or encrypted reading-order content is declared |

## Limits

| Limit | Value |
|---|---|
| Maximum source file | 100 MiB (1 MiB = 1,048,576 bytes) |
| Maximum EPUB ZIP entries | 10,000 |
| Maximum uncompressed EPUB content | 250 MiB |
| Maximum single EPUB entry | 25 MiB |
| ZIP path traversal | Rejected; resources resolve only inside the ZIP |

## Out of scope in v1

PDF, DOCX, HTML, RTF, MOBI, Kindle, audiobooks, images, OCR, DRM removal, and cloud/network voices are not supported.

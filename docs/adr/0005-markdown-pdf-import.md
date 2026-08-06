# ADR 0005 — Markdown and local PDF text import

Status: Accepted  
Date: 2026-08-06

## Context

Orator originally accepted TXT and EPUB. Users also need Markdown novels and PDFs selected from local Android document providers. The existing import pipeline already owns private copying, duplicate detection, normalized paragraph persistence, and parser selection. PDF is a container format whose text extraction is not provided by the Android framework, while Markdown can be converted to readable text without a rendering engine.

The feature must preserve offline privacy, deterministic progress, bounded resource use, and the parser substitution rules in the main architecture. It must not imply OCR, PDF rendering, password recovery, or execution of active document content.

## Decision

Register `MarkdownParser` and `PdfParser` through the existing Hilt `BookParser` multibinding and `BookParserRegistry`.

- Markdown accepts `.md` and `.markdown`, `text/markdown`, and `text/x-markdown`. It uses the shared deterministic text decoder, emits readable headings/paragraphs/lists/blockquotes, removes inline formatting syntax, and excludes fenced code.
- PDF accepts `.pdf` and `application/pdf`. It uses PDFBox-Android `2.0.27.0` locally, initializes `PDFBoxResourceLoader` from the application context, extracts embedded text one page at a time, and creates one section per page.
- A recognized extension wins only over weak provider MIME (`text/plain` or `application/octet-stream`). Contradictory strong supported MIME and extension claims are rejected.
- The existing 100 MiB source limit applies to every format. PDF additionally has a 10,000-page limit and a 25,000,000 UTF-16-code-unit extracted-text limit.
- Encrypted PDFs return `PDF_ENCRYPTED`; unreadable or invalid PDFs return `MALFORMED_DOCUMENT`; limit breaches return `PDF_LIMIT_EXCEEDED`; documents with no extracted paragraph return `NO_READABLE_TEXT`.
- PDF extraction does not render pages, run JavaScript or embedded media, inspect form behavior, contact a network, perform OCR, or attempt passwords.

## Consequences

- Markdown and text-based PDFs participate in the same Library, Reader, narration, progress, duplicate, deletion, and private-copy flows as TXT and EPUB.
- The `:data` module gains a pinned PDFBox-Android dependency and its packaged font/CMap assets, increasing application size.
- PDF reading order is best effort and depends on the document's internal text positioning. Visually complex or multi-column PDFs may narrate differently from their visible layout.
- Scanned/image-only PDFs fail clearly because no readable text is produced; OCR remains a separate future architecture decision.
- Parser resolution stays open for additional formats because format claims are declared by each parser rather than hard-coded into the importer.

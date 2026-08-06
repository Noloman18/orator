# ADR 0002 — Private import copy

Status: Accepted  
Date: 2026-08-06

## Context

Books are selected through the Storage Access Framework and may come from cloud providers, removable media, or document apps whose access can be revoked or go offline. Orator must keep imported books functional without the original source, and must never request broad storage access.

## Decision

On import, Orator streams the selected source into app-private storage below `filesDir/documents/{documentId}/source.{extension}` and never depends on the original content URI afterwards. The URI exists only inside the in-memory `ImportSource` during import; `takePersistableUriPermission` is not called and the URI is never persisted. The private copy is kept until the user deletes the document, and it is excluded from Android cloud backup.

## Consequences

- Imported books survive the original being deleted, moved, or going offline.
- Storage usage equals the original file size; the 100 MiB source limit bounds this.
- Duplicate imports are detected by SHA-256 before a second copy is retained.
- The private copy is the recovery source for re-parsing or diagnostics.

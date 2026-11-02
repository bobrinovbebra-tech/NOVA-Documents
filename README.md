# NOVA Documents

An offline-first Android document library with a clean Office-inspired interface.

## Current milestone
- Android app foundation using Kotlin and Jetpack Compose
- Android document picker (no broad storage permission)
- Local document library and favorites
- Built-in PDF viewing using Android PdfRenderer
- No account, ads, analytics, or backend
- GitHub Actions workflow to build a debug APK

## Supported formats
PDF viewing is the first implemented viewer. DOC/DOCX, XLS/XLSX and PPT/PPTX are planned next; they are not yet supported by the in-app viewer.

## Build
Push to `main` or manually run **Actions → Build NOVA APK**.
Download the `NOVA-Documents-debug` artifact from the workflow run.

Package: `com.nova.documents`

# NOVA Documents

An offline-first Android document reader with an Office-inspired interface.

## Current implementation
- Kotlin + Jetpack Compose Android app
- Document picker and locally saved library
- Recent documents and favorites
- Built-in PDF page viewer using Android PdfRenderer
- Offline text/content preview for modern DOCX, XLSX, PPTX and TXT files
- No account, advertising, analytics, or backend
- GitHub Actions workflow for debug APK builds

## Office preview limitations
The Office reader extracts readable text and basic cell values from modern Office Open XML files. It does not edit documents or reproduce all Word page layout, Excel formatting/formulas/charts, or PowerPoint animations. Legacy binary DOC/XLS/PPT and RTF are not yet implemented.

## Build
Push to `main` or run **Actions → Build NOVA APK**. Download the `NOVA-Documents-debug` artifact from the workflow run.

Package: `com.nova.documents`

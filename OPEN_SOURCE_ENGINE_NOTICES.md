# Open-source document engine notices

## PDFBox-Android

- Artifact: `com.tom-roush:pdfbox-android:2.0.27.0`
- License: Apache License 2.0
- Upstream: https://github.com/TomRoush/PdfBox-Android
- Scope in this app: PDF page-object operations (delete, reorder, insert blank page, rotate, extract and merge).

## Important capability boundaries

PDFBox-Android is a PDF object/content library, not a full visual-layout editor. The current integration does not claim arbitrary in-place editing of every existing text run, perfect automatic font matching, OCR of all writing systems, or complete preservation of every vendor-specific PDF extension. Scanned pages still require OCR. Font substitution, unusual encodings, outlined text, encryption, signatures and complex forms may require special handling.

The PDF editor must preserve original PDF page content when applying supported page operations. Any rasterized export path must be clearly identified as a flattened copy and must not silently replace the structure-preserving path.

## Office formats

Apache POI is under evaluation for structured DOCX/XLSX/PPTX editing. It is not yet integrated into the editing workflow. Existing XML extraction screens remain viewers, not full Office editors, until round-trip editing and regression tests are implemented.

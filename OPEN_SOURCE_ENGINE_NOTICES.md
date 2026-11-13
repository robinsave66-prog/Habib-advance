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


## Offline OCR

- Android wrapper: Tesseract4Android 4.9.0, Apache License 2.0.
- OCR engine: Tesseract 5.5.1 with Leptonica 1.85.0.
- Language models: official `tessdata_fast` Bengali (`ben`) and English (`eng`) models from commit `87416418657359cb625c412a48b6e1d6d41c29bd`, Apache License 2.0.
- Models are embedded in the APK at build time. The app copies them to private storage on first OCR use; recognition then works offline.
- Supported choices: English, Bengali, and Bengali + English. Arabic OCR is not included.
- Sources: https://github.com/adaptech-cz/Tesseract4Android and https://github.com/tesseract-ocr/tessdata_fast

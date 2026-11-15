# Open-source document engine notices

## PDFBox-Android

- Artifact: `com.tom-roush:pdfbox-android:2.0.27.0`
- License: Apache License 2.0
- Upstream: https://github.com/TomRoush/PdfBox-Android
- Scope: PDF rendering support, page operations, native content-stream text replacement for a conservative supported subset, image/text overlays, signatures, password protection, and secure raster redaction.

### PDF editing boundaries

- Native text replacement is deliberately conservative: it requires a unique supported Latin-text match in one page text-show operation, an encodable active font, and an identical measured text advance. It supports a phrase spanning adjacent strings in one `TJ` array when no numeric kerning adjustment separates those strings.
- Bengali and other complex-script native replacement are not supported. The Bengali fallback is an explicitly destructive redaction-and-raster-overlay workflow; it flattens every page and makes replacement text an image, not searchable text.
- Form XObjects, Type 3 fonts, scanned pages without OCR text, subset/custom encodings, and many vendor-specific PDF structures may require special handling.
- Secure redaction creates a flattened copy. It removes searchable text and vector content throughout the document; it must never be presented as a structure-preserving edit.
- Signature placement uses adjustable normalized position and width controls. It is not a freehand drag-placement tool.

## Office formats

- DOCX, XLSX, and PPTX editing uses native Open XML package updates.
- DOCX supports exact text replacement in document paragraphs, headers/footers, footnotes and endnotes while retaining run formatting.
- XLSX supports worksheet cell values/formulas and bounded cell/row insertion. Formula recalculation is performed by a compatible spreadsheet app when opened.
- PPTX supports exact text replacement in existing slide text runs.
- These are structured text/cell editing workflows, not complete visual-layout editors. Arbitrary drawing, slide, table, page-layout and object manipulation is not claimed.

## Offline OCR

- Android wrapper: Tesseract4Android 4.9.0, Apache License 2.0.
- OCR engine: Tesseract 5.5.1 with Leptonica 1.85.0.
- Language models: official `tessdata_fast` Bengali (`ben`) and English (`eng`) models from commit `87416418657359cb625c412a48b6e1d6d41c29bd`, Apache License 2.0.
- Models are embedded in the APK at build time and copied to private storage on first OCR use; recognition then works offline.
- Supported choices: English, Bengali, and Bengali + English. Arabic OCR is not included.
- Sources: https://github.com/adaptech-cz/Tesseract4Android and https://github.com/tesseract-ocr/tessdata_fast

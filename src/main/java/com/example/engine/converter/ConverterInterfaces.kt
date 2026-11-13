package com.example.engine.converter

import android.net.Uri

sealed interface ConversionStatus {
    data object Idle : ConversionStatus
    data class InProgress(val progressPercent: Int, val message: String) : ConversionStatus
    data class Completed(val outputUri: Uri, val outputFileSizeBytes: Long) : ConversionStatus
    data class Failed(val reason: String) : ConversionStatus
}

interface IDocumentConverter {
    val supportedSourceFormats: List<String>
    val targetFormat: String
    val isImplementedInCurrentPhase: Boolean
    suspend fun convert(sourceUri: Uri, outputUri: Uri, onProgress: (Int, String) -> Unit): ConversionStatus
}

interface IPdfToDocxConverter : IDocumentConverter
interface IDocxToPdfConverter : IDocumentConverter
interface IPdfToJpgConverter : IDocumentConverter
interface IPdfToPngConverter : IDocumentConverter
interface IJpgToPdfConverter : IDocumentConverter
interface IPngToPdfConverter : IDocumentConverter
interface IXlsxToPdfConverter : IDocumentConverter
interface IPptxToPdfConverter : IDocumentConverter
interface IPdfToTxtConverter : IDocumentConverter

class PlannedConverter(
    override val supportedSourceFormats: List<String>,
    override val targetFormat: String,
    val plannedPhase: String = "Phase 2"
) : IDocumentConverter {
    override val isImplementedInCurrentPhase: Boolean = false

    override suspend fun convert(
        sourceUri: Uri,
        outputUri: Uri,
        onProgress: (Int, String) -> Unit
    ): ConversionStatus {
        return ConversionStatus.Failed(
            "This conversion pipeline is scheduled for $plannedPhase. Core engine interfaces and SAF endpoints are prepared."
        )
    }
}

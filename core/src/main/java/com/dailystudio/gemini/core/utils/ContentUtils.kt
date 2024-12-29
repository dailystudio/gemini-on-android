package com.dailystudio.gemini.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.dailystudio.devbricksx.GlobalContextWrapper
import com.dailystudio.devbricksx.development.Logger
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.resume


object ContentUtils {

    fun uriToBitmap(context: Context,
                    uri: Uri): Bitmap? {
        var stream: InputStream? = null

        return try {
            stream = context.contentResolver?.openInputStream(uri)

            stream?.let {
                BitmapFactory.decodeStream(it)
            }
        } catch (e: IOException) {
            Logger.error("failed to extract text from pdf [${uri}]: $e")
            null
        } finally {
            stream?.close()
        }
    }
    suspend fun extractTextFromImage(imagePath: String): String? = suspendCancellableCoroutine {
        var stream: InputStream? = null
        try {
            stream =
                GlobalContextWrapper.context?.contentResolver?.openInputStream(
                    Uri.parse(imagePath)
                )
            val bitmap: Bitmap = BitmapFactory.decodeStream(stream)

            val image = InputImage.fromBitmap(bitmap, 0)
            val recognizer = TextRecognition.getClient(
                ChineseTextRecognizerOptions.Builder().build()
//                TextRecognizerOptions.DEFAULT_OPTIONS
            )

            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    it.resume(visionText.text)
                }
        } catch (e: IOException) {
            Logger.error("failed to extract text from image [${imagePath}]: $e")

            it.resume(null)
        }
    }


    fun pdfToBitmap(context: Context,
                    pdfUri: Uri): Bitmap? {
        var stream: InputStream? = null
        var pdfDocument: PDDocument? = null

        return try {
            stream = context.contentResolver?.openInputStream(pdfUri)

            stream?.let {
                pdfDocument = PDDocument.load(it)
                val renderer = PDFRenderer(pdfDocument)

                renderer.renderImageWithDPI(0, 300f) // 300 DPI for better quality
            }
        } catch (e: IOException) {
            Logger.error("failed to extract text from pdf [${pdfUri}]: $e")
            null
        } finally {
            pdfDocument?.close()
            stream?.close()
        }
    }

    fun pdfToBitmapNative(context: Context,
                          pdfUri: Uri): Bitmap? {

        val fd: ParcelFileDescriptor? =
            context.contentResolver.openFileDescriptor(pdfUri, "r")

        return try {
            fd?.let {
                val pdfRenderer = PdfRenderer(fd)
                val page = pdfRenderer.openPage(0)

                val bitmap = Bitmap.createBitmap(
                    page.width, page.height,
                    Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()
                pdfRenderer.close()

                bitmap
            }
        } catch (e: IOException) {
            Logger.error("failed to extract text from pdf [${pdfUri}]: $e")
            null
        } finally {
            fd?.close()
        }
    }

    fun extractTextFromPdf(pdfPath: String): String? {
        // Using the `PdfDocument` class
        var stream: InputStream? = null
        var pdfDocument: PDDocument? = null

        return try {
            stream =
                GlobalContextWrapper.context?.contentResolver?.openInputStream(
                    Uri.parse(pdfPath)
                )

            stream?.let {
                pdfDocument = PDDocument.load(it)
                val pdfStripper = PDFTextStripper()

                pdfStripper.getText(pdfDocument)
            }
        } catch (e: IOException) {
            Logger.error("failed to extract text from pdf [${pdfPath}]: $e")
            null
        } finally {
            pdfDocument?.close()
            stream?.close()
        }
    }
}
package io.github.teamomuito.octopotato.scan

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Reads the words and barcodes in a screenshot, on the phone, with ML Kit. */
class Reader(private val resolver: ContentResolver) : Closeable {

    data class Reading(val text: String, val codes: List<FoundCode>)

    private val words = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val barcodes = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE, Barcode.FORMAT_AZTEC, Barcode.FORMAT_PDF417, Barcode.FORMAT_DATA_MATRIX)
            .build()
    )

    suspend fun read(uri: Uri): Reading {
        val text = StringBuilder()
        val codes = ArrayList<FoundCode>()
        eachSlice(uri) { bitmap ->
            val image = InputImage.fromBitmap(bitmap, 0)
            val found = words.process(image).await().text
            if (found.isNotBlank()) text.appendLine(found)
            val area = bitmap.width.toFloat() * bitmap.height
            for (b in barcodes.process(image).await()) {
                val box = b.boundingBox
                val share = if (box != null && area > 0) box.width() * box.height() / area else 0f
                codes += FoundCode(formatOf(b.format), b.rawValue, share)
            }
        }
        return Reading(text.toString().trim(), codes)
    }

    /**
     * Normal screenshots are read in one go. Long scrolling ones get cut into phone-sized
     * slices, which keeps memory in check and gives ML Kit text at a size it likes.
     */
    private suspend fun eachSlice(uri: Uri, block: suspend (Bitmap) -> Unit) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) throw IllegalStateException("can't decode $uri")

        if (h <= w * 3) {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                if (w > MAX_WIDTH) decoder.setTargetSampleSize(2)
            }
            try {
                block(bitmap)
            } finally {
                bitmap.recycle()
            }
            return
        }

        val input = resolver.openInputStream(uri) ?: return
        input.use { stream ->
            @Suppress("DEPRECATION")
            val decoder = BitmapRegionDecoder.newInstance(stream, false) ?: return
            try {
                val options = BitmapFactory.Options().apply { inSampleSize = if (w > MAX_WIDTH) 2 else 1 }
                val slice = w * 2
                val overlap = w / 8
                var top = 0
                while (top < h) {
                    val bottom = minOf(h, top + slice)
                    val bitmap = decoder.decodeRegion(Rect(0, top, w, bottom), options)
                    if (bitmap != null) {
                        try {
                            block(bitmap)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                    if (bottom == h) break
                    top = bottom - overlap
                }
            } finally {
                decoder.recycle()
            }
        }
    }

    override fun close() {
        words.close()
        barcodes.close()
    }

    private fun formatOf(format: Int) = when (format) {
        Barcode.FORMAT_QR_CODE -> CodeFormat.QR
        Barcode.FORMAT_AZTEC -> CodeFormat.AZTEC
        Barcode.FORMAT_PDF417 -> CodeFormat.PDF417
        Barcode.FORMAT_DATA_MATRIX -> CodeFormat.DATA_MATRIX
        else -> CodeFormat.OTHER
    }

    private companion object {
        const val MAX_WIDTH = 1600
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        val error = task.exception
        when {
            error != null -> cont.resumeWithException(error)
            task.isCanceled -> cont.cancel()
            else -> cont.resume(task.result)
        }
    }
}

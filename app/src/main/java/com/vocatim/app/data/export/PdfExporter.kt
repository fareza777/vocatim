package com.vocatim.app.data.export

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.IOException

object PdfExporter {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48
    private const val LINE_HEIGHT = 18f

    fun write(
        context: Context,
        uri: Uri,
        title: String,
        body: String,
        imagePaths: List<String> = emptyList(),
    ) {
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 16f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val maxWidth = PAGE_WIDTH - MARGIN * 2
        val lines = wrap(title, titlePaint, maxWidth) + listOf("") +
            wrap(body, paint, maxWidth)

        var pageNumber = 1
        var lineIndex = 0
        // A live page and cursor shared by text and images, so photos flow
        // right after the body instead of forcing blank pages.
        var page = document.startPage(pageInfo(pageNumber))
        var y = MARGIN.toFloat() + LINE_HEIGHT
        while (lineIndex < lines.size) {
            if (y >= PAGE_HEIGHT - MARGIN) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(pageInfo(pageNumber))
                y = MARGIN.toFloat() + LINE_HEIGHT
            }
            val line = lines[lineIndex]
            val p = if (pageNumber == 1 && lineIndex == 0) titlePaint else paint
            page.canvas.drawText(line, MARGIN.toFloat(), y, p)
            y += LINE_HEIGHT
            lineIndex++
        }

        // Append attached photos, each scaled to the text column width.
        for (path in imagePaths) {
            val bitmap = decodeScaled(path, maxWidth) ?: continue
            val drawHeight = bitmap.height * (maxWidth.toFloat() / bitmap.width)
            // A photo that can't fit the remaining space starts a fresh page.
            if (y + drawHeight > PAGE_HEIGHT - MARGIN) {
                document.finishPage(page)
                pageNumber++
                page = document.startPage(pageInfo(pageNumber))
                y = MARGIN.toFloat() + LINE_HEIGHT
            }
            val dest = Rect(
                MARGIN, y.toInt(),
                MARGIN + maxWidth, (y + drawHeight).toInt(),
            )
            page.canvas.drawBitmap(bitmap, null, dest, null)
            bitmap.recycle()
            y += drawHeight + LINE_HEIGHT
        }
        document.finishPage(page)

        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            document.writeTo(out)
        } ?: throw IOException("Couldn't open destination")
        document.close()
    }

    private fun pageInfo(pageNumber: Int) =
        PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()

    /** Decodes [path] downsampled to at most [targetWidth]px wide; null if unreadable. */
    private fun decodeScaled(path: String, targetWidth: Int): android.graphics.Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > targetWidth * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeFile(path, opts)
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Int): List<String> {
        if (text.isBlank()) return listOf("")
        val result = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            if (paragraph.isBlank()) {
                result.add("")
                return@forEach
            }
            var start = 0
            while (start < paragraph.length) {
                var end = paragraph.length
                while (end > start && paint.measureText(paragraph, start, end) > maxWidth) {
                    end--
                }
                if (end == start) end = minOf(start + 1, paragraph.length)
                result.add(paragraph.substring(start, end))
                start = end
            }
        }
        return result
    }
}

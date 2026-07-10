package com.vocatim.app.data.export

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
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
    private const val FOOTER_Y = PAGE_HEIGHT - 34f
    private const val BODY_BOTTOM = FOOTER_Y - 10f

    private val BRAND = Color.rgb(0x5B, 0x4B, 0xE0)   // VioletDeep
    private val MUTED = Color.rgb(0x6B, 0x70, 0x82)
    private val HAIRLINE = Color.rgb(0xD8, 0xDA, 0xE4)

    fun write(
        context: Context,
        uri: Uri,
        title: String,
        body: String,
        imagePaths: List<String> = emptyList(),
        meta: String? = null,
    ) {
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            color = BRAND
            letterSpacing = 0.18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val metaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            color = MUTED
        }
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f
            color = MUTED
        }
        val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = HAIRLINE
            strokeWidth = 0.8f
        }
        val maxWidth = PAGE_WIDTH - MARGIN * 2

        // No brand text in the footer: the store listing promises
        // watermark-free exports, and the user's document should be theirs.
        fun drawFooter(canvas: android.graphics.Canvas, pageNumber: Int) {
            canvas.drawLine(MARGIN.toFloat(), FOOTER_Y, (PAGE_WIDTH - MARGIN).toFloat(), FOOTER_Y, rulePaint)
            val label = "Page $pageNumber"
            val w = footerPaint.measureText(label)
            canvas.drawText(label, PAGE_WIDTH - MARGIN - w, FOOTER_Y + 15, footerPaint)
        }

        // Page 1 header: brand kicker, title, meta line, hairline rule.
        fun drawHeader(canvas: android.graphics.Canvas): Float {
            var y = MARGIN.toFloat() + 6
            canvas.drawText("VOCATIM", MARGIN.toFloat(), y, brandPaint)
            y += 24
            for (line in wrap(title, titlePaint, maxWidth)) {
                canvas.drawText(line, MARGIN.toFloat(), y, titlePaint)
                y += 26
            }
            if (!meta.isNullOrBlank()) {
                y += 2
                canvas.drawText(meta, MARGIN.toFloat(), y, metaPaint)
                y += 14
            }
            y += 8
            canvas.drawLine(MARGIN.toFloat(), y, (PAGE_WIDTH - MARGIN).toFloat(), y, rulePaint)
            return y + 22
        }

        val bodyLines = wrap(body, paint, maxWidth)
        var pageNumber = 1
        var page = document.startPage(pageInfo(pageNumber))
        var y = drawHeader(page.canvas)

        var lineIndex = 0
        while (lineIndex < bodyLines.size) {
            if (y >= BODY_BOTTOM) {
                drawFooter(page.canvas, pageNumber)
                document.finishPage(page)
                pageNumber++
                page = document.startPage(pageInfo(pageNumber))
                y = MARGIN.toFloat() + LINE_HEIGHT
            }
            page.canvas.drawText(bodyLines[lineIndex], MARGIN.toFloat(), y, paint)
            y += LINE_HEIGHT
            lineIndex++
        }

        // Append attached photos, each scaled to the text column width.
        for (path in imagePaths) {
            val bitmap = decodeScaled(path, maxWidth) ?: continue
            val drawHeight = bitmap.height * (maxWidth.toFloat() / bitmap.width)
            if (y + drawHeight > BODY_BOTTOM) {
                drawFooter(page.canvas, pageNumber)
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
        drawFooter(page.canvas, pageNumber)
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

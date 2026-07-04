package com.vocatim.app.data.export

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import java.io.IOException

object PdfExporter {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48
    private const val LINE_HEIGHT = 18f

    fun write(context: Context, uri: Uri, title: String, body: String) {
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
        while (lineIndex < lines.size) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            val page = document.startPage(pageInfo)
            var y = MARGIN.toFloat() + LINE_HEIGHT
            while (lineIndex < lines.size && y < PAGE_HEIGHT - MARGIN) {
                val line = lines[lineIndex]
                val p = if (pageNumber == 1 && lineIndex == 0) titlePaint else paint
                page.canvas.drawText(line, MARGIN.toFloat(), y, p)
                y += LINE_HEIGHT
                lineIndex++
            }
            document.finishPage(page)
            pageNumber++
        }

        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            document.writeTo(out)
        } ?: throw IOException("Couldn't open destination")
        document.close()
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

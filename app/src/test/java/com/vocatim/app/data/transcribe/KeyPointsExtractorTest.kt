package com.vocatim.app.data.transcribe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyPointsExtractorTest {

    @Test
    fun `short text returns all sentences`() {
        val text = "Rapat dimulai pukul sembilan pagi. Semua anggota tim sudah hadir lengkap."
        assertEquals(2, KeyPointsExtractor.extract(text, maxPoints = 5).size)
    }

    @Test
    fun `long text is reduced to max points in original order`() {
        val text = buildString {
            append("Proyek transkripsi offline menjadi prioritas utama kuartal ini. ")
            append("Cuaca hari ini cukup cerah dan menyenangkan sekali rasanya. ")
            append("Tim transkripsi akan merilis proyek aplikasi ke Play Store bulan depan. ")
            append("Ada banyak makanan ringan tersedia di ruang rapat belakang. ")
            append("Prioritas berikutnya proyek adalah fitur ringkasan transkripsi otomatis. ")
            append("Seseorang lupa mematikan lampu di koridor kemarin malam katanya. ")
        }
        val points = KeyPointsExtractor.extract(text, maxPoints = 3)

        assertEquals(3, points.size)
        // Topical sentences (sharing repeated words) outrank one-off fillers.
        assertTrue(points.any { it.contains("prioritas utama") })
        assertTrue(points.any { it.contains("ringkasan") })
        // Original order preserved.
        val first = points[0]
        assertTrue(text.indexOf(first) < text.indexOf(points[1]))
    }

    @Test
    fun `blank text returns empty`() {
        assertTrue(KeyPointsExtractor.extract("").isEmpty())
    }

    @Test
    fun `very short fragments are dropped`() {
        assertTrue(KeyPointsExtractor.extract("Ok. Ya. Baik.").isEmpty())
    }
}

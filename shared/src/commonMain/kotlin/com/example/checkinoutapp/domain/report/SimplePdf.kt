package com.example.checkinoutapp.domain.report

/**
 * A minimal PDF writer: A4 pages of text in Helvetica / Helvetica-Bold plus thin rules. It is
 * enough for a tabular report and needs no library, so reports work on every platform.
 *
 * Limit, stated plainly: the built-in PDF fonts cover Latin-1 only. Characters outside it
 * (Arabic, for example) are written as "?". The CSV exports keep every character.
 */
class SimplePdf {
    private val pages = mutableListOf<StringBuilder>()
    private var current = StringBuilder()

    init {
        pages += current
    }

    fun newPage() {
        current = StringBuilder()
        pages += current
    }

    /** [y] is measured from the top of the page, in points. */
    fun text(x: Double, y: Double, text: String, size: Double = 10.0, bold: Boolean = false) {
        current.append("BT /${if (bold) "F2" else "F1"} ${fmt(size)} Tf ${fmt(x)} ${fmt(PAGE_HEIGHT - y)} Td (${escape(text)}) Tj ET\n")
    }

    fun rule(x1: Double, y: Double, x2: Double, gray: Double = 0.8) {
        current.append("${fmt(gray)} G 0.5 w ${fmt(x1)} ${fmt(PAGE_HEIGHT - y)} m ${fmt(x2)} ${fmt(PAGE_HEIGHT - y)} l S\n")
    }

    fun build(): ByteArray {
        val out = StringBuilder("%PDF-1.4\n")
        val offsets = mutableListOf<Int>()
        fun obj(body: String) {
            offsets += out.length
            out.append("${offsets.size} 0 obj\n$body\nendobj\n")
        }
        // 1 catalog, 2 page tree, 3/4 fonts, then (page, content) pairs.
        val pageIds = pages.indices.map { 5 + it * 2 }
        obj("<< /Type /Catalog /Pages 2 0 R >>")
        obj("<< /Type /Pages /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] /Count ${pages.size} >>")
        obj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
        obj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>")
        pages.forEachIndexed { index, content ->
            obj("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${fmt(PAGE_WIDTH)} ${fmt(PAGE_HEIGHT)}] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents ${pageIds[index] + 1} 0 R >>")
            obj("<< /Length ${content.length} >>\nstream\n${content}endstream")
        }
        val xref = out.length
        out.append("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { out.append(it.toString().padStart(10, '0')).append(" 00000 n \n") }
        out.append("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        // Every character written is <= 0xFF (see escape), so one char is exactly one byte.
        return ByteArray(out.length) { out[it].code.toByte() }
    }

    private fun escape(text: String): String = buildString {
        for (raw in text) {
            val c = when (raw) {
                '–', '—' -> '-'
                '’', '‘' -> '\''
                '“', '”' -> '"'
                '…' -> '.'
                else -> raw
            }
            when {
                c == '(' || c == ')' || c == '\\' -> append('\\').append(c)
                c.code in 32..126 -> append(c)
                c.code in 160..255 -> append('\\').append(c.code.toString(8).padStart(3, '0'))
                else -> append('?')
            }
        }
    }

    private fun fmt(value: Double): String {
        val scaled = kotlin.math.round(value * 100).toLong()
        val whole = scaled / 100
        val frac = kotlin.math.abs(scaled % 100)
        return if (frac == 0L) whole.toString() else "$whole.${frac.toString().padStart(2, '0')}"
    }

    companion object {
        const val PAGE_WIDTH = 595.0
        const val PAGE_HEIGHT = 842.0
    }
}

package com.nova.documents

import android.net.Uri
import android.util.Xml
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser

/**
 * Lightweight, offline text/table extraction for modern Office Open XML files.
 * It intentionally presents readable content rather than reproducing complex layout.
 */
object OfficeReader {
    fun read(uri: Uri, fileName: String, open: (Uri) -> java.io.InputStream): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext == "txt") return open(uri).bufferedReader(Charsets.UTF_8).use { it.readText() }
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(open(uri)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && (
                    entry.name == "word/document.xml" ||
                    entry.name == "xl/sharedStrings.xml" ||
                    entry.name.startsWith("xl/worksheets/sheet") ||
                    entry.name.startsWith("ppt/slides/slide")
                )) {
                    entries[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        return when (ext) {
            "docx" -> readDocx(entries["word/document.xml"])
            "xlsx" -> readXlsx(entries)
            "pptx" -> readPptx(entries)
            else -> throw IllegalArgumentException("Формат .$ext пока не поддерживается встроенным просмотрщиком.")
        }
    }

    private fun parser(bytes: ByteArray): XmlPullParser =
        Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(bytes.inputStream(), "UTF-8")
        }

    private fun readDocx(xml: ByteArray?): String {
        requireNotNull(xml) { "В документе не найден основной текст." }
        val p = parser(xml)
        val out = StringBuilder()
        var event = p.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.TEXT) out.append(p.text)
            if (event == XmlPullParser.END_TAG) {
                when (p.name.substringAfter(':')) {
                    "tab" -> out.append('\t')
                    "br", "p" -> out.append('\n')
                }
            }
            event = p.next()
        }
        return out.toString().trim().ifBlank { "В документе не найден текст." }
    }

    private fun readXlsx(entries: Map<String, ByteArray>): String {
        val shared = mutableListOf<String>()
        entries["xl/sharedStrings.xml"]?.let { bytes ->
            val p = parser(bytes)
            var current: StringBuilder? = null
            var inText = false
            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && p.name.substringAfter(':') == "si") current = StringBuilder()
                if (event == XmlPullParser.START_TAG && p.name.substringAfter(':') == "t") inText = true
                if (event == XmlPullParser.TEXT && inText) current?.append(p.text)
                if (event == XmlPullParser.END_TAG && p.name.substringAfter(':') == "t") inText = false
                if (event == XmlPullParser.END_TAG && p.name.substringAfter(':') == "si") {
                    shared.add(current?.toString().orEmpty()); current = null
                }
                event = p.next()
            }
        }
        val sheets = entries.filterKeys { it.startsWith("xl/worksheets/sheet") }.toSortedMap()
        if (sheets.isEmpty()) return "В книге не найдены листы."
        val result = StringBuilder()
        sheets.forEach { (path, bytes) ->
            result.append("Лист ").append(path.substringAfterLast("sheet").substringBefore(".xml")).append('\n')
            val p = parser(bytes)
            var row = false
            var cell = false
            var cellType = ""
            var value = StringBuilder()
            var cellValue = ""
            var rowValues = mutableListOf<Pair<String, String>>()
            var cellRef = ""
            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                val tag = p.name?.substringAfter(':')
                if (event == XmlPullParser.START_TAG && tag == "row") {
                    row = true; rowValues = mutableListOf()
                }
                if (event == XmlPullParser.START_TAG && tag == "c") {
                    cell = true; value = StringBuilder()
                    cellRef = p.getAttributeValue(null, "r") ?: ""
                    cellType = p.getAttributeValue(null, "t") ?: ""
                }
                if (event == XmlPullParser.TEXT && cell) value.append(p.text)
                if (event == XmlPullParser.END_TAG && tag == "c") {
                    cellValue = value.toString()
                    if (cellType == "s") {
                        cellValue = shared.getOrNull(cellValue.toIntOrNull() ?: -1) ?: cellValue
                    }
                    if (cellType == "inlineStr") cellValue = value.toString()
                    if (cellValue.isNotBlank()) rowValues.add(cellRef to cellValue)
                    cell = false
                }
                if (event == XmlPullParser.END_TAG && tag == "row") {
                    if (rowValues.isNotEmpty()) {
                        result.append(rowValues.joinToString("    ") { (ref, v) -> "$ref: $v" }).append('\n')
                    }
                    row = false
                }
                event = p.next()
            }
            result.append('\n')
        }
        return result.toString().trim().ifBlank { "В книге нет отображаемых значений." }
    }

    private fun readPptx(entries: Map<String, ByteArray>): String {
        val slides = entries.filterKeys { it.startsWith("ppt/slides/slide") }
            .toSortedMap(compareBy { it.substringAfterLast("slide").substringBefore(".xml").toIntOrNull() ?: 0 })
        if (slides.isEmpty()) return "В презентации не найдены слайды."
        val result = StringBuilder()
        slides.entries.forEachIndexed { index, entry ->
            val bytes = entry.value
            result.append("Слайд ").append(index + 1).append('\n')
            val p = parser(bytes)
            var paragraphHasText = false
            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                val tag = p.name?.substringAfter(':')
                if (event == XmlPullParser.TEXT && paragraphHasText) result.append(p.text)
                if (event == XmlPullParser.START_TAG && tag == "t") paragraphHasText = true
                if (event == XmlPullParser.END_TAG && tag == "t") paragraphHasText = false
                if (event == XmlPullParser.END_TAG && tag == "p") result.append('\n')
                event = p.next()
            }
            result.append("\n\n")
        }
        return result.toString().trim()
    }
}

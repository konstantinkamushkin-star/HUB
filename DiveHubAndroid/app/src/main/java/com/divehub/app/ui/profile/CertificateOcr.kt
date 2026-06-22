package com.divehub.app.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

data class CertificateOcrResult(
    val organization: String? = null,
    val level: String? = null,
    val issueDateMillis: Long? = null,
    val instructorNumber: String? = null,
    val instructorName: String? = null,
    val certificateNumber: String? = null,
) {
    fun isNotEmpty() =
        listOf(organization, level, instructorNumber, instructorName, certificateNumber).any { !it.isNullOrBlank() } ||
        issueDateMillis != null
}

object CertificateOcr {
    private const val maxBitmapSide = 1600
    private const val minValidYear = 1990

    private val uiNoiseSubstrings = listOf(
        "ОТМЕНА", "СОХРАНИТЬ", "ДОБАВИТЬ С", "ДЕТАЛИ СЕРТИФИКАТА",
        "ОРГАНИЗАЦИЯ (", "УРОВЕНЬ (", "ДАТА ВЫДАЧИ", "НОМЕР ИНСТРУКТОРА",
        "ФОТО КАРТОЧКИ", "ИЗВЛЕЧЬ ДАННЫЕ", "НЕОБЯЗАТЕЛЬНО",
        "CANCEL", "SAVE", "CERTIFICATION DETAILS", "CARD PHOTO",
        "EXTRACT DATA", "ISSUE DATE", "INSTRUCTOR NUMBER",
        "НАПРИМЕР, PADI", "E.G., PADI", "OPEN WATER, ADVANCED",
    )

    suspend fun extractFromCardUri(context: Context, imageUri: Uri): CertificateOcrResult = withContext(Dispatchers.Default) {
        val bitmap = decodeBitmapForOcr(context, imageUri) ?: return@withContext CertificateOcrResult()
        try {
            val lines = mutableListOf<String>()
            for (angle in listOf(0, 90, 180, 270)) {
                val working = if (angle == 0) bitmap else rotateBitmap(bitmap, angle.toFloat())
                lines += runTextRecognition(working)
                if (angle != 0) working.recycle()
            }
            CertificateTextParser.parseFromLines(filterNoiseLines(lines.distinct()))
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun filterNoiseLines(lines: List<String>): List<String> =
        lines.filter { line ->
            val upper = line.uppercase(Locale.US)
            if (upper.length <= 2) return@filter false
            if (uiNoiseSubstrings.any { upper.contains(it) }) return@filter false
            if (upper.matches(Regex("""^\d{1,2}:\d{2}$"""))) return@filter false
            true
        }

    private fun decodeBitmapForOcr(context: Context, uri: Uri): Bitmap? {
        val b1 = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, b1) } ?: return null
        var sample = 1
        while (b1.outWidth / sample > maxBitmapSide || b1.outHeight / sample > maxBitmapSide) {
            sample *= 2
        }
        val b2 = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, b2)
        } ?: return null
        return applyExifOrientation(context, uri, decoded)
    }

    private fun applyExifOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val rotation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (_: Exception) {
            0f
        }
        return if (rotation == 0f) bitmap else rotateBitmap(bitmap, rotation)
    }

    private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return source
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private suspend fun runTextRecognition(bitmap: Bitmap): List<String> = withContext(Dispatchers.Default) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = client.process(image).await()
            result.text
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } finally {
            client.close()
        }
    }
}

/**
 * Heuristics aligned with iOS [OCRService].
 */
object CertificateTextParser {
    private const val minValidYear = 1990

    private val orgPhrases: List<Pair<Regex, String>> = listOf(
        "AUSTRALIAN\\s+DIVER\\s+ACCREDITATION\\s+SCHEME".toRegex(RegexOption.IGNORE_CASE) to "ADAS",
        "SCUBA\\s+SCHOOLS\\s+INTERNATIONAL".toRegex(RegexOption.IGNORE_CASE) to "SSI",
        "PROFESSIONAL\\s+ASSOCIATION\\s+OF\\s+DIVING\\s+INSTRUCTORS".toRegex(RegexOption.IGNORE_CASE) to "PADI",
        "NATIONAL\\s+ASSOCIATION\\s+OF\\s+UNDERWATER\\s+INSTRUCTORS".toRegex(RegexOption.IGNORE_CASE) to "NAUI",
        "CONFEDERATION\\s+MONDIALE\\s+DES\\s+ACTIVITES\\s+SUBAQUATIQUES".toRegex(RegexOption.IGNORE_CASE) to "CMAS",
        "BRITISH\\s+SUB\\s+AQUA\\s+CLUB".toRegex(RegexOption.IGNORE_CASE) to "BSAC",
        "SCUBA\\s+DIVING\\s+INTERNATIONAL".toRegex(RegexOption.IGNORE_CASE) to "SDI",
        "TECHNICAL\\s+DIVING\\s+INTERNATIONAL".toRegex(RegexOption.IGNORE_CASE) to "TDI",
        "GLOBAL\\s+UNDERWATER\\s+EXPLORERS".toRegex(RegexOption.IGNORE_CASE) to "GUE",
    )

    private val orgFuzzy = listOf(
        "T\\s*D\\s*I".toRegex(RegexOption.IGNORE_CASE) to "TDI",
        "T0I".toRegex(RegexOption.IGNORE_CASE) to "TDI",
        "P\\s*A\\s*D\\s*I".toRegex(RegexOption.IGNORE_CASE) to "PADI",
    )

    private val orgAbbr2 = listOf(
        "ADAS" to "ADAS", "SSI" to "SSI", "PADI" to "PADI", "NAUI" to "NAUI",
        "CMAS" to "CMAS", "BSAC" to "BSAC", "SDI" to "SDI", "TDI" to "TDI", "GUE" to "GUE",
    )

    private val levelRes: List<Pair<Regex, String>> = listOf(
        "OPEN\\s*\\.?\\s*WATER".toRegex(RegexOption.IGNORE_CASE) to "Open Water",
        "PRO(?:FESSIONAL)?\\s*OPEN\\s*WATER".toRegex(RegexOption.IGNORE_CASE) to "Open Water",
        "ADVANCED\\s+OPEN\\s+WATER".toRegex(RegexOption.IGNORE_CASE) to "Advanced Open Water",
        "ADVANCED\\s+NITROX".toRegex(RegexOption.IGNORE_CASE) to "Advanced Nitrox",
        "NITROX\\s+DIVER".toRegex(RegexOption.IGNORE_CASE) to "Nitrox Diver",
        "DECO(?:MPRESSION)?\\s+PROCEDURES".toRegex(RegexOption.IGNORE_CASE) to "Decompression Procedures",
        "INTRO(?:DUCTION)?\\s+TO\\s+TECH".toRegex(RegexOption.IGNORE_CASE) to "Intro to Tech",
        "TRIMIX".toRegex(RegexOption.IGNORE_CASE) to "Trimix Diver",
        "ADVANCED\\s+DIVER".toRegex(RegexOption.IGNORE_CASE) to "Advanced Diver",
        "RESCUE\\s+DIVER".toRegex(RegexOption.IGNORE_CASE) to "Rescue Diver",
        "DIVE\\s*MASTER".toRegex(RegexOption.IGNORE_CASE) to "Divemaster",
        "MASTER\\s+DIVER".toRegex(RegexOption.IGNORE_CASE) to "Master Diver",
        "ASSISTANT\\s+INSTRUCTOR".toRegex(RegexOption.IGNORE_CASE) to "Assistant Instructor",
        "MASTER\\s+INSTRUCTOR".toRegex(RegexOption.IGNORE_CASE) to "Master Instructor",
        "COURSE\\s+DIRECTOR".toRegex(RegexOption.IGNORE_CASE) to "Course Director",
        "INSTRUCT(?:OR|ER)".toRegex(RegexOption.IGNORE_CASE) to "Instructor",
    )

    private val certNumberPatterns = listOf(
        """(?:CERTIFICATION|CERT|C)\s*\.?\s*#?\s*NO\.?\s*[:\s]*([A-Z0-9][A-Z0-9\-/]{2,32})""".toRegex(RegexOption.IGNORE_CASE),
        """(?:C[-\s])?NUMBER\s*[:#]?\s*([A-Z0-9][A-Z0-9\-/]{2,32})""".toRegex(RegexOption.IGNORE_CASE),
        """MEMBER\s*(?:ID|#)\s*[:#]?\s*([0-9]{4,20})""".toRegex(RegexOption.IGNORE_CASE),
    )

    private val dateLinePatterns = listOf(
        """\b\d{1,2}[/\-.]\d{1,2}[/\-.]\d{2,4}\b""",
        """\b\d{4}[/\-.]\d{1,2}[/\-.]\d{1,2}\b""",
        """\b\d{1,2}[-.\s]*(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)[A-Z]*[-.\s]*\d{2,4}\b""",
        """\b(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)[A-Z]*\s+\d{1,2},?\s+\d{2,4}\b""",
        """\b\d{1,2}\s+(?:JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)[A-Z]*\s+\d{2,4}\b""",
    )

    private val excludeContext = listOf("D.O.B", "BIRTH", "EXPIR", "VALID UNTIL", "BIRTHDATE")
    private val goodKeys = listOf("CERT. DATE", "CERT DATE", "ISSUE", "ISSUED", "DATE OF CERT", "CERTIFICATION DATE")

    fun parseFromBlocks(plainText: String): CertificateOcrResult =
        parseFromLines(plainText.lines().map { it.trim() }.filter { it.isNotEmpty() })

    fun parseFromLines(lines: List<String>): CertificateOcrResult {
        if (lines.isEmpty()) return CertificateOcrResult()
        val t = lines.joinToString(" ").replace(Regex("""\s+"""), " ")
        if (t.isBlank()) return CertificateOcrResult()
        val u = t.uppercase(Locale.US)
        val lineUpper = lines.map { it.uppercase(Locale.US) }

        var org: String? = null
        for ((rx, name) in orgPhrases) {
            if (rx.containsMatchIn(u)) {
                org = name
                break
            }
        }
        if (org == null) {
            for ((rx, name) in orgFuzzy) {
                if (rx.containsMatchIn(u)) {
                    org = name
                    break
                }
            }
        }
        if (org == null) {
            for (pair in orgAbbr2) {
                if (isWholeWord(u, pair.first)) {
                    org = pair.second
                    break
                }
            }
        }
        if (org == null) {
            for (pair in orgAbbr2) {
                if (lineUpper.any { it == pair.first || (it.contains(pair.first) && it.length <= pair.first.length + 4) }) {
                    org = pair.second
                    break
                }
            }
        }

        var level: String? = null
        for ((pat, display) in levelRes) {
            if (!pat.containsMatchIn(u)) continue
            if (display == "Instructor" && instructorHeaderContext(u)) continue
            level = display
            break
        }

        var inst = extractInstructorNumberFull(u)

        val instName = extractInstructorNameFlexible(lines, u, org, inst)

        var certNo: String? = null
        for (p in certNumberPatterns) {
            val m = p.find(u)
            if (m != null && m.groupValues.size > 1) {
                val v = m.groupValues[1].trim()
                if (v.length >= 3) {
                    certNo = v
                    break
                }
            }
        }

        val date = extractIssueDate(u, t)
        return CertificateOcrResult(
            organization = org,
            level = level,
            issueDateMillis = date,
            instructorNumber = inst,
            instructorName = instName,
            certificateNumber = certNo,
        )
    }

    // If OCR shows instructor ID nearby, bare «INSTRUCTOR» is likely a header — not diver level «Instructor».
    private fun instructorHeaderContext(u: String): Boolean {
        if (!u.contains("INSTRUCTOR")) return false
        if (Regex("""INST\s*R?\.?\s*N[O0Ø]""", RegexOption.IGNORE_CASE).containsMatchIn(u)) return true
        if (Regex("""INSTRUCTOR\s*(NUMBER|NO|ID|[#№]|N\.?)""", RegexOption.IGNORE_CASE).containsMatchIn(u)) return true
        return false
    }

    private fun extractInstructorNumberFull(fullTextUpper: String): String? {
        val instructorPatterns = listOf(
            """INST\.?\s*R\.?\s*N[O0º]\.?\s*[:>#№]?\s*([A-Z0-9]{3,22})\b""",
            """INST\.?\s*N[O0]\.?\s*[:>#№]?\s*([A-Z0-9]{3,22})\b""",
            """INSTRUCTOR\s*(?:NUMBER|NO\.?|ID\.?|N[O0]\.?)\s*[:>#№]?\s*([A-Z0-9\-]{3,22})\b""",
            """INSTRUCTOR\s*[#№]\s*([A-Z0-9\-]{3,22})\b""",
            """INSTR(?:UCTOR)?\s*ID\s*[:#]?\s*([A-Z0-9\-]{3,22})\b""",
            """INSTRUCTOR[^A-Z0-9]{0,12}([A-Z]{1,4}-?\d{3,12})\b""",
            """INSTRUCTOR[^A-Z0-9]{0,12}(\d{5,12})\b""",
            """ИНСТР(?:УКТОР)?[^А-ЯA-Z0-9]{0,8}(?:№|N[O.]?|#)\s*[:.]?\s*([A-ZА-Я0-9\-]{3,22})\b""",
        )
        for (pat in instructorPatterns) {
            val m = pat.toRegex(RegexOption.IGNORE_CASE).find(fullTextUpper) ?: continue
            if (m.groupValues.size < 2) continue
            val v = m.groupValues[1].trim()
            if (v.length >= 3 && !v.all { it == '0' || it == '-' }) return v
        }
        val idx = fullTextUpper.indexOf("INSTRUCTOR")
        if (idx >= 0) {
            val window = fullTextUpper.substring(idx + "INSTRUCTOR".length).take(90)
            Regex("""#\s*([A-Z0-9]{4,22})\b""", RegexOption.IGNORE_CASE).find(window)?.groupValues?.get(1)?.let { return it }
            Regex("""\b([0-9]{5,14})\b""").find(window)?.groupValues?.get(1)?.let { v ->
                if (v.toLongOrNull() != null && v.length >= 5) return v
            }
        }
        return null
    }

    private fun extractInstructorNameFlexible(
        lines: List<String>,
        uppercaseFlat: String,
        organization: String?,
        existingNumber: String?,
    ): String? {
        val namePatterns = listOf(
            """INSTRUCTOR(?:\s+NAME)?\s*[>:.]\s*([^|\n#/]+)""",
            """TRAINING\s+DIRECTOR\s*[>:.]\s*([^|\n#/]+)""",
            """CERTIFIED\s+BY\s*[>:.]\s*([^|\n#/]+)""",
            """FACULT(?:Y|\s+MEMBER)\s*[>:.]\s*([^|\n#/]+)""",
            """ФИО\s+(?:ИНСТРУКТОРА|PREPOD)\s*[>:.]\s*([^|\n#/]+)""",
            """ИНСТРУКТОР(?:\s+ФИО)?\s*[>:.]\s*([^|\n#/]+)""",
        )
        var nameFromLabel: String? = null
        for (pat in namePatterns) {
            val m = pat.toRegex(RegexOption.IGNORE_CASE).find(uppercaseFlat) ?: continue
            if (m.groupValues.size < 2) continue
            val raw = m.groupValues[1].trim().uppercase(Locale.US)
            val cleaned = cleanInstructorLabelValue(raw)
            val resolved = normalizedPersonNameFromUpper(cleaned)
            if (resolved != null) {
                nameFromLabel = resolved
                break
            }
        }
        val nameNearby = if (nameFromLabel == null) extractInstructorNameFromNextLine(lines, existingNumber) else null
        val name = nameFromLabel ?: nameNearby
        val orgU = organization?.uppercase(Locale.US)
        if (name != null && orgU != null && name.uppercase(Locale.US) == orgU) return null
        return name?.takeIf { it.isNotBlank() }
    }

    private fun extractInstructorNameFromNextLine(lines: List<String>, existingNumber: String?): String? {
        val upLines = lines.map { it.trim().uppercase(Locale.US) }
        for ((i, lineU) in upLines.withIndex()) {
            val looksLikeInstLine =
                (Regex("""INST\s*R?\.?""", RegexOption.IGNORE_CASE).containsMatchIn(lineU) &&
                    Regex("""N[OØ0º]|NUMBER|ID|[#№]""", RegexOption.IGNORE_CASE).containsMatchIn(lineU)) ||
                    Regex("""INSTRUCTOR\s*(NUMBER|NO|ID|[#№])""", RegexOption.IGNORE_CASE).containsMatchIn(lineU) ||
                    (lineU.contains("ИНСТР") && Regex("""№|N[O.]?\s|\#""", RegexOption.IGNORE_CASE).containsMatchIn(lineU))
            if (!looksLikeInstLine || i + 1 >= lines.size) continue
            val next = lines[i + 1].trim()
            val n = normalizedPersonNameFromOriginal(next) ?: continue
            if (existingNumber != null && next.uppercase(Locale.US).contains(existingNumber)) continue
            return n
        }
        return null
    }

    private fun cleanInstructorLabelValue(s: String): String {
        var t = s.trim()
        Regex("""(\s+[##№.]+\s*[0-9A-Z\-]+)\s*$""").find(t)?.let { r ->
            t = t.removeRange(r.range).trim()
        }
        return t.trim()
    }

    private fun normalizedPersonNameFromOriginal(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.length !in 4..72) return null
        if (Regex("""^\d""").containsMatchIn(trimmed)) return null
        if (Regex("""^\d{1,4}[/\-]\d""").containsMatchIn(trimmed)) return null
        val upper = trimmed.uppercase(Locale.US)
        if (!matchesPersonName(trimmed, upper)) return null
        return formatPersonName(trimmed)
    }

    private fun normalizedPersonNameFromUpper(upper: String): String? {
        val t = upper.trim()
        if (t.length < 4) return null
        if (Regex("""^\d""").containsMatchIn(t)) return null
        if (!matchesPersonName(t, t)) return null
        return formatPersonName(t)
    }

    private fun matchesPersonName(raw: String, upper: String): Boolean {
        val blacklist = listOf(
            "TDI", "PADI", "SSI", "NAUI", "SDI", "GUE", "CMAS", "BSAC", "ADAS", "DIVING", "INTERNATIONAL",
            "CERTIFICATE", "CERTIFICATION", "ADVANCED", "NITROX", "INSTRUCTOR", "MEMBER", "LICENSE", "OPEN WATER",
            "TECHNICAL", "STUDENT", "DIVEMASTER", "COURSE", "EXPIR", "VALID", "ISSUED",
        )
        for (w in blacklist) {
            if (upper == w || upper.startsWith("$w ") || upper.endsWith(" $w") || upper.contains(" $w ")) return false
        }
        return Regex("""^[\p{L}][\p{L}\-'\.]+(?:\s+[\p{L}][\p{L}\-'\.]+){1,4}$""").matches(raw)
    }

    private fun formatPersonName(upperOrMixed: String): String =
        upperOrMixed.split(Regex("""\s+""")).joinToString(" ") { part ->
            if (part.isEmpty()) return@joinToString part
            part.first().uppercase(Locale.getDefault()) + part.drop(1).lowercase(Locale.getDefault())
        }

    private fun isWholeWord(s: String, w: String): Boolean {
        val p = """(?<![A-Z0-9])${Regex.escape(w)}(?![A-Z0-9])""".toRegex()
        return p.containsMatchIn(s)
    }

    private fun extractIssueDate(upper: String, orig: String): Long? {
        for (key in goodKeys) {
            val i = upper.indexOf(key, ignoreCase = true)
            if (i < 0) continue
            val end = (i + 120).coerceAtMost(upper.length)
            if (i >= end) continue
            val sub = orig.substring(i, end)
            for (pat in dateLinePatterns) {
                val r = pat.toRegex(RegexOption.IGNORE_CASE)
                val mInSub = r.find(sub)
                val m = mInSub ?: r.find(upper) ?: continue
                val s = m.value
                val pos = if (mInSub != null) i + m.range.first else m.range.first
                if (isExcludedContext(upper, pos)) continue
                parseToMillis(s.trim())?.let { return it }
                mergePartialDate(s.trim(), orig.substring(m.range.last + 1).take(20))?.let { return it }
            }
        }
        for (m in dateLinePatterns.flatMap { pat -> pat.toRegex(RegexOption.IGNORE_CASE).findAll(upper).asIterable() }) {
            if (isExcludedContext(upper, m.range.first)) continue
            parseToMillis(m.value.trim())?.let { return it }
        }
        return null
    }

    private fun mergePartialDate(partial: String, tail: String): Long? {
        val year4 = Regex("""\d{4}""").find(tail) ?: return null
        return parseToMillis("$partial ${year4.value}")
    }

    private fun isExcludedContext(t: String, pos: Int): Boolean {
        val a = (pos - 30).coerceAtLeast(0)
        val b = (pos + 40).coerceAtMost(t.length)
        val ctx = t.substring(a, b)
        return excludeContext.any { ctx.contains(it, ignoreCase = true) }
    }

    private val parseFormatters: Array<DateTimeFormatter> = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US),
        DateTimeFormatter.ofPattern("d/M/yyyy", Locale.US),
        DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.UK),
        DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.UK),
        DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT),
        DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d-MMM-yy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("dd MMM yy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MMM d yy", Locale.ENGLISH),
    ).toTypedArray()

    private fun parseToMillis(raw: String): Long? {
        val normalized = raw.trim().replace(",", "").replace(Regex("""\s+"""), " ")
        for (f in parseFormatters) {
            try {
                var d = LocalDate.parse(normalized, f)
                if (f.toString().contains("yy") && !f.toString().contains("yyyy")) {
                    d = expandTwoDigitYear(d)
                }
                if (!isValidCertificateYear(d.year)) return null
                return d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
            }
        }
        return null
    }

    private fun expandTwoDigitYear(date: LocalDate): LocalDate {
        val y = date.year
        if (y >= 100) return date
        val expanded = if (y <= 30) 2000 + y else 1900 + y
        return date.withYear(expanded)
    }

    private fun isValidCertificateYear(year: Int): Boolean {
        val currentYear = LocalDate.now().year
        return year in minValidYear..(currentYear + 1)
    }
}

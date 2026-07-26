package com.example.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.CompetitionEntity
import com.example.data.GroupEntity
import com.example.data.StudentEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportManager {

    // --- CSV (EXCEL) GENERATION ---

    fun generateStudentsCsv(students: List<StudentEntity>, groups: List<GroupEntity>): String {
        val groupMap = groups.associateBy { it.id }
        val sb = StringBuilder()
        sb.append("Student ID,Name,Group Name\n")
        students.forEach { s ->
            val gName = groupMap[s.groupId]?.name ?: "No Group"
            sb.append("${s.id},\"${escapeCsv(s.name)}\",\"${escapeCsv(gName)}\"\n")
        }
        return sb.toString()
    }

    fun generateCompetitionsCsv(competitions: List<CompetitionEntity>): String {
        val sb = StringBuilder()
        sb.append("Competition ID,Competition Name\n")
        competitions.forEach { c ->
            sb.append("${c.id},\"${escapeCsv(c.name)}\"\n")
        }
        return sb.toString()
    }

    fun generateResultsCsv(
        competitions: List<CompetitionEntity>,
        students: List<StudentEntity>,
        groups: List<GroupEntity>
    ): String {
        val studentMap = students.associateBy { it.id }
        val groupMap = groups.associateBy { it.id }
        
        val sb = StringBuilder()
        sb.append("Competition,1st Place,1st Group,1st Grade,1st Marks,2nd Place,2nd Group,2nd Grade,2nd Marks,3rd Place,3rd Group,3rd Grade,3rd Marks\n")
        
        competitions.forEach { c ->
            val s1 = studentMap[c.firstStudentId]
            val g1 = s1?.let { groupMap[it.groupId] }
            val s2 = studentMap[c.secondStudentId]
            val g2 = s2?.let { groupMap[it.groupId] }
            val s3 = studentMap[c.thirdStudentId]
            val g3 = s3?.let { groupMap[it.groupId] }

            sb.append("\"${escapeCsv(c.name)}\",")
            sb.append("\"${escapeCsv(s1?.name ?: "Vacant")}\",\"${escapeCsv(g1?.name ?: "")}\",\"${c.firstGrade ?: ""}\",${c.firstMarks ?: 0},")
            sb.append("\"${escapeCsv(s2?.name ?: "Vacant")}\",\"${escapeCsv(g2?.name ?: "")}\",\"${c.secondGrade ?: ""}\",${c.secondMarks ?: 0},")
            sb.append("\"${escapeCsv(s3?.name ?: "Vacant")}\",\"${escapeCsv(g3?.name ?: "")}\",\"${c.thirdGrade ?: ""}\",${c.thirdMarks ?: 0}\n")
        }
        return sb.toString()
    }

    fun generateAllDataCsv(
        groupScores: List<GroupScore>,
        students: List<StudentEntity>,
        groups: List<GroupEntity>,
        competitions: List<CompetitionEntity>
    ): String {
        val sb = StringBuilder()
        
        // Section 1: Team Standings
        sb.append("TEAM / GROUP SCORES & STANDINGS\n")
        sb.append("Rank,Group Name,Total Points\n")
        groupScores.forEachIndexed { index, gs ->
            sb.append("${index + 1},\"${escapeCsv(gs.group.name)}\",${gs.totalPoints}\n")
        }
        sb.append("\n\n")

        // Section 2: Student List
        sb.append("STUDENT ROSTER\n")
        sb.append(generateStudentsCsv(students, groups))
        sb.append("\n\n")

        // Section 3: Competition List
        sb.append("COMPETITION EVENTS\n")
        sb.append(generateCompetitionsCsv(competitions))
        sb.append("\n\n")

        // Section 4: Full Winner Standings
        sb.append("COMPETITION WINNERS & GRADES REPORT\n")
        sb.append(generateResultsCsv(competitions, students, groups))
        
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        return value.replace("\"", "\"\"")
    }

    // --- CSV SHARING AND SAVING ---

    fun shareCsv(context: Context, csvContent: String, title: String) {
        try {
            val cachePath = File(context.cacheDir, "shared_images")
            cachePath.mkdirs()
            val file = File(cachePath, "$title.csv")
            FileOutputStream(file).use { out ->
                out.write(csvContent.toByteArray(Charsets.UTF_8))
            }

            val contentUri = FileProvider.getUriForFile(context, "com.example.scoreboard.fileprovider", file)
            if (contentUri != null) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share CSV Excel Report"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error sharing Excel: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveCsvToDownloads(context: Context, csvContent: String, title: String): Boolean {
        val filename = "${title.replace(" ", "_")}_${System.currentTimeMillis()}.csv"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Scoreboard")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            try {
                resolver.openOutputStream(uri).use { outStream ->
                    if (outStream != null) {
                        outStream.write(csvContent.toByteArray(Charsets.UTF_8))
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                }
                return true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return false
    }


    // --- PDF GENERATION ENGINE ---

    fun exportStudentsPdf(
        context: Context,
        madrasaName: String,
        eventName: String,
        students: List<StudentEntity>,
        groups: List<GroupEntity>,
        isShare: Boolean
    ) {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1

        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        
        val groupMap = groups.associateBy { it.id }

        // Paints
        val textPaint = Paint().apply { isAntiAlias = true; color = Color.BLACK; textSize = 11f }
        val boldPaint = Paint().apply { isAntiAlias = true; color = Color.BLACK; textSize = 11f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val titlePaint = Paint().apply { isAntiAlias = true; color = Color.parseColor("#022C22"); textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val headerLabelPaint = Paint().apply { isAntiAlias = true; color = Color.parseColor("#D4AF37"); textSize = 10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); letterSpacing = 0.1f }
        val borderPaint = Paint().apply { isAntiAlias = true; color = Color.parseColor("#CCCCCC"); style = Paint.Style.STROKE; strokeWidth = 0.5f }
        val thBgPaint = Paint().apply { isAntiAlias = true; color = Color.parseColor("#F1F5F9"); style = Paint.Style.FILL }

        var currentY = 50f

        fun drawPdfHeader() {
            // Draw elegant top bar line
            val topBarPaint = Paint().apply { color = Color.parseColor("#022C22") }
            canvas.drawRect(40f, 40f, 555f, 45f, topBarPaint)
            
            currentY = 70f
            canvas.drawText(madrasaName.uppercase(), 40f, currentY, headerLabelPaint)
            currentY += 24f
            canvas.drawText("STUDENT ROSTER REPORT", 40f, currentY, titlePaint)
            currentY += 16f
            textPaint.apply { textSize = 10f; color = Color.GRAY }
            canvas.drawText("Event: $eventName | Generated on: ${getFormattedDate()}", 40f, currentY, textPaint)
            
            currentY += 25f
            // Separator double-line
            canvas.drawLine(40f, currentY, 555f, currentY, borderPaint)
            currentY += 30f

            // Draw Table Headers
            canvas.drawRect(40f, currentY - 18f, 555f, currentY + 8f, thBgPaint)
            canvas.drawLine(40f, currentY - 18f, 555f, currentY - 18f, borderPaint)
            canvas.drawLine(40f, currentY + 8f, 555f, currentY + 8f, borderPaint)
            
            canvas.drawText("S.No", 50f, currentY, boldPaint)
            canvas.drawText("Student ID", 110f, currentY, boldPaint)
            canvas.drawText("Student Name", 200f, currentY, boldPaint)
            canvas.drawText("Group / Team", 420f, currentY, boldPaint)
            currentY += 25f
        }

        drawPdfHeader()

        students.forEachIndexed { idx, s ->
            if (currentY > 780f) {
                doc.finishPage(page)
                pageNumber++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                drawPdfHeader()
            }

            textPaint.apply { textSize = 10.5f; color = Color.BLACK }
            canvas.drawText("${idx + 1}", 50f, currentY, textPaint)
            canvas.drawText("ST-${s.id}", 110f, currentY, textPaint)
            canvas.drawText(s.name, 200f, currentY, textPaint)
            
            val gName = groupMap[s.groupId]?.name ?: "No Group"
            canvas.drawText(gName, 420f, currentY, textPaint)

            canvas.drawLine(40f, currentY + 8f, 555f, currentY + 8f, borderPaint)
            currentY += 24f
        }

        // Draw Footer
        textPaint.apply { textSize = 9f; color = Color.GRAY }
        canvas.drawText("Page $pageNumber | Leadscorer v1.0", 40f, 810f, textPaint)

        doc.finishPage(page)
        saveOrSharePdf(context, doc, "Students_Report", isShare)
    }

    fun exportCompetitionsPdf(
        context: Context,
        madrasaName: String,
        eventName: String,
        competitions: List<CompetitionEntity>,
        isShare: Boolean
    ) {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1

        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas

        // Paints
        val textPaint = Paint().apply { isAntiAlias = true; color = Color.BLACK; textSize = 11f }
        val boldPaint = Paint().apply { isAntiAlias = true; color = Color.BLACK; textSize = 11f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val titlePaint = Paint().apply { isAntiAlias = true; color = Color.parseColor("#022C22"); textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val headerLabelPaint = Paint().apply { isAntiAlias = true; color = Color.parseColor("#D4AF37"); textSize = 10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); letterSpacing = 0.1f }
        val borderPaint = Paint().apply { isAntiAlias = true; color = Color.parseColor("#CCCCCC"); style = Paint.Style.STROKE; strokeWidth = 0.5f }
        val thBgPaint = Paint().apply { isAntiAlias = true; color = Color.parseColor("#F1F5F9"); style = Paint.Style.FILL }

        var currentY = 50f

        fun drawPdfHeader() {
            val topBarPaint = Paint().apply { color = Color.parseColor("#022C22") }
            canvas.drawRect(40f, 40f, 555f, 45f, topBarPaint)
            
            currentY = 70f
            canvas.drawText(madrasaName.uppercase(), 40f, currentY, headerLabelPaint)
            currentY += 24f
            canvas.drawText("COMPETITIONS LIST REPORT", 40f, currentY, titlePaint)
            currentY += 16f
            textPaint.apply { textSize = 10f; color = Color.GRAY }
            canvas.drawText("Event: $eventName | Generated on: ${getFormattedDate()}", 40f, currentY, textPaint)
            
            currentY += 25f
            canvas.drawLine(40f, currentY, 555f, currentY, borderPaint)
            currentY += 30f

            canvas.drawRect(40f, currentY - 18f, 555f, currentY + 8f, thBgPaint)
            canvas.drawLine(40f, currentY - 18f, 555f, currentY - 18f, borderPaint)
            canvas.drawLine(40f, currentY + 8f, 555f, currentY + 8f, borderPaint)
            
            canvas.drawText("S.No", 50f, currentY, boldPaint)
            canvas.drawText("Competition ID", 110f, currentY, boldPaint)
            canvas.drawText("Competition Name", 220f, currentY, boldPaint)
            canvas.drawText("Status / Action", 430f, currentY, boldPaint)
            currentY += 25f
        }

        drawPdfHeader()

        competitions.forEachIndexed { idx, c ->
            if (currentY > 780f) {
                doc.finishPage(page)
                pageNumber++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                drawPdfHeader()
            }

            textPaint.apply { textSize = 10.5f; color = Color.BLACK }
            canvas.drawText("${idx + 1}", 50f, currentY, textPaint)
            canvas.drawText("CP-${c.id}", 110f, currentY, textPaint)
            canvas.drawText(c.name, 220f, currentY, textPaint)
            
            val isCompleted = c.firstStudentId != null || c.secondStudentId != null || c.thirdStudentId != null
            val status = if (isCompleted) "Results Declared" else "Pending Results"
            canvas.drawText(status, 430f, currentY, textPaint)

            canvas.drawLine(40f, currentY + 8f, 555f, currentY + 8f, borderPaint)
            currentY += 24f
        }

        // Draw Footer
        textPaint.apply { textSize = 9f; color = Color.GRAY }
        canvas.drawText("Page $pageNumber | Leadscorer v1.0", 40f, 810f, textPaint)

        doc.finishPage(page)
        saveOrSharePdf(context, doc, "Competitions_Report", isShare)
    }

    fun exportResultsPdf(
        context: Context,
        madrasaName: String,
        eventName: String,
        competitions: List<CompetitionEntity>,
        students: List<StudentEntity>,
        groups: List<GroupEntity>,
        isShare: Boolean
    ) {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1

        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas

        val studentMap = students.associateBy { it.id }
        val groupMap = groups.associateBy { it.id }

        // Paints
        val textPaint = Paint().apply { isAntiAlias = true; color = Color.BLACK; textSize = 11f }
        val boldPaint = Paint().apply { isAntiAlias = true; color = Color.BLACK; textSize = 11f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val titlePaint = Paint().apply { isAntiAlias = true; color = Color.parseColor("#022C22"); textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val headerLabelPaint = Paint().apply { isAntiAlias = true; color = Color.parseColor("#D4AF37"); textSize = 10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); letterSpacing = 0.1f }
        val borderPaint = Paint().apply { isAntiAlias = true; color = Color.parseColor("#CCCCCC"); style = Paint.Style.STROKE; strokeWidth = 0.5f }
        val thBgPaint = Paint().apply { isAntiAlias = true; color = Color.parseColor("#F1F5F9"); style = Paint.Style.FILL }

        var currentY = 50f

        fun drawPdfHeader() {
            val topBarPaint = Paint().apply { color = Color.parseColor("#022C22") }
            canvas.drawRect(40f, 40f, 555f, 45f, topBarPaint)
            
            currentY = 70f
            canvas.drawText(madrasaName.uppercase(), 40f, currentY, headerLabelPaint)
            currentY += 24f
            canvas.drawText("COMPETITIONS STANDINGS & GRADES", 40f, currentY, titlePaint)
            currentY += 16f
            textPaint.apply { textSize = 10f; color = Color.GRAY }
            canvas.drawText("Event: $eventName | Generated on: ${getFormattedDate()}", 40f, currentY, textPaint)
            
            currentY += 25f
            canvas.drawLine(40f, currentY, 555f, currentY, borderPaint)
            currentY += 30f

            // Header labels
            canvas.drawRect(40f, currentY - 18f, 555f, currentY + 8f, thBgPaint)
            canvas.drawLine(40f, currentY - 18f, 555f, currentY - 18f, borderPaint)
            canvas.drawLine(40f, currentY + 8f, 555f, currentY + 8f, borderPaint)
            
            canvas.drawText("Competition Name", 50f, currentY, boldPaint)
            canvas.drawText("First Place (1st)", 200f, currentY, boldPaint)
            canvas.drawText("Second Place (2nd)", 320f, currentY, boldPaint)
            canvas.drawText("Third Place (3rd)", 440f, currentY, boldPaint)
            currentY += 25f
        }

        drawPdfHeader()

        competitions.forEach { c ->
            if (currentY > 750f) {
                doc.finishPage(page)
                pageNumber++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                drawPdfHeader()
            }

            // Competition Name Bold
            textPaint.apply { textSize = 11f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.parseColor("#0F172A") }
            canvas.drawText(c.name, 50f, currentY, textPaint)

            // Winners format
            textPaint.apply { textSize = 9.5f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL); color = Color.BLACK }
            
            // 1st Place info
            val s1 = studentMap[c.firstStudentId]
            val g1 = s1?.let { groupMap[it.groupId] }
            val firstText1 = s1?.name ?: "Vacant"
            val firstText2 = if (g1 != null) "${g1.name} (${c.firstGrade ?: "No Grade"})" else ""
            canvas.drawText(firstText1, 200f, currentY - 3f, textPaint)
            if (firstText2.isNotEmpty()) {
                textPaint.color = Color.GRAY
                canvas.drawText(firstText2, 200f, currentY + 8f, textPaint)
                textPaint.color = Color.BLACK
            }

            // 2nd Place info
            val s2 = studentMap[c.secondStudentId]
            val g2 = s2?.let { groupMap[it.groupId] }
            val secondText1 = s2?.name ?: "Vacant"
            val secondText2 = if (g2 != null) "${g2.name} (${c.secondGrade ?: "No Grade"})" else ""
            canvas.drawText(secondText1, 320f, currentY - 3f, textPaint)
            if (secondText2.isNotEmpty()) {
                textPaint.color = Color.GRAY
                canvas.drawText(secondText2, 320f, currentY + 8f, textPaint)
                textPaint.color = Color.BLACK
            }

            // 3rd Place info
            val s3 = studentMap[c.thirdStudentId]
            val g3 = s3?.let { groupMap[it.groupId] }
            val thirdText1 = s3?.name ?: "Vacant"
            val thirdText2 = if (g3 != null) "${g3.name} (${c.thirdGrade ?: "No Grade"})" else ""
            canvas.drawText(thirdText1, 440f, currentY - 3f, textPaint)
            if (thirdText2.isNotEmpty()) {
                textPaint.color = Color.GRAY
                canvas.drawText(thirdText2, 440f, currentY + 8f, textPaint)
                textPaint.color = Color.BLACK
            }

            canvas.drawLine(40f, currentY + 16f, 555f, currentY + 16f, borderPaint)
            currentY += 34f
        }

        // Draw Footer
        textPaint.apply { textSize = 9f; color = Color.GRAY }
        canvas.drawText("Page $pageNumber | Leadscorer v1.0", 40f, 810f, textPaint)

        doc.finishPage(page)
        saveOrSharePdf(context, doc, "Winners_Report", isShare)
    }

    fun exportAllDataPdf(
        context: Context,
        madrasaName: String,
        eventName: String,
        groupScores: List<GroupScore>,
        students: List<StudentEntity>,
        groups: List<GroupEntity>,
        competitions: List<CompetitionEntity>,
        isShare: Boolean
    ) {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1

        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas

        // Paints
        val textPaint = Paint().apply { isAntiAlias = true; color = Color.BLACK; textSize = 11f }
        val boldPaint = Paint().apply { isAntiAlias = true; color = Color.BLACK; textSize = 11f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val titlePaint = Paint().apply { isAntiAlias = true; color = Color.parseColor("#022C22"); textSize = 22f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD) }
        val headerLabelPaint = Paint().apply { isAntiAlias = true; color = Color.parseColor("#D4AF37"); textSize = 10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); letterSpacing = 0.1f }
        val borderPaint = Paint().apply { isAntiAlias = true; color = Color.parseColor("#CCCCCC"); style = Paint.Style.STROKE; strokeWidth = 0.5f }
        val thBgPaint = Paint().apply { isAntiAlias = true; color = Color.parseColor("#F1F5F9"); style = Paint.Style.FILL }

        var currentY = 50f

        fun drawPdfHeader(sectionTitle: String) {
            val topBarPaint = Paint().apply { color = Color.parseColor("#022C22") }
            canvas.drawRect(40f, 40f, 555f, 45f, topBarPaint)
            
            currentY = 70f
            canvas.drawText(madrasaName.uppercase(), 40f, currentY, headerLabelPaint)
            currentY += 24f
            canvas.drawText("COMPREHENSIVE ALL DATA REPORT", 40f, currentY, titlePaint)
            currentY += 16f
            textPaint.apply { textSize = 10f; color = Color.GRAY }
            canvas.drawText("Event: $eventName | Section: $sectionTitle | Date: ${getFormattedDate()}", 40f, currentY, textPaint)
            
            currentY += 25f
            canvas.drawLine(40f, currentY, 555f, currentY, borderPaint)
            currentY += 25f
        }

        // --- PAGE 1: STANDINGS & STATISTICS ---
        drawPdfHeader("Team Standings & Stats")

        // Draw Standings Table Title
        textPaint.apply { textSize = 14f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.parseColor("#022C22") }
        canvas.drawText("Group Leaderboard Standings", 40f, currentY, textPaint)
        currentY += 25f

        // Table Header
        canvas.drawRect(40f, currentY - 18f, 555f, currentY + 8f, thBgPaint)
        canvas.drawLine(40f, currentY - 18f, 555f, currentY - 18f, borderPaint)
        canvas.drawLine(40f, currentY + 8f, 555f, currentY + 8f, borderPaint)
        
        canvas.drawText("Rank", 50f, currentY, boldPaint)
        canvas.drawText("Group / Team Name", 120f, currentY, boldPaint)
        canvas.drawText("Total Registered Students", 320f, currentY, boldPaint)
        canvas.drawText("Total Points Scored", 460f, currentY, boldPaint)
        
        currentY += 25f

        groupScores.forEachIndexed { rank, gs ->
            val registeredCount = students.count { it.groupId == gs.group.id }
            
            textPaint.apply { textSize = 11f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL); color = Color.BLACK }
            if (rank == 0) {
                textPaint.color = Color.parseColor("#D4AF37")
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText("🏆 #${rank + 1}", 50f, currentY, textPaint)
            } else {
                canvas.drawText("#${rank + 1}", 50f, currentY, textPaint)
            }
            
            canvas.drawText(gs.group.name, 120f, currentY, textPaint)
            canvas.drawText("$registeredCount Students", 320f, currentY, textPaint)
            
            if (rank == 0) {
                canvas.drawText("${gs.totalPoints} PTS (Leader)", 460f, currentY, textPaint)
            } else {
                textPaint.color = Color.BLACK
                canvas.drawText("${gs.totalPoints} PTS", 460f, currentY, textPaint)
            }
            
            canvas.drawLine(40f, currentY + 8f, 555f, currentY + 8f, borderPaint)
            currentY += 24f
        }

        currentY += 40f
        // Overall Metrics Box
        val metricsBg = Paint().apply { isAntiAlias = true; color = Color.parseColor("#F8FAFC"); style = Paint.Style.FILL }
        canvas.drawRoundRect(40f, currentY, 555f, currentY + 110f, 8f, 8f, metricsBg)
        canvas.drawRoundRect(40f, currentY, 555f, currentY + 110f, 8f, 8f, borderPaint)
        
        textPaint.apply { textSize = 12f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.parseColor("#0F172A") }
        canvas.drawText("APPLICATION OVERVIEW SUMMARY", 55f, currentY + 25f, textPaint)

        textPaint.apply { textSize = 10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL); color = Color.BLACK }
        canvas.drawText("• Total Teams: ${groups.size} Teams", 60f, currentY + 50f, textPaint)
        canvas.drawText("• Total Registered Students: ${students.size} Active Students", 60f, currentY + 70f, textPaint)
        canvas.drawText("• Total Competition Activities: ${competitions.size} Event Categories", 60f, currentY + 90f, textPaint)

        // Draw Footer
        textPaint.apply { textSize = 9f; color = Color.GRAY }
        canvas.drawText("Page $pageNumber | Leadscorer Comprehensive Report", 40f, 810f, textPaint)
        doc.finishPage(page)


        // --- PAGE 2: STUDENTS LIST ---
        pageNumber++
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        canvas = page.canvas
        drawPdfHeader("Students Roster List")
        
        val groupMap = groups.associateBy { it.id }

        // Headers
        canvas.drawRect(40f, currentY - 18f, 555f, currentY + 8f, thBgPaint)
        canvas.drawLine(40f, currentY - 18f, 555f, currentY - 18f, borderPaint)
        canvas.drawLine(40f, currentY + 8f, 555f, currentY + 8f, borderPaint)
        
        canvas.drawText("S.No", 50f, currentY, boldPaint)
        canvas.drawText("Student ID", 110f, currentY, boldPaint)
        canvas.drawText("Student Name", 200f, currentY, boldPaint)
        canvas.drawText("Group / Team", 420f, currentY, boldPaint)
        currentY += 25f

        students.forEachIndexed { idx, s ->
            if (currentY > 770f) {
                doc.finishPage(page)
                pageNumber++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                drawPdfHeader("Students Roster List")
                
                // Headers on new page
                canvas.drawRect(40f, currentY - 18f, 555f, currentY + 8f, thBgPaint)
                canvas.drawLine(40f, currentY - 18f, 555f, currentY - 18f, borderPaint)
                canvas.drawLine(40f, currentY + 8f, 555f, currentY + 8f, borderPaint)
                canvas.drawText("S.No", 50f, currentY, boldPaint)
                canvas.drawText("Student ID", 110f, currentY, boldPaint)
                canvas.drawText("Student Name", 200f, currentY, boldPaint)
                canvas.drawText("Group / Team", 420f, currentY, boldPaint)
                currentY += 25f
            }

            textPaint.apply { textSize = 10f; color = Color.BLACK; typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL) }
            canvas.drawText("${idx + 1}", 50f, currentY, textPaint)
            canvas.drawText("ST-${s.id}", 110f, currentY, textPaint)
            canvas.drawText(s.name, 200f, currentY, textPaint)
            val gName = groupMap[s.groupId]?.name ?: "No Group"
            canvas.drawText(gName, 420f, currentY, textPaint)

            canvas.drawLine(40f, currentY + 8f, 555f, currentY + 8f, borderPaint)
            currentY += 24f
        }
        
        // Draw Footer
        textPaint.apply { textSize = 9f; color = Color.GRAY }
        canvas.drawText("Page $pageNumber | Leadscorer Comprehensive Report", 40f, 810f, textPaint)
        doc.finishPage(page)


        // --- PAGE 3+: WINNER CARDS AND GRADES ---
        pageNumber++
        page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        canvas = page.canvas
        drawPdfHeader("Competitions Results")

        val studentMap = students.associateBy { it.id }

        // Table Header
        canvas.drawRect(40f, currentY - 18f, 555f, currentY + 8f, thBgPaint)
        canvas.drawLine(40f, currentY - 18f, 555f, currentY - 18f, borderPaint)
        canvas.drawLine(40f, currentY + 8f, 555f, currentY + 8f, borderPaint)
        
        canvas.drawText("Competition Name", 50f, currentY, boldPaint)
        canvas.drawText("First Place (1st)", 200f, currentY, boldPaint)
        canvas.drawText("Second Place (2nd)", 320f, currentY, boldPaint)
        canvas.drawText("Third Place (3rd)", 440f, currentY, boldPaint)
        currentY += 25f

        competitions.forEach { c ->
            if (currentY > 740f) {
                doc.finishPage(page)
                pageNumber++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
                canvas = page.canvas
                drawPdfHeader("Competitions Results")
                
                // Headers
                canvas.drawRect(40f, currentY - 18f, 555f, currentY + 8f, thBgPaint)
                canvas.drawLine(40f, currentY - 18f, 555f, currentY - 18f, borderPaint)
                canvas.drawLine(40f, currentY + 8f, 555f, currentY + 8f, borderPaint)
                canvas.drawText("Competition Name", 50f, currentY, boldPaint)
                canvas.drawText("First Place (1st)", 200f, currentY, boldPaint)
                canvas.drawText("Second Place (2nd)", 320f, currentY, boldPaint)
                canvas.drawText("Third Place (3rd)", 440f, currentY, boldPaint)
                currentY += 25f
            }

            // Competition Name Bold
            textPaint.apply { textSize = 11f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); color = Color.parseColor("#0F172A") }
            canvas.drawText(c.name, 50f, currentY, textPaint)

            // Winners details
            textPaint.apply { textSize = 9.5f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL); color = Color.BLACK }
            
            // 1st Place info
            val s1 = studentMap[c.firstStudentId]
            val g1 = s1?.let { groupMap[it.groupId] }
            val firstText1 = s1?.name ?: "Vacant"
            val firstText2 = if (g1 != null) "${g1.name} (${c.firstGrade ?: "No Grade"})" else ""
            canvas.drawText(firstText1, 200f, currentY - 3f, textPaint)
            if (firstText2.isNotEmpty()) {
                textPaint.color = Color.GRAY
                canvas.drawText(firstText2, 200f, currentY + 8f, textPaint)
                textPaint.color = Color.BLACK
            }

            // 2nd Place info
            val s2 = studentMap[c.secondStudentId]
            val g2 = s2?.let { groupMap[it.groupId] }
            val secondText1 = s2?.name ?: "Vacant"
            val secondText2 = if (g2 != null) "${g2.name} (${c.secondGrade ?: "No Grade"})" else ""
            canvas.drawText(secondText1, 320f, currentY - 3f, textPaint)
            if (secondText2.isNotEmpty()) {
                textPaint.color = Color.GRAY
                canvas.drawText(secondText2, 320f, currentY + 8f, textPaint)
                textPaint.color = Color.BLACK
            }

            // 3rd Place info
            val s3 = studentMap[c.thirdStudentId]
            val g3 = s3?.let { groupMap[it.groupId] }
            val thirdText1 = s3?.name ?: "Vacant"
            val thirdText2 = if (g3 != null) "${g3.name} (${c.thirdGrade ?: "No Grade"})" else ""
            canvas.drawText(thirdText1, 440f, currentY - 3f, textPaint)
            if (thirdText2.isNotEmpty()) {
                textPaint.color = Color.GRAY
                canvas.drawText(thirdText2, 440f, currentY + 8f, textPaint)
                textPaint.color = Color.BLACK
            }

            canvas.drawLine(40f, currentY + 16f, 555f, currentY + 16f, borderPaint)
            currentY += 34f
        }

        // Draw Footer
        textPaint.apply { textSize = 9f; color = Color.GRAY }
        canvas.drawText("Page $pageNumber | Leadscorer Comprehensive Report", 40f, 810f, textPaint)
        doc.finishPage(page)

        saveOrSharePdf(context, doc, "Comprehensive_All_Data_Report", isShare)
    }

    private fun saveOrSharePdf(context: Context, doc: PdfDocument, title: String, isShare: Boolean) {
        if (isShare) {
            try {
                val cachePath = File(context.cacheDir, "shared_images")
                cachePath.mkdirs()
                val file = File(cachePath, "$title.pdf")
                FileOutputStream(file).use { out ->
                    doc.writeTo(out)
                }
                doc.close()

                val contentUri = FileProvider.getUriForFile(context, "com.example.scoreboard.fileprovider", file)
                if (contentUri != null) {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share PDF Report"))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error sharing PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                doc.close()
            }
        } else {
            val filename = "${title.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Scoreboard")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                try {
                    resolver.openOutputStream(uri).use { outStream ->
                        if (outStream != null) {
                            doc.writeTo(outStream)
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, contentValues, null, null)
                    }
                    Toast.makeText(context, "Saved PDF to Downloads/Scoreboard!", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Error saving PDF: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    doc.close()
                }
            } else {
                Toast.makeText(context, "Could not insert PDF file into downloads registry", Toast.LENGTH_SHORT).show()
                doc.close()
            }
        }
    }

    private fun getFormattedDate(): String {
        return SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
    }
}

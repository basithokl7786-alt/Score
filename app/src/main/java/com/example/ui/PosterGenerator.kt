package com.example.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.CompetitionEntity
import com.example.data.GroupEntity
import com.example.data.StudentEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PosterGenerator {

    fun generatePoster(
        context: Context,
        madrasaName: String,
        eventName: String,
        groupScores: List<GroupScore>,
        studentScores: List<StudentScore>,
        competitions: List<CompetitionEntity>,
        students: List<StudentEntity>,
        groups: List<GroupEntity>
    ): Bitmap {
        val width = 1080
        // Calculate dynamic height based on item counts to prevent overlapping
        val baseHeight = 1000
        val groupsSectionHeight = groupScores.size * 90 + 150
        val studentsSectionHeight = minOf(studentScores.size, 5) * 80 + 150
        val compsSectionHeight = minOf(competitions.size, 5) * 100 + 150
        val height = baseHeight + groupsSectionHeight + studentsSectionHeight + compsSectionHeight

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Paints
        val bgPaint = Paint().apply { isAntiAlias = true }
        val goldBorderPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#D4AF37")
            style = Paint.Style.STROKE
            strokeWidth = 12f
        }
        val goldInnerBorderPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#F6E294")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        val accentGoldPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#D4AF37")
        }

        // Draw Royal Blue Gradient Background
        val bgGradient = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            Color.parseColor("#0F172A"), Color.parseColor("#090D1A"),
            Shader.TileMode.CLAMP
        )
        bgPaint.shader = bgGradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Draw Premium Islamic Geometric Borders
        canvas.drawRect(24f, 24f, (width - 24).toFloat(), (height - 24).toFloat(), goldBorderPaint)
        canvas.drawRect(36f, 36f, (width - 36).toFloat(), (height - 36).toFloat(), goldInnerBorderPaint)

        // Draw Corner Islamic Stars
        val corners = listOf(
            Pair(36f, 36f),
            Pair((width - 36).toFloat(), 36f),
            Pair(36f, (height - 36).toFloat()),
            Pair((width - 36).toFloat(), (height - 36).toFloat())
        )
        for ((cx, cy) in corners) {
            drawIslamicStar(canvas, cx, cy, 30f, accentGoldPaint)
        }

        var currentY = 130f

        // 1. App/Event Header
        textPaint.apply {
            color = Color.parseColor("#D4AF37")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText("مدرسة സ്കോർബോർഡ്", (width / 2).toFloat(), currentY, textPaint)
        currentY += 45f

        textPaint.apply {
            color = Color.WHITE
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        canvas.drawText(madrasaName.uppercase(Locale.ROOT), (width / 2).toFloat(), currentY, textPaint)
        currentY += 60f

        textPaint.apply {
            color = Color.parseColor("#F6E294")
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText(eventName, (width / 2).toFloat(), currentY, textPaint)
        currentY += 70f

        // Draw elegant title separator line
        val sepPath = Path().apply {
            moveTo(150f, currentY)
            lineTo((width / 2 - 50).toFloat(), currentY)
            lineTo((width / 2).toFloat(), currentY - 10f)
            lineTo((width / 2 + 50).toFloat(), currentY)
            lineTo((width - 150).toFloat(), currentY)
        }
        canvas.drawPath(sepPath, goldInnerBorderPaint)
        currentY += 60f

        // 2. Section: Group Standings (Leaderboard)
        drawSectionHeader(canvas, "ഗ്രൂപ്പ് നില / GROUP STANDINGS", currentY, width)
        currentY += 100f

        // Column Titles
        textPaint.apply {
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
            color = Color.parseColor("#F6E294")
        }
        canvas.drawText("RANK", 100f, currentY, textPaint)
        canvas.drawText("GROUP NAME", 220f, currentY, textPaint)
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText("MEDALS (G/S/B)", 680f, currentY, textPaint)
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText("TOTAL", 980f, currentY, textPaint)
        currentY += 30f

        canvas.drawLine(100f, currentY, 980f, currentY, goldInnerBorderPaint)
        currentY += 50f

        // Draw group rows
        groupScores.forEachIndexed { index, score ->
            // Row background if leading
            if (index == 0) {
                val rowBgPaint = Paint().apply {
                    color = Color.parseColor("#22D4AF37") // transparent gold
                    style = Paint.Style.FILL
                }
                canvas.drawRect(80f, currentY - 45f, 1000f, currentY + 25f, rowBgPaint)
            }

            textPaint.apply {
                textSize = 28f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.LEFT
                color = if (index == 0) Color.parseColor("#D4AF37") else Color.WHITE
            }
            
            // Rank
            val rankText = when (index) {
                0 -> "🏆 1ST"
                1 -> "🥈 2ND"
                2 -> "🥉 3RD"
                else -> "  ${index + 1}TH"
            }
            canvas.drawText(rankText, 100f, currentY, textPaint)

            // Group Badge Circle
            val badgePaint = Paint().apply {
                isAntiAlias = true
                color = Color.parseColor(score.group.colorHex)
            }
            canvas.drawCircle(235f, currentY - 10f, 12f, badgePaint)

            // Group Name
            canvas.drawText(score.group.name, 270f, currentY, textPaint)

            // Medals
            textPaint.textAlign = Paint.Align.CENTER
            val medalsText = "${score.goldCount}🥇 / ${score.silverCount}🥈 / ${score.bronzeCount}🥉"
            canvas.drawText(medalsText, 680f, currentY, textPaint)

            // Total Score
            textPaint.apply {
                textAlign = Paint.Align.RIGHT
                textSize = 32f
                color = Color.parseColor("#F6E294")
            }
            canvas.drawText("${score.totalPoints} pts", 980f, currentY, textPaint)

            currentY += 80f
        }

        currentY += 30f

        // 3. Section: Top Students
        if (studentScores.isNotEmpty()) {
            drawSectionHeader(canvas, "വ്യക്തിഗത മികവ് / OUTSTANDING STUDENTS", currentY, width)
            currentY += 100f

            textPaint.apply {
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.LEFT
                color = Color.parseColor("#F6E294")
            }
            canvas.drawText("RANK", 100f, currentY, textPaint)
            canvas.drawText("STUDENT NAME", 220f, currentY, textPaint)
            canvas.drawText("GROUP", 600f, currentY, textPaint)
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText("TOTAL", 980f, currentY, textPaint)
            currentY += 30f

            canvas.drawLine(100f, currentY, 980f, currentY, goldInnerBorderPaint)
            currentY += 50f

            // Top 5 Students
            studentScores.take(5).forEachIndexed { index, sScore ->
                textPaint.apply {
                    textSize = 26f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    textAlign = Paint.Align.LEFT
                    color = Color.WHITE
                }

                val rankSymbol = when (index) {
                    0 -> "🥇"
                    1 -> "🥈"
                    2 -> "🥉"
                    else -> "${index + 1}."
                }
                canvas.drawText(rankSymbol, 100f, currentY, textPaint)
                canvas.drawText(sScore.student.name, 220f, currentY, textPaint)
                
                // Group Name
                sScore.group?.let { group ->
                    textPaint.color = Color.parseColor(group.colorHex)
                    canvas.drawText(group.name, 600f, currentY, textPaint)
                }

                // Points
                textPaint.apply {
                    textAlign = Paint.Align.RIGHT
                    color = Color.parseColor("#F6E294")
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText("${sScore.totalPoints} pts", 980f, currentY, textPaint)

                currentY += 75f
            }
            currentY += 40f
        }

        // 4. Section: Recent Competition Results
        val recentComps = competitions.filter { it.firstStudentId != null || it.secondStudentId != null || it.thirdStudentId != null }
        if (recentComps.isNotEmpty()) {
            drawSectionHeader(canvas, "മത്സര ഫലങ്ങൾ / COMPETITION RESULTS", currentY, width)
            currentY += 100f

            val studentMap = students.associateBy { it.id }
            val groupMap = groups.associateBy { it.id }

            recentComps.take(5).forEach { comp ->
                // Draw Competition Item Container Box
                val containerPaint = Paint().apply {
                    color = Color.parseColor("#1E293B")
                    style = Paint.Style.FILL
                }
                canvas.drawRect(100f, currentY, 980f, currentY + 90f, containerPaint)

                textPaint.apply {
                    textSize = 24f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textAlign = Paint.Align.LEFT
                    color = Color.parseColor("#D4AF37")
                }
                canvas.drawText("★ ${comp.name}", 130f, currentY + 38f, textPaint)

                // Render Winners row
                textPaint.apply {
                    textSize = 18f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    color = Color.WHITE
                }

                val winnersTextList = mutableListOf<String>()
                comp.firstStudentId?.let { id ->
                    studentMap[id]?.let { st ->
                        val groupName = groupMap[st.groupId]?.name ?: ""
                        winnersTextList.add("1st: ${st.name} ($groupName)")
                    }
                }
                comp.secondStudentId?.let { id ->
                    studentMap[id]?.let { st ->
                        val groupName = groupMap[st.groupId]?.name ?: ""
                        winnersTextList.add("2nd: ${st.name} ($groupName)")
                    }
                }
                comp.thirdStudentId?.let { id ->
                    studentMap[id]?.let { st ->
                        val groupName = groupMap[st.groupId]?.name ?: ""
                        winnersTextList.add("3rd: ${st.name} ($groupName)")
                    }
                }

                val winnersText = winnersTextList.joinToString("  |  ")
                textPaint.color = Color.parseColor("#94A3B8")
                canvas.drawText(winnersText, 130f, currentY + 70f, textPaint)

                currentY += 115f
            }
        }

        // 5. Elegant Islamic Footer
        currentY = (height - 120).toFloat()
        canvas.drawLine(150f, currentY, (width - 150).toFloat(), currentY, goldInnerBorderPaint)
        currentY += 45f

        textPaint.apply {
            color = Color.parseColor("#A0AEC0")
            textSize = 22f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val dateString = SimpleDateFormat("EEEE, d MMMM yyyy (hh:mm a)", Locale.getDefault()).format(Date())
        canvas.drawText("Generated on: $dateString", (width / 2).toFloat(), currentY, textPaint)
        currentY += 30f
        canvas.drawText("Malayalam Competition Score Board App", (width / 2).toFloat(), currentY, textPaint)

        return bitmap
    }

    private fun drawSectionHeader(canvas: Canvas, text: String, y: Float, width: Int) {
        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#22D4AF37")
            style = Paint.Style.FILL
        }
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#D4AF37")
            textSize = 28f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        // Background banner block
        canvas.drawRect(100f, y - 40f, (width - 100).toFloat(), y + 20f, paint)
        canvas.drawText(text, (width / 2).toFloat(), y - 3f, textPaint)

        // Golden line underlines
        val linePaint = Paint().apply {
            color = Color.parseColor("#D4AF37")
            strokeWidth = 3f
        }
        canvas.drawLine(100f, y + 20f, (width - 100).toFloat(), y + 20f, linePaint)
    }

    private fun drawIslamicStar(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        val path = Path()
        val numPoints = 8
        for (i in 0 until numPoints * 2) {
            val r = if (i % 2 == 0) radius else radius * 0.5f
            val angle = i * Math.PI / numPoints
            val x = (cx + r * Math.cos(angle)).toFloat()
            val y = (cy + r * Math.sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    // Generate minimal poster for single competition result
    fun generateCompetitionPoster(
        context: Context,
        madrasaName: String,
        eventName: String,
        competition: CompetitionEntity,
        students: List<StudentEntity>,
        groups: List<GroupEntity>
    ): Bitmap {
        val width = 1080
        val height = 1080

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Paints
        val bgPaint = Paint().apply { isAntiAlias = true }
        val goldBorderPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#D4AF37")
            style = Paint.Style.STROKE
            strokeWidth = 10f
        }
        val goldInnerBorderPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#F6E294")
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        val accentGoldPaint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#D4AF37")
        }

        // Draw Deep Islamic Emerald-Green Gradient Background
        val bgGradient = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            Color.parseColor("#022C22"), Color.parseColor("#01140F"),
            Shader.TileMode.CLAMP
        )
        bgPaint.shader = bgGradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Draw Premium Gold Borders
        canvas.drawRect(30f, 30f, (width - 30).toFloat(), (height - 30).toFloat(), goldBorderPaint)
        canvas.drawRect(42f, 42f, (width - 42).toFloat(), (height - 42).toFloat(), goldInnerBorderPaint)

        // Corner Stars
        val corners = listOf(
            Pair(42f, 42f),
            Pair((width - 42).toFloat(), 42f),
            Pair(42f, (height - 42).toFloat()),
            Pair((width - 42).toFloat(), (height - 42).toFloat())
        )
        for ((cx, cy) in corners) {
            drawIslamicStar(canvas, cx, cy, 25f, accentGoldPaint)
        }

        // Header
        var currentY = 120f
        textPaint.apply {
            color = Color.parseColor("#D4AF37")
            textSize = 26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.1f
        }
        canvas.drawText("മദ്രസ മത്സര ഫലം", (width / 2).toFloat(), currentY, textPaint)
        currentY += 40f

        textPaint.apply {
            color = Color.WHITE
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        canvas.drawText(madrasaName.uppercase(Locale.ROOT), (width / 2).toFloat(), currentY, textPaint)
        currentY += 50f

        textPaint.apply {
            color = Color.parseColor("#F6E294")
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        canvas.drawText(eventName, (width / 2).toFloat(), currentY, textPaint)
        currentY += 60f

        // Separator Line
        val sepPath = Path().apply {
            moveTo(200f, currentY)
            lineTo((width / 2 - 40).toFloat(), currentY)
            lineTo((width / 2).toFloat(), currentY - 8f)
            lineTo((width / 2 + 40).toFloat(), currentY)
            lineTo((width - 200).toFloat(), currentY)
        }
        canvas.drawPath(sepPath, goldInnerBorderPaint)
        currentY += 80f

        // Competition Name
        textPaint.apply {
            color = Color.parseColor("#D4AF37")
            textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
        }
        canvas.drawText(competition.name.uppercase(Locale.ROOT), (width / 2).toFloat(), currentY, textPaint)
        currentY += 30f

        textPaint.apply {
            color = Color.WHITE
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            letterSpacing = 0.15f
        }
        canvas.drawText("RESULT CARD", (width / 2).toFloat(), currentY, textPaint)
        currentY += 70f

        // Draw Winners (1st, 2nd, 3rd)
        val studentMap = students.associateBy { it.id }
        val groupMap = groups.associateBy { it.id }

        // Position details list
        val positions = listOf(
            Triple(competition.firstStudentId, competition.firstGrade, competition.firstMarks ?: 5),
            Triple(competition.secondStudentId, competition.secondGrade, competition.secondMarks ?: 3),
            Triple(competition.thirdStudentId, competition.thirdGrade, competition.thirdMarks ?: 1)
        )

        positions.forEachIndexed { index, (studentId, grade, marks) ->
            val rankLabel = when (index) {
                0 -> "🏆 FIRST PLACE"
                1 -> "🥈 SECOND PLACE"
                else -> "🥉 THIRD PLACE"
            }
            val rankBadgeColor = when (index) {
                0 -> "#D4AF37" // Gold
                1 -> "#C0C0C0" // Silver
                else -> "#CD7F32" // Bronze
            }

            // Draw a rounded card container for each winner position
            val cardLeft = 100f
            val cardRight = 980f
            val cardTop = currentY
            val cardBottom = currentY + 160f

            val cardPaint = Paint().apply {
                isAntiAlias = true
                color = Color.parseColor("#122A22") // Dark teal surface
                style = Paint.Style.FILL
            }
            val cardBorderPaint = Paint().apply {
                isAntiAlias = true
                val parsedColor = Color.parseColor(rankBadgeColor)
                val alphaColor = Color.argb(100, Color.red(parsedColor), Color.green(parsedColor), Color.blue(parsedColor))
                color = alphaColor
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }

            // Draw card background
            canvas.drawRoundRect(cardLeft, cardTop, cardRight, cardBottom, 16f, 16f, cardPaint)
            canvas.drawRoundRect(cardLeft, cardTop, cardRight, cardBottom, 16f, 16f, cardBorderPaint)

            // Rank Badge indicator on left
            val rankLabelPaint = Paint().apply {
                isAntiAlias = true
                color = Color.parseColor(rankBadgeColor)
                textSize = 22f
                typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            }
            canvas.drawText(rankLabel, cardLeft + 40f, cardTop + 55f, rankLabelPaint)

            if (studentId != null && studentMap.containsKey(studentId)) {
                val student = studentMap[studentId]!!
                val group = groupMap[student.groupId]
                val groupColor = group?.colorHex ?: "#94A3B8"

                // Student Name
                val namePaint = Paint().apply {
                    isAntiAlias = true
                    color = Color.WHITE
                    textSize = 34f
                    typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
                }
                canvas.drawText(student.name, cardLeft + 40f, cardTop + 105f, namePaint)

                // Group Badge name
                if (group != null) {
                    val groupPaint = Paint().apply {
                        isAntiAlias = true
                        color = Color.parseColor(groupColor)
                        textSize = 22f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    canvas.drawText(group.name, cardLeft + 40f, cardTop + 140f, groupPaint)
                }

                // Marks and Grade on right side of card
                val infoPaint = Paint().apply {
                    isAntiAlias = true
                    color = Color.parseColor("#F6E294")
                    textSize = 26f
                    typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
                    textAlign = Paint.Align.RIGHT
                }
                
                var details = "$marks PTS"
                if (grade != null) {
                    details += "  |  GRADE $grade"
                }
                canvas.drawText(details, cardRight - 40f, cardTop + 95f, infoPaint)

            } else {
                // Empty / Position Vacant
                val emptyPaint = Paint().apply {
                    isAntiAlias = true
                    color = Color.parseColor("#5F7A70")
                    textSize = 28f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                }
                canvas.drawText("No entry / Vacant", cardLeft + 40f, cardTop + 105f, emptyPaint)
            }

            currentY += 200f
        }

        // Footer Separator & Signature
        currentY = (height - 110).toFloat()
        canvas.drawLine(200f, currentY, (width - 200).toFloat(), currentY, goldInnerBorderPaint)
        currentY += 45f

        textPaint.apply {
            color = Color.parseColor("#94A3B8")
            textSize = 20f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val dateString = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())
        canvas.drawText("Congratulations to all winners!  •  $dateString", (width / 2).toFloat(), currentY, textPaint)
        currentY += 28f
        canvas.drawText("Malayalam Competition Scoreboard v1.0", (width / 2).toFloat(), currentY, textPaint)

        return bitmap
    }

    // Save bitmap locally and return Uri
    fun sharePoster(context: Context, bitmap: Bitmap, title: String) {
        try {
            val cachePath = File(context.cacheDir, "shared_images")
            cachePath.mkdirs()
            val file = File(cachePath, "$title-result.png")
            val stream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.close()

            val contentUri = FileProvider.getUriForFile(context, "com.example.scoreboard.fileprovider", file)
            if (contentUri != null) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Scoreboard Results"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Save bitmap to gallery (supports Q+ MediaStore and older SDKs)
    fun savePosterToGallery(context: Context, bitmap: Bitmap, title: String): Boolean {
        val filename = "${title.replace(" ", "_")}_Scoreboard_${System.currentTimeMillis()}.png"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Scoreboard")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            try {
                resolver.openOutputStream(uri).use { outStream ->
                    if (outStream != null) {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
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
}

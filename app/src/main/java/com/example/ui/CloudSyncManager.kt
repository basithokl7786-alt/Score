package com.example.ui

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.data.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CloudSyncManager {

    private val client = OkHttpClient()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    data class SyncedData(
        val madrasaName: String,
        val eventName: String,
        val groups: List<GroupEntity>,
        val students: List<StudentEntity>,
        val competitions: List<CompetitionEntity>
    )

    // --- JSON SERIALIZATION ---

    fun serializeScoreboard(
        madrasaName: String,
        eventName: String,
        groups: List<GroupEntity>,
        students: List<StudentEntity>,
        competitions: List<CompetitionEntity>
    ): String {
        val sb = StringBuilder()
        sb.append("{")
        sb.append("\"madrasaName\":\"${escapeJson(madrasaName)}\",")
        sb.append("\"eventName\":\"${escapeJson(eventName)}\",")
        sb.append("\"lastUpdated\":\"${getFormattedTimestamp()}\",")

        // Groups
        sb.append("\"groups\":[")
        groups.forEachIndexed { idx, g ->
            sb.append("{")
            sb.append("\"id\":${g.id},")
            sb.append("\"name\":\"${escapeJson(g.name)}\",")
            sb.append("\"colorHex\":\"${escapeJson(g.colorHex)}\"")
            sb.append("}")
            if (idx < groups.size - 1) sb.append(",")
        }
        sb.append("],")

        // Students
        sb.append("\"students\":[")
        students.forEachIndexed { idx, s ->
            sb.append("{")
            sb.append("\"id\":${s.id},")
            sb.append("\"name\":\"${escapeJson(s.name)}\",")
            sb.append("\"groupId\":${s.groupId}")
            sb.append("}")
            if (idx < students.size - 1) sb.append(",")
        }
        sb.append("],")

        // Competitions
        sb.append("\"competitions\":[")
        competitions.forEachIndexed { idx, c ->
            sb.append("{")
            sb.append("\"id\":${c.id},")
            sb.append("\"name\":\"${escapeJson(c.name)}\",")
            sb.append("\"firstStudentId\":${c.firstStudentId ?: "null"},")
            sb.append("\"firstGrade\":${c.firstGrade?.let { "\"$it\"" } ?: "null"},")
            sb.append("\"firstMarks\":${c.firstMarks ?: "null"},")
            sb.append("\"secondStudentId\":${c.secondStudentId ?: "null"},")
            sb.append("\"secondGrade\":${c.secondGrade?.let { "\"$it\"" } ?: "null"},")
            sb.append("\"secondMarks\":${c.secondMarks ?: "null"},")
            sb.append("\"thirdStudentId\":${c.thirdStudentId ?: "null"},")
            sb.append("\"thirdGrade\":${c.thirdGrade?.let { "\"$it\"" } ?: "null"},")
            sb.append("\"thirdMarks\":${c.thirdMarks ?: "null"}")
            sb.append("}")
            if (idx < competitions.size - 1) sb.append(",")
        }
        sb.append("]")
        sb.append("}")
        return sb.toString()
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    private fun getFormattedTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    // --- JSON DESERIALIZATION ---

    fun deserializeScoreboard(jsonString: String): SyncedData {
        val obj = JSONObject(jsonString)
        val madrasaName = obj.optString("madrasaName", "Leadscorer Academy")
        val eventName = obj.optString("eventName", "Competition Event")

        val groupsList = mutableListOf<GroupEntity>()
        val groupsArr = obj.optJSONArray("groups") ?: JSONArray()
        for (i in 0 until groupsArr.length()) {
            val gObj = groupsArr.getJSONObject(i)
            groupsList.add(
                GroupEntity(
                    id = gObj.getInt("id"),
                    name = gObj.getString("name"),
                    colorHex = gObj.getString("colorHex")
                )
            )
        }

        val studentsList = mutableListOf<StudentEntity>()
        val studentsArr = obj.optJSONArray("students") ?: JSONArray()
        for (i in 0 until studentsArr.length()) {
            val sObj = studentsArr.getJSONObject(i)
            studentsList.add(
                StudentEntity(
                    id = sObj.getInt("id"),
                    name = sObj.getString("name"),
                    groupId = sObj.getInt("groupId")
                )
            )
        }

        val competitionsList = mutableListOf<CompetitionEntity>()
        val compsArr = obj.optJSONArray("competitions") ?: JSONArray()
        for (i in 0 until compsArr.length()) {
            val cObj = compsArr.getJSONObject(i)
            competitionsList.add(
                CompetitionEntity(
                    id = cObj.getInt("id"),
                    name = cObj.getString("name"),
                    firstStudentId = if (cObj.isNull("firstStudentId")) null else cObj.getInt("firstStudentId"),
                    firstGrade = if (cObj.isNull("firstGrade")) null else cObj.getString("firstGrade"),
                    firstMarks = if (cObj.isNull("firstMarks")) null else cObj.getInt("firstMarks"),
                    secondStudentId = if (cObj.isNull("secondStudentId")) null else cObj.getInt("secondStudentId"),
                    secondGrade = if (cObj.isNull("secondGrade")) null else cObj.getString("secondGrade"),
                    secondMarks = if (cObj.isNull("secondMarks")) null else cObj.getInt("secondMarks"),
                    thirdStudentId = if (cObj.isNull("thirdStudentId")) null else cObj.getInt("thirdStudentId"),
                    thirdGrade = if (cObj.isNull("thirdGrade")) null else cObj.getString("thirdGrade"),
                    thirdMarks = if (cObj.isNull("thirdMarks")) null else cObj.getInt("thirdMarks")
                )
            )
        }

        return SyncedData(madrasaName, eventName, groupsList, studentsList, competitionsList)
    }

    // --- REST CLIENT IMPLEMENTATIONS ---

    fun uploadToCloud(
        jsonPayload: String,
        existingKey: String?,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val url: String
        val request: Request

        if (existingKey.isNullOrEmpty()) {
            // POST to create new
            url = "https://jsonblob.com/api/jsonBlob"
            request = Request.Builder()
                .url(url)
                .post(jsonPayload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        } else {
            // PUT to update existing
            url = "https://jsonblob.com/api/jsonBlob/$existingKey"
            request = Request.Builder()
                .url(url)
                .put(jsonPayload.toRequestBody(JSON_MEDIA_TYPE))
                .build()
        }

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onFailure(e.message ?: "Network failure connection lost")
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    onFailure("Server returned error: ${response.code}")
                    response.close()
                    return
                }

                if (existingKey.isNullOrEmpty()) {
                    // Extract ID from Location header
                    val location = response.header("Location")
                    val newId = location?.substringAfterLast("/") ?: ""
                    response.close()
                    if (newId.isNotEmpty()) {
                        onSuccess(newId)
                    } else {
                        onFailure("No location header or sync key generated")
                    }
                } else {
                    response.close()
                    onSuccess(existingKey)
                }
            }
        })
    }

    fun downloadFromCloud(
        syncKey: String,
        onSuccess: (SyncedData) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val url = "https://jsonblob.com/api/jsonBlob/$syncKey"
        val request = Request.Builder().url(url).get().build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onFailure(e.message ?: "Failed to connect to cloud service")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                val code = response.code
                response.close()

                if (code != 200 || body.isNullOrEmpty()) {
                    onFailure("No synced data found for Sync Key: $syncKey")
                    return
                }

                try {
                    val data = deserializeScoreboard(body)
                    onSuccess(data)
                } catch (e: Exception) {
                    onFailure("Corrupted sync data: ${e.message}")
                }
            }
        })
    }

    // --- HTML LIVE WEBSITE GENERATOR ---

    fun generateLiveDashboardHtml(syncKey: String, madrasaName: String, eventName: String): String {
        return """<!DOCTYPE html>
<html lang="ml">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>$eventName - തത്സമയ വെബ് സ്കോർ ബോർഡ്</title>
    <!-- Google Fonts -->
    <link href="https://fonts.googleapis.com/css2?family=Manrope:wght@500;600;700;800&family=Noto+Sans+Malayalam:wght@500;600;700;800&display=swap" rel="stylesheet">
    <!-- FontAwesome Icons -->
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    
    <style>
        :root {
            --bg-dark: #0B132B;
            --bg-card: #1C2541;
            --bg-glass: rgba(28, 37, 65, 0.85);
            --gold: #F59E0B;
            --gold-bright: #FBBF24;
            --gold-glow: rgba(245, 158, 11, 0.25);
            --gold-light: #FEF3C7;
            --text-primary: #F8FAFC;
            --text-secondary: #94A3B8;
            --border-color: rgba(245, 158, 11, 0.2);
            --silver: #CBD5E1;
            --bronze: #D97706;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            font-family: "Noto Sans Malayalam", "Manrope", sans-serif;
            -webkit-tap-highlight-color: transparent;
        }

        body {
            background: linear-gradient(135deg, #070A13 0%, #0F172A 50%, #0A0F1D 100%);
            color: var(--text-primary);
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            overflow-x: hidden;
        }

        /* HEADER */
        header {
            background: linear-gradient(180deg, rgba(15, 23, 42, 0.95) 0%, rgba(11, 19, 43, 0.95) 100%);
            border-bottom: 2px solid var(--gold);
            padding: 20px 16px;
            text-align: center;
            box-shadow: 0 4px 25px rgba(0, 0, 0, 0.4);
            position: relative;
        }

        header .academy {
            font-size: 11px;
            font-weight: 800;
            letter-spacing: 2px;
            color: var(--gold);
            text-transform: uppercase;
            margin-bottom: 4px;
        }

        header .event {
            font-size: 24px;
            font-weight: 800;
            color: #FFFFFF;
            text-shadow: 0 2px 8px rgba(0, 0, 0, 0.5);
        }

        header .live-badge {
            display: inline-flex;
            align-items: center;
            gap: 6px;
            background: rgba(239, 68, 68, 0.15);
            color: #EF4444;
            border: 1px solid rgba(239, 68, 68, 0.4);
            font-size: 11px;
            font-weight: 700;
            padding: 4px 12px;
            border-radius: 999px;
            margin-top: 8px;
            text-transform: uppercase;
        }

        header .live-badge span {
            width: 8px;
            height: 8px;
            background-color: #EF4444;
            border-radius: 50%;
            display: inline-block;
            animation: pulse 1.5s infinite;
        }

        @keyframes pulse {
            0% { transform: scale(0.9); opacity: 0.6; }
            50% { transform: scale(1.3); opacity: 1; }
            100% { transform: scale(0.9); opacity: 0.6; }
        }

        /* NAVIGATION TABS */
        nav {
            display: flex;
            justify-content: center;
            gap: 6px;
            background: rgba(11, 19, 43, 0.9);
            padding: 10px;
            border-bottom: 1px solid var(--border-color);
            position: sticky;
            top: 0;
            z-index: 100;
            backdrop-filter: blur(12px);
            box-shadow: 0 4px 15px rgba(0,0,0,0.3);
        }

        nav button {
            background: transparent;
            border: 1px solid transparent;
            color: var(--text-secondary);
            font-size: 12px;
            font-weight: 700;
            padding: 8px 14px;
            border-radius: 8px;
            cursor: pointer;
            transition: all 0.25s ease;
            display: flex;
            align-items: center;
            gap: 6px;
        }

        nav button.active {
            background: linear-gradient(135deg, var(--gold) 0%, var(--gold-bright) 100%);
            color: #0F172A;
            box-shadow: 0 0 12px var(--gold-glow);
            font-weight: 800;
        }

        /* MAIN WORKSPACE */
        main {
            flex: 1;
            max-width: 900px;
            width: 100%;
            margin: 0 auto;
            padding: 20px 14px;
        }

        .tab-content {
            display: none;
            animation: fadeIn 0.3s ease-in-out;
        }

        .tab-content.active {
            display: block;
        }

        @keyframes fadeIn {
            from { opacity: 0; transform: translateY(6px); }
            to { opacity: 1; transform: translateY(0); }
        }

        /* SYNC BAR */
        .sync-info {
            display: flex;
            justify-content: space-between;
            align-items: center;
            background: var(--bg-card);
            border: 1px solid var(--border-color);
            padding: 10px 14px;
            border-radius: 12px;
            margin-bottom: 20px;
            font-size: 11px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.2);
        }

        .sync-info .time {
            color: var(--text-secondary);
        }

        .sync-info button {
            background: rgba(245, 158, 11, 0.1);
            color: var(--gold-bright);
            border: 1px solid var(--gold);
            padding: 6px 12px;
            border-radius: 6px;
            font-weight: 700;
            font-size: 11px;
            cursor: pointer;
            display: flex;
            align-items: center;
            gap: 6px;
        }

        /* PODIUM */
        .podium {
            display: flex;
            justify-content: center;
            align-items: flex-end;
            gap: 12px;
            margin: 15px 0 30px 0;
            height: 210px;
            padding-bottom: 10px;
        }

        .podium-spot {
            flex: 1;
            max-width: 120px;
            display: flex;
            flex-direction: column;
            align-items: center;
            position: relative;
        }

        .podium-avatar {
            width: 58px;
            height: 58px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 18px;
            font-weight: 800;
            color: #fff;
            margin-bottom: 6px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.4);
            border: 2.5px solid transparent;
            z-index: 2;
            text-shadow: 0 1px 3px rgba(0,0,0,0.6);
        }

        .podium-spot.first .podium-avatar {
            width: 70px;
            height: 70px;
            font-size: 22px;
            border-color: var(--gold-bright);
            box-shadow: 0 0 18px var(--gold-glow);
        }

        .podium-column {
            width: 100%;
            background: linear-gradient(180deg, #1E293B 0%, #0F172A 100%);
            border: 1.5px solid var(--border-color);
            border-bottom: none;
            border-radius: 12px 12px 0 0;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            padding: 12px 6px;
            position: relative;
        }

        .podium-spot.first .podium-column {
            height: 130px;
            border-color: var(--gold);
        }

        .podium-spot.second .podium-column {
            height: 95px;
        }

        .podium-spot.third .podium-column {
            height: 75px;
        }

        .podium-rank {
            font-size: 22px;
            font-weight: 800;
        }

        .podium-spot.first .podium-rank { color: var(--gold-bright); }
        .podium-spot.second .podium-rank { color: var(--silver); }
        .podium-spot.third .podium-rank { color: var(--bronze); }

        .podium-name {
            font-size: 12px;
            font-weight: 800;
            text-align: center;
            width: 100%;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            color: var(--text-primary);
        }

        .podium-points {
            font-size: 11px;
            font-weight: 800;
            color: var(--gold-bright);
            margin-top: 3px;
        }

        /* CARDS & ITEMS */
        .grid-cards {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
            gap: 14px;
        }

        .team-card {
            background-color: var(--bg-card);
            border: 1px solid var(--border-color);
            border-radius: 12px;
            padding: 16px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            box-shadow: 0 4px 12px rgba(0,0,0,0.2);
        }

        .team-badge {
            width: 12px;
            height: 38px;
            border-radius: 4px;
            margin-right: 12px;
        }

        .team-name {
            font-size: 15px;
            font-weight: 700;
            color: #FFFFFF;
        }

        .team-prizes-summary {
            font-size: 11px;
            color: var(--text-secondary);
            margin-top: 3px;
            display: flex;
            gap: 8px;
        }

        .team-points {
            font-size: 20px;
            font-weight: 800;
            color: var(--gold-bright);
            text-align: right;
        }

        /* SEARCH BAR */
        .search-bar {
            width: 100%;
            background: var(--bg-card);
            border: 1px solid var(--border-color);
            border-radius: 10px;
            padding: 12px 14px;
            color: #FFFFFF;
            font-size: 13px;
            margin-bottom: 16px;
            outline: none;
        }

        .search-bar:focus {
            border-color: var(--gold-bright);
        }

        .list-container {
            display: flex;
            flex-direction: column;
            gap: 12px;
        }

        .row-item {
            background: var(--bg-card);
            border: 1px solid var(--border-color);
            padding: 14px 16px;
            border-radius: 12px;
            display: flex;
            justify-content: space-between;
            align-items: center;
        }

        .row-title {
            font-weight: 700;
            font-size: 14px;
            color: #FFFFFF;
        }

        .row-subtitle {
            font-size: 11px;
            color: var(--text-secondary);
            margin-top: 2px;
        }

        .pill {
            display: inline-block;
            font-size: 10px;
            font-weight: 700;
            padding: 4px 10px;
            border-radius: 999px;
            background: rgba(245, 158, 11, 0.12);
            color: var(--gold-bright);
            border: 1px solid var(--gold);
        }

        /* COMPETITION RESULT CARD */
        .competition-card {
            background: var(--bg-card);
            border: 1px solid var(--border-color);
            border-radius: 14px;
            padding: 18px;
            margin-bottom: 16px;
            box-shadow: 0 4px 16px rgba(0,0,0,0.25);
        }

        .competition-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            border-bottom: 1px solid var(--border-color);
            padding-bottom: 10px;
            margin-bottom: 12px;
            flex-wrap: wrap;
            gap: 8px;
        }

        .competition-title {
            font-size: 16px;
            font-weight: 800;
            color: var(--gold-bright);
        }

        .btn-poster {
            background: linear-gradient(135deg, var(--gold) 0%, var(--gold-bright) 100%);
            color: #0F172A;
            border: none;
            padding: 7px 14px;
            border-radius: 8px;
            font-size: 11px;
            font-weight: 800;
            cursor: pointer;
            display: inline-flex;
            align-items: center;
            gap: 6px;
            transition: transform 0.15s ease;
        }

        .btn-poster:active {
            transform: scale(0.96);
        }

        .winners-row {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 10px;
        }

        .winner-spot {
            border-radius: 10px;
            padding: 12px;
            display: flex;
            align-items: center;
            gap: 10px;
            background: rgba(15, 23, 42, 0.6);
            border: 1px solid rgba(255, 255, 255, 0.05);
        }

        .winner-spot.first-place {
            background: linear-gradient(135deg, rgba(245, 158, 11, 0.15) 0%, rgba(15, 23, 42, 0.8) 100%);
            border: 1px solid var(--gold);
        }

        .winner-spot.second-place {
            background: linear-gradient(135deg, rgba(203, 213, 225, 0.1) 0%, rgba(15, 23, 42, 0.8) 100%);
            border: 1px solid var(--silver);
        }

        .winner-spot.third-place {
            background: linear-gradient(135deg, rgba(217, 119, 6, 0.1) 0%, rgba(15, 23, 42, 0.8) 100%);
            border: 1px solid var(--bronze);
        }

        .winner-icon {
            width: 32px;
            height: 32px;
            border-radius: 50%;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 16px;
        }

        .winner-icon.gold { background: rgba(245, 158, 11, 0.2); color: var(--gold-bright); }
        .winner-icon.silver { background: rgba(203, 213, 225, 0.2); color: var(--silver); }
        .winner-icon.bronze { background: rgba(217, 119, 6, 0.2); color: var(--bronze); }
        .winner-icon.none { background: rgba(255, 255, 255, 0.05); color: var(--text-secondary); }

        .winner-details {
            display: flex;
            flex-direction: column;
            flex: 1;
        }

        .winner-details .badge-title {
            font-size: 10px;
            font-weight: 800;
            text-transform: uppercase;
        }

        .winner-details .name {
            font-size: 13px;
            font-weight: 700;
            color: #FFFFFF;
            margin-top: 1px;
        }

        .winner-details .team {
            font-size: 10px;
            color: var(--text-secondary);
        }

        .winner-details .grade {
            font-size: 10px;
            font-weight: 700;
            color: var(--gold-bright);
            margin-top: 2px;
        }

        /* MODAL FOR POSTER PREVIEW */
        .modal-overlay {
            display: none;
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0, 0, 0, 0.85);
            z-index: 1000;
            align-items: center;
            justify-content: center;
            padding: 16px;
            backdrop-filter: blur(8px);
        }

        .modal-content {
            background: #0F172A;
            border: 2px solid var(--gold);
            border-radius: 16px;
            max-width: 500px;
            width: 100%;
            max-height: 90vh;
            display: flex;
            flex-direction: column;
            align-items: center;
            padding: 16px;
            position: relative;
            box-shadow: 0 10px 30px rgba(0,0,0,0.8);
            overflow-y: auto;
        }

        .modal-close {
            position: absolute;
            top: 12px;
            right: 12px;
            background: rgba(255, 255, 255, 0.1);
            color: #FFF;
            border: none;
            width: 32px;
            height: 32px;
            border-radius: 50%;
            font-size: 16px;
            cursor: pointer;
        }

        canvas#poster-canvas {
            width: 100%;
            max-width: 420px;
            border-radius: 12px;
            border: 1px solid var(--border-color);
            box-shadow: 0 6px 20px rgba(0,0,0,0.5);
            margin: 12px 0;
        }

        .btn-download-poster {
            width: 100%;
            max-width: 420px;
            background: linear-gradient(135deg, var(--gold) 0%, var(--gold-bright) 100%);
            color: #0F172A;
            border: none;
            padding: 12px;
            border-radius: 10px;
            font-size: 14px;
            font-weight: 800;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 8px;
            margin-top: 8px;
        }

        /* FOOTER */
        footer {
            background: #070A13;
            padding: 16px;
            text-align: center;
            font-size: 11px;
            color: var(--text-secondary);
            border-top: 1px solid var(--border-color);
            margin-top: auto;
        }

        @media (max-width: 600px) {
            header .event { font-size: 20px; }
            nav button { padding: 6px 10px; font-size: 11px; }
            .podium { height: 180px; }
            .podium-avatar { width: 44px; height: 44px; font-size: 14px; }
            .podium-spot.first .podium-avatar { width: 54px; height: 54px; font-size: 18px; }
            .podium-spot.first .podium-column { height: 100px; }
            .podium-spot.second .podium-column { height: 75px; }
            .podium-spot.third .podium-column { height: 55px; }
        }
    </style>
</head>
<body>

    <!-- Header -->
    <header>
        <div class="academy" id="dom-madrasa-name">$madrasaName</div>
        <div class="event" id="dom-event-name">$eventName</div>
        <div class="live-badge">
            <span></span> ലൈവ് സ്കോർ ബോർഡ് (LIVE)
        </div>
    </header>

    <!-- Navigation -->
    <nav>
        <button onclick="switchTab(0)" class="nav-btn active"><i class="fa-solid fa-trophy"></i> ടീം സ്കോർ</button>
        <button onclick="switchTab(1)" class="nav-btn"><i class="fa-solid fa-users"></i> ടീമുകൾ</button>
        <button onclick="switchTab(2)" class="nav-btn"><i class="fa-solid fa-award"></i> റിസൾട്ടുകൾ</button>
        <button onclick="switchTab(3)" class="nav-btn"><i class="fa-solid fa-graduation-cap"></i> കുട്ടികൾ</button>
    </nav>

    <main>
        <!-- Sync Bar -->
        <div class="sync-info">
            <div class="time" id="sync-timestamp">ക്ലൗഡിലേക്ക് കണക്ട് ചെയ്യുന്നു...</div>
            <button onclick="fetchScores()"><i class="fa-solid fa-arrows-rotate" id="refresh-icon"></i> റിഫ്രഷ്</button>
        </div>

        <div id="loading-spinner" style="text-align: center; padding: 40px; color: var(--text-secondary);">
            <i class="fa-solid fa-circle-notch fa-spin fa-2x" style="color: var(--gold-bright);"></i>
            <p style="margin-top: 10px; font-size: 13px;">തത്സമയ വിവരങ്ങൾ ലോഡ് ചെയ്യുന്നു...</p>
        </div>

        <!-- Tab 0: Team Scores & Leaderboard -->
        <div id="tab-0" class="tab-content">
            <div class="podium" id="dom-podium">
                <!-- Injected via JS -->
            </div>
            
            <div style="margin-top: 24px;">
                <h3 style="color: var(--gold-bright); margin-bottom: 12px; font-weight: 800; font-size: 13px; letter-spacing: 1px; text-transform: uppercase;">ടീം പോയിന്റ് നില (TEAM LEADERBOARD)</h3>
                <div class="list-container" id="dom-leaderboard-list">
                    <!-- Injected via JS -->
                </div>
            </div>
        </div>

        <!-- Tab 1: Teams List -->
        <div id="tab-1" class="tab-content">
            <input type="text" id="search-teams" class="search-bar" placeholder="ടീം പേര് തിരയുക..." oninput="renderTeams()">
            <div class="grid-cards" id="dom-teams-grid">
                <!-- Injected via JS -->
            </div>
        </div>

        <!-- Tab 2: Results & Poster Download -->
        <div id="tab-2" class="tab-content">
            <input type="text" id="search-winners" class="search-bar" placeholder="മത്സരത്തിന്റെ പേര് അല്ലെങ്കിൽ വിജയിയെ തിരയുക..." oninput="renderWinners()">
            <div class="list-container" id="dom-winners-list">
                <!-- Injected via JS -->
            </div>
        </div>

        <!-- Tab 3: Students -->
        <div id="tab-3" class="tab-content">
            <input type="text" id="search-students" class="search-bar" placeholder="കുട്ടിയുടെ പേര് തിരയുക..." oninput="renderStudents()">
            <div class="list-container" id="dom-students-list">
                <!-- Injected via JS -->
            </div>
        </div>
    </main>

    <!-- Poster Modal Overlay -->
    <div id="poster-modal" class="modal-overlay">
        <div class="modal-content">
            <button class="modal-close" onclick="closePosterModal()">&times;</button>
            <h3 style="color: var(--gold-bright); font-size: 16px; font-weight: 800; text-align: center;">🎨 മത്സരം റിസൾട്ട് പോസ്റ്റർ</h3>
            <p style="color: var(--text-secondary); font-size: 11px; margin-top: 2px;">കഴിഞ്ഞുപോയ മത്സരത്തിന്റെ ഒഫീഷ്യൽ പോസ്റ്റർ</p>
            
            <canvas id="poster-canvas"></canvas>
            
            <button class="btn-download-poster" onclick="downloadPosterCanvas()">
                <i class="fa-solid fa-download"></i> പോസ്റ്റർ ഡൗൺലോഡ് ചെയ്യുക (Download PNG)
            </button>
        </div>
    </div>

    <!-- Footer -->
    <footer>
        <p>തത്സമയ തത്സമയ ഫലങ്ങൾ • Live Webcast Powered by Leadscorer</p>
        <p style="margin-top: 4px; opacity: 0.5; font-size: 9px;">Sync Key: $syncKey</p>
    </footer>

    <script>
        const BLOB_ID = "$syncKey";
        const API_URL = "https://jsonblob.com/api/jsonBlob/" + BLOB_ID;

        let dbData = {
            madrasaName: "$madrasaName",
            eventName: "$eventName",
            groups: [],
            students: [],
            competitions: []
        };

        let groupScores = [];
        let studentMap = {};
        let groupMap = {};

        async function fetchScores() {
            const icon = document.getElementById('refresh-icon');
            if (icon) icon.classList.add('fa-spin');
            
            try {
                const response = await fetch(API_URL + "?_t=" + new Date().getTime(), {
                    cache: "no-store",
                    headers: { "Pragma": "no-cache", "Cache-Control": "no-cache" }
                });
                if (!response.ok) throw new Error("Status " + response.status);
                
                dbData = await response.json();
                
                studentMap = {};
                (dbData.students || []).forEach(s => { studentMap[s.id] = s; });
                
                groupMap = {};
                (dbData.groups || []).forEach(g => { groupMap[g.id] = g; });

                calculateLeaderboard();

                document.getElementById('dom-madrasa-name').innerText = dbData.madrasaName || 'Madrasa';
                document.getElementById('dom-event-name').innerText = dbData.eventName || 'Arts Fest';
                document.getElementById('sync-timestamp').innerText = "അവസാനം പുതുക്കിയത്: " + (dbData.lastUpdated || new Date().toLocaleTimeString());
                
                document.getElementById('loading-spinner').style.display = 'none';
                renderCurrentTab();

            } catch (err) {
                console.error("Sync Error", err);
                document.getElementById('sync-timestamp').innerText = "കണക്ഷൻ പ്രശ്നം. വീണ്ടും ശ്രമിക്കുന്നു...";
            } finally {
                if (icon) icon.classList.remove('fa-spin');
            }
        }

        function calculateLeaderboard() {
            const scoreMap = {}, goldMap = {}, silverMap = {}, bronzeMap = {};

            (dbData.groups || []).forEach(g => {
                scoreMap[g.id] = 0;
                goldMap[g.id] = 0;
                silverMap[g.id] = 0;
                bronzeMap[g.id] = 0;
            });

            (dbData.competitions || []).forEach(c => {
                if (c.firstStudentId && studentMap[c.firstStudentId]) {
                    const gId = studentMap[c.firstStudentId].groupId;
                    scoreMap[gId] = (scoreMap[gId] || 0) + (c.firstMarks || 5);
                    goldMap[gId] = (goldMap[gId] || 0) + 1;
                }
                if (c.secondStudentId && studentMap[c.secondStudentId]) {
                    const gId = studentMap[c.secondStudentId].groupId;
                    scoreMap[gId] = (scoreMap[gId] || 0) + (c.secondMarks || 3);
                    silverMap[gId] = (silverMap[gId] || 0) + 1;
                }
                if (c.thirdStudentId && studentMap[c.thirdStudentId]) {
                    const gId = studentMap[c.thirdStudentId].groupId;
                    scoreMap[gId] = (scoreMap[gId] || 0) + (c.thirdMarks || 1);
                    bronzeMap[gId] = (bronzeMap[gId] || 0) + 1;
                }
            });

            groupScores = (dbData.groups || []).map(g => {
                return {
                    group: g,
                    points: scoreMap[g.id] || 0,
                    gold: goldMap[g.id] || 0,
                    silver: silverMap[g.id] || 0,
                    bronze: bronzeMap[g.id] || 0
                };
            }).sort((a, b) => b.points - a.points);
        }

        let activeTab = 0;
        function switchTab(idx) {
            activeTab = idx;
            document.querySelectorAll('nav button').forEach((btn, i) => {
                if (i === idx) btn.classList.add('active');
                else btn.classList.remove('active');
            });
            document.querySelectorAll('.tab-content').forEach((c, i) => {
                if (i === idx) c.classList.add('active');
                else c.classList.remove('active');
            });
            renderCurrentTab();
        }

        function renderCurrentTab() {
            if (activeTab === 0) renderLeaderboard();
            if (activeTab === 1) renderTeams();
            if (activeTab === 2) renderWinners();
            if (activeTab === 3) renderStudents();
        }

        function renderLeaderboard() {
            const podiumDiv = document.getElementById('dom-podium');
            podiumDiv.innerHTML = '';

            const first = groupScores[0];
            const second = groupScores[1];
            const third = groupScores[2];

            if (second) {
                podiumDiv.innerHTML += `
                <div class="podium-spot second">
                    <div class="podium-avatar" style="background-color: ${'$'}{second.group.colorHex}">${'$'}{second.group.name.substring(0, 2).toUpperCase()}</div>
                    <div class="podium-column">
                        <div class="podium-rank">2</div>
                        <div class="podium-name">${'$'}{second.group.name}</div>
                        <div class="podium-points">${'$'}{second.points} PTS</div>
                    </div>
                </div>`;
            }

            if (first) {
                podiumDiv.innerHTML += `
                <div class="podium-spot first">
                    <div class="podium-avatar" style="background-color: ${'$'}{first.group.colorHex}">
                        <i class="fa-solid fa-crown" style="position: absolute; top: -14px; font-size: 16px; color: var(--gold-bright);"></i>
                        ${'$'}{first.group.name.substring(0, 2).toUpperCase()}
                    </div>
                    <div class="podium-column">
                        <div class="podium-rank">1</div>
                        <div class="podium-name">${'$'}{first.group.name}</div>
                        <div class="podium-points">${'$'}{first.points} PTS</div>
                    </div>
                </div>`;
            }

            if (third) {
                podiumDiv.innerHTML += `
                <div class="podium-spot third">
                    <div class="podium-avatar" style="background-color: ${'$'}{third.group.colorHex}">${'$'}{third.group.name.substring(0, 2).toUpperCase()}</div>
                    <div class="podium-column">
                        <div class="podium-rank">3</div>
                        <div class="podium-name">${'$'}{third.group.name}</div>
                        <div class="podium-points">${'$'}{third.points} PTS</div>
                    </div>
                </div>`;
            }

            const listDiv = document.getElementById('dom-leaderboard-list');
            listDiv.innerHTML = '';

            groupScores.forEach((gs, idx) => {
                listDiv.innerHTML += `
                <div class="row-item">
                    <div style="display: flex; align-items: center; gap: 12px;">
                        <div style="font-weight: 800; color: var(--gold-bright); font-size: 15px; width: 22px;">#${'$'}{idx + 1}</div>
                        <div class="team-badge" style="background-color: ${'$'}{gs.group.colorHex}"></div>
                        <div>
                            <div class="row-title">${'$'}{gs.group.name}</div>
                            <div class="team-prizes-summary">
                                <span>🥇 ${'$'}{gs.gold} Gold</span>
                                <span>🥈 ${'$'}{gs.silver} Silver</span>
                                <span>🥉 ${'$'}{gs.bronze} Bronze</span>
                            </div>
                        </div>
                    </div>
                    <div style="text-align: right;">
                        <div class="team-points">${'$'}{gs.points}</div>
                        <div style="font-size: 10px; color: var(--text-secondary); text-transform: uppercase;">Points</div>
                    </div>
                </div>`;
            });
        }

        function renderTeams() {
            const query = (document.getElementById('search-teams').value || '').toLowerCase();
            const gridDiv = document.getElementById('dom-teams-grid');
            gridDiv.innerHTML = '';

            groupScores.forEach((gs, idx) => {
                if (gs.group.name.toLowerCase().includes(query)) {
                    gridDiv.innerHTML += `
                    <div class="team-card">
                        <div style="display: flex; align-items: center;">
                            <div class="team-badge" style="background-color: ${'$'}{gs.group.colorHex}"></div>
                            <div>
                                <div class="team-name">${'$'}{gs.group.name}</div>
                                <div class="team-prizes-summary">
                                    <span>🥇 ${'$'}{gs.gold} G</span>
                                    <span>🥈 ${'$'}{gs.silver} S</span>
                                    <span>🥉 ${'$'}{gs.bronze} B</span>
                                </div>
                            </div>
                        </div>
                        <div style="text-align: right;">
                            <div class="team-points">${'$'}{gs.points}</div>
                            <div style="font-size: 10px; color: var(--text-secondary); font-weight: 800;">RANK #${'$'}{idx + 1}</div>
                        </div>
                    </div>`;
                }
            });
        }

        function renderWinners() {
            const query = (document.getElementById('search-winners').value || '').toLowerCase();
            const listDiv = document.getElementById('dom-winners-list');
            listDiv.innerHTML = '';

            (dbData.competitions || []).forEach(c => {
                const s1 = studentMap[c.firstStudentId];
                const s2 = studentMap[c.secondStudentId];
                const s3 = studentMap[c.thirdStudentId];

                const matchesQuery = c.name.toLowerCase().includes(query) ||
                    (s1 && s1.name.toLowerCase().includes(query)) ||
                    (s2 && s2.name.toLowerCase().includes(query)) ||
                    (s3 && s3.name.toLowerCase().includes(query));

                if (!matchesQuery) return;

                const g1 = s1 ? groupMap[s1.groupId] : null;
                const g2 = s2 ? groupMap[s2.groupId] : null;
                const g3 = s3 ? groupMap[s3.groupId] : null;

                const isPublished = s1 || s2 || s3;

                listDiv.innerHTML += `
                <div class="competition-card">
                    <div class="competition-header">
                        <div class="competition-title">
                            <i class="fa-solid fa-star" style="color: var(--gold-bright); margin-right: 6px;"></i> ${'$'}{c.name}
                        </div>
                        ${'$'}{isPublished ? `
                            <button class="btn-poster" onclick="openPosterModal(${'$'}{c.id})">
                                <i class="fa-solid fa-image"></i> പോസ്റ്റർ കാണുക / ഡൗൺലോഡ്
                            </button>
                        ` : '<span class="pill" style="opacity: 0.6;">ഫലം പ്രസിദ്ധീകരിച്ചിട്ടില്ല</span>'}
                    </div>
                    <div class="winners-row">
                        <!-- First Place -->
                        <div class="winner-spot ${'$'}{s1 ? 'first-place' : ''}">
                            <div class="winner-icon ${'$'}{s1 ? 'gold' : 'none'}"><i class="fa-solid fa-trophy"></i></div>
                            <div class="winner-details">
                                <div class="badge-title" style="color: var(--gold-bright);">1st Place (🥇 ഒന്നാം സ്ഥാനം)</div>
                                <div class="name">${'$'}{s1 ? s1.name : 'വിജയി ആയിട്ടില്ല'}</div>
                                <div class="team">${'$'}{g1 ? '🚩 ' + g1.name : ''}</div>
                                <div class="grade">${'$'}{s1 ? (c.firstGrade ? 'Grade ' + c.firstGrade + ' | ' : '') + (c.firstMarks || 5) + ' Pts' : ''}</div>
                            </div>
                        </div>
                        <!-- Second Place -->
                        <div class="winner-spot ${'$'}{s2 ? 'second-place' : ''}">
                            <div class="winner-icon ${'$'}{s2 ? 'silver' : 'none'}"><i class="fa-solid fa-award"></i></div>
                            <div class="winner-details">
                                <div class="badge-title" style="color: var(--silver);">2nd Place (🥈 രണ്ടാം സ്ഥാനം)</div>
                                <div class="name">${'$'}{s2 ? s2.name : 'വിജയി ആയിട്ടില്ല'}</div>
                                <div class="team">${'$'}{g2 ? '🚩 ' + g2.name : ''}</div>
                                <div class="grade">${'$'}{s2 ? (c.secondGrade ? 'Grade ' + c.secondGrade + ' | ' : '') + (c.secondMarks || 3) + ' Pts' : ''}</div>
                            </div>
                        </div>
                        <!-- Third Place -->
                        <div class="winner-spot ${'$'}{s3 ? 'third-place' : ''}">
                            <div class="winner-icon ${'$'}{s3 ? 'bronze' : 'none'}"><i class="fa-solid fa-medal"></i></div>
                            <div class="winner-details">
                                <div class="badge-title" style="color: var(--bronze);">3rd Place (🥉 മൂന്നാം സ്ഥാനം)</div>
                                <div class="name">${'$'}{s3 ? s3.name : 'വിജയി ആയിട്ടില്ല'}</div>
                                <div class="team">${'$'}{g3 ? '🚩 ' + g3.name : ''}</div>
                                <div class="grade">${'$'}{s3 ? (c.thirdGrade ? 'Grade ' + c.thirdGrade + ' | ' : '') + (c.thirdMarks || 1) + ' Pts' : ''}</div>
                            </div>
                        </div>
                    </div>
                </div>`;
            });
        }

        function renderStudents() {
            const query = (document.getElementById('search-students').value || '').toLowerCase();
            const listDiv = document.getElementById('dom-students-list');
            listDiv.innerHTML = '';

            let count = 0;
            (dbData.students || []).forEach(s => {
                if (s.name.toLowerCase().includes(query)) {
                    count++;
                    const g = groupMap[s.groupId];
                    const teamMarkup = g ? `<span class="pill" style="border-color: ${'$'}{g.colorHex}; color: ${'$'}{g.colorHex}; background: transparent">${'$'}{g.name}</span>` : '';
                    listDiv.innerHTML += `
                    <div class="row-item">
                        <div>
                            <div class="row-title">${'$'}{s.name}</div>
                            <div class="row-subtitle">ID: ST-${'$'}{s.id}</div>
                        </div>
                        <div>${'$'}{teamMarkup}</div>
                    </div>`;
                }
            });

            if (count === 0) {
                listDiv.innerHTML = '<div style="text-align: center; color: var(--text-secondary); font-size: 12px; padding: 20px;">കുട്ടികളെ കണ്ടെത്തിയില്ല.</div>';
            }
        }

        /* POSTER GENERATOR ON CANVAS */
        function openPosterModal(compId) {
            const comp = (dbData.competitions || []).find(c => c.id === compId);
            if (!comp) return;

            const s1 = studentMap[comp.firstStudentId];
            const s2 = studentMap[comp.secondStudentId];
            const s3 = studentMap[comp.thirdStudentId];

            const g1 = s1 ? groupMap[s1.groupId] : null;
            const g2 = s2 ? groupMap[s2.groupId] : null;
            const g3 = s3 ? groupMap[s3.groupId] : null;

            const canvas = document.getElementById('poster-canvas');
            const ctx = canvas.getContext('2d');

            canvas.width = 800;
            canvas.height = 1080;

            // Background
            const bg = ctx.createLinearGradient(0, 0, 0, 1080);
            bg.addColorStop(0, '#0F172A');
            bg.addColorStop(0.5, '#1E293B');
            bg.addColorStop(1, '#020617');
            ctx.fillStyle = bg;
            ctx.fillRect(0, 0, 800, 1080);

            // Double Gold Borders
            ctx.strokeStyle = '#F59E0B';
            ctx.lineWidth = 10;
            ctx.strokeRect(20, 20, 760, 1040);

            ctx.strokeStyle = 'rgba(245, 158, 11, 0.4)';
            ctx.lineWidth = 2;
            ctx.strokeRect(32, 32, 736, 1016);

            // Corner Accents
            ctx.fillStyle = '#F59E0B';
            ctx.fillRect(20, 20, 30, 30);
            ctx.fillRect(750, 20, 30, 30);
            ctx.fillRect(20, 1030, 30, 30);
            ctx.fillRect(750, 1030, 30, 30);

            // Header Texts
            ctx.textAlign = 'center';

            ctx.fillStyle = '#FBBF24';
            ctx.font = 'bold 22px "Noto Sans Malayalam", sans-serif';
            ctx.fillText((dbData.madrasaName || 'MADRASA ARTS FEST').toUpperCase(), 400, 85);

            ctx.fillStyle = '#FFFFFF';
            ctx.font = '800 36px "Noto Sans Malayalam", sans-serif';
            ctx.fillText(dbData.eventName || 'ARTS FESTIVAL', 400, 135);

            ctx.beginPath();
            ctx.moveTo(180, 160);
            ctx.lineTo(620, 160);
            ctx.strokeStyle = '#F59E0B';
            ctx.lineWidth = 3;
            ctx.stroke();

            // Competition Title Box
            ctx.fillStyle = 'rgba(245, 158, 11, 0.12)';
            ctx.fillRect(100, 185, 600, 75);
            ctx.strokeStyle = '#F59E0B';
            ctx.lineWidth = 1.5;
            ctx.strokeRect(100, 185, 600, 75);

            ctx.fillStyle = '#FBBF24';
            ctx.font = '700 18px "Noto Sans Malayalam", sans-serif';
            ctx.fillText('ഫലപ്രഖ്യാപനം (RESULT ANNOUNCEMENT)', 400, 215);

            ctx.fillStyle = '#FFFFFF';
            ctx.font = '800 28px "Noto Sans Malayalam", sans-serif';
            ctx.fillText('മത്സരം: ' + comp.name, 400, 248);

            // Winner Boxes
            drawPosterWinnerSpot(ctx, 80, 285, 640, 175, '#F59E0B', '🥇 ഒന്നാം സ്ഥാനം (FIRST PLACE)',
                s1 ? s1.name : 'വിജയി ആയിട്ടില്ല', g1 ? g1.name : '', comp.firstGrade, comp.firstMarks || 5);

            drawPosterWinnerSpot(ctx, 80, 480, 640, 165, '#CBD5E1', '🥈 രണ്ടാം സ്ഥാനം (SECOND PLACE)',
                s2 ? s2.name : 'വിജയി ആയിട്ടില്ല', g2 ? g2.name : '', comp.secondGrade, comp.secondMarks || 3);

            drawPosterWinnerSpot(ctx, 80, 665, 640, 165, '#D97706', '🥉 മൂന്നാം സ്ഥാനം (THIRD PLACE)',
                s3 ? s3.name : 'വിജയി ആയിട്ടില്ല', g3 ? g3.name : '', comp.thirdGrade, comp.thirdMarks || 1);

            // Footer Stamp
            ctx.fillStyle = '#94A3B8';
            ctx.font = '14px "Noto Sans Malayalam", sans-serif';
            ctx.fillText('തത്സമയ ഒഫീഷ്യൽ വെബ്കാസ്റ്റ് റിസൾട്ട് • Date: ' + new Date().toLocaleDateString(), 400, 915);

            ctx.fillStyle = '#FBBF24';
            ctx.font = 'bold 18px "Noto Sans Malayalam", sans-serif';
            ctx.fillText('★ വിന്നേഴ്സിന് ആശംസകൾ ★', 400, 955);

            document.getElementById('poster-modal').style.display = 'flex';
        }

        function drawPosterWinnerSpot(ctx, x, y, w, h, color, title, name, team, grade, marks) {
            ctx.fillStyle = 'rgba(15, 23, 42, 0.85)';
            ctx.fillRect(x, y, w, h);

            ctx.strokeStyle = color;
            ctx.lineWidth = 2.5;
            ctx.strokeRect(x, y, w, h);

            ctx.fillStyle = color;
            ctx.fillRect(x, y, 14, h);

            ctx.textAlign = 'left';
            ctx.fillStyle = color;
            ctx.font = '700 16px "Noto Sans Malayalam", sans-serif';
            ctx.fillText(title, x + 30, y + 36);

            ctx.fillStyle = '#FFFFFF';
            ctx.font = '800 26px "Noto Sans Malayalam", sans-serif';
            ctx.fillText(name, x + 30, y + 78);

            if (team) {
                ctx.fillStyle = '#CBD5E1';
                ctx.font = '600 18px "Noto Sans Malayalam", sans-serif';
                ctx.fillText('🚩 ടീം: ' + team, x + 30, y + 118);
            }

            if (name !== 'വിജയി ആയിട്ടില്ല') {
                ctx.textAlign = 'right';
                ctx.fillStyle = color;
                ctx.font = '700 20px "Noto Sans Malayalam", sans-serif';
                const txt = (grade ? 'Grade: ' + grade + ' | ' : '') + marks + ' Pts';
                ctx.fillText(txt, x + w - 20, y + 78);
            }
        }

        function downloadPosterCanvas() {
            const canvas = document.getElementById('poster-canvas');
            const link = document.createElement('a');
            link.download = 'Result_Poster.png';
            link.href = canvas.toDataURL('image/png');
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
        }

        function closePosterModal() {
            document.getElementById('poster-modal').style.display = 'none';
        }

        fetchScores();
        setInterval(fetchScores, 10000);
    </script>
</body>
</html>
"""
    }
    // --- HTML WRITER AND SHARER ---

    fun shareLiveDashboardLink(context: Context, syncKey: String, madrasaName: String, eventName: String, customUrl: String = "") {
        val link = if (customUrl.isNotBlank()) customUrl.trim() else "https://staticsave.com"
        val shareText = """
            🏆 *$madrasaName* 🏆
            🎉 *$eventName - Live Scoreboard Webcast*

            ✨ ഫലങ്ങളും സ്കോറുകളും തത്സമയം കാണാൻ താഴെയുള്ള വെബ് ലിങ്ക് ഉപയോഗിക്കുക:
            (ഒരു തവണ മാത്രം ഷെയർ ചെയ്താൽ മതി, എല്ലാ അപ്ഡേറ്റുകളും ഓട്ടോമാറ്റിക്കായി ലൈവായി കാണാം!)

            🔗 *Direct Web Link:*
            $link

            🔑 *Live Sync Key:* $syncKey

            📱 ഫലങ്ങൾ തത്സമയം അറിയാൻ ഇപ്പോൾ തന്നെ ലിങ്ക് ക്ലിക്ക് ചെയ്യുക!
        """.trimIndent()

        try {
            val intent = Intent().apply {
                action = android.content.Intent.ACTION_SEND
                type = "text/plain"
                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(android.content.Intent.createChooser(intent, "Share Live Scoreboard Link"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to share link: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareLiveDashboardHtmlFile(context: Context, syncKey: String, madrasaName: String, eventName: String) {
        val htmlContent = generateLiveDashboardHtml(syncKey, madrasaName, eventName)
        try {
            val cachePath = File(context.cacheDir, "shared_images")
            cachePath.mkdirs()
            val file = File(cachePath, "Live_Scoreboard_Website.html")
            FileOutputStream(file).use { out ->
                out.write(htmlContent.toByteArray(Charsets.UTF_8))
            }

            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                context, "com.example.scoreboard.fileprovider", file
            )
            if (contentUri != null) {
                val shareIntent = Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    type = "text/html"
                    putExtra(android.content.Intent.EXTRA_STREAM, contentUri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Standalone Web Dashboard"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Failed to share web dashboard: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

package com.example.data

import android.util.Log
import com.example.BuildConfig
import com.example.api.ContentPart
import com.example.api.GeminiClient
import com.example.api.GeminiRequest
import com.example.api.TextPart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class HealthRepository(private val healthDao: HealthDao) {

    val allHeartRates: Flow<List<HeartRateEntry>> = healthDao.getAllHeartRates()
    val recentHeartRates: Flow<List<HeartRateEntry>> = healthDao.getRecentHeartRates()
    val allSleepEntries: Flow<List<SleepEntry>> = healthDao.getAllSleepEntries()
    val allSyncStates: Flow<List<SyncState>> = healthDao.getAllSyncStates()
    val weeklyInsight: Flow<WeeklyInsight?> = healthDao.getWeeklyInsight()

    suspend fun addHeartRate(bpm: Int, activityType: String, source: String) {
        withContext(Dispatchers.IO) {
            healthDao.insertHeartRate(
                HeartRateEntry(
                    bpm = bpm,
                    timestamp = System.currentTimeMillis(),
                    activityType = activityType,
                    deviceSource = source
                )
            )
        }
    }

    suspend fun addSleep(dateLabel: String, deep: Int, light: Int, rem: Int, awake: Int, source: String) {
        withContext(Dispatchers.IO) {
            healthDao.insertSleep(
                SleepEntry(
                    dateLabel = dateLabel,
                    timestamp = System.currentTimeMillis(),
                    deepMinutes = deep,
                    lightMinutes = light,
                    remMinutes = rem,
                    awakeMinutes = awake,
                    deviceSource = source
                )
            )
        }
    }

    suspend fun clearAllData() {
        withContext(Dispatchers.IO) {
            healthDao.deleteAllHeartRates()
            healthDao.deleteAllSleep()
            healthDao.insertWeeklyInsight(
                WeeklyInsight(
                    id = 1,
                    insightText = "",
                    generatedTime = 0,
                    summaryNotes = ""
                )
            )
            healthDao.insertSyncState(SyncState("Apple Watch", false, 0))
            healthDao.insertSyncState(SyncState("Fitbit", false, 0))
        }
    }

    suspend fun syncDevice(deviceName: String) = withContext(Dispatchers.IO) {
        // Set sync state to connected
        healthDao.insertSyncState(
            SyncState(
                deviceName = deviceName,
                isConnected = true,
                lastSyncedTime = System.currentTimeMillis()
            )
        )

        // Generate synthetic historical telemetry representing a healthy synced record
        val sourceSymbol = deviceName // "Fitbit" or "Apple Watch"
        
        // Generate sleep entries for last 7 days (Mon-Sun) if they do not exist
        val baseTime = System.currentTimeMillis() - 7 * 24 * 3600 * 1000
        val sleepEntries = listOf(
            SleepEntry(dateLabel = "Mon", timestamp = baseTime + 1 * 24 * 3600 * 1000, deepMinutes = 75, lightMinutes = 240, remMinutes = 90, awakeMinutes = 15, deviceSource = sourceSymbol),
            SleepEntry(dateLabel = "Tue", timestamp = baseTime + 2 * 24 * 3600 * 1000, deepMinutes = 90, lightMinutes = 220, remMinutes = 95, awakeMinutes = 25, deviceSource = sourceSymbol),
            SleepEntry(dateLabel = "Wed", timestamp = baseTime + 3 * 24 * 3600 * 1000, deepMinutes = 65, lightMinutes = 250, remMinutes = 80, awakeMinutes = 30, deviceSource = sourceSymbol),
            SleepEntry(dateLabel = "Thu", timestamp = baseTime + 4 * 24 * 3600 * 1000, deepMinutes = 80, lightMinutes = 210, remMinutes = 110, awakeMinutes = 15, deviceSource = sourceSymbol),
            SleepEntry(dateLabel = "Fri", timestamp = baseTime + 5 * 24 * 3600 * 1000, deepMinutes = 110, lightMinutes = 180, remMinutes = 120, awakeMinutes = 20, deviceSource = sourceSymbol),
            SleepEntry(dateLabel = "Sat", timestamp = baseTime + 6 * 24 * 3600 * 1000, deepMinutes = 95, lightMinutes = 260, remMinutes = 100, awakeMinutes = 10, deviceSource = sourceSymbol),
            SleepEntry(dateLabel = "Sun", timestamp = baseTime + 7 * 24 * 3600 * 1000, deepMinutes = 85, lightMinutes = 230, remMinutes = 85, awakeMinutes = 20, deviceSource = sourceSymbol)
        )
        healthDao.insertSleepEntries(sleepEntries)

        // Generate heart rate entries (resting/active/sleeping cycles across the past 24 hours)
        val hrEntries = mutableListOf<HeartRateEntry>()
        val hrBaseTime = System.currentTimeMillis() - 24 * 3600 * 1000
        
        // Sampling every 30 minutes for a realistic set
        for (i in 0 until 48) {
            val entryTime = hrBaseTime + i * 30 * 60 * 1000
            val hourOfDay = (i / 2) % 24
            
            val (bpm, activity) = when {
                i == 30 -> {
                    // Inject a simulated anomalous resting heart rate spike (e.g. 104)
                    Pair(104, "Resting")
                }
                hourOfDay in 0..6 -> {
                    // Sleeping heart rate (low, steady)
                    val variance = (-4..4).random()
                    Pair(58 + variance, "Sleep")
                }
                hourOfDay in 7..8 -> {
                    // Morning wakeup / walking
                    val variance = (-5..8).random()
                    Pair(72 + variance, "Walking")
                }
                hourOfDay in 12..13 -> {
                    // Lunch stroll
                    val variance = (-5..10).random()
                    Pair(80 + variance, "Walking")
                }
                hourOfDay in 17..18 -> {
                    // Evening active workout (high heart rate!)
                    val variance = (-10..30).random()
                    Pair(125 + variance, "Cardio")
                }
                else -> {
                    // Routine resting active state
                    val variance = (-6..6).random()
                    Pair(68 + variance, "Resting")
                }
            }
            
            hrEntries.add(
                HeartRateEntry(
                    bpm = bpm,
                    timestamp = entryTime,
                    activityType = activity,
                    deviceSource = sourceSymbol
                )
            )
        }
        healthDao.insertHeartRates(hrEntries)
    }

    suspend fun generateWeeklyInsight(): String = withContext(Dispatchers.IO) {
        val hrs = healthDao.getAllHeartRates().firstOrNull() ?: emptyList()
        val sleep = healthDao.getAllSleepEntries().firstOrNull() ?: emptyList()

        if (hrs.isEmpty() && sleep.isEmpty()) {
            return@withContext "You don't have enough telemetry data to generate weekly insights yet. Please sync your Fitbit or Apple Watch to populate heart rate and sleep logs."
        }

        // Calculate summary metrics for the prompt
        val bpmList = hrs.map { it.bpm }
        val avgBpm = if (bpmList.isNotEmpty()) bpmList.average().toInt() else 72
        val maxBpm = if (bpmList.isNotEmpty()) bpmList.maxOrNull() ?: 140 else 140
        val minBpm = if (bpmList.isNotEmpty()) bpmList.minOrNull() ?: 55 else 55

        val totalSleepNights = sleep.size
        val avgDurationHrs = if (sleep.isNotEmpty()) {
            sleep.map { (it.deepMinutes + it.lightMinutes + it.remMinutes + it.awakeMinutes) / 60.0 }.average()
        } else {
            7.5
        }
        val avgDeepMinutes = if (sleep.isNotEmpty()) sleep.map { it.deepMinutes }.average().toInt() else 85
        val avgRemMinutes = if (sleep.isNotEmpty()) sleep.map { it.remMinutes }.average().toInt() else 95

        // Prepare prompt
        val deviceSources = (hrs.map { it.deviceSource } + sleep.map { it.deviceSource }).distinct().joinToString(" & ")
        val prompt = """
            Analyze the following personal health telemetry compiled from synced wearable devices ($deviceSources):
            
            HEART RATE SUMMARY:
            - Average: $avgBpm bpm
            - Peak Active: $maxBpm bpm
            - Minimum Resting: $minBpm bpm
            - Logged intervals includes resting steady states, walks, cardio activity, and sleeping periods.
            
            SLEEP QUALITY SUMMARY:
            - Recorded Nights: $totalSleepNights nights
            - Avg Sleep Duration: ${String.format("%.1f", avgDurationHrs)} hours
            - Avg Deep Sleep: $avgDeepMinutes minutes per night
            - Avg REM Sleep: $avgRemMinutes minutes per night
            
            Please compile a comprehensive, highly personalized health evaluation report.
            Write it in a friendly, encouraging, and highly professional biohacking coaching tone.
            Include:
            1. 📊 WEEKLY PROGRESS SYNOPSIS - Synthesize trends with relevant metrics.
            2. ❤️ CARDIOVASCULAR HEALING INSIGHTS - Address resting heart rate efficiency and cardiovascular capacity during active exercises.
            3. 🌙 SLEEP CYCLES AUDIT - Highlight if they are getting sufficient deep/REM periods compared to baseline recommendations (deep sleep should ideally be 15-25% of total sleep, REM should be 20-25%).
            4. 🚀 TOP 3 PRACTICAL BIOHACKS - Deliver concrete, non-cliché wellness and lifestyle changes they should perform this upcoming week based on this actual telemetry.
            
            Make sure your response uses rich markdown (bold items, headers, lists) for exceptional visual presentation.
        """.trimIndent()

        // Check BuildConfig API Key
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w("HealthRepository", "Gemini API key is holding default placeholder values.")
            val placeholderReport = """
                ### 📊 WEEKLY PROGRESS SYNOPSIS
                Your fitness tracking telemetry displays excellent trends over the last **$totalSleepNights nights** with an average sleep duration of **${String.format("%.1f", avgDurationHrs)} hours**. 

                ### ❤️ CARDIOVASCULAR HEALING INSIGHTS
                - **Resting Heart Rate**: Your resting heart rate averaged **$minBpm BPM**, pointing to high parasympathetic efficiency and good physical recovery.
                - **Cardio Training Peak**: During active periods, your heart rate hit a peak of **$maxBpm BPM**. This indicates active cardiovascular capacity. 

                ### 🌙 SLEEP CYCLES AUDIT
                Your sleep ratio is looking highly balanced:
                - **Deep Sleep**: **$avgDeepMinutes mins** (${String.format("%.0f", (avgDeepMinutes / (avgDurationHrs * 60)) * 100)}% of total sleep). Excellent recovery for cognitive and physical muscles.
                - **REM Sleep**: **$avgRemMinutes mins** (${String.format("%.0f", (avgRemMinutes / (avgDurationHrs * 60)) * 100)}% of total sleep). Promotes rich emotional regulation and memory consolidation.

                ### 🚀 TOP 3 PRACTICAL BIOHACKS
                1. ☕ **Aspirate Caffeine 10 Hours Prior to Bed**: Safeguard your rich **Deep Sleep** cycles by finishing your last cup before 12:00 PM.
                2. 🏃 **Target 15-Minute Zone 2 Cardio Sessions**: Since your active peak reached **$maxBpm BPM**, aim for moderate aerobic walking in the morning to stabilize baseline cardiovascular flow.
                3. 🌅 **AM Sunlight Grounding**: Look towards the east for 10 minutes after waking to anchor your circadian clock, paving the way for deeper melatonin secretion tonight.

                ---
                
                > 💡 **Notice**: To unlock real-time, AI-generated insights customized directly to your live telemetry, please enter your actual Gemini API Key in the **Secrets panel** on the bottom-left in Google AI Studio.
            """.trimIndent()
            
            // Cache in room
            healthDao.insertWeeklyInsight(
                WeeklyInsight(
                    id = 1,
                    insightText = placeholderReport,
                    generatedTime = System.currentTimeMillis(),
                    summaryNotes = "Resting: $avgBpm BPM, Sleep: ${String.format("%.1f", avgDurationHrs)} Hrs"
                )
            )
            return@withContext placeholderReport
        }

        return@withContext try {
            val response = GeminiClient.service.generateContent(
                apiKey = apiKey,
                request = GeminiRequest(
                    contents = listOf(
                        ContentPart(
                            parts = listOf(
                                TextPart(text = prompt)
                            )
                        )
                    )
                )
            )
            
            val contentText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "Unable to compile recommendations from AI. Please try editing telemetry parameters or logging values manually."
            
            healthDao.insertWeeklyInsight(
                WeeklyInsight(
                    id = 1,
                    insightText = contentText,
                    generatedTime = System.currentTimeMillis(),
                    summaryNotes = "Resting: $avgBpm BPM, Sleep: ${String.format("%.1f", avgDurationHrs)} Hrs"
                )
            )
            
            contentText
        } catch (e: Exception) {
            Log.e("HealthRepository", "Failed to contact Gemini API via REST service", e)
            val errString = "### ⚠️ Insight Compilation Error\nConnection to Gemini API failed: ${e.localizedMessage}\n\nPlease check your internet connection or verify your Gemini API key in the Google AI Studio secrets panel."
            errString
        }
    }
}

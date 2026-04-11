package foo.pilz.freaklog.data.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import android.util.Log
import foo.pilz.freaklog.data.room.experiences.ExperienceDao
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import kotlinx.coroutines.flow.firstOrNull
import com.google.ai.client.generativeai.GenerativeModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiChatbotRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val experienceDao: ExperienceDao,
    private val userPreferences: UserPreferences
) {
    suspend fun getGenerativeModelReady(): GenerativeModel? {
        val apiKey = userPreferences.aiApiKeyFlow.firstOrNull() ?: ""
        val modelName = userPreferences.aiModelNameFlow.firstOrNull() ?: "gemini-1.5-flash"

        Log.d("AiChatbotRepository", "getGenerativeModelReady called. ApiKey isBlank: ${apiKey.isBlank()}, modelName: $modelName")

        if (apiKey.isBlank()) {
            Log.e("AiChatbotRepository", "Aborting getGenerativeModelReady because API key is empty.")
            return null
        }

        return try {
            GenerativeModel(
                modelName = modelName,
                apiKey = apiKey
            ).also {
                Log.d("AiChatbotRepository", "Successfully initialized GenerativeModel($modelName)")
            }
        } catch (e: Exception) {
            Log.e("AiChatbotRepository", "Exception initializing GenerativeModel: ${e.message}", e)
            e.printStackTrace()
            null
        }
    }

    suspend fun buildContextPrompt(
        experienceId: Int,
        includeHistorySummary: Boolean
    ): String {
        val experience = experienceDao.getExperienceWithIngestionsCompanionsAndRatings(experienceId)
        
        val contextBuilder = java.lang.StringBuilder()
        contextBuilder.append("You are an assistant for a substance journaling app. You help the user primarily by providing harm reduction info and recommending complementary substances or activities based on their current state.\n\n")
        
        contextBuilder.append("Current Session State:\n")
        if (experience != null && experience.ingestionsWithCompanions.isNotEmpty()) {
            experience.ingestionsWithCompanions.forEach { ingestionWithComp ->
                val ing = ingestionWithComp.ingestion
                contextBuilder.append("- Substance: ${ing.substanceName}, Dose: ${ing.dose} ${ing.units}, Route: ${ing.administrationRoute}\n")
            }
        } else {
            contextBuilder.append("The user has not taken any substances in this session yet.\n")
        }

        if (includeHistorySummary) {
            contextBuilder.append("\nUser History Summary (recent favorites/frequent):\n")
            val latestIngestions = experienceDao.getSortedIngestions(30).firstOrNull()
            if (!latestIngestions.isNullOrEmpty()) {
                val stats = latestIngestions.groupingBy { it.substanceName }.eachCount()
                stats.forEach { (substance, count) ->
                    contextBuilder.append("- $substance (logged $count times recently)\n")
                }
            } else {
                contextBuilder.append("No previous history.\n")
            }
        }

        contextBuilder.append("\nBased on this context, reply to the user's following messages. Keep it safe, harm-reduction focused, and conversational.\n")
        
        val prompt = contextBuilder.toString()
        Log.d("AiChatbotRepository", "Built context prompt of length: ${prompt.length}")
        return prompt
    }
}

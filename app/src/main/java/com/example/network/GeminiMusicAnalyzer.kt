package com.example.network

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class MusicAnalysis(
    val trackId: Long,
    val genre: String,
    val mood: String,
    val lyricsMeaning: String,
    val funFacts: String,
    val recommendedActivity: String,
    val colorSuggestion: String
)

object GeminiMusicAnalyzer {
    private const val TAG = "GeminiMusicAnalyzer"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun analyzeSong(
        trackId: Long,
        title: String,
        artist: String,
        album: String,
        lyrics: String
    ): MusicAnalysis? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API Key is blank or placeholder")
            return@withContext null
        }

        val hasLyrics = lyrics.isNotBlank() && !lyrics.startsWith("No hay letras")
        val lyricsContext = if (hasLyrics) {
            "Letra de la canción:\n$lyrics"
        } else {
            "No se dispone de la letra exacta actualmente, analiza basándote en el título, artista y álbum."
        }

        val prompt = """
            Eres un analizador musical experto con Inteligencia Artificial. Analiza la siguiente canción y proporciona información detallada y exacta en formato JSON estructurado.
            
            Información de la canción:
            - Título: "$title"
            - Artista: "$artist"
            - Álbum: "$album"
            $lyricsContext

            Por favor, genera una respuesta en formato JSON puro. No agregues formatos markdown como ```json o ```, ni comentarios de introducción. La respuesta debe tener exactamente los siguientes campos en español:
            {
              "genre": "Género musical detallado y estilo de producción (de 1 a 2 frases en español)",
              "mood": "Estado de ánimo predominante, nivel de energía y vibra emocional (ej: 'Melancolía íntima', 'Euforia enérgica' con una breve descripción de por qué)",
              "lyricsMeaning": "Análisis y significado de las letras o el concepto/temática que evoca la canción (de 2 a 3 frases en español)",
              "funFacts": "Datos curiosos interesantes sobre el artista, la creación del álbum, su composición u otras anécdotas de producción (de 2 a 3 frases en español)",
              "recommendedActivity": "Mejores actividades para escuchar esta canción (ej: Correr, estudiar con enfoque profundo, relajarse al atardecer, viaje por carretera)",
              "colorSuggestion": "Un color hexadecimal representativo del estado de ánimo/carátula de la canción que empiece con '#' (ej: '#E53935' para canciones ardientes/enérgicas, '#3949AB' para tonos fríos, '#2E7D32' para naturaleza/desconexión)"
            }
        """.trimIndent()

        // Escaping prompt
        val escapedPrompt = prompt
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

        val jsonPayload = """
            {
              "contents": [
                {
                  "parts": [
                    {
                      "text": "$escapedPrompt"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = jsonPayload.toRequestBody(mediaType)

        val request = Request.Builder()
            .url(endpoint)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Request failed with code: ${response.code}")
                    return@withContext null
                }
                val bodyString = response.body?.string() ?: return@withContext null
                val jsonResponse = JSONObject(bodyString)
                val candidates = jsonResponse.optJSONArray("candidates") ?: return@withContext null
                val candidate = candidates.optJSONObject(0) ?: return@withContext null
                val content = candidate.optJSONObject("content") ?: return@withContext null
                val parts = content.optJSONArray("parts") ?: return@withContext null
                val part = parts.optJSONObject(0) ?: return@withContext null
                val rawText = part.optString("text")
                if (rawText.isNullOrBlank()) return@withContext null

                // Clean-up formatting from codeblocks block if any (sometimes models ignore directives)
                var cleanJson = rawText.trim()
                if (cleanJson.startsWith("```json")) {
                    cleanJson = cleanJson.removePrefix("```json")
                } else if (cleanJson.startsWith("```")) {
                    cleanJson = cleanJson.removePrefix("```")
                }
                if (cleanJson.endsWith("```")) {
                    cleanJson = cleanJson.removeSuffix("```")
                }
                cleanJson = cleanJson.trim()

                val resultObj = JSONObject(cleanJson)
                val genre = resultObj.optString("genre", "Desconocido")
                val mood = resultObj.optString("mood", "Desconocido")
                val lyricsMeaning = resultObj.optString("lyricsMeaning", "Sin significado disponible")
                val funFacts = resultObj.optString("funFacts", "No hay curiosidades cargadas.")
                val recommendedActivity = resultObj.optString("recommendedActivity", "Escuchar música")
                val colorSuggestion = resultObj.optString("colorSuggestion", "#9C27B0")

                MusicAnalysis(
                    trackId = trackId,
                    genre = genre,
                    mood = mood,
                    lyricsMeaning = lyricsMeaning,
                    funFacts = funFacts,
                    recommendedActivity = recommendedActivity,
                    colorSuggestion = colorSuggestion
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing music AI analysis", e)
            null
        }
    }
}

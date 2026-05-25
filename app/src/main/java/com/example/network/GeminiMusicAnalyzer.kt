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

data class ResearchMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val coverUrl: String
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

    suspend fun researchSongMetadata(
        title: String,
        artist: String
    ): ResearchMetadata? = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API Key is blank or placeholder")
            return@withContext null
        }

        val prompt = """
            Eres un catalogador y archivista musical experto con Inteligencia Artificial.
            Investiga la información real y correcta para el archivo o canción con el título "$title" e interpretado por "$artist" (si se indica o deduce).
            Limpia cualquier ruido del nombre de archivo (como .mp3, .m4a, CD-Rip, etc.), faltas de ortografía o nombres de archivo crudos.
            
            Proporciona la información real en un formato JSON puro. No agregues formatos markdown como ```json o ```, ni comentarios iniciales o finales.
            Si no estás seguro del álbum o género real, utiliza tus mejores conocimientos de bases de datos de música para proporcionar uno adecuado y realista.
            Para el campo "coverUrl", selecciona una de las siguientes URL de imágenes gratuitas de alta resolución de Unsplash correspondientes al género de la canción u obra artística musical. Elige una temática acorde (ej. para techno una imagen abstracta oscura, para lofi algo relajado, para acústico una guitarra o bosque, etc.) usando URLs genéricas seguras como:
            - https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&auto=format&fit=crop (Música General Estudio / Micrófono)
            - https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=500&auto=format&fit=crop (Conciertos, Pop, Rock, Luces)
            - https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=500&auto=format&fit=crop (Música Electrónica, Techno, DJ)
            - https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=500&auto=format&fit=crop (Rock, Concierto, Escenario)
            - https://images.unsplash.com/photo-1511379938547-c1f69419868d?w=500&auto=format&fit=crop (Piano Clásico, Acústico, Estudio)
            - https://images.unsplash.com/photo-1507838153414-b4b713384a76?w=500&auto=format&fit=crop (Sinfónica, Violín, Clásica)
            - https://images.unsplash.com/photo-1487180142328-0c4e37023af5?w=500&auto=format&fit=crop (Vinilo, Lofi, Jazz)
            - https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=500&auto=format&fit=crop (Estilo Retro, Synthwave, Luces de Neón)
            O una URL similar de Unsplash que sea válida para mostrar en la carátula.

            Formato del JSON de respuesta en español:
            {
              "title": "Título real u oficial correcto",
              "artist": "Nombre real u oficial correcto del artista principal",
              "album": "Nombre oficial del álbum (o 'Sencillo' si no aplica)",
              "genre": "Género o categoría de música (ej: Pop, Synthwave, Lofi, Rock, Metal, Acoustic, Jazz, Ambient)",
              "coverUrl": "URL de imagen de Unsplash de la lista seleccionada o similar"
            }
        """.trimIndent()

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
                    Log.e(TAG, "Metadata request failed with code: ${response.code}")
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
                val retTitle = resultObj.optString("title", title)
                val retArtist = resultObj.optString("artist", artist)
                val retAlbum = resultObj.optString("album", "Sencillo")
                val retGenre = resultObj.optString("genre", "Ambient")
                val retCover = resultObj.optString("coverUrl", "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&auto=format&fit=crop")

                ResearchMetadata(
                    title = retTitle,
                    artist = retArtist,
                    album = retAlbum,
                    genre = retGenre,
                    coverUrl = retCover
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error researching music data via Gemini", e)
            null
        }
    }
}

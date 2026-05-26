package com.example.network

import android.util.Log
import com.example.data.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder

@Serializable
data class MetadataResult(
    val title: String,
    val artist: String,
    val album: String,
    val category: String,
    val description: String = ""
)

@Serializable
data class AnalysisResult(
    val mood: String,
    val influences: String,
    val summary: String
)

@Serializable
data class SortResult(
    val sortedIds: List<Long>
)

object GeminiMusicAnalyzer {
    private const val TAG = "GeminiMusicAnalyzer"
    private val jsonDecoder = Json { ignoreUnknownKeys = true }

    suspend fun researchSongMetadata(title: String, artist: String): MetadataResult {
        if (!GeminiApiClient.isApiKeyConfigured()) {
            return MetadataResult(title, artist, "Desconocido", "Por defecto", "API Key no configurada.")
        }

        val prompt = "Investiga los metadatos oficiales de la canción titulada '$title' del artista '$artist'. " +
                "Proporciona la información exacta en formato JSON con la siguiente estructura:\n" +
                "{\n" +
                "  \"title\": \"Título oficial de la canción\",\n" +
                "  \"artist\": \"Nombre oficial del artista\",\n" +
                "  \"album\": \"Álbum correspondiente\",\n" +
                "  \"category\": \"Género musical predominante (por ejemplo: Pop, Rock, Reggae, Lo-Fi, Clásica, Jazz)\",\n" +
                "  \"description\": \"Una breve descripción de la canción e historia de su lanzamiento.\"\n" +
                "}\n" +
                "Sé preciso y responde únicamente con el objeto JSON válido."

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                responseFormat = ResponseFormat(type = "application/json"),
                temperature = 0.1f
            )
        )

        return try {
            val response = GeminiApiClient.service.generateContent(GeminiApiClient.getApiKey(), request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            Log.d(TAG, "Metadata Raw JSON: $jsonText")
            jsonDecoder.decodeFromString<MetadataResult>(jsonText)
        } catch (e: Exception) {
            Log.e(TAG, "Error researching music data via Gemini", e)
            MetadataResult(title, artist, "Desconocido", "Por defecto", "Error al consultar: ${e.localizedMessage}")
        }
    }

    suspend fun analyzeSong(title: String, artist: String, lyrics: String?): AnalysisResult {
        if (!GeminiApiClient.isApiKeyConfigured()) {
            return AnalysisResult("N/A", "N/A", "API Key de Gemini no configurada.")
        }

        val lyricsText = if (!lyrics.isNullOrBlank()) "Letra:\n$lyrics" else "Letra: No disponible"
        val prompt = "Analiza musicalmente la canción '$title' de '$artist'.\n" +
                "$lyricsText\n\n" +
                "Devuelve un objeto JSON con las claves:\n" +
                "- \"mood\": El estado de ánimo, vibra o frecuencia de la canción (por ejemplo: Energizante, Melancólico, Calmado, Festivo).\n" +
                "- \"influences\": Influencias musicales u otros artistas/corrientes recomendadas.\n" +
                "- \"summary\": Un análisis interpretativo de la composición o letra (máximo 3 párrafos).\n" +
                "Responde únicamente con el JSON estructurado."

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                responseFormat = ResponseFormat(type = "application/json"),
                temperature = 0.3f
            )
        )

        return try {
            val response = GeminiApiClient.service.generateContent(GeminiApiClient.getApiKey(), request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            Log.d(TAG, "Analysis Raw JSON: $jsonText")
            jsonDecoder.decodeFromString<AnalysisResult>(jsonText)
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing song with Gemini", e)
            AnalysisResult("Desconocida", "Desconocidas", "Error del análisis: ${e.localizedMessage}")
        }
    }

    suspend fun organizeSongsWithAi(songs: List<Track>, instruction: String): List<Long> {
        if (!GeminiApiClient.isApiKeyConfigured() || songs.isEmpty()) {
            return songs.map { it.id }
        }

        val songsListText = songs.joinToString("\n") { "- ID: ${it.id} | Título: ${it.title} | Artista: ${it.artist} | Categoría: ${it.category}" }
        val prompt = "Tienes las siguientes canciones:\n$songsListText\n\n" +
                "Por favor, organízalas u ordénalas de acuerdo con la siguiente instrucción del usuario: '$instruction'.\n" +
                "Devuelve un objeto JSON con la clave \"sortedIds\" conteniendo un array de números (los IDs) ordenados según coincida con la solicitud.\n" +
                "Ejemplo de formato: { \"sortedIds\": [32, 12, 10] }\n" +
                "Asegúrate de incluir exactamente los IDs de las canciones proporcionadas y nada más. Responde únicamente con el JSON."

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                responseFormat = ResponseFormat(type = "application/json"),
                temperature = 0.2f
            )
        )

        return try {
            val response = GeminiApiClient.service.generateContent(GeminiApiClient.getApiKey(), request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            Log.d(TAG, "Sort Raw JSON: $jsonText")
            val result = jsonDecoder.decodeFromString<SortResult>(jsonText)
            result.sortedIds
        } catch (e: Exception) {
            Log.e(TAG, "Error sorting songs with Gemini AI", e)
            songs.map { it.id }
        }
    }
}

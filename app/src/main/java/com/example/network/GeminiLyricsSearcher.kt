package com.example.network

import android.util.Log

object GeminiLyricsSearcher {
    private const val TAG = "GeminiLyricsSearcher"

    suspend fun searchLyricsOnline(title: String, artist: String): String {
        if (!GeminiApiClient.isApiKeyConfigured()) {
            return "[Error: API Key no configurada]"
        }

        val prompt = "Busca la letra oficial de la canción '$title' del artista '$artist'. " +
                "Por favor, devuelve ÚNICAMENTE la letra de la canción de manera limpia, sin comentarios, introducciones o anotaciones adicionales de ningún tipo. " +
                "Si por alguna razón no encuentras la letra real, responde exactamente con '[Error: Letra no encontrada]'."

        val systemInstruction = "Eres un asistente experto en música que devuelve letras de canciones de manera limpia, estructurada estrofa por estrofa, sin comentarios iniciales ni finales."

        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            systemInstruction = Content(parts = listOf(Part(text = systemInstruction))),
            generationConfig = GenerationConfig(temperature = 0.2f)
        )

        return try {
            val response = GeminiApiClient.service.generateContent(GeminiApiClient.getApiKey(), request)
            val result = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            if (result.isNullOrBlank() || result.contains("[Error: Letra no encontrada]")) {
                "No se pudo encontrar la letra de esta canción de forma automática."
            } else {
                result
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching lyrics from Gemini", e)
            "Error al consultar la letra: ${e.localizedMessage}"
        }
    }
}

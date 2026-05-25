package com.example

import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun diagnoseGeminiApiKey() {
    val apiKeyFromBuildConfig = BuildConfig.GEMINI_API_KEY
    val apiKeyFromEnv = System.getenv("GEMINI_API_KEY")
    println("--- Gemini API Key Diagnostics ---")
    println("BuildConfig.GEMINI_API_KEY Is Blank: ${apiKeyFromBuildConfig.isBlank()}")
    println("BuildConfig.GEMINI_API_KEY Length: ${apiKeyFromBuildConfig.length}")
    println("BuildConfig.GEMINI_API_KEY Is Placeholder: ${apiKeyFromBuildConfig == "MY_GEMINI_API_KEY"}")
    
    println("System.getenv(\"GEMINI_API_KEY\") Is Null: ${apiKeyFromEnv == null}")
    if (apiKeyFromEnv != null) {
        println("System.getenv(\"GEMINI_API_KEY\") Is Blank: ${apiKeyFromEnv.isBlank()}")
        println("System.getenv(\"GEMINI_API_KEY\") Length: ${apiKeyFromEnv.length}")
        println("System.getenv(\"GEMINI_API_KEY\") Is Placeholder: ${apiKeyFromEnv == "MY_GEMINI_API_KEY"}")
    }
    
    println("Available Env Var Keys: ${System.getenv().keys.joinToString(", ")}")
    println("----------------------------------")
  }
}

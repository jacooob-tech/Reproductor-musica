// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
}

// Automatically generate .env from .env.example and system environment variables
val envFile = file("${rootDir}/.env")
val exampleFile = file("${rootDir}/.env.example")
if (exampleFile.exists()) {
    val properties = java.util.Properties()
    exampleFile.reader().use { properties.load(it) }
    val sb = StringBuilder()
    properties.stringPropertyNames().forEach { key ->
        val envValue = System.getenv(key) ?: properties.getProperty(key) ?: ""
        sb.append("${key}=${envValue}\n")
    }
    envFile.writeText(sb.toString())
}

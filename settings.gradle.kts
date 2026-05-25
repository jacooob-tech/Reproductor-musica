pluginManagement {
  val envFile = java.io.File(settingsDir, ".env")
  val exampleFile = java.io.File(settingsDir, ".env.example")
  if (exampleFile.exists()) {
      val properties = java.util.Properties()
      exampleFile.reader().use { properties.load(it) }
      val sb = java.lang.StringBuilder()
      properties.stringPropertyNames().forEach { key ->
          val envValue = System.getenv(key) ?: properties.getProperty(key) ?: ""
          sb.append("${key}=${envValue}\n")
      }
      envFile.writeText(sb.toString())
  }

  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
    gradlePluginPortal()
  }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    google()
    mavenCentral()
  }
}

rootProject.name = "My Application"

include(":app")

package com.example.ui.theme

import androidx.compose.ui.graphics.Color

enum class ThemeColorOption(
    val id: String,
    val displayName: String,
    val lightPrimaryHex: String,
    val lightPrimaryContainerHex: String,
    val lightOnPrimaryHex: String,
    val lightOnPrimaryContainerHex: String,
    val darkPrimaryHex: String,
    val darkPrimaryContainerHex: String,
    val darkOnPrimaryHex: String,
    val darkOnPrimaryContainerHex: String
) {
    BLUE(
        "BLUE", "Azul Clásico",
        "#0061A4", "#D1E4FF", "#FFFFFF", "#001D36",
        "#A1C9FF", "#00497E", "#00325A", "#D1E4FF"
    ),
    EMERALD(
        "EMERALD", "Verde Esmeralda",
        "#006B54", "#96F7D2", "#FFFFFF", "#002117",
        "#79DBB5", "#00513E", "#00382A", "#96F7D2"
    ),
    AMBER(
        "AMBER", "Naranja Atardecer",
        "#8B5000", "#FFFFDCBE", "#FFFFFF", "#2C1400",
        "#FFFFB85F", "#6A3B00", "#4A2800", "#FFFFDCBE"
    ),
    ROSE(
        "ROSE", "Rosa Rubí",
        "#9C3B62", "#FFFFD9E2", "#FFFFFF", "#3E0021",
        "#FFFFB1C8", "#7E244A", "#5F0433", "#FFFFD9E2"
    ),
    VIOLET(
        "VIOLET", "Violeta Místico",
        "#734CA9", "#EDDCFF", "#FFFFFF", "#290055",
        "#D7BAFF", "#5A3190", "#421577", "#EDDCFF"
    );

    fun getLightPrimary() = Color(android.graphics.Color.parseColor(lightPrimaryHex))
    fun getLightPrimaryContainer() = Color(android.graphics.Color.parseColor(lightPrimaryContainerHex))
    fun getLightOnPrimary() = Color(android.graphics.Color.parseColor(lightOnPrimaryHex))
    fun getLightOnPrimaryContainer() = Color(android.graphics.Color.parseColor(lightOnPrimaryContainerHex))

    fun getDarkPrimary() = Color(android.graphics.Color.parseColor(darkPrimaryHex))
    fun getDarkPrimaryContainer() = Color(android.graphics.Color.parseColor(darkPrimaryContainerHex))
    fun getDarkOnPrimary() = Color(android.graphics.Color.parseColor(darkOnPrimaryHex))
    fun getDarkOnPrimaryContainer() = Color(android.graphics.Color.parseColor(darkOnPrimaryContainerHex))
}

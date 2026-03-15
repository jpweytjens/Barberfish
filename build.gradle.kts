plugins {
    id("com.diffplug.spotless") version "6.25.0"

    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
}

spotless {
    kotlin {
        target("**/*.kt")
        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()
        ktlint().editorConfigOverride(
            mapOf(
                "max_line_length" to 2147483647,
                "ktlint_standard_value-argument-comment" to "disabled",
                "ktlint_standard_value-parameter-comment" to "disabled",
                "ktlint_standard_comment-wrapping" to "disabled",
                "ktlint_standard_property-naming" to "disabled",
                "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
            ),
        )
    }
}

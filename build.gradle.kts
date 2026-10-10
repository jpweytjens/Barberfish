plugins {
    id("com.diffplug.spotless") version "8.9.0"

    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude(".claude/**")
        trimTrailingWhitespace()
        leadingTabsToSpaces()
        endWithNewline()
        ktfmt("0.64").kotlinlangStyle()
    }
}

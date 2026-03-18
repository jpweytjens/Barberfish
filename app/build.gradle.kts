plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    kotlin("plugin.serialization") version "2.0.20"
}

android {
    namespace = "com.jpweytjens.barberfish"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jpweytjens.barberfish"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.register("generateManifest") {
    description = "Generates manifest.json with current version information"
    group = "build"

    doLast {
        val baseUrl = System.getenv("BASE_URL") ?: "https://github.com/jpweytjens/barberfish/releases/latest/download"
        val manifestFile = file("$projectDir/manifest.json")
        val manifest = mapOf(
            "label" to "Barberfish",
            "packageName" to "com.jpweytjens.barberfish",
            "iconUrl" to "$baseUrl/ic_extension.png",
            "latestApkUrl" to "$baseUrl/app-release.apk",
            "latestVersion" to android.defaultConfig.versionName,
            "latestVersionCode" to android.defaultConfig.versionCode,
            "developer" to "github.com/jpweytjens",
            "description" to "Barberfish keeps Hammerheads sharp, on your handlebars and in the ocean. Native-feeling data field enhancements for the Hammerhead Karoo.",
            "releaseNotes" to "",
            "screenshotUrls" to listOf<String>()
        )

        val gson = groovy.json.JsonBuilder(manifest).toPrettyString()
        manifestFile.writeText(gson)
        println("Generated manifest.json with version ${android.defaultConfig.versionName} (${android.defaultConfig.versionCode})")
    }
}

dependencies {
    implementation(libs.hammerhead.karoo.ext)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.timber)
    testImplementation("junit:junit:4.13.2")
}

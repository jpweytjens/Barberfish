import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.detekt)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.jpweytjens.barberfish"
    compileSdk = 37
    // Barberfish builds no native code of its own; the NDK is here to strip the road matching
    // engine's library (see the release size check below). The release workflows read this
    // line to install the same version.
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.jpweytjens.barberfish"
        minSdk = 26
        targetSdk = 34
        versionCode = 14
        versionName = "4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // The road matching engine is native code. Package it for the two processor types the
        // Karoos run, and not for the emulator types it also ships. Lint asks for x86_64 for
        // ChromeOS, where a Karoo extension never runs.
        //noinspection ChromeOsAbiSupport
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        // Update channel: the Karoo polls this manifest for new versions.
        // Beta builds override it via MANIFEST_URL to point at the betafish repo.
        manifestPlaceholders["manifestUrl"] = System.getenv("MANIFEST_URL")
            ?: "https://github.com/jpweytjens/barberfish/releases/latest/download/manifest.json"
    }

    signingConfigs {
        create("release") {
            val storePath = localProperties["signing.store.file"] as? String
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = localProperties["signing.store.password"] as? String
                keyAlias = localProperties["signing.key.alias"] as? String
                keyPassword = localProperties["signing.key.password"] as? String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // The fake-Karoo suite waits out real-time settle periods, so parallel JVMs cut its wall
        // time. Half the cores, at most four: a starved JVM misses those waits.
        unitTests.all {
            it.maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
        }
    }

    lint {
        // Findings older than the baseline are grandfathered; new warnings fail the build.
        baseline = file("lint-baseline.xml")
        warningsAsErrors = true
        // Field layouts are RemoteViews on a bike computer; there is no screen reader to serve.
        disable += "ContentDescription"
        // Dependency freshness is a maintenance task, not a per-commit check.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
        // targetSdk 34 is a deliberate choice for the Karoo, not a lag to be flagged.
        disable += "OldTargetApi"
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    metricsDestination = layout.buildDirectory.dir("compose_compiler")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    baseline = rootProject.file("config/detekt/baseline.xml")
}

// The road matching engine is over 100 MB per processor type with its debug information and
// under 8 MB without. The Android build strips that information only when the NDK is installed,
// and says nothing when it is not. Fail a release that came out unstripped.
val maxReleaseApkBytes = 20L * 1024 * 1024

tasks.matching { it.name == "packageRelease" }.configureEach {
    val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk")
    doLast {
        val bytes = apk.get().asFile.length()
        if (bytes > maxReleaseApkBytes) {
            throw GradleException(
                "app-release.apk is ${bytes / (1024 * 1024)} MB, over the 20 MB limit. The " +
                    "native libraries were not stripped: the NDK (android.ndkVersion in " +
                    "app/build.gradle.kts) is probably not installed on this machine."
            )
        }
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
            "latestApkUrl" to "$baseUrl/barberfish.apk",
            "latestVersion" to android.defaultConfig.versionName,
            "latestVersionCode" to android.defaultConfig.versionCode,
            "developer" to "github.com/jpweytjens",
            "description" to "Barberfish keeps Hammerheads sharp, on your handlebars and in the ocean. Native-feeling data field enhancements for the Hammerhead Karoo.",
            "releaseNotes" to (System.getenv("RELEASE_NOTES") ?: ""),
            "tags" to listOf("performance")
        )

        val gson = groovy.json.JsonBuilder(manifest).toPrettyString()
        manifestFile.writeText(gson)
        println("Generated manifest.json with version ${android.defaultConfig.versionName} (${android.defaultConfig.versionCode})")
    }
}

dependencies {
    implementation(libs.hammerhead.karoo.ext)
    implementation(libs.androidx.collection)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.timber)
    implementation(libs.valhalla.mobile)
    implementation(libs.valhalla.models)
    implementation(libs.valhalla.models.config)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.karoo.ext.testing.robolectric)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

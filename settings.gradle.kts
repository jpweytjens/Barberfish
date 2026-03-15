import java.util.Properties

val localProperties = Properties().also { props ->
    rootDir.resolve("local.properties").takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
}

pluginManagement {
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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // karoo-ext from GitHub Packages (public but always requires authentication)
        maven {
            url = uri("https://maven.pkg.github.com/hammerheadnav/karoo-ext")
            credentials {
                username = localProperties["gpr.user"] as? String ?: System.getenv("USERNAME")
                password = localProperties["gpr.key"] as? String ?: System.getenv("TOKEN")
            }
        }
    }
}

rootProject.name = "barberfish"
include(":app")

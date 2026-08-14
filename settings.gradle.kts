pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        flatDir {
            dirs("app/libs") // ✅ เพิ่มให้สามารถโหลด .aar จาก app/libs
        }
    }
}

rootProject.name = "DemoAssist"
include(":app")
include(":sensorlib")

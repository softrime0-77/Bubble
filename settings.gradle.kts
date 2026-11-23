pluginManagement {
    repositories {
        google()
        // Maven Central의 구글 미러 (CI 러너가 Maven Central에서 429를 받을 때 대비, 같은 파일)
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Maven Central의 구글 미러 (CI 러너가 Maven Central에서 429를 받을 때 대비, 같은 파일)
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}
rootProject.name = "Bubble"
include(":app")

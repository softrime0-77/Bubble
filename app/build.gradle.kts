import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firebase 설정 파일(app/google-services.json)이 있을 때만 온라인 기능을 켭니다.
// 없으면 앱은 AI 연습 모드만으로 동작합니다.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// 고정 서명 키 (구글 로그인은 Firebase에 등록한 SHA-1과 서명이 같아야 동작함)
// keystore/bubble.jks 는 저장소에 올리지 않고, 비밀번호는 local.properties 또는 환경변수로 받습니다.
val bubbleKeystore = rootProject.file("keystore/bubble.jks")
val bubbleKeystorePassword: String? = System.getenv("BUBBLE_KEYSTORE_PASSWORD")
    ?: rootProject.file("local.properties").takeIf { it.exists() }?.let { f ->
        Properties().apply { f.inputStream().use { load(it) } }.getProperty("bubbleKeystorePassword")
    }

android {
    namespace = "com.bubble.game"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bubble.game"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (bubbleKeystore.exists() && bubbleKeystorePassword != null) {
            create("bubble") {
                storeFile = bubbleKeystore
                storePassword = bubbleKeystorePassword
                keyAlias = "bubble"
                keyPassword = bubbleKeystorePassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("bubble")?.let { signingConfig = it }
        }
        release {
            signingConfigs.findByName("bubble")?.let { signingConfig = it }
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

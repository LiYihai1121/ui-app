import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/** 签名信息从 local.properties（gitignored）读取：adskip.storeFile / storePassword / keyAlias / keyPassword */
val signingProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val releaseSigningReady = listOf(
    "adskip.storeFile", "adskip.storePassword", "adskip.keyAlias", "adskip.keyPassword"
).all { !signingProps.getProperty(it).isNullOrBlank() }

android {
    namespace = "com.ldp.adskip"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ldp.adskip"
        minSdk = 26
        targetSdk = 35
        versionCode = 6
        versionName = "3.0"
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                storeFile = rootProject.file(signingProps.getProperty("adskip.storeFile"))
                storePassword = signingProps.getProperty("adskip.storePassword")
                keyAlias = signingProps.getProperty("adskip.keyAlias")
                keyPassword = signingProps.getProperty("adskip.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    lint {
        // release 构建（lintVital）在 JDK 25 + AGP 8.7 内置 lint 下崩溃（ASM 不识别新 class 文件版本）；
        // CI 仅构建 debug，发布质量由 R8 + 单测保障，故关闭 release 构建的 lint 检查
        checkReleaseBuilds = false
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // Jetpack Compose（BOM 统一版本）
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // MVVM：Activity Compose + ViewModel + 生命周期感知收集
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // 单 Activity 导航
    implementation("androidx.navigation:navigation-compose:2.8.5")

    testImplementation("junit:junit:4.13.2")
}

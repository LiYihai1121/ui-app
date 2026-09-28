import java.util.Properties

plugins {
    id("adskip.android.application")
}

/**
 * 签名信息从 local.properties（gitignored）读取：adskip.storeFile / storePassword / keyAlias / keyPassword。
 * local.properties 属于「本机/CI 秘密」，版本号事实源不包含签名——签名永远留在 :app，不进入约定插件。
 */
val signingProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val releaseSigningReady = listOf(
    "adskip.storeFile", "adskip.storePassword", "adskip.keyAlias", "adskip.keyPassword"
).all { !signingProps.getProperty(it).isNullOrBlank() }

/**
 * 未配置正式签名时的 release 行为：
 * - 默认回退 debug 签名，保证 assembleRelease 产物可直接安装
 *   （未签名 APK 在手机上必然报「解析软件包时出现问题」，见 Issue #23）；
 * - adskip.unsignedRelease=true 时保留未签名产物，仅供受信任环境自行签名。
 */
val unsignedRelease = signingProps.getProperty("adskip.unsignedRelease")?.toBoolean() ?: false

if (!releaseSigningReady) {
    logger.lifecycle(
        if (unsignedRelease) {
            "[adskip] release 未配置签名：按 adskip.unsignedRelease=true 产出未签名 APK（仅供受信任环境自行签名）"
        } else {
            "[adskip] release 未配置签名：已回退 debug 签名以保证 APK 可安装；正式分发请在 local.properties 配置 " +
                "adskip.storeFile / adskip.storePassword / adskip.keyAlias / adskip.keyPassword"
        }
    )
}

android {
    // namespace/compileSdk/minSdk/targetSdk 等公共项由 adskip.android.application 约定插件统一提供
    defaultConfig {
        applicationId = "com.ldp.adskip"
        versionCode = 10
        versionName = "3.1"
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
            signingConfig = when {
                releaseSigningReady -> signingConfigs.getByName("release")
                unsignedRelease -> null // 仅在显式选择时产出未签名包
                else -> signingConfigs.getByName("debug") // 回退：产物必须可安装
            }
        }
    }
}

dependencies {
    // Jetpack Compose（BOM 统一版本）
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // MVVM：Activity Compose + ViewModel + 生命周期感知收集
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // 单 Activity 导航
    implementation(libs.androidx.navigation.compose)

    testImplementation(libs.junit)
}

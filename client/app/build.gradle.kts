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
    "adskip.storeFile",
    "adskip.storePassword",
    "adskip.keyAlias",
    "adskip.keyPassword",
).all { !signingProps.getProperty(it).isNullOrBlank() }

/**
 * 未配置正式签名时的 release 行为：
 * - 默认产出**未签名** APK：debug keystore 是公开已知密钥（口令 "android"），
 *   用它签发的「release」可被任何拿到该密钥的人伪造升级（供应链风险）；
 * - adskip.unsignedRelease=true：同样未签名（显式声明，语义同上）；
 * - adskip.allowDebugSigning=true：显式选择回退 debug 签名，仅供本机试装调试，
 *   **禁止**以该产物对外分发。
 */
val unsignedRelease = signingProps.getProperty("adskip.unsignedRelease")?.toBoolean() ?: false
val allowDebugSigning = signingProps.getProperty("adskip.allowDebugSigning")?.toBoolean() ?: false

if (!releaseSigningReady) {
    logger.lifecycle(
        when {
            unsignedRelease ->
                "[adskip] release 未配置签名：按 adskip.unsignedRelease=true 产出未签名 APK（仅供受信任环境自行签名）"

            allowDebugSigning ->
                "[adskip] release 未配置签名：按 adskip.allowDebugSigning=true 回退 debug 签名，仅供本机试装，禁止分发"

            else ->
                "[adskip] release 未配置签名：产出未签名 APK；本机试装可在 local.properties 设 adskip.allowDebugSigning=true，" +
                    "正式分发请配置 adskip.storeFile / adskip.storePassword / adskip.keyAlias / adskip.keyPassword"
        },
    )
}

android {
    // namespace/compileSdk/minSdk/targetSdk 等公共项由 adskip.android.application 约定插件统一提供
    defaultConfig {
        applicationId = "com.ldp.adskip"
        // 单调递增，禁止复用已发布编号（v3.1.0 = 10，本版必须 > 10）
        versionCode = 12
        // 展示值用 X.Y（与 CONTRIBUTING「版本策略」一致；server 侧用完整 X.Y.Z）
        versionName = "3.2"
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
                "proguard-rules.pro",
            )
            signingConfig = when {
                releaseSigningReady -> signingConfigs.getByName("release")

                // 显式选择才回退 debug 签名（仅供本机试装，禁止分发）
                allowDebugSigning -> signingConfigs.getByName("debug")

                // 默认未签名：debug keystore 公开可得，用它签 release 等于交出升级签名权
                else -> null
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
    // compose 1.9+ 起图标库不再由 material3 传递提供；仍只用 core 图标集（理由见 catalog 注释）
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // MVVM：Activity Compose + ViewModel + 生命周期感知收集
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // 单 Activity 导航
    implementation(libs.androidx.navigation.compose)

    // 安全存储（EncryptedSharedPreferences）
    implementation(libs.androidx.security.crypto)

    // HTTP 客户端（连接池、证书锁定、重试）
    implementation(libs.okhttp)

    // 网络层测试（MockWebServer）
    testImplementation(libs.okhttp.mockwebserver)
    // org.json：单测跑在 JVM，android.jar 只提供桩（returnDefaultValues 下方法全返回默认值），需真实实现
    testImplementation(libs.org.json)

    testImplementation(libs.junit)
}

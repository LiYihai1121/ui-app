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
 * 未配置正式签名时的 release 行为（M2 修复：不再静默回退 debug 签名）：
 *
 * - 历史版本默认回退 debug 签名只为「产物能装」，代价是正式分发路径上可能
 *   静默发出 debug 证书签名的 APK——第三方可生成同签名的升级包覆盖安装。
 * - 现在：请求 release 相关任务时直接失败；`adskip.unsignedRelease=true`
 *   显式选择产出未签名 APK（仅供受信任环境自行签名）；本机调试用 assembleDebug。
 *
 * 为什么不在配置期 error：配置期抛错会连带挡掉 assembleDebug / ktlintCheck /
 * testDebugUnitTest 等全部任务，把「发布路径加固」变成「全员本地不可开发」。
 * 因此只对**点名了 release 的任务**失败；再禁用 release 变体本身，堵死
 * `./gradlew build` 这类聚合任务带着无签名配置静默产出未签名 APK 的入口。
 */
val unsignedRelease = signingProps.getProperty("adskip.unsignedRelease")?.toBoolean() ?: false

val releaseSigningHint =
    "release 未配置正式签名（local.properties 缺少 adskip.storeFile/storePassword/keyAlias/keyPassword）。" +
        "正式分发请配置签名；确需未签名产物请设置 adskip.unsignedRelease=true；本机调试请使用 assembleDebug。"

if (!releaseSigningReady && !unsignedRelease) {
    afterEvaluate {
        val releaseRequested = gradle.startParameter.taskNames
            .any { it.contains("release", ignoreCase = true) }
        if (releaseRequested) {
            throw GradleException(releaseSigningHint)
        }
    }

    androidComponents {
        beforeVariants(selector().withBuildType("release")) { variant ->
            variant.enable = false
        }
    }
}

android {
    // namespace/compileSdk/minSdk/targetSdk 等公共项由 adskip.android.application 约定插件统一提供
    defaultConfig {
        // BREAKING（v3.4.0，PR #74）：applicationId 与源码 namespace 一并迁移到 qingqi。
        // 已装用户需卸载重装，旧 EncryptedSharedPreferences 数据无法跨包名迁移；re-install 分级在 CHANGELOG 3.4.0 说明。
        applicationId = "com.qingqi.adskip"
        // 单调递增，禁止复用已发布编号（v3.1.0 = 10，v3.2.0 = 12，v3.3.0 = 13）
        versionCode = 14
        // 展示值用 X.Y（与 CONTRIBUTING「版本策略」一致；server 侧用完整 X.Y.Z）
        versionName = "3.4"
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
            // M2 后这里的三态：已配置 → release 签名；显式 unsignedRelease → 未签名产物；
            // 其余（未配置且未声明）的 release 变体已在上面被禁用，走到此处属纵深防御——
            // 宁可 unsigned 让打包阶段报错，也绝不再回退 debug 签名混入发布路径。
            signingConfig = when {
                releaseSigningReady -> signingConfigs.getByName("release")
                unsignedRelease -> null
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
    testImplementation(libs.okhttp.tls)
    // org.json：单测跑在 JVM，android.jar 只提供桩（returnDefaultValues 下方法全返回默认值），需真实实现
    testImplementation(libs.org.json)

    testImplementation(libs.junit)
}

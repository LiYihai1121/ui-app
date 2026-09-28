package com.ldp.adskip.gradle

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

/**
 * 约定插件 `adskip.android.application`：Android 应用模块的公共配置一站式收口。
 *
 * 当前项目只有一个 `:app` 模块，本插件把「所有模块必须一致」的设定下沉到一处：
 *   - 插件应用：com.android.application + org.jetbrains.kotlin.plugin.compose
 *     （AGP 9 起 Kotlin 内置，不再单独应用 org.jetbrains.kotlin.android；
 *      但 Compose 编译器插件仍需单独应用，且版本必须与 AGP 内置 Kotlin 一致）
 *   - namespace / compileSdk / minSdk / targetSdk
 *   - Java 17 源/目标兼容性（内置 Kotlin 的 jvmTarget 随 compileOptions 对齐）
 *   - Compose buildFeature、单测返回默认值、release lint 关闭（见下方注释）
 *
 * 设计依据：docs/planning/DESIGN-BUILD-FRAMEWORK.md 阶段 B。
 * 约定边界：模块特有内容（applicationId / versionCode / versionName / 签名 / 依赖）留在 :app。
 */
class AdskipAndroidApplicationPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            // 静态检查同样是跨模块一致的公共约定，与 SDK 版本一起收口在此
            pluginManager.apply("org.jlleitschuh.gradle.ktlint")

            extensions.configure(ApplicationExtension::class.java) {
                namespace = "com.ldp.adskip"
                // compose 1.12（BOM 2026.08.00）强制 compileSdk 37，两者必须同步抬升；
                // targetSdk 刻意留在 35：升 36 会引入 Android 16 行为变更（强制 edge-to-edge 等），
                // 与 UI 改动耦合会让「为什么这次界面变了」难以归因。compileSdk 与 targetSdk 是两件事。
                compileSdk = 37

                defaultConfig {
                    minSdk = 26
                    targetSdk = 35
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }

                buildFeatures {
                    compose = true
                }

                lint {
                    // release 构建（lintVital）曾在 JDK 25 + AGP 8.7 内置 lint 下崩溃（ASM 不识别新 class 文件版本）；
                    // CI 仅构建 debug，发布质量由 R8 + 单测保障，故关闭 release 构建的 lint 检查。
                    // AGP 9 升级后未重新验证 lintVital，先维持关闭，验证通过后再回收
                    //（DESIGN-BUILD-FRAMEWORK.md 阶段 B·坑位 4）。
                    checkReleaseBuilds = false
                }

                testOptions {
                    unitTests {
                        isReturnDefaultValues = true
                    }
                }
            }

            configureKtlint()
        }
    }

    /**
     * ktlint 收口：把「怎么检查」也固定下来，避免各模块自行配置出不同结果。
     *
     * - 规则集与行宽来自仓库根 `.editorconfig`（唯一事实源），此处不重复定义；
     * - `android = true` 必须显式打开，否则插件不认识 Android 源集，会「检查了 0 个文件」
     *   却报通过——这种空门禁比没有门禁更危险；
     * - ktlint 本体版本不在此处锁定：version catalog 的类型安全访问器只在构建脚本里存在，
     *   约定插件的 apply() 阶段拿不到 `libs`。该锁定放在 client/build.gradle.kts 的
     *   subprojects 块里，规则来源仍是同一个 catalog。
     */
    private fun Project.configureKtlint() {
        extensions.configure<KtlintExtension> {
            android.set(true)
            // 显式声明：违规必须让构建失败，不能被「先不阻断」悄悄放过
            ignoreFailures.set(false)
        }
    }
}

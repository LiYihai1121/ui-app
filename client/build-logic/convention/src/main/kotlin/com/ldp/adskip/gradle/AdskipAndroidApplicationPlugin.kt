package com.ldp.adskip.gradle

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project

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

            extensions.configure(ApplicationExtension::class.java) {
                namespace = "com.ldp.adskip"
                // material3 1.4（Expressive）强制要求 compileSdk 36；targetSdk 暂留 35，
                // 避免 Android 16 行为变更（强制 edge-to-edge 等）与本次 UI 升级耦合
                compileSdk = 36

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
        }
    }
}

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

group = "com.qingqi.adskip.buildlogic"

dependencies {
    // 把 AGP / Compose 编译器插件放进约定插件自己的 classpath，
    // 使 AdskipAndroidApplicationPlugin 能以 pluginManager.apply(id) 原样应用。
    // AGP 9 起 Kotlin 内置（不再应用 kotlin.android），但 Compose 编译器插件仍需单独应用，
    // 且其版本必须与 AGP 内置 Kotlin 版本一致。
    // 版本来自共享 version catalog（build-logic/settings.gradle.kts 指向 ../gradle/libs.versions.toml）。
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.compose.compiler.plugin)
    // ktlint-gradle：静态检查同样是「所有模块必须一致」的公共约定，故一并收口
    implementation(libs.ktlint.gradle)
    // ktlint 本体：显式锁定版本，避免默认版本随插件补丁版漂移导致门禁结果不可复现
    implementation(libs.ktlint.cli)
}

gradlePlugin {
    plugins {
        create("adskipAndroidApplication") {
            id = "adskip.android.application"
            implementationClass = "com.qingqi.adskip.gradle.AdskipAndroidApplicationPlugin"
        }
    }
}

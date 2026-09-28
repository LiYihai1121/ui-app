plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

group = "com.ldp.adskip.buildlogic"

dependencies {
    // 把 AGP / Compose 编译器插件放进约定插件自己的 classpath，
    // 使 AdskipAndroidApplicationPlugin 能以 pluginManager.apply(id) 原样应用。
    // AGP 9 起 Kotlin 内置（不再应用 kotlin.android），但 Compose 编译器插件仍需单独应用，
    // 且其版本必须与 AGP 内置 Kotlin 版本一致。
    // 版本来自共享 version catalog（build-logic/settings.gradle.kts 指向 ../gradle/libs.versions.toml）。
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.compose.compiler.plugin)
}

gradlePlugin {
    plugins {
        create("adskipAndroidApplication") {
            id = "adskip.android.application"
            implementationClass = "com.ldp.adskip.gradle.AdskipAndroidApplicationPlugin"
        }
    }
}

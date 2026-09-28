plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

group = "com.ldp.adskip.buildlogic"

dependencies {
    // 把 AGP / KGP / Compose 编译器插件放进约定插件自己的 classpath，
    // 使 AdskipAndroidApplicationPlugin 能以 pluginManager.apply(id) 原样应用它们。
    // 版本一律来自共享 version catalog（build-logic/settings.gradle.kts 指向 ../gradle/libs.versions.toml）。
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
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
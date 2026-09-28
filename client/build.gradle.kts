// 根项目构建脚本：只负责以 apply false 披露插件版本，供子模块通过 version catalog 的 alias 解析。
// 插件/依赖版本号唯一事实源 = gradle/libs.versions.toml；如需新增只见名不加例的插件，在此注册。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// 根项目构建脚本：披露插件版本供子模块解析，并收口跨模块一致的检查配置。
// 插件/依赖版本号唯一事实源 = gradle/libs.versions.toml；如需新增只见名不加例的插件，在此注册。
import org.gradle.kotlin.dsl.configure
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    alias(libs.plugins.android.application) apply false
    // ktlint 以 apply false 披露：约定插件会把它应用到各 Android 模块，
    // 而本脚本需要它的 KtlintExtension 类型来锁定 ktlint 本体版本。
    alias(libs.plugins.ktlint) apply false
}

// ktlint 本体版本锁定在此：插件版本（libs.plugins.ktlint）已固定，但 ktlint 本体版本
// 决定 ktlint_standard_* 规则集的行为，随插件补丁版静默变化会让「同一份代码在不同时刻
// 跑出不同结论」——门禁一旦不可复现，团队就会开始忽略它。
//
// 放在根脚本而不是约定插件：version catalog 的类型安全访问器只在构建脚本里存在，
// 约定插件的 apply() 阶段拿不到 `libs`（它是编译期生成物，不是运行时扩展）。
// 规则的其余部分（行宽、代码风格、import 顺序）一律以仓库根 .editorconfig 为准，此处不重复定义。
subprojects {
    // 必须用 plugins.withId 而非直接 configure：约定插件是在子项目求值阶段才应用 ktlint，
    // 直接 configure 会在扩展还不存在时就执行（报 "Extension of type 'KtlintExtension'
    // does not exist"）。withId 保证插件一落地就配置，时序正确且不依赖求值顺序。
    plugins.withId("org.jlleitschuh.gradle.ktlint") {
        extensions.configure<KtlintExtension> {
            version.set(libs.versions.ktlintCli.get())
        }
    }
}

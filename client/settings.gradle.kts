// 国内镜像仅用于本机开发：CI 上 aliyun 会间歇 502，而 Gradle 只对 404 做仓库间回退
//（5xx 直接判死），冷缓存解析会整体失败——PR #95/#93 的 Structure Contract 红灯根因
//（FOLLOW-UP-PROCESS-AUDIT.md）。CI 判据用 GitHub Actions 注入的 CI 变量。
// 注意：pluginManagement 块由 Gradle 独立编译，看不到脚本主体声明，故各块内自取环境变量。

pluginManagement {
    val useChinaMirrors = System.getenv("CI") == null
    repositories {
        if (useChinaMirrors) {
            // 国内镜像优先（本机网络直连 dl.google.com/mavenCentral 会读超时），官方仓库回退
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    val useChinaMirrors = System.getenv("CI") == null
    repositories {
        if (useChinaMirrors) {
            // 国内镜像优先，官方仓库回退
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "AdSkip-main"

// 约定插件复合构建：把「哪些插件/配置属于所有模块」收口到 build-logic/，避免各模块自行粘贴
// （同目录下 gradle/build-logic 的 docs/planning/DESIGN-BUILD-FRAMEWORK.md 阶段 B 有完整动机）
includeBuild("build-logic")

include(":app")

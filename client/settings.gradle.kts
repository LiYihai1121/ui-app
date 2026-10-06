pluginManagement {
    repositories {
        // 国内镜像优先（本机网络直连 dl.google.com/mavenCentral 会读超时），官方仓库回退
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 国内镜像优先，官方仓库回退
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
    }
}
rootProject.name = "AdSkip-perm-status"

// 约定插件复合构建：把「哪些插件/配置属于所有模块」收口到 build-logic/，避免各模块自行粘贴
// （同目录下 gradle/build-logic 的 docs/planning/DESIGN-BUILD-FRAMEWORK.md 阶段 B 有完整动机）
includeBuild("build-logic")

include(":app")

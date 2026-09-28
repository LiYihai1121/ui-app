import org.gradle.api.initialization.resolve.RepositoriesMode

// build-logic 是「构建的构建」：仓库解析与依赖管理独立于主工程，
// 通过共享 gradle/libs.versions.toml 让约定插件与业务模块看到同一份版本事实源。
pluginManagement {
    repositories {
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
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        // gradle-plugin 镜像：build-logic 的 classpath 上有 Gradle 插件本体（如 ktlint-gradle），
        // 它们发布在插件仓库而非 Maven Central，只配 public 会解析失败；
        // 加上镜像而不是直接指向 plugins.gradle.org，是为了保持「国内镜像优先」策略一致。
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        google()
        mavenCentral()
    }

    // 复用主工程 gradle/libs.versions.toml：约定插件里可直接写 libs.xxx 访问器
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
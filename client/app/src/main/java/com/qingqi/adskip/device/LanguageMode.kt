package com.qingqi.adskip.device

/**
 * 应用内语言选择。
 *
 * 本项目此前没有语言开关。宿主是纯 `ComponentActivity`（无 AppCompat），
 * 因此「固定语言」要自己实现；本文件只负责**表达用户选了什么**，
 * 映射到平台类型与落到运行时的工作交给 [LocaleApplier]。
 *
 * 为什么单独建模而不是直接用字符串：`SYSTEM` 与「具体语言」在语义上不同——
 * 前者要**清除**应用级覆盖、回到系统语言，后者是设一个具体覆盖。
 * 用可空字符串表达会让「跟随系统」与「未设置」混为一谈。
 */
enum class LanguageMode(val tag: String?) {
    /** 跟随系统：不设应用级覆盖。 */
    SYSTEM(null),

    /** 简体中文。 */
    CHINESE("zh-CN"),

    /** 英文。 */
    ENGLISH("en"),
    ;

    companion object {

        /** 默认值：跟随系统。写死具体语言会让第一次安装的用户语言与系统不符。 */
        val DEFAULT = SYSTEM

        /** 从持久化的字符串还原；无法识别时回落到 [DEFAULT]（旧值/损坏值不应崩溃）。 */
        fun fromTag(tag: String?): LanguageMode = entries.firstOrNull { it.tag != null && it.tag == tag } ?: DEFAULT

        /**
         * 供调用方构造平台对象用的语言标签；`null` 表示**跟随系统**。
         *
         * 本文件刻意不引用 `android.os.LocaleList` / `java.util.Locale`：
         * 那会让 `LanguageMode` 在纯 JVM 单测里不可用（`LocaleList` 在 JVM 测试中
         * 是抛异常的 stub）。映射到平台类型由 [LocaleApplier] 负责，
         * 本层只回答「用户选了什么」，因此可被穷举单测。
         */
        fun languageTagOrNull(mode: LanguageMode): String? = mode.tag
    }
}

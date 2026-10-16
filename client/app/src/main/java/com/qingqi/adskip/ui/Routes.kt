package com.qingqi.adskip.ui

/**
 * 导航路由（唯一事实源）。
 *
 * 分两层，边界由 `ProjectStructureTest` 的路由契约守护：
 *
 * **一级（底部导航四页）**——`TopLevelDestinations` 必须与之一一对应，
 * 且**恰好 4 项**：用户在主界面能直达的全部页面。
 *  - [HOME]    首页：服务状态 + 快捷入口 + 今日统计
 *  - [LOGS]    记录：跳过流水（单页深色）
 *  - [RULES]   规则：关键词管理
 *  - [PROFILE] 我的：账号卡 + 设置索引
 *
 * **二级（从一级页进入）**——不出现在底部导航。
 *  - [APPS]       应用管理：由首页「管理应用」瓦片进入
 *  - [ONBOARDING] 引导页：仅在首次启动时作为起始目的地
 *
 * v4 变更：v3 的四个一级页是「首页 / 应用 / 记录 / 我的」，其中「应用」被
 * 「规则」取代并降为二级页。理由见 `AppsScreen` 的类注释（核心配置升一级、
 * 例外配置降一级，贴合用户「先定规则、再挑例外」的实际顺序）。
 */
object Routes {
    // ---------- 一级：底部导航 ----------
    const val HOME = "home"
    const val LOGS = "logs"
    const val RULES = "rules"
    const val PROFILE = "profile"

    // ---------- 二级：二级页面 ----------
    const val APPS = "apps"
    const val ONBOARDING = "onboarding"
}

# AdSkip ProGuard / R8 规则

# 无障碍服务类名不可被混淆/移除（系统通过反射发现服务）
-keep class com.ldp.adskip.service.SkipAdService { *; }
-keep class com.ldp.adskip.sync.SyncJobService { *; }
-keep class com.ldp.adskip.AdskipApp { *; }

# 引擎接口和实现（运行时反射）
-keep class com.ldp.adskip.engine.** { *; }

# 数据模型
-keep class com.ldp.adskip.data.** { *; }

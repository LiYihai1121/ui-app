# AdSkip ProGuard / R8 规则

# 无障碍服务类名不可被混淆/移除（系统通过反射发现服务）
-keep class com.ldp.adskip.service.SkipAdService { *; }
-keep class com.ldp.adskip.sync.SyncJobService { *; }
-keep class com.ldp.adskip.AdskipApp { *; }

# 引擎接口与模型（保守保留；无反射引用，框架适配 FrameworkAdNode 归属 service 层）
-keep class com.ldp.adskip.engine.** { *; }

# 数据模型
-keep class com.ldp.adskip.data.** { *; }

# security-crypto (Tink) 仅编译期引用的注解不在运行时 classpath，R8 压缩时显式忽略缺失类
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

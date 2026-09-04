# 开发环境

本项目采用本机开发方式，不依赖 VS Code Dev Container。Android 客户端和 Bun 服务端可以分别启动，互不要求同时运行。

## 前置条件

- JDK 17 或更高版本，以及 Android SDK（compileSdk 35）。
- Bun 1.1 或更高版本。
- Windows 用户建议使用 PowerShell；macOS/Linux 使用 Bash。
- 使用 VS Code 或 Android Studio 打开项目根目录 `ui-app`。

## 启动

服务端：

```powershell
cd server
bun install
bun test
bun run typecheck
```

Android 客户端：

```powershell
cd client
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

Linux/macOS 将 `.\gradlew.bat` 替换为 `./gradlew`。APK 输出在 `client/app/build/outputs/apk/debug/app-debug.apk`，该目录属于构建产物，不提交到 Git。

## 本地运行

启动服务端：

```powershell
cd server
$env:ADMIN_TOKEN = "your-secret-token"
bun run server.ts
```

服务端默认监听 `http://localhost:3210`。未配置 `ADMIN_TOKEN` 时，公开读取接口仍可用，但管理写接口返回 `503`。端口或数据目录等配置见 `server/src/config.ts`。

## 环境清理

- 可安全删除 `client/build/`、`.gradle/`、`.kotlin/` 等构建缓存；Gradle 会自动重新生成。
- 服务端 `server/data/stats/` 和 `server/data/backups/` 是运行数据，删除前先确认无需保留统计和回滚记录。
- 不要删除 `server/data/rules.json`，它是服务端初始规则数据。

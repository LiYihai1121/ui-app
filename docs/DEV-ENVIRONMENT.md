# 独立开发环境

本项目使用 VS Code Dev Container 隔离 Android 客户端和 Bun 服务端环境。

## 前置条件

- Windows 安装 Docker Desktop，并启用 WSL 2 后端。
- VS Code 安装 Dev Containers 扩展：`ms-vscode-remote.remote-containers`。
- 使用 VS Code 打开项目根目录 `ui-app`。

## 启动

1. 执行 `Dev Containers: Reopen in Container`。
2. 首次启动会构建固定的 Android SDK/JDK/Bun 镜像。
3. 在容器终端中运行：

   ```bash
   cd server
   bun test
   bun run dev
   ```

4. Android 客户端使用：

   ```bash
   cd client
   ./gradlew assembleDebug
   ```

Windows 容器终端也可以运行 `gradlew.bat assembleDebug`。

## 隔离边界

- Gradle 缓存使用 Docker volume `ui-app-gradle-cache`。
- Bun 缓存使用 Docker volume `ui-app-bun-cache`。
- 容器内设置的 `ANDROID_HOME`、`GRADLE_USER_HOME`、`BUN_INSTALL` 不写入系统环境变量。
- 服务端仅转发容器端口 `3210`，不会占用其他项目的依赖环境。
- 项目数据目录仍挂载在工作区中，便于本地查看和保留；不要把真实密钥提交到仓库。

## 退出和清理

关闭 VS Code 窗口即可停止容器。需要彻底删除本项目环境时，在 Docker Desktop 中删除容器及以下两个项目专属 volume：

- `ui-app-gradle-cache`
- `ui-app-bun-cache`

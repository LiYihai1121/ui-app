# AdSkip 服务端镜像（Bun，零运行时依赖，无构建步骤）
#
# 构建：docker build -t adskip-server ./server
# 运行：docker run -d -p 3210:3210 -e ADMIN_TOKEN=<token> -v adskip-data:/app/data adskip-server
# 一键：cd server && docker compose up -d
#
# 密钥（ADMIN_TOKEN / ADMIN_TOTP_SECRET / RULES_SIGNING_KEY）一律经环境变量
# 注入，不入库；数据（规则/统计/备份）经卷持久化。
FROM oven/bun:1-alpine

WORKDIR /app

# 依赖先行（利用层缓存）：零运行时依赖，实际只装 @types/bun
COPY package.json bun.lock ./
RUN bun install --frozen-lockfile

COPY . .

# 运行时数据目录挂载卷，容器重建不丢
VOLUME ["/app/data"]

EXPOSE 3210

ENTRYPOINT ["bun", "run", "server.ts"]

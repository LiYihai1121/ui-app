# server_python — 规则审核服务原型（脚手架）

> **状态：脚手架 / 原型，非生产实现。** 正式服务端是 `server/`（Bun + TypeScript，零运行时依赖），
> 架构与协议见 [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md) 与 [docs/API.md](../docs/API.md)。
> 本目录对应路线图中「规则审核服务」的探索性原型（见 [docs/ROADMAP-ADS.md](../docs/ROADMAP-ADS.md)）。

## 定位

- 承载 Python 生态下的规则模型校验实验：`src/models/` 是 `server/src/types/rules.ts` 的 pydantic 镜像；
- 为规则订阅的审核 / 校验流程提供可演进的最小骨架（配置 + 域模型）；
- **不进入 CI 与发布链路**；HTTP 路由、存储与统计尚未实现，暂无入口文件。

## 结构

```text
server_python/
├── pyproject.toml          # 项目元数据 + setuptools 打包 + pytest 配置
├── README.md               # 本文件（定位与状态说明）
└── src/                    # 包名 src（import 用 from src.config import config）
    ├── __init__.py
    ├── config.py           # 与 server/src/config.ts 对齐的集中配置（env 覆盖）
    └── models/__init__.py  # pydantic 域模型（规则包 / 统计 / 协议响应）
```

## 使用

```powershell
cd server_python
py -3 -m venv .venv                  # .venv 不入库（见根 .gitignore）
.venv\Scripts\pip install -e ".[dev]"
py -3 -m compileall -q src           # 语法校验
# 测试目录 tests/ 待补（pytest 已在 pyproject 配置 testpaths）
```

要求 Python 3.11+。虚拟环境、`__pycache__/` 与 `.pytest_cache/` 均已忽略，不进入版本库。

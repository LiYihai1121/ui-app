import os
from pathlib import Path
from typing import List, Optional

ROOT = Path(__file__).parent.parent

class Config:
    PORT: int = int(os.getenv("PORT", "3210"))
    HOST: str = os.getenv("HOST", "0.0.0.0")
    ROOT: Path = ROOT
    DATA_DIR: Path = ROOT / "data"
    RULES_FILE: Path = ROOT / "data" / "rules.json"
    STATS_DIR: Path = ROOT / "data" / "stats"
    PUBLIC_DIR: Path = ROOT / "public"
    APK_FILE: Path = ROOT.parent / "AdSkip-latest.apk"
    MAX_BODY: int = 1024 * 1024
    RECENT_CAP: int = 500
    STATS_RETENTION_DAYS: int = 90
    STATS_DIR_CLEANUP_ON_START: bool = True
    BACKUP_DIR: Path = ROOT / "data" / "backups"
    BACKUP_COUNT: int = 5
    ADMIN_TOKEN: str = os.getenv("ADMIN_TOKEN", "")
    STATS_READ_AUTH: bool = os.getenv("STATS_READ_AUTH", "false").lower() == "true"
    CORS_ORIGINS: Optional[List[str]] = (
        [s.strip() for s in os.getenv("CORS_ORIGINS", "").split(",") if s.strip()]
        if os.getenv("CORS_ORIGINS")
        else None
    )
    RATE_LIMIT_READ_PER_MIN: int = int(os.getenv("RATE_LIMIT_READ_PER_MIN", "120"))
    RATE_LIMIT_REPORT_PER_MIN: int = int(os.getenv("RATE_LIMIT_REPORT_PER_MIN", "30"))
    RATE_LIMIT_WRITE_PER_MIN: int = int(os.getenv("RATE_LIMIT_WRITE_PER_MIN", "10"))
    SCHEMA_VERSION: int = 1
    SCHEMA_VERSION_MIN: int = 1
    MAX_KEYWORD_LEN: int = 12
    MAX_VIEWID_LEN: int = 256
    MAX_VIEWID_RULE_LEN: int = 256
    MAX_RULES_PER_APP: int = 512
    MAX_APPS: int = 2000
    MAX_BATCH_EVENTS: int = 50
    MAX_BODY_KEYS: int = 100
    MAX_BODY_DEPTH: int = 5

    @classmethod
    def ensure_dirs(cls):
        cls.DATA_DIR.mkdir(parents=True, exist_ok=True)
        cls.STATS_DIR.mkdir(parents=True, exist_ok=True)
        cls.BACKUP_DIR.mkdir(parents=True, exist_ok=True)
        cls.PUBLIC_DIR.mkdir(parents=True, exist_ok=True)


config = Config()
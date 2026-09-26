from typing import Dict, List, Optional, Any
from pydantic import BaseModel, Field
from datetime import datetime


class AppRule(BaseModel):
    keywords: List[str] = Field(default_factory=list)
    viewIds: List[str] = Field(default_factory=list)
    disabled: bool = False


class RuleSetV1(BaseModel):
    globalKeywords: List[str] = Field(default_factory=list)
    globalViewIds: List[str] = Field(default_factory=list)
    apps: Dict[str, AppRule] = Field(default_factory=dict)
    disabled: List[str] = Field(default_factory=list)


class RulesPackage(BaseModel):
    schemaVersion: int = 1
    version: int = 1
    updatedAt: str
    hash: str = ""
    rules: RuleSetV1
    keywords: List[str] = Field(default_factory=list)
    viewIds: List[str] = Field(default_factory=list)
    packages: Dict[str, AppRule] = Field(default_factory=dict)


class CleanedRules(BaseModel):
    keywords: List[str] = Field(default_factory=list)
    viewIds: List[str] = Field(default_factory=list)
    packages: Dict[str, AppRule] = Field(default_factory=dict)


class SkipEvent(BaseModel):
    ts: str
    pkg: str
    label: str
    channel: str


class AppStat(BaseModel):
    label: str
    count: int
    byChannel: Dict[str, int] = Field(default_factory=dict)


class StatsDay(BaseModel):
    day: str
    byApp: Dict[str, AppStat] = Field(default_factory=dict)
    events: List[SkipEvent] = Field(default_factory=list)


class StatsSummary(BaseModel):
    total: int
    today: int
    byDay: List[Dict[str, Any]]
    byApp: List[Dict[str, Any]]
    recent: List[SkipEvent]


class BatchReportRequest(BaseModel):
    deviceId: str
    events: List[Dict[str, Any]]


class TestRuleRequest(BaseModel):
    pkg: str = ""
    text: str = ""
    viewId: str = ""


class TestRuleResponse(BaseModel):
    hits: List[Dict[str, Any]]
    hit: bool
    disabled: bool


class V0LatestResponse(BaseModel):
    version: int
    updatedAt: str
    keywords: List[str]
    viewIds: List[str]
    packages: Dict[str, AppRule]


class V1LatestResponse(BaseModel):
    schemaVersion: int
    version: int
    hash: str
    updatedAt: str
    rules: RuleSetV1


class PublishResponse(BaseModel):
    ok: bool
    version: int
    hash: Optional[str] = None


class BatchReportResponse(BaseModel):
    ok: bool
    accepted: int


class HealthResponse(BaseModel):
    status: str
    timestamp: str


class LogEntry(BaseModel):
    method: str
    path: str
    status: int
    ip: str
    ms: int


class AccessLogResponse(BaseModel):
    entries: List[LogEntry]
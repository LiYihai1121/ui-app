import { jsonResponse } from "../httpUtil";
import type { Handler } from "../types";

export const health: Handler = () =>
  jsonResponse({ status: "ok", timestamp: new Date().toISOString() });

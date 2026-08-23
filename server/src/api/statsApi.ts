import { statsSummary } from "../store";
import { limitRead } from "../rateLimit";
import { jsonResponse, errorJson } from "../httpUtil";
import type { Handler } from "../types";

export const summary: Handler = (req, _url, ctx) => {
  if (!limitRead(req, ctx.ip)) return errorJson(429, "rate limited");
  return jsonResponse(statsSummary());
};

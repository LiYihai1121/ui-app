import { statsSummary } from "../storage/store";
import { limitRead } from "../middleware/rateLimit";
import { jsonResponse, errorJson } from "../utils/httpUtil";
import type { Handler } from "../types";

export const summary: Handler = (req, _url, ctx) => {
  if (!limitRead(req, ctx.ip)) return errorJson(429, "rate limited");
  return jsonResponse(statsSummary());
};

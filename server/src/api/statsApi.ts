import { statsSummary } from "../storage/store";
import { limitRead } from "../middleware/rateLimit";
import { jsonResponse, errorJson, type Handler } from "../utils/httpUtil";

export const summary: Handler = (req, _url, ctx) => {
  if (!limitRead(req, ctx.ip)) return errorJson(429, "rate limited");
  return jsonResponse(statsSummary());
};

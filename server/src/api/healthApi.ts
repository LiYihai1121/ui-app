import { limitRead } from "../middleware/rateLimit";
import { jsonResponse, errorJson, type Handler } from "../utils/httpUtil";

export const health: Handler = (req, _url, ctx) => {
  if (!limitRead(req, ctx.ip)) return errorJson(429, "rate limited");
  return jsonResponse({ status: "ok", timestamp: new Date().toISOString() });
};

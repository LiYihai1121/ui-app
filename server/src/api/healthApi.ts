import { jsonResponse, type Handler } from "../utils/httpUtil";

export const health: Handler = () =>
  jsonResponse({ status: "ok", timestamp: new Date().toISOString() });

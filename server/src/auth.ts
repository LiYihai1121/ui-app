import { config } from "./config";

export function checkAdminAuth(req: Request): boolean {
  const token = config.ADMIN_TOKEN;
  if (!token) return false;
  const header = req.headers.get("authorization") ?? "";
  if (!header.toLowerCase().startsWith("bearer ")) return false;
  return header.slice(7).trim() === token;
}

export function requireAdmin(
  req: Request
): { ok: true } | { ok: false; status: number; error: string } {
  if (!config.ADMIN_TOKEN) {
    return { ok: false, status: 503, error: "ADMIN_TOKEN not configured" };
  }
  const header = req.headers.get("authorization") ?? "";
  if (!header.toLowerCase().startsWith("bearer ")) {
    return { ok: false, status: 401, error: "unauthorized" };
  }
  if (header.slice(7).trim() !== config.ADMIN_TOKEN) {
    return { ok: false, status: 401, error: "unauthorized" };
  }
  return { ok: true };
}

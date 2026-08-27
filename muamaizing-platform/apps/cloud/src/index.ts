import { serve } from "@hono/node-server";
import { Hono } from "hono";
import {
  ApiPaths,
  type HostHeartbeatRequest,
  type HostHeartbeatResponse,
  type HostRecord,
  type HostsListResponse,
} from "@muamaizing/protocol";
import { HostStore } from "./store.js";

const PORT = Number(process.env.PORT ?? 8787);
const HOST_TOKEN = process.env.MU_HOST_TOKEN ?? "dev-host-token";

const app = new Hono();
const store = new HostStore();

app.get(ApiPaths.health, (c) =>
  c.json({ ok: true, service: "muamaizing-cloud", part: 1 }),
);

app.post(ApiPaths.hostsHeartbeat, async (c) => {
  const auth = c.req.header("authorization") ?? "";
  const token = auth.startsWith("Bearer ") ? auth.slice(7) : "";
  if (token !== HOST_TOKEN) {
    return c.json({ ok: false, error: "UNAUTHORIZED" }, 401);
  }

  let body: HostHeartbeatRequest;
  try {
    body = await c.req.json<HostHeartbeatRequest>();
  } catch {
    return c.json({ ok: false, error: "INVALID_JSON" }, 400);
  }

  if (!body.hostId || !body.displayName) {
    return c.json({ ok: false, error: "MISSING_FIELDS" }, 400);
  }

  const record = store.upsertFromHeartbeat(body);
  const response: HostHeartbeatResponse = {
    ok: true,
    serverTime: new Date().toISOString(),
    commands: [], // Parte 2: start/restart queue
  };

  console.log(
    `[heartbeat] host=${record.hostId} instances=${record.instances.length} os=${record.os}`,
  );
  return c.json(response);
});

app.get(ApiPaths.hostsList, (c) => {
  // Parte 1: open list for local dev. Parte 2+: admin auth.
  const payload: HostsListResponse = { hosts: store.list() };
  return c.json(payload);
});

app.get("/api/v1/hosts/:hostId", (c) => {
  const host = store.get(c.req.param("hostId"));
  if (!host) return c.json({ error: "NOT_FOUND" }, 404);
  return c.json(host satisfies HostRecord);
});

console.log(`[cloud] listening on http://127.0.0.1:${PORT}`);
console.log(`[cloud] host token = ${HOST_TOKEN === "dev-host-token" ? "dev-host-token (default)" : "(custom)"}`);

serve({ fetch: app.fetch, port: PORT, hostname: "127.0.0.1" });

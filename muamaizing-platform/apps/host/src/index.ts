import { hostname as osHostname, platform as osPlatform } from "node:os";
import { randomUUID } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import {
  ApiPaths,
  type HostHeartbeatRequest,
  type HostHeartbeatResponse,
  type HostOs,
  type ReportedInstance,
} from "@muamaizing/protocol";
import { listAdbDevices } from "./adb.js";

const __dirname = dirname(fileURLToPath(import.meta.url));
const DATA_DIR = join(__dirname, "..", "data");
const STATE_FILE = join(DATA_DIR, "host-state.json");

const CLOUD_URL = (process.env.MU_CLOUD_URL ?? "http://127.0.0.1:8787").replace(/\/$/, "");
const HOST_TOKEN = process.env.MU_HOST_TOKEN ?? "dev-host-token";
const INTERVAL_MS = Number(process.env.MU_HEARTBEAT_MS ?? 10_000);
const AGENT_VERSION = "0.1.0-part1";

function detectOs(): HostOs {
  switch (osPlatform()) {
    case "darwin":
      return "macos";
    case "win32":
      return "windows";
    case "linux":
      return "linux";
    default:
      return "unknown";
  }
}

function loadOrCreateHostId(): string {
  mkdirSync(DATA_DIR, { recursive: true });
  if (existsSync(STATE_FILE)) {
    try {
      const parsed = JSON.parse(readFileSync(STATE_FILE, "utf8")) as { hostId?: string };
      if (parsed.hostId) return parsed.hostId;
    } catch {
      // recreate below
    }
  }
  const hostId = randomUUID();
  writeFileSync(STATE_FILE, JSON.stringify({ hostId }, null, 2));
  return hostId;
}

async function sendHeartbeat(body: HostHeartbeatRequest): Promise<HostHeartbeatResponse> {
  const res = await fetch(`${CLOUD_URL}${ApiPaths.hostsHeartbeat}`, {
    method: "POST",
    headers: {
      "content-type": "application/json",
      authorization: `Bearer ${HOST_TOKEN}`,
    },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`heartbeat HTTP ${res.status}: ${text}`);
  }
  return (await res.json()) as HostHeartbeatResponse;
}

async function tick(hostId: string): Promise<void> {
  const devices = await listAdbDevices();
  const instances: ReportedInstance[] = devices.map((d) => ({
    localId: d.serial,
    displayName: d.serial,
    adbSerial: d.serial,
    state: d.state === "device" ? "online" : "unknown",
  }));

  const body: HostHeartbeatRequest = {
    hostId,
    displayName: process.env.MU_HOST_NAME ?? osHostname(),
    os: detectOs(),
    agentVersion: AGENT_VERSION,
    instances,
  };

  const resp = await sendHeartbeat(body);
  console.log(
    `[host] heartbeat ok instances=${instances.length} commands=${resp.commands.length} server=${resp.serverTime}`,
  );
  // Parte 2: execute resp.commands
}

async function main(): Promise<void> {
  const hostId = loadOrCreateHostId();
  console.log(`[host] id=${hostId}`);
  console.log(`[host] cloud=${CLOUD_URL}`);
  console.log(`[host] interval=${INTERVAL_MS}ms`);

  for (;;) {
    try {
      await tick(hostId);
    } catch (err) {
      console.error(`[host] heartbeat failed:`, err instanceof Error ? err.message : err);
    }
    await new Promise((r) => setTimeout(r, INTERVAL_MS));
  }
}

main();

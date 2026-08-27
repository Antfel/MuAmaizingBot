/** Shared API paths for cloud ↔ host ↔ remote. */
export const ApiPaths = {
  health: "/health",
  hostsHeartbeat: "/api/v1/hosts/heartbeat",
  hostsList: "/api/v1/hosts",
  // Parte 2+
  slotsMine: "/api/v1/rental/my-slots",
  slotStart: "/api/v1/rental/slots/:id/start",
  slotRestart: "/api/v1/rental/slots/:id/restart",
} as const;

export type HostOs = "macos" | "windows" | "linux" | "unknown";

export type InstanceState = "online" | "offline" | "starting" | "stopping" | "unknown";

export interface ReportedInstance {
  /** Stable id on this host (BlueStacks instance name or adb serial fallback). */
  localId: string;
  displayName: string;
  adbSerial: string | null;
  state: InstanceState;
}

export interface HostHeartbeatRequest {
  hostId: string;
  displayName: string;
  os: HostOs;
  agentVersion: string;
  instances: ReportedInstance[];
}

export interface HostHeartbeatResponse {
  ok: true;
  serverTime: string;
  /** Commands for the host to execute (Parte 2). */
  commands: HostCommand[];
}

export type HostCommand =
  | { type: "noop" }
  | { type: "start_instance"; localId: string; commandId: string }
  | { type: "restart_instance"; localId: string; commandId: string }
  | { type: "stop_instance"; localId: string; commandId: string };

export interface HostRecord {
  hostId: string;
  displayName: string;
  os: HostOs;
  agentVersion: string;
  lastHeartbeatAt: string;
  instances: ReportedInstance[];
}

export interface HostsListResponse {
  hosts: HostRecord[];
}

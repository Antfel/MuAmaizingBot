import type { HostHeartbeatRequest, HostRecord } from "@muamaizing/protocol";

/** In-memory host registry (Parte 1). Replace with DB in Parte 2+. */
export class HostStore {
  private readonly byId = new Map<string, HostRecord>();

  upsertFromHeartbeat(req: HostHeartbeatRequest): HostRecord {
    const record: HostRecord = {
      hostId: req.hostId,
      displayName: req.displayName,
      os: req.os,
      agentVersion: req.agentVersion,
      lastHeartbeatAt: new Date().toISOString(),
      instances: req.instances ?? [],
    };
    this.byId.set(record.hostId, record);
    return record;
  }

  list(): HostRecord[] {
    return [...this.byId.values()].sort((a, b) =>
      a.displayName.localeCompare(b.displayName),
    );
  }

  get(hostId: string): HostRecord | undefined {
    return this.byId.get(hostId);
  }
}

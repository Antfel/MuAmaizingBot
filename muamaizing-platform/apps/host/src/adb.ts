import { execFile } from "node:child_process";
import { promisify } from "node:util";

const execFileAsync = promisify(execFile);

export interface AdbDevice {
  serial: string;
  state: string;
}

/**
 * Parse `adb devices` output. Agnostic to Mac/Windows/Linux as long as adb is on PATH.
 */
export async function listAdbDevices(): Promise<AdbDevice[]> {
  try {
    const { stdout } = await execFileAsync("adb", ["devices"], {
      timeout: 8_000,
      maxBuffer: 1024 * 1024,
    });
    return parseAdbDevices(stdout);
  } catch (err) {
    const message = err instanceof Error ? err.message : String(err);
    console.warn(`[adb] unavailable: ${message}`);
    return [];
  }
}

export function parseAdbDevices(stdout: string): AdbDevice[] {
  const lines = stdout.split(/\r?\n/).map((l) => l.trim()).filter(Boolean);
  const out: AdbDevice[] = [];
  for (const line of lines) {
    if (line.startsWith("List of devices")) continue;
    const [serial, state] = line.split(/\s+/);
    if (!serial || !state) continue;
    out.push({ serial, state });
  }
  return out;
}

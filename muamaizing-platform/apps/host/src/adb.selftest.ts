import { parseAdbDevices } from "./adb.js";

const sample = `
List of devices attached
emulator-5554	device
emulator-5584	device
127.0.0.1:5555	offline
`.trim();

const devices = parseAdbDevices(sample);
if (devices.length !== 3) {
  console.error("expected 3 devices, got", devices);
  process.exit(1);
}
if (devices[0]?.serial !== "emulator-5554") {
  console.error("bad first serial", devices[0]);
  process.exit(1);
}
console.log("adb parse ok", devices);

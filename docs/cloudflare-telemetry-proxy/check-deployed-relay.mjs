import { pathToFileURL } from "node:url";
import { CONTRACT_VERSION } from "./worker.js";

const DEFAULT_RELAY_URL = "https://skill-bill-telemetry-proxy.skillbill.workers.dev";

export function relayDriftError(capabilities) {
  const deployed = capabilities?.contract_version;
  if (deployed === CONTRACT_VERSION) {
    return null;
  }
  return `Deployed relay reports contract_version ${JSON.stringify(deployed)}; this checkout is ${JSON.stringify(CONTRACT_VERSION)}. Redeploy docs/cloudflare-telemetry-proxy before releasing.`;
}

async function main() {
  const relayUrl = (process.argv[2] || DEFAULT_RELAY_URL).replace(/\/+$/, "");
  const response = await fetch(`${relayUrl}/capabilities`);
  if (!response.ok) {
    console.error(`GET ${relayUrl}/capabilities returned HTTP ${response.status}.`);
    process.exit(1);
  }
  const error = relayDriftError(await response.json());
  if (error) {
    console.error(error);
    process.exit(1);
  }
  console.log(`Deployed relay matches contract_version ${CONTRACT_VERSION}.`);
}

if (import.meta.url === pathToFileURL(process.argv[1] ?? "").href) {
  await main();
}

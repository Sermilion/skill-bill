package skillbill.mcp.system

import skillbill.contracts.system.UpdateCheckContract
import skillbill.mcp.shared.McpComponent

internal fun doctor(component: McpComponent): Map<String, Any?> = component.systemService.doctor().toPayload()

internal fun updateCheck(component: McpComponent): Map<String, Any?> {
  val result = component.updateCheckService.check(includePrereleases = false)
  return UpdateCheckContract(
    status = result.status.wireName,
    installedVersion = result.installedVersion,
    latestVersion = result.latestVersion,
    releaseUrl = result.releaseUrl,
    recommendedInstallCommand = result.recommendedInstallCommand,
    reason = result.reason,
    releaseNotes = result.releaseNotes,
  ).toPayload()
}

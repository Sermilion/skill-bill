package skillbill.install.model

data class SharedInstallSelection(
  val selectedAgents: Set<SupportedAgent>,
  val platformPackSelection: PlatformPackSelection,
  val telemetryLevel: InstallTelemetryLevel,
  val mcpRegistrationChoice: McpRegistrationChoice,
)

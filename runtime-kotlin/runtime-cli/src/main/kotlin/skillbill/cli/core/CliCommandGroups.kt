package skillbill.cli.core

import com.github.ajalt.clikt.core.CliktCommand
import me.tatarka.inject.annotations.Inject
import skillbill.cli.experiment.ExperimentsCommand
import skillbill.cli.install.core.InstallTopLevelCommands
import skillbill.cli.learning.LearningsCommand
import skillbill.cli.review.ReviewTopLevelCommands
import skillbill.cli.scaffold.commands.ScaffoldTopLevelCommands
import skillbill.cli.telemetry.TelemetryCommand

@Inject
class ReviewCliCommandGroup(
  reviewCommands: ReviewTopLevelCommands,
  learningsCommand: LearningsCommand,
  telemetryCommand: TelemetryCommand,
) {
  val commands: List<CliktCommand> =
    reviewCommands.commands +
      listOf(
        learningsCommand,
        telemetryCommand,
      )
}

@Inject
class ScaffoldCliCommandGroup(
  scaffoldCommands: ScaffoldTopLevelCommands,
  installCommands: InstallTopLevelCommands,
) {
  val commands: List<CliktCommand> = scaffoldCommands.commands + installCommands.command
}

@Inject
class UtilityCliCommandGroup(
  workflowGoalFeature: WorkflowGoalFeatureCliCommands,
  systemMaintenance: SystemMaintenanceCliCommands,
  misc: MiscCliCommands,
  experimentsCommand: ExperimentsCommand,
) {
  val commands: List<CliktCommand> =
    workflowGoalFeature.workflowCommands.commands +
      workflowGoalFeature.repoValidationCommands.commands +
      listOf(
        workflowGoalFeature.goalRunCommand,
        workflowGoalFeature.featureTaskRunCommand,
        workflowGoalFeature.featureTaskRuntimeDeprecatedRunCommand,
        systemMaintenance.versionCommand,
        systemMaintenance.updateCommand,
        systemMaintenance.updateCheckCommand,
        systemMaintenance.uninstallCommand,
        systemMaintenance.doctorCommand,
        systemMaintenance.removeCommand,
        misc.codeReviewCommand,
        misc.configCommand,
        misc.workCommands.command,
        misc.agentAddonCommand,
        experimentsCommand,
      )
}

@Inject
class TopLevelCliCommands(
  reviewCommands: ReviewCliCommandGroup,
  scaffoldCommands: ScaffoldCliCommandGroup,
  utilityCommands: UtilityCliCommandGroup,
) {
  val rootCommands: List<CliktCommand> =
    reviewCommands.commands + scaffoldCommands.commands + utilityCommands.commands
}

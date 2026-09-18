import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.getByType

val infraFsAreaLayerOrder =
  listOf(
    "Jvm",
    "Contracts",
    "AgentAddon",
    "NativeAgent",
    "Scaffold",
    "Install",
    "Launcher",
    "Infrastructure",
    "GoalPlanning",
    "SkillRemove",
  )

val infraFsAreaSourceDirs =
  mapOf(
    "Jvm" to listOf("skillbill/infrastructure/fs/jvm"),
    "Infrastructure" to listOf("skillbill/infrastructure/fs"),
    "Install" to listOf("skillbill/infrastructure/fs/install"),
    "Launcher" to listOf("skillbill/infrastructure/fs/launcher"),
    "NativeAgent" to listOf("skillbill/infrastructure/fs/nativeagent"),
    "Scaffold" to listOf("skillbill/infrastructure/fs/scaffold"),
    "AgentAddon" to listOf("skillbill/infrastructure/fs/agentaddon"),
    "Contracts" to
      listOf(
        "skillbill/infrastructure/fs/contracts",
        "skillbill/infrastructure/fs/phaseoutput",
      ),
    "GoalPlanning" to listOf("skillbill/infrastructure/fs/goalplanning"),
    "SkillRemove" to listOf("skillbill/infrastructure/fs/skillremove"),
  )

val javaPlugin = extensions.getByType(JavaPluginExtension::class.java)
val mainSourceSet = javaPlugin.sourceSets.getByName("main")
val infraFsAreaSourceSets =
  infraFsAreaLayerOrder.associateWith { areaName ->
    val sourceSetName = "infraFs${areaName}Area"
    val areaSourceSet = javaPlugin.sourceSets.create(sourceSetName)
    infraFsAreaSourceDirs.getValue(areaName).forEach { sourceDir ->
      areaSourceSet.java.srcDir(layout.projectDirectory.dir("src/main/kotlin/$sourceDir"))
    }
    configurations.getByName(areaSourceSet.implementationConfigurationName).extendsFrom(
      configurations.getByName(mainSourceSet.implementationConfigurationName),
    )
    configurations.getByName(areaSourceSet.compileOnlyConfigurationName).extendsFrom(
      configurations.getByName(mainSourceSet.compileOnlyConfigurationName),
    )
    areaSourceSet
  }

infraFsAreaLayerOrder.forEachIndexed { areaIndex, areaName ->
  val areaSourceSet = infraFsAreaSourceSets.getValue(areaName)
  infraFsAreaLayerOrder.take(areaIndex).forEach { lowerAreaName ->
    val lowerSourceSet = infraFsAreaSourceSets.getValue(lowerAreaName)
    dependencies.add(areaSourceSet.implementationConfigurationName, lowerSourceSet.output)
  }
  val compileTaskName = "compileInfraFs${areaName}AreaKotlin"
  tasks.named(compileTaskName) {
    val friendPaths =
      javaClass.getMethod("getFriendPaths").invoke(this) as ConfigurableFileCollection
    infraFsAreaLayerOrder.take(areaIndex).forEach { lowerAreaName ->
      val lowerSourceSet = infraFsAreaSourceSets.getValue(lowerAreaName)
      friendPaths.from(lowerSourceSet.output.classesDirs)
    }
  }
}

val verifyInfraFsAreaCompileTasks =
  infraFsAreaLayerOrder.mapIndexed { areaIndex, areaName ->
    val compileTaskName = "compileInfraFs${areaName}AreaKotlin"
    tasks.named(compileTaskName) {
      infraFsAreaLayerOrder.take(areaIndex).forEach { lowerAreaName ->
        dependsOn("compileInfraFs${lowerAreaName}AreaKotlin")
      }
      dependsOn("processResources")
    }
  }

tasks.register("verifyInfraFsAreaCompile") {
  group = "verification"
  description = "Compile each runtime-infra/fs area without sibling areas on the classpath."
  verifyInfraFsAreaCompileTasks.forEach { dependsOn(it) }
}

tasks.named("check") {
  dependsOn("verifyInfraFsAreaCompile")
}

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class GovernedResourcesExtension @Inject constructor(objects: ObjectFactory) {
  val missingSourceMessageTemplate: Property<String> = objects.property(String::class.java)

  private val entries = mutableListOf<GovernedResourceEntry>()

  fun entry(
    taskName: String,
    repoRelativeSource: String,
    destinationDir: String,
    owner: String,
    sourceFromRuntimeKotlinProject: Boolean = false,
    requireSourceIsFile: Boolean = false,
    includeInMainProcessResources: Boolean = true,
    includeInTestProcessResources: Boolean = true,
  ) {
    entries +=
      GovernedResourceEntry(
        taskName = taskName,
        repoRelativeSource = repoRelativeSource,
        destinationDir = destinationDir,
        owner = owner,
        sourceFromRuntimeKotlinProject = sourceFromRuntimeKotlinProject,
        requireSourceIsFile = requireSourceIsFile,
        includeInMainProcessResources = includeInMainProcessResources,
        includeInTestProcessResources = includeInTestProcessResources,
      )
  }

  internal fun registeredEntries(): List<GovernedResourceEntry> = entries.toList()
}

internal data class GovernedResourceEntry(
  val taskName: String,
  val repoRelativeSource: String,
  val destinationDir: String,
  val owner: String,
  val sourceFromRuntimeKotlinProject: Boolean,
  val requireSourceIsFile: Boolean,
  val includeInMainProcessResources: Boolean,
  val includeInTestProcessResources: Boolean,
)

package dev.skillbill.runtime.buildlogic

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

abstract class GovernedResourcesExtension @Inject constructor(objects: ObjectFactory) {
  val missingSourceMessageTemplate: Property<String> = objects.property(String::class.java)

  private val entries = mutableListOf<GovernedResourceEntry>()

  fun entry(entry: GovernedResourceEntry) {
    entries += entry
  }

  internal fun registeredEntries(): List<GovernedResourceEntry> = entries.toList()
}

data class GovernedResourceEntry(
  val taskName: String,
  val repoRelativeSource: String,
  val destinationDir: String,
  val owner: String,
  val sourceFromRuntimeKotlinProject: Boolean = false,
  val requireSourceIsFile: Boolean = false,
  val includeInMainProcessResources: Boolean = true,
  val includeInTestProcessResources: Boolean = true,
)

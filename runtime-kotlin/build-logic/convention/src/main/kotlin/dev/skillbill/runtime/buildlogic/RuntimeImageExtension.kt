package dev.skillbill.runtime.buildlogic

import org.gradle.api.provider.Property

abstract class RuntimeImageExtension {
  abstract val imageBaseName: Property<String>
}

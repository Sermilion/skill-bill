package dev.skillbill.runtime.buildlogic

import org.gradle.api.provider.Property

abstract class RuntimeImageExtension {

  abstract val imageBaseName: Property<String>

  val runtimeTargetTokens: List<String> = dev.skillbill.runtime.buildlogic.runtimeTargetTokens

  val hostRuntimeToken: String? = resolveHostRuntimeToken()
}

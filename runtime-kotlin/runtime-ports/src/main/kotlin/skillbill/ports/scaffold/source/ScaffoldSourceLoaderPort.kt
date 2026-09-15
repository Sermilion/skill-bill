package skillbill.ports.scaffold.source

import skillbill.ports.scaffold.source.model.ScaffoldPlatformPackLoadRequest
import skillbill.ports.scaffold.source.model.ScaffoldPlatformPackLoadResult

fun interface ScaffoldSourceLoaderPort {
  fun loadPlatformPack(request: ScaffoldPlatformPackLoadRequest): ScaffoldPlatformPackLoadResult
}

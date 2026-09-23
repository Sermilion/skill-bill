package skillbill.ports.process

import skillbill.ports.process.model.ReleaseCatalogResult

/**
 * Lists the published Skill Bill runtime releases from the release host.
 */
interface ReleaseCatalogPort {
  /**
   * Returns every release entry the host reports, in host order, or a [ReleaseCatalogResult.Failure]
   * carrying the operator-facing reason when the request or payload fails. Interruption propagates.
   */
  fun listReleases(): ReleaseCatalogResult
}

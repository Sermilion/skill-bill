package skillbill.error.featuretask

import skillbill.error.shellcontent.ShellContentContractException

class FeatureTaskRuntimeSharedEvidenceFingerprintContradictionError(
  val addressedFingerprint: String,
  val recordedFingerprint: String,
  val sourceLabel: String,
  cause: Throwable? = null,
) : ShellContentContractException(
    "Shared review evidence at '$sourceLabel' is addressed by fingerprint '$addressedFingerprint' but " +
      "records fingerprint '$recordedFingerprint'; refusing to serve evidence for a contradicted checkpoint.",
    cause,
  )

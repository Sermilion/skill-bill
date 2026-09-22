package skillbill.infrastructure.skills.scaffold.platformpack.loader
import skillbill.error.shellcontent.ContractVersionMismatchError
import skillbill.error.shellcontent.InvalidFallbackCapabilityError
import skillbill.error.shellcontent.InvalidManifestSchemaError
import skillbill.error.shellcontent.InvalidValidationGateDeclarationError
import skillbill.error.shellcontent.MissingContentFileError
import skillbill.error.shellcontent.MissingRequiredSectionError

internal fun invalidManifestSchema(message: String): Nothing {
  throw InvalidManifestSchemaError(message)
}

internal fun missingManifestContent(message: String): Nothing {
  throw MissingContentFileError(message)
}

internal fun missingManifestSection(message: String): Nothing {
  throw MissingRequiredSectionError(message)
}

internal fun invalidFallbackCapability(message: String): Nothing {
  throw InvalidFallbackCapabilityError(message)
}

internal fun contractVersionMismatch(message: String): Nothing {
  throw ContractVersionMismatchError(message)
}

internal fun invalidValidationGateDeclaration(message: String): Nothing {
  throw InvalidValidationGateDeclarationError(message)
}

internal fun invalidManifestSchemaFromPath(
  message: String,
  cause: Throwable,
): Nothing {
  throw InvalidManifestSchemaError(message, cause)
}

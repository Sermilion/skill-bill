package skillbill.cli.kernel.cli

import skillbill.cli.model.CliRunInputs
import java.nio.file.Path

fun resolveCliRepositoryRoot(
  explicitRepoRoot: String?,
  inputs: CliRunInputs,
): Path = explicitRepoRoot?.let { Path.of(it).toAbsolutePath().normalize() } ?: inputs.repositoryRoot

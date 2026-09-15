package skillbill.workflow.gitops

object ProtectedBranches {
  val names: Set<String> = setOf("main", "master", "trunk")

  fun protectedName(branch: String?): String? = branch
    ?.trim()
    ?.takeIf(String::isNotBlank)
    ?.removePrefix("refs/heads/")
    ?.takeIf { candidate -> candidate.lowercase() in names }
}

package skillbill.engine.agentoutput

import skillbill.application.agentoutput.headAndTailExcerpt

fun stderrExcerpt(
  stderr: String,
  maxChars: Int,
): String? = headAndTailExcerpt(stderr, maxChars)

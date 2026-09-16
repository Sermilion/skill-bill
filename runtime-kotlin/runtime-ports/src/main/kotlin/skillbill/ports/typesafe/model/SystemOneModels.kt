package skillbill.ports.typesafe.model

import skillbill.contracts.typesafe.SystemOneQuestionType

data class SystemOneCredentials(
  val apiKey: String,
  val baseUrl: String,
  val defaultModel: String,
)

data class SystemOneConfiguration(
  val enabled: Boolean,
  val apiKeyConfigured: Boolean,
  val baseUrl: String,
  val defaultModel: String,
)

data class SystemOneEvaluateRequest(
  val state: String,
  val questions: Map<String, SystemOneQuestionSpec>,
  val model: String? = null,
)

sealed interface SystemOneQuestionSpec {
  val questionType: SystemOneQuestionType
}

data class SystemOneNoulQuestion(
  val instructions: String,
  val criteria: Map<String, String>? = null,
) : SystemOneQuestionSpec {
  override val questionType: SystemOneQuestionType = SystemOneQuestionType.NOUL
}

data class SystemOneChoiceQuestion(
  val instructions: String,
  val criteria: Map<String, String?>,
) : SystemOneQuestionSpec {
  override val questionType: SystemOneQuestionType = SystemOneQuestionType.CHOICE
}

data class SystemOneScoreQuestion(
  val instructions: String,
  val criteria: List<String>,
) : SystemOneQuestionSpec {
  override val questionType: SystemOneQuestionType = SystemOneQuestionType.SCORE
}

data class SystemOneEvaluateResult(
  val model: String,
  val answers: Map<String, SystemOneAnswer>,
  val usage: SystemOneTokenUsage?,
)

data class SystemOneTokenUsage(
  val inputTokens: Int,
  val outputTokens: Int,
)

sealed interface SystemOneAnswer {
  val answerType: SystemOneQuestionType
}

data class SystemOneNoulAnswer(
  val probabilityYes: Double,
) : SystemOneAnswer {
  override val answerType: SystemOneQuestionType = SystemOneQuestionType.NOUL
}

data class SystemOneChoiceAnswer(
  val choice: String,
  val probabilities: Map<String, Double>,
  val confidence: Double,
) : SystemOneAnswer {
  override val answerType: SystemOneQuestionType = SystemOneQuestionType.CHOICE
}

data class SystemOneScoreAnswer(
  val score: Double,
  val legend: Map<String, String>,
  val probabilities: Map<String, Double>,
  val confidence: Double,
) : SystemOneAnswer {
  override val answerType: SystemOneQuestionType = SystemOneQuestionType.SCORE
}

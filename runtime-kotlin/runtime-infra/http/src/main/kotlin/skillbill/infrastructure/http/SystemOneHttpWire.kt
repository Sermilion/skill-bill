package skillbill.infrastructure.http

import kotlinx.serialization.json.JsonObject
import skillbill.contracts.JsonCodec
import skillbill.contracts.typesafe.SystemOneQuestionType
import skillbill.contracts.typesafe.SystemOneWireKeys
import skillbill.error.SystemOneMalformedResponseError
import skillbill.ports.typesafe.model.SystemOneAnswer
import skillbill.ports.typesafe.model.SystemOneChoiceAnswer
import skillbill.ports.typesafe.model.SystemOneChoiceQuestion
import skillbill.ports.typesafe.model.SystemOneEvaluateRequest
import skillbill.ports.typesafe.model.SystemOneEvaluateResult
import skillbill.ports.typesafe.model.SystemOneNoulAnswer
import skillbill.ports.typesafe.model.SystemOneNoulQuestion
import skillbill.ports.typesafe.model.SystemOneQuestionSpec
import skillbill.ports.typesafe.model.SystemOneScoreAnswer
import skillbill.ports.typesafe.model.SystemOneScoreQuestion
import skillbill.ports.typesafe.model.SystemOneTokenUsage

internal fun systemOneRequestJson(request: SystemOneEvaluateRequest, resolvedModel: String): String {
  val questions =
    request.questions.mapValues { (_, question) -> question.toWireMap() }
  return JsonCodec.mapToJsonString(
    linkedMapOf(
      SystemOneWireKeys.STATE to request.state,
      SystemOneWireKeys.MODEL to resolvedModel,
      SystemOneWireKeys.QUESTIONS to questions,
    ),
  )
}

internal fun parseSystemOneEvaluateResult(body: String): SystemOneEvaluateResult {
  val root =
    JsonCodec.parseObjectOrNull(body)
      ?: malformedResponse("response body is not a JSON object")
  val rootMap = root.toStringAnyMap()
  val model =
    rootMap.stringField(SystemOneWireKeys.MODEL)
      ?: malformedResponse("missing model")
  val answersObject =
    JsonCodec.anyToStringAnyMap(rootMap[SystemOneWireKeys.ANSWERS])
      ?: malformedResponse("missing answers object")
  val answers =
    answersObject.mapValues { (questionId, rawAnswer) ->
      parseSystemOneAnswer(questionId, rawAnswer)
    }
  return SystemOneEvaluateResult(
    model = model,
    answers = answers,
    usage = parseSystemOneUsage(JsonCodec.anyToStringAnyMap(rootMap[SystemOneWireKeys.USAGE])),
  )
}

private fun JsonObject.toStringAnyMap(): Map<String, Any?> =
  entries.associate { (key, value) -> key to JsonCodec.jsonElementToValue(value) }

private fun Map<String, Any?>.stringField(key: String): String? = this[key]?.toString()?.takeIf(String::isNotBlank)

private fun malformedResponse(detail: String): Nothing = throw SystemOneMalformedResponseError(detail)

private fun parseSystemOneUsage(raw: Map<String, Any?>?): SystemOneTokenUsage? {
  if (raw == null) return null
  val input = raw[SystemOneWireKeys.INPUT_TOKENS].asInt()
  val output = raw[SystemOneWireKeys.OUTPUT_TOKENS].asInt()
  return if (input != null && output != null) {
    SystemOneTokenUsage(inputTokens = input, outputTokens = output)
  } else {
    null
  }
}

private fun parseSystemOneAnswer(questionId: String, raw: Any?): SystemOneAnswer {
  val answerMap =
    JsonCodec.anyToStringAnyMap(raw)
      ?: malformedResponse("answer '$questionId' is not an object")
  val typeWire =
    answerMap.stringField(SystemOneWireKeys.TYPE)
      ?: malformedResponse("answer '$questionId' is missing type")
  val type =
    SystemOneQuestionType.fromWire(typeWire)
      ?: malformedResponse("answer '$questionId' has unknown type '$typeWire'")
  return when (type) {
    SystemOneQuestionType.NOUL -> parseNoulAnswer(questionId, answerMap)
    SystemOneQuestionType.CHOICE -> parseChoiceAnswer(questionId, answerMap)
    SystemOneQuestionType.SCORE -> parseScoreAnswer(questionId, answerMap)
  }
}

private fun parseNoulAnswer(questionId: String, answerMap: Map<String, Any?>): SystemOneNoulAnswer {
  val noul =
    answerMap[SystemOneWireKeys.NOUL].asDouble()
      ?: malformedResponse("answer '$questionId' is missing noul")
  return SystemOneNoulAnswer(probabilityYes = noul)
}

private fun parseChoiceAnswer(questionId: String, answerMap: Map<String, Any?>): SystemOneChoiceAnswer {
  val choice =
    answerMap.stringField(SystemOneWireKeys.CHOICE)
      ?: malformedResponse("answer '$questionId' is missing choice")
  val probabilities = answerProbabilities(questionId, answerMap)
  val confidence = answerConfidence(questionId, answerMap)
  return SystemOneChoiceAnswer(choice = choice, probabilities = probabilities, confidence = confidence)
}

private fun parseScoreAnswer(questionId: String, answerMap: Map<String, Any?>): SystemOneScoreAnswer {
  val score =
    answerMap[SystemOneWireKeys.SCORE].asDouble()
      ?: malformedResponse("answer '$questionId' is missing score")
  val legend =
    JsonCodec.anyToStringAnyMap(answerMap[SystemOneWireKeys.LEGEND])
      ?.mapValues { (_, value) -> value?.toString() ?: "" }
      ?: malformedResponse("answer '$questionId' is missing legend")
  val probabilities = answerProbabilities(questionId, answerMap)
  val confidence = answerConfidence(questionId, answerMap)
  return SystemOneScoreAnswer(
    score = score,
    legend = legend,
    probabilities = probabilities,
    confidence = confidence,
  )
}

private fun answerProbabilities(questionId: String, answerMap: Map<String, Any?>): Map<String, Double> =
  JsonCodec.anyToStringAnyMap(answerMap[SystemOneWireKeys.PROBABILITIES])
    ?.mapValues { (_, value) ->
      value.asDouble() ?: malformedResponse("answer '$questionId' has invalid probabilities")
    }
    ?: malformedResponse("answer '$questionId' is missing probabilities")

private fun answerConfidence(questionId: String, answerMap: Map<String, Any?>): Double =
  answerMap[SystemOneWireKeys.CONFIDENCE].asDouble()
    ?: malformedResponse("answer '$questionId' is missing confidence")

private fun SystemOneQuestionSpec.toWireMap(): Map<String, Any?> = when (this) {
  is SystemOneNoulQuestion -> buildMap {
    put(SystemOneWireKeys.TYPE, questionType.wireValue)
    put(SystemOneWireKeys.INSTRUCTIONS, instructions)
    criteria?.let { put(SystemOneWireKeys.CRITERIA, it) }
  }
  is SystemOneChoiceQuestion -> buildMap {
    put(SystemOneWireKeys.TYPE, questionType.wireValue)
    put(SystemOneWireKeys.INSTRUCTIONS, instructions)
    put(SystemOneWireKeys.CRITERIA, criteria)
  }
  is SystemOneScoreQuestion -> buildMap {
    put(SystemOneWireKeys.TYPE, questionType.wireValue)
    put(SystemOneWireKeys.INSTRUCTIONS, instructions)
    put(SystemOneWireKeys.CRITERIA, criteria)
  }
}

private fun Any?.asDouble(): Double? = when (this) {
  is Number -> toDouble()
  is String -> toDoubleOrNull()
  else -> null
}

private fun Any?.asInt(): Int? = when (this) {
  is Number -> toInt()
  is String -> toIntOrNull()
  else -> null
}

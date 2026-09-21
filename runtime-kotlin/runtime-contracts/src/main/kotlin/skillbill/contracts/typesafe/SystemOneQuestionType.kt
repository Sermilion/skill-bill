package skillbill.contracts.typesafe

enum class SystemOneQuestionType(val wireValue: String) {
  NOUL("noul"),
  CHOICE("choice"),
  SCORE("score"),
  ;

  companion object {
    fun fromWire(value: String): SystemOneQuestionType? = entries.firstOrNull { it.wireValue == value }
  }
}

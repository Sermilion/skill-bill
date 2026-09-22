package skillbill.ports.review.preparation

interface ReviewInputSource {
  fun readInput(
    inputPath: String,
    stdinText: String? = null,
  ): Pair<String, String?>
}

package skillbill.experiment

import skillbill.contracts.experiment.config.ExperimentConfigPayloadKeys
import skillbill.error.shellcontent.ExperimentParameterMalformedError
import skillbill.experiment.model.ExperimentConfigParse
import skillbill.experiment.model.parseExperimentAvailabilityValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExperimentParameterParserTest {
  @Test
  fun `comma separated names form one set`() {
    val a = ExperimentParameterParser.parse("b,a")
    val b = ExperimentParameterParser.parse("a,b")
    assertEquals(a.normalizedNames, b.normalizedNames)
  }

  @Test
  fun `none mixed with name fails`() {
    assertFailsWith<ExperimentParameterMalformedError> {
      ExperimentParameterParser.parse("none,codegraph")
    }
  }

  @Test
  fun `null means omitted but blank parameter fails`() {
    assertEquals(emptyList(), ExperimentParameterParser.parse(null).normalizedNames)
    assertFailsWith<ExperimentParameterMalformedError> {
      ExperimentParameterParser.parse(" ")
    }
  }

  @Test
  fun `empty comma entries fail instead of silently changing the selected set`() {
    assertFailsWith<ExperimentParameterMalformedError> {
      ExperimentParameterParser.parse("first,,second")
    }
  }

  @Test
  fun `config parser rejects malformed and reserved values with the owning key`() {
    val invalidValues = listOf(
      null,
      "fixture",
      listOf(1),
      listOf(""),
      listOf("none"),
      listOf("fixture", "fixture"),
    )

    invalidValues.forEach { value ->
      when (val result = parseExperimentAvailabilityValue(value)) {
        is ExperimentConfigParse.Invalid -> assertEquals(ExperimentConfigPayloadKeys.EXPERIMENTS, result.key)
        is ExperimentConfigParse.Valid -> assertTrue(false, "expected malformed value: $value")
      }
    }
  }
}

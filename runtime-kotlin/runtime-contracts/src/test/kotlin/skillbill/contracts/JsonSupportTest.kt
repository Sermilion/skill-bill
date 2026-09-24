package skillbill.contracts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JsonSupportTest {
  @Test
  fun `json strings that start with numbers remain strings`() {
    val parsed =
      requireNotNull(
        JsonCodec.parseObjectOrNull("""{"decisions":["1 fix","2 reject"],"count":2}"""),
      )
    val decoded = requireNotNull(JsonCodec.anyToStringAnyMap(JsonCodec.jsonElementToValue(parsed)))

    assertEquals(listOf("1 fix", "2 reject"), decoded["decisions"])
    assertEquals(2, decoded["count"])
  }

  @Test
  fun `parseObjectOrNull returns null for malformed text and for well-formed non-objects`() {
    assertNull(
      JsonCodec.parseObjectOrNull("{not json"),
      "Malformed JSON must surface as null rather than escaping as a serialization exception.",
    )
    assertNull(
      JsonCodec.parseObjectOrNull("[1]"),
      "A well-formed JSON array is not a payload object; callers branch on null instead of class-casting.",
    )
    assertNull(
      JsonCodec.parseObjectOrNull("42"),
      "A well-formed JSON scalar is not a payload object; callers branch on null instead of class-casting.",
    )
  }
}

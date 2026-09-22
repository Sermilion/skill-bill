package skillbill.contracts

import skillbill.error.core.JsonWrongRootTypeError
import skillbill.error.core.MalformedJsonTextError
import skillbill.error.core.UnsupportedJsonValueError
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JsonCodecTest {
  @Test
  fun `strict array parse accepts whitespace formatted empty arrays`() {
    assertEquals(emptyList(), JsonCodec.parseJsonArrayStrict("[]"))
    assertEquals(emptyList(), JsonCodec.parseJsonArrayStrict("[ ]"))
  }

  @Test
  fun `strict array parse rejects malformed text and wrong roots`() {
    assertFailsWith<MalformedJsonTextError> { JsonCodec.parseJsonArrayStrict("{") }
    assertFailsWith<JsonWrongRootTypeError> { JsonCodec.parseJsonArrayStrict("{}") }
  }

  @Test
  fun `BigInteger encoding preserves integers above double precision`() {
    val value = BigInteger("9007199254740993")
    val encoded = JsonCodec.valueToJsonString(value)
    assertEquals("9007199254740993", encoded)
    assertEquals(value.toLong(), JsonCodec.parseValue(encoded))
  }

  @Test
  fun `decoding an integer above Long preserves the exact BigInteger`() {
    val value = BigInteger("9223372036854775808")

    assertEquals(value, JsonCodec.parseValue(value.toString()))
  }

  @Test
  fun `BigDecimal encoding avoids double rounding`() {
    val value = BigDecimal("1.234567890123456789")
    val encoded = JsonCodec.valueToJsonString(value)
    assertEquals("1.234567890123456789", encoded)
    assertEquals(value, JsonCodec.parseValue(encoded))
  }

  @Test
  fun `mixed key maps fail instead of dropping entries`() {
    val map: Map<*, *> = mapOf("ok" to 1, 2 to 3)
    assertFailsWith<UnsupportedJsonValueError> {
      JsonCodec.valueToJsonString(map)
    }
    assertFailsWith<UnsupportedJsonValueError> {
      JsonCodec.anyToStringAnyMap(map)
    }
  }

  @Test
  fun `unsupported value types fail instead of using toString`() {
    assertFailsWith<UnsupportedJsonValueError> {
      JsonCodec.valueToJsonString(object {})
    }
  }

  @Test
  fun `non-finite floating point values fail instead of entering JSON`() {
    assertFailsWith<UnsupportedJsonValueError> {
      JsonCodec.valueToJsonString(Double.NaN)
    }
    assertFailsWith<UnsupportedJsonValueError> {
      JsonCodec.valueToJsonString(Float.POSITIVE_INFINITY)
    }
  }
}

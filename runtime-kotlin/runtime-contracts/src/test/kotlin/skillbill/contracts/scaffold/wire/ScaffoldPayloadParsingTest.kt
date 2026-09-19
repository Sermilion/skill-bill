package skillbill.contracts.scaffold.wire

import skillbill.error.shellcontent.InvalidScaffoldPayloadError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
class ScaffoldPayloadParsingTest {
  @Test
  fun `present wrong type description fails before defaulting to empty`() {
    assertFailsWith<InvalidScaffoldPayloadError> {
      requireStringOrDefault(mapOf("description" to 123), "description", "")
    }
  }

  @Test
  fun `present wrong type optional string fails before treating as absent`() {
    assertFailsWith<InvalidScaffoldPayloadError> {
      optionalString(mapOf("content_body" to 123), "content_body")
    }
  }

  @Test
  fun `omitted null and blank optional strings retain their defaults`() {
    assertEquals("fallback", requireStringOrDefault(emptyMap(), "description", "fallback"))
    assertEquals("fallback", requireStringOrDefault(mapOf("description" to null), "description", "fallback"))
    assertEquals("fallback", requireStringOrDefault(mapOf("description" to " "), "description", "fallback"))
    assertEquals(null, optionalString(emptyMap(), "content_body"))
    assertEquals(null, optionalString(mapOf("content_body" to null), "content_body"))
    assertEquals(null, optionalString(mapOf("content_body" to " "), "content_body"))
  }
}

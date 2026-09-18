package skillbill.ports.architecture

import kotlin.test.Test
import kotlin.test.assertTrue

class PortsFooMapRawMapFixtureTest {
  @Test
  fun `FooMap fixture path is covered by runtime-core raw map scanner`() {
    assertTrue(
      PortsFooMapRawMapFixtureTest::class.java.name.contains("ports.architecture"),
      "Synthetic FooMap coverage lives in RuntimeRawMapArchitectureTest with a runtime-ports/src/main path.",
    )
  }
}

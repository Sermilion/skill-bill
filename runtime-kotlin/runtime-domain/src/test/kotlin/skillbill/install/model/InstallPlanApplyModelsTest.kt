package skillbill.install.model

import skillbill.model.FileLocation
import kotlin.test.Test
import kotlin.test.assertEquals

class InstallPlanApplyModelsTest {
  @Test
  fun `recording a symlink returns a new transaction without mutating the prior result`() {
    val initial = InstallTransaction()
    val firstPath = FileLocation("/skills/first")
    val secondPath = FileLocation("/skills/second")

    val afterFirst = initial.withRecordedSymlink(firstPath)
    val afterSecond = afterFirst.withRecordedSymlink(secondPath)

    assertEquals(emptyList(), initial.createdSymlinks)
    assertEquals(listOf(firstPath), afterFirst.createdSymlinks)
    assertEquals(listOf(firstPath, secondPath), afterSecond.createdSymlinks)
  }
}

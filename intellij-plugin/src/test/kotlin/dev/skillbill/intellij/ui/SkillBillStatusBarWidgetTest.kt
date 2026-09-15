package dev.skillbill.intellij.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test


class SkillBillStatusBarWidgetTest {
    @Test
    fun `factory and widget ids stay identical`() {
        assertEquals(SkillBillStatusBarIds.ID, SkillBillStatusBarWidgetFactory().getId())
        assertEquals("SkillBillStatusBarWidget", SkillBillStatusBarIds.ID)
        assertEquals(SkillBillStatusBarIds.DISPLAY_NAME, SkillBillStatusBarWidgetFactory().getDisplayName())
    }

    @Test
    fun `bar click surface stays refresh and details only`() {
        val kinds = SkillBillStatusBarWidget.ClickKind.entries.map { it.name }.toSet()
        assertEquals(setOf("REFRESH_AND_DETAILS"), kinds)
        assertTrue(kinds.none { it.contains("START") || it.contains("RESUME") || it.contains("CANCEL") })
    }
}

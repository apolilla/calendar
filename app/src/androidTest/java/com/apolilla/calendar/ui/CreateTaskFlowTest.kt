package com.apolilla.calendar.ui

import android.Manifest
import android.os.Build
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.apolilla.calendar.CalendarApp
import com.apolilla.calendar.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** End-to-end smoke tests of the main flows on a device or emulator. */
@RunWith(AndroidJUnit4::class)
class CreateTaskFlowTest {

    @get:Rule(order = 0)
    val permission: GrantPermissionRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val name = "UI test ${System.currentTimeMillis()}"

    @After
    fun cleanUp() = runBlocking {
        // Remove what this test created so repeated runs start clean.
        val container = (compose.activity.application as CalendarApp).container
        val today = LocalDate.now()
        container.repository.observeTasksBetween(today.minusDays(1), today.plusDays(1)).first()
            .filter { it.name == name }
            .forEach { container.taskService.delete(it.id) }
    }

    private fun titleText(): String =
        compose.onNodeWithTag("month_title").fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text)?.joinToString { it.text } ?: ""

    @Test
    fun createTask_appearsInSelectedDayList_andOpensDetails() {
        compose.onNodeWithTag("new_task").performClick()
        compose.onNodeWithTag("name_field").performTextInput(name)
        compose.onNodeWithTag("save").performClick()

        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("task_list")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("task_list").performScrollToNode(hasText(name))
        compose.onNode(hasText(name)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("detail_name")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("detail_name").assertIsDisplayed()

        runBlocking {
            val container = (compose.activity.application as CalendarApp).container
            val today = LocalDate.now()
            val stored = container.repository.observeTasksBetween(today, today).first().filter { it.name == name }
            assertEquals(1, stored.size)
            assertEquals(10, stored.single().time.hour) // default time 10:00
        }
    }

    @Test
    fun emptyName_showsErrorAndStaysOnForm() {
        compose.onNodeWithTag("new_task").performClick()
        compose.onNodeWithTag("save").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("name_field").assertIsDisplayed()
        compose.onNodeWithTag("cancel").performClick()
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("new_task")).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun monthNavigation_changesAndRestoresTitle() {
        val initial = titleText()
        assertTrue(initial.isNotBlank())
        compose.onNodeWithTag("next_month").performClick()
        compose.waitForIdle()
        assertNotEquals(initial, titleText())
        compose.onNodeWithTag("prev_month").performClick()
        compose.waitForIdle()
        assertEquals(initial, titleText())
    }
}

package com.skestc.calendarwidget.settings

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class EventEditorTest {

    private val target = EventEditor.insertIntent()

    @Test
    fun defaultOpensTheTargetAsIs() {
        val intents = EventEditor.launchIntents(null, target, "t")
        assertEquals(1, intents.size)
        assertNull(intents[0].`package`)
    }

    @Test
    fun askShowsTheChooserThenFallsBack() {
        val intents = EventEditor.launchIntents(EventEditor.ASK, target, "t")
        assertEquals(Intent.ACTION_CHOOSER, intents[0].action)
        assertNull(intents[1].`package`)
    }

    @Test
    fun chosenAppComesFirstWithSystemDefaultAsFallback() {
        val intents = EventEditor.launchIntents("com.example.cal", target, "t")
        assertEquals("com.example.cal", intents[0].`package`)
        assertEquals(Intent.ACTION_INSERT, intents[0].action)
        assertNull(intents[1].`package`)
    }

    @Test
    fun choiceIsStoredAndCleared() {
        val context = RuntimeEnvironment.getApplication()
        EventEditor.setChoice(context, "com.example.cal")
        assertEquals("com.example.cal", EventEditor.choice(context))
        EventEditor.setChoice(context, null)
        assertNull(EventEditor.choice(context))
    }
}

package com.hush.app.domain.usecase

import com.hush.app.domain.model.MatchField
import com.hush.app.domain.model.RuleAction
import com.hush.app.domain.repository.AppInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class FallbackCommandParserTest {

    private val parser = FallbackCommandParser()
    private val apps = listOf(
        AppInfo("Instagram", "com.instagram.android"),
        AppInfo("WhatsApp", "com.whatsapp"),
        AppInfo("Slack", "com.slack"),
        AppInfo("Gmail", "com.google.android.gm")
    )

    @Test
    fun muteApp_simple() {
        val result = parser.parse("Mute Instagram", apps)
        assertEquals(RuleAction.MUTE, result.action)
        assertEquals("com.instagram.android", result.app)
        assertNull(result.matchPattern)
        assertFalse(result.isInverted)
    }

    @Test
    fun blockApp_withAfterTime() {
        val result = parser.parse("Block Slack after 6pm", apps)
        assertEquals(RuleAction.BLOCK, result.action)
        assertEquals("com.slack", result.app)
        assertEquals(LocalTime.of(18, 0), result.timeStart)
        assertNull(result.timeEnd)
    }

    @Test
    fun muteApp_betweenTimes() {
        val result = parser.parse("Mute Slack between 10pm and 7am", apps)
        assertEquals(RuleAction.MUTE, result.action)
        assertEquals("com.slack", result.app)
        assertEquals(LocalTime.of(22, 0), result.timeStart)
        assertEquals(LocalTime.of(7, 0), result.timeEnd)
    }

    @Test
    fun exceptionRule_invertedSenderMatch() {
        val result = parser.parse("Mute WhatsApp except from Bob", apps)
        assertEquals(RuleAction.MUTE, result.action)
        assertEquals("com.whatsapp", result.app)
        assertTrue(result.isInverted)
        assertEquals(MatchField.SENDER, result.matchField)
        assertEquals("bob", result.matchPattern?.lowercase())
    }

    @Test
    fun categoryKeyword_promos() {
        val result = parser.parse("Silence promos", apps)
        assertEquals(RuleAction.MUTE, result.action)
        assertNull(result.app)
        assertEquals("promo", result.matchPattern)
    }

    @Test
    fun containsPattern_freeText() {
        val result = parser.parse("Block Gmail containing invoice", apps)
        assertEquals(RuleAction.BLOCK, result.action)
        assertEquals("com.google.android.gm", result.app)
        assertEquals("invoice", result.matchPattern)
    }

    @Test
    fun allowAction_recognized() {
        val result = parser.parse("Allow WhatsApp", apps)
        assertEquals(RuleAction.ALLOW, result.action)
        assertEquals("com.whatsapp", result.app)
    }

    @Test
    fun unintelligible_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            parser.parse("What is the weather today", apps)
        }
    }

    @Test
    fun summary_isHumanReadable() {
        val result = parser.parse("Block Slack after 6pm", apps)
        assertEquals("Block Slack notifications after 6 PM", result.summary)
    }
}

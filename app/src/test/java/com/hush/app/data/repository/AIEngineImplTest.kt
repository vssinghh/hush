package com.hush.app.data.repository

import android.content.ContextWrapper
import com.hush.app.domain.repository.AppInfo
import com.hush.app.domain.repository.PackageResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.LocalTime

class LocalTestPackageResolver : PackageResolver {
    override fun getInstalledApps(): List<AppInfo> = emptyList()
    override fun resolvePackage(appName: String): String? = null
    override fun isInstalled(packageName: String): Boolean = false
}

class AIEngineImplTest {

    private val context = ContextWrapper(null)
    private val packageResolver = LocalTestPackageResolver()
    private val aiEngine = AIEngineImpl(context, packageResolver)

    @Test
    fun testIsAvailable_InitiallyFalse() {
        assertFalse(aiEngine.isAvailable())
    }

    @Test
    fun testParseCommand_ThrowsWhenUnavailable() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                aiEngine.parseCommand("Mute WhatsApp")
            }
        }
    }

    @Test
    fun testParseCommand_ThrowsIllegalArgumentException_WhenPromptIsBlank() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                aiEngine.parseCommand("")
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                aiEngine.parseCommand("   ")
            }
        }
    }

    @Test
    fun testCleanJsonText_ValidAndMarkdown() {
        val method = AIEngineImpl::class.java.getDeclaredMethod("cleanJsonText", String::class.java)
        method.isAccessible = true

        // Standard JSON
        val res1 = method.invoke(aiEngine, "{\"action\": \"allow\"}") as String
        assertEquals("{\"action\": \"allow\"}", res1)

        // Markdown JSON
        val res2 = method.invoke(aiEngine, "```json\n{\"action\": \"mute\"}\n```") as String
        assertEquals("{\"action\": \"mute\"}", res2)

        // Text wrapper
        val res3 = method.invoke(aiEngine, "Here is the response: {\"action\": \"block\"} hope that helps.") as String
        assertEquals("{\"action\": \"block\"}", res3)

        // Swapped braces or missing braces throws IllegalArgumentException (wrapped in InvocationTargetException)
        val ex1 = assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
            method.invoke(aiEngine, "invalid json")
        }
        assertNotNull(ex1.cause)
        assertEquals(IllegalArgumentException::class.java, ex1.cause!!.javaClass)

        val ex2 = assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
            method.invoke(aiEngine, "} swapped {")
        }
        assertNotNull(ex2.cause)
        assertEquals(IllegalArgumentException::class.java, ex2.cause!!.javaClass)
    }

    @Test
    fun testParseTimeRobust_Formats() {
        val method = AIEngineImpl::class.java.getDeclaredMethod("parseTimeRobust", String::class.java)
        method.isAccessible = true

        // HH:mm
        val t1 = method.invoke(aiEngine, "14:30") as LocalTime?
        assertNotNull(t1)
        assertEquals(LocalTime.of(14, 30), t1)

        // H:mm
        val t2 = method.invoke(aiEngine, "9:15") as LocalTime?
        assertNotNull(t2)
        assertEquals(LocalTime.of(9, 15), t2)

        // HH:mm:ss
        val t3 = method.invoke(aiEngine, "08:45:10") as LocalTime?
        assertNotNull(t3)
        assertEquals(LocalTime.of(8, 45, 10), t3)

        // h:mm a
        val t4 = method.invoke(aiEngine, "2:30 PM") as LocalTime?
        assertNotNull(t4)
        assertEquals(LocalTime.of(14, 30), t4)

        // hh:mm a
        val t5 = method.invoke(aiEngine, "02:30 AM") as LocalTime?
        assertNotNull(t5)
        assertEquals(LocalTime.of(2, 30), t5)

        // Invalid format falls back to null
        val t6 = method.invoke(aiEngine, "invalid time") as LocalTime?
        assertNull(t6)
    }

    @Test
    fun testParseCommand_BypassesAvailabilityCheck_WhenCachedTrue() {
        val field = AIEngineImpl::class.java.getDeclaredField("isAvailableCached")
        field.isAccessible = true
        field.set(aiEngine, true)

        val exception = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                aiEngine.parseCommand("Mute WhatsApp")
            }
        }
        val message = exception.message ?: ""
        assert(message.startsWith("AI engine failure:")) {
            "Expected message to start with 'AI engine failure:', but was '$message'"
        }
    }
}

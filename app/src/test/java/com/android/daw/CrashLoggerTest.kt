package com.android.daw

import android.content.Context
import com.android.daw.diagnostics.CrashLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

/**
 * Unit test suite for [CrashLogger].
 */
class CrashLoggerTest {

    private lateinit var mockContext: Context

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        CrashLogger.clearLogs()
    }

    @Test
    fun testLogRecordingAndCapacity() {
        CrashLogger.i("TestTag", "Info message")
        CrashLogger.w("TestTag", "Warning message")
        CrashLogger.e("TestTag", "Error message")

        val logs = CrashLogger.getInMemoryLogs()
        assertTrue(logs.size >= 3)
        assertTrue(logs.any { it.message == "Info message" && it.level == "I" })
        assertTrue(logs.any { it.message == "Warning message" && it.level == "W" })
        assertTrue(logs.any { it.message == "Error message" && it.level == "E" })
    }

    @Test
    fun testLogFormatWithException() {
        val testException = IllegalStateException("Test crash state")
        val entry = CrashLogger.LogEntry(
            level = "E",
            tag = "TestTag",
            message = "Critical failure",
            throwable = testException
        )

        val formatted = entry.format()
        assertTrue(formatted.contains("[E/TestTag] Critical failure"))
        assertTrue(formatted.contains("IllegalStateException: Test crash state"))
    }

    @Test
    fun testClearLogs() {
        CrashLogger.d("Tag", "Msg 1")
        CrashLogger.d("Tag", "Msg 2")
        CrashLogger.clearLogs()
        val logs = CrashLogger.getInMemoryLogs()
        // After clear, only the info log "In-memory logs cleared." should remain or empty
        assertTrue(logs.isEmpty() || logs.size == 1)
    }

    @Test
    fun testReportGeneration() {
        CrashLogger.i("App", "Session launched")
        val report = CrashLogger.buildFullDiagnosticReport(mockContext, "Simulated crash stack trace")
        assertNotNull(report)
        assertTrue(report.contains("SD STUDIO DAW - DIAGNOSTIC & CRASH REPORT"))
        assertTrue(report.contains("Simulated crash stack trace"))
        assertTrue(report.contains("Session launched"))
    }
}

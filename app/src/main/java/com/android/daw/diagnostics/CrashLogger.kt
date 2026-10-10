package com.android.daw.diagnostics

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.android.daw.CrashActivity
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * CrashLogger & Diagnostics Hub
 *
 * Provides:
 * 1. Global uncaught crash interception & automatic recovery launcher (CrashActivity).
 * 2. In-memory circular log buffering with logcat extraction.
 * 3. 1-click clipboard copy of comprehensive crash & debug traces.
 * 4. 1-click direct export of log.txt into the public external Downloads folder.
 */
object CrashLogger {

    private const val TAG = "CrashLogger"
    private const val MAX_IN_MEMORY_LOGS = 1000
    private const val CRASH_FILE_NAME = "crash_report.txt"

    data class LogEntry(
        val timestamp: Long = System.currentTimeMillis(),
        val level: String,
        val tag: String,
        val message: String,
        val throwable: Throwable? = null
    ) {
        fun format(): String {
            val dateStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(timestamp))
            val base = "[$dateStr] [$level/$tag] $message"
            return if (throwable != null) {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                "$base\n$sw"
            } else {
                base
            }
        }
    }

    private val logQueue = ConcurrentLinkedQueue<LogEntry>()

    @Volatile
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    @Volatile
    var lastCrashReport: String? = null
        private set

    /**
     * Install the global crash watchdog on application launch
     */
    fun install(app: Application) {
        if (defaultHandler != null) return

        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        // Read any existing crash from previous launch if available
        try {
            val crashFile = File(app.filesDir, CRASH_FILE_NAME)
            if (crashFile.exists() && crashFile.length() > 0) {
                lastCrashReport = crashFile.readText()
            }
        } catch (_: Throwable) {}

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleUncaughtCrash(app, thread, throwable)
        }

        i(TAG, "CrashLogger installed successfully. Global watchdog active.")
    }

    /**
     * Log messages into memory buffer and Android system logcat
     */
    fun d(tag: String, msg: String) = log("D", tag, msg, null)
    fun i(tag: String, msg: String) = log("I", tag, msg, null)
    fun w(tag: String, msg: String, t: Throwable? = null) = log("W", tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = log("E", tag, msg, t)

    fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
        val entry = LogEntry(level = level, tag = tag, message = message, throwable = throwable)
        logQueue.add(entry)
        while (logQueue.size > MAX_IN_MEMORY_LOGS) {
            logQueue.poll()
        }

        try {
            when (level) {
                "D" -> Log.d(tag, message, throwable)
                "I" -> Log.i(tag, message, throwable)
                "W" -> Log.w(tag, message, throwable)
                "E" -> Log.e(tag, message, throwable)
                else -> Log.v(tag, message, throwable)
            }
        } catch (_: Throwable) {
            // Safe fallback on environments without mocked android.util.Log
        }
    }

    fun getInMemoryLogs(): List<LogEntry> = logQueue.toList()

    fun clearLogs() {
        logQueue.clear()
        i(TAG, "In-memory logs cleared.")
    }

    /**
     * Extract recent logcat entries from the Android operating system
     */
    fun captureLogcat(maxLines: Int = 300): String {
        return try {
            val process = Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "-v", "time", "-t", maxLines.toString())
            )
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            reader.close()
            process.waitFor()
            if (output.isBlank()) {
                "No logcat entries available or permission restricted."
            } else {
                output.toString()
            }
        } catch (t: Throwable) {
            "Failed to capture logcat: ${t.message}"
        }
    }

    /**
     * Build comprehensive diagnostic report including device specs, RAM, app logs, and logcat
     */
    fun buildFullDiagnosticReport(context: Context, extraCrashHeader: String? = null): String {
        val sb = StringBuilder()
        val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())

        sb.appendLine("=================================================================")
        sb.appendLine("             SD STUDIO DAW - DIAGNOSTIC & CRASH REPORT           ")
        sb.appendLine("=================================================================")
        sb.appendLine("Generated At : $timeStr")
        sb.appendLine("Manufacturer : ${Build.MANUFACTURER}")
        sb.appendLine("Model        : ${Build.MODEL} (${Build.PRODUCT})")
        sb.appendLine("Device       : ${Build.DEVICE}")
        sb.appendLine("Android OS   : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        sb.appendLine("Supported ABI: ${Build.SUPPORTED_ABIS.joinToString(", ")}")

        val runtime = Runtime.getRuntime()
        val freeMb = runtime.freeMemory() / (1024 * 1024)
        val totalMb = runtime.totalMemory() / (1024 * 1024)
        val maxMb = runtime.maxMemory() / (1024 * 1024)
        sb.appendLine("Heap Memory  : Free: ${freeMb}MB / Total: ${totalMb}MB / Max: ${maxMb}MB")

        if (!extraCrashHeader.isNullOrBlank()) {
            sb.appendLine()
            sb.appendLine("-----------------------------------------------------------------")
            sb.appendLine("CRASH EXCEPTION DETAILS")
            sb.appendLine("-----------------------------------------------------------------")
            sb.appendLine(extraCrashHeader)
        }

        sb.appendLine()
        sb.appendLine("-----------------------------------------------------------------")
        sb.appendLine("RECENT IN-MEMORY APPLICATION LOGS (${logQueue.size} events)")
        sb.appendLine("-----------------------------------------------------------------")
        if (logQueue.isEmpty()) {
            sb.appendLine("(No in-memory logs recorded)")
        } else {
            logQueue.forEach { sb.appendLine(it.format()) }
        }

        sb.appendLine()
        sb.appendLine("-----------------------------------------------------------------")
        sb.appendLine("SYSTEM LOGCAT BUFFER (Recent Events)")
        sb.appendLine("-----------------------------------------------------------------")
        sb.appendLine(captureLogcat(250))

        sb.appendLine("=================================================================")
        sb.appendLine("                       END OF REPORT                             ")
        sb.appendLine("=================================================================")
        return sb.toString()
    }

    /**
     * Copy entire diagnostic report or stack trace to system clipboard
     */
    fun copyToClipboard(context: Context, text: String): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("SD Studio Diagnostic Log", text)
            clipboard.setPrimaryClip(clip)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Save report directly to the public Download folder (e.g. /storage/emulated/0/Download/log.txt)
     */
    fun saveLogToDownloads(context: Context, logContent: String): Result<String> {
        val fileName = "log.txt"

        // 1. Android 10+ (API 29+) MediaStore scoped approach
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                // Delete previous file if exists under same name to prevent log(1).txt proliferation
                val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val uri = resolver.insert(collection, values)
                    ?: resolver.insert(MediaStore.Files.getContentUri("external"), values)

                if (uri != null) {
                    resolver.openOutputStream(uri, "wt")?.use { os ->
                        os.write(logContent.toByteArray())
                        os.flush()
                    }
                    val path = "Downloads/$fileName"
                    i(TAG, "Successfully exported log via MediaStore to $path")
                    return Result.success(path)
                }
            } catch (t: Throwable) {
                w(TAG, "MediaStore export error, attempting direct file fallback: ${t.message}")
            }
        }

        // 2. Direct File API fallback targeting public Downloads directory
        return try {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            val targetFile = File(downloadDir, fileName)
            targetFile.writeText(logContent)
            i(TAG, "Successfully exported log via File API to ${targetFile.absolutePath}")
            Result.success(targetFile.absolutePath)
        } catch (t: Throwable) {
            e(TAG, "Failed to write log to Downloads folder: ${t.message}", t)
            Result.failure(t)
        }
    }

    private fun handleUncaughtCrash(context: Application, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val stackTrace = sw.toString()

        val crashHeader = buildString {
            appendLine("Exception  : ${throwable.javaClass.name}")
            appendLine("Message    : ${throwable.message ?: "None"}")
            appendLine("Thread     : ${thread.name} (id: ${thread.id})")
            appendLine()
            appendLine("Stack Trace:")
            appendLine(stackTrace)
        }

        e(TAG, "FATAL CRASH on thread ${thread.name}: ${throwable.message}", throwable)

        val fullReport = buildFullDiagnosticReport(context, crashHeader)
        lastCrashReport = fullReport

        // Persist crash report to internal file for recovery
        try {
            val crashFile = File(context.filesDir, CRASH_FILE_NAME)
            crashFile.writeText(fullReport)
        } catch (_: Throwable) {}

        // Automatically launch CrashActivity in isolated process
        try {
            val intent = Intent(context, CrashActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                putExtra(CrashActivity.EXTRA_CRASH_REPORT, fullReport)
                putExtra(CrashActivity.EXTRA_IS_CRASH, true)
            }
            context.startActivity(intent)
        } catch (e: Throwable) {
            // If starting CrashActivity fails, pass to original default handler
            defaultHandler?.uncaughtException(thread, throwable)
            return
        }

        // Allow graceful exit of the crashing process
        try {
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(10)
        } catch (_: Throwable) {}
    }
}

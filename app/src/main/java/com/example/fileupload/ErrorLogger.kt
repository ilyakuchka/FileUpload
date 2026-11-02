package com.example.fileupload

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Централизованный логгер ошибок приложения.
 * Пишет в Logcat (tag = FileUpload) и хранит историю в памяти для показа в UI.
 */
object ErrorLogger {
    private const val TAG = "FileUpload"
    private const val MAX_ENTRIES = 100

    data class Entry(
        val time: String,
        val level: String,
        val message: String,
        val detail: String? = null
    )

    private val entries = CopyOnWriteArrayList<Entry>()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun e(message: String, throwable: Throwable? = null) {
        val detail = throwable?.let {
            "${it.javaClass.simpleName}: ${it.message}\n${it.stackTraceToString().take(1500)}"
        }
        Log.e(TAG, message, throwable)
        add("E", message, detail)
    }

    fun e(message: String, httpCode: Int, body: String?) {
        val shortBody = body?.take(800) ?: ""
        val msg = "$message (HTTP $httpCode)"
        Log.e(TAG, "$msg body=$shortBody")
        add("E", msg, shortBody.ifBlank { null })
    }

    fun w(message: String) {
        Log.w(TAG, message)
        add("W", message, null)
    }

    fun i(message: String) {
        Log.i(TAG, message)
        add("I", message, null)
    }

    fun lastMessage(): String? = entries.lastOrNull()?.let { e ->
        if (e.detail.isNullOrBlank()) e.message else "${e.message}\n${e.detail}"
    }

    fun history(): List<Entry> = entries.toList()

    fun clear() {
        entries.clear()
    }

    private fun add(level: String, message: String, detail: String?) {
        entries.add(
            Entry(
                time = timeFmt.format(Date()),
                level = level,
                message = message,
                detail = detail
            )
        )
        while (entries.size > MAX_ENTRIES) {
            entries.removeAt(0)
        }
    }
}

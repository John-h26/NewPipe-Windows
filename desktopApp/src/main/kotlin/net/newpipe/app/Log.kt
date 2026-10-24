/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package net.newpipe.app

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Journal préfixé par [NPW], écrit sur la console et dans %TEMP%\npw-app.log
 * (l'exécutable installé n'a pas de console)
 */
object Log {
    private val time = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    val file = File(System.getProperty("java.io.tmpdir"), "npw-app.log")

    private val writer: PrintWriter? = runCatching {
        PrintWriter(file.bufferedWriter(Charsets.UTF_8), true)
    }.getOrNull()

    fun i(message: String) = write("${LocalTime.now().format(time)} [NPW] $message", error = false)

    fun e(message: String, error: Throwable? = null) {
        val trace = error?.let { StringWriter().also { w -> it.printStackTrace(PrintWriter(w)) }.toString() }
        write("${LocalTime.now().format(time)} [NPW] ERREUR $message" + (trace?.let { "\n$it" } ?: ""), error = true)
    }

    @Synchronized
    private fun write(line: String, error: Boolean) {
        if (error) System.err.println(line) else println(line)
        writer?.println(line)
    }
}

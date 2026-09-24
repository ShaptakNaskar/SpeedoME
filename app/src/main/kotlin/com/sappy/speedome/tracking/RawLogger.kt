package com.sappy.speedome.tracking

import android.content.Context
import com.sappy.speedome.engine.FixEvent
import com.sappy.speedome.engine.replay.RawLog
import java.io.File
import java.io.Writer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Developer raw logger (docs/plan.md §11): every raw fix in [RawLog] format plus GNSS status as
 * `#` comment lines, so a log from a real phone can be replayed straight into an engine test.
 * Files go to the app's external files dir, `rawlogs/` (readable with `adb pull`, no permission).
 */
class RawLogger(context: Context) {
    private val dir = File(context.applicationContext.getExternalFilesDir(null), "rawlogs")
    private var writer: Writer? = null
    private var lines = 0

    var file: File? = null
        private set

    @Synchronized
    fun setEnabled(on: Boolean) {
        if (on == (writer != null)) return
        if (on) {
            dir.mkdirs()
            val f = File(dir, "raw-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))}.csv")
            writer = f.bufferedWriter().apply { write(RawLog.HEADER + "\n") }
            file = f
        } else {
            runCatching { writer?.close() }
            writer = null
        }
    }

    @Synchronized
    fun fix(e: FixEvent) = append(RawLog.line(e))

    @Synchronized
    fun satellites(tNanos: Long, used: Int, inView: Int) = append("# gnss t=$tNanos used=$used view=$inView")

    private fun append(line: String) {
        val w = writer ?: return
        runCatching {
            w.write(line)
            w.write("\n")
            if (++lines % 10 == 0) w.flush() // a crash loses at most ~10 lines
        }
    }
}

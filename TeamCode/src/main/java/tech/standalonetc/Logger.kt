package tech.standalonetc

import android.annotation.TargetApi
import android.os.Build
import org.firstinspires.ftc.robotcore.internal.system.AppUtil
import java.io.File
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.locks.ReentrantLock

/**
 * 具有[name]名字的日志器
 */
class Logger(vararg names: String) {
    private val buffer = StringBuilder()
    private val name = "${names.joinToString("_")}.txt"
    private val file by lazy { File(currentLogPath.toString(), name) }
    private val lock = ReentrantLock()

    var period = 0x10

    /** 记录一行日志 */
    infix fun log(msg: Any?) {
        lock.lock()
        buffer.appendln("${SimpleDateFormat("HH:mm:ss:SSS").format(Date())}\t${System.currentTimeMillis()}\t$msg")
        lock.unlock()
        if (buffer.length >= period) flush()
    }

    /** 记录一行无时间的日志 */
    infix fun logWithoutStamp(msg: String) {
        lock.lock()
        buffer.appendln(msg)
        lock.unlock()
        if (buffer.length >= period) flush()
    }

    /** 记录一行日志，用制表符隔开 */
    fun log(vararg msg: Any?) = log(msg.joinToString("\t"))

    /** 清除缓存内容 */
    fun clear() {
        lock.lock()
        buffer.setLength(0)
        lock.unlock()
    }

    /** 清除日志文件内容 */
    fun forgetMemory() = file.writeText("")

    /** 缓存内容刷到文件 */
    fun flush() {
        lock.lock()
        val text = buffer.toString()
        buffer.setLength(0)
        lock.unlock()
        file.appendText(text)
    }

    @TargetApi(Build.VERSION_CODES.O)
    companion object {
        // 日志
        // 运行目录下创建log文件夹
        private val logPath: String =
            File(AppUtil.FIRST_FOLDER, "log")
                .also { if (!it.exists()) it.mkdir() }
                .toPath()
                .toString()

        // log文件夹下创建本次运行的文件夹
        private val currentLogPath: Path by lazy {
            File(logPath, SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(Date()))
                .also { if (!it.exists()) it.mkdir() }
                .toPath()
        }

        inline fun <reified T> logger(name: String) =
            Logger(T::class.java.simpleName, name)
    }
}

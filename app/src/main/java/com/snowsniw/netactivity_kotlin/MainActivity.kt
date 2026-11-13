
package com.snowsniw.netactivity_kotlin 

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.color.DynamicColors
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private enum class Mode { TCP, ICMP, SCAN }

    private lateinit var toggleMode: MaterialButtonToggleGroup
    private lateinit var tilHost: TextInputLayout
    private lateinit var tilPort: TextInputLayout
    private lateinit var tilCount: TextInputLayout
    private lateinit var etHost: TextInputEditText
    private lateinit var etPort: TextInputEditText
    private lateinit var etCount: TextInputEditText
    private lateinit var btnAction: MaterialButton

    private lateinit var tvStat1Value: TextView
    private lateinit var tvStat1Label: TextView
    private lateinit var tvStat2Value: TextView
    private lateinit var tvStat2Label: TextView
    private lateinit var tvStat3Value: TextView
    private lateinit var tvStat3Label: TextView

    private lateinit var scrollLog: ScrollView
    private lateinit var tvOutput: TextView

    private var currentMode = Mode.TCP
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newCachedThreadPool()

    @Volatile
    private var isRunning = false

    @Volatile
    private var activeProcess: Process? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupPresets()
        setupModeSwitcher()
        setupButtons()
        resetStatsForMode(Mode.TCP)
    }

    private fun bindViews() {
        toggleMode = findViewById(R.id.toggleMode)
        tilHost = findViewById(R.id.tilHost)
        tilPort = findViewById(R.id.tilPort)
        tilCount = findViewById(R.id.tilCount)
        etHost = findViewById(R.id.etHost)
        etPort = findViewById(R.id.etPort)
        etCount = findViewById(R.id.etCount)
        btnAction = findViewById(R.id.btnAction)

        tvStat1Value = findViewById(R.id.tvStat1Value)
        tvStat1Label = findViewById(R.id.tvStat1Label)
        tvStat2Value = findViewById(R.id.tvStat2Value)
        tvStat2Label = findViewById(R.id.tvStat2Label)
        tvStat3Value = findViewById(R.id.tvStat3Value)
        tvStat3Label = findViewById(R.id.tvStat3Label)

        scrollLog = findViewById(R.id.scrollLog)
        tvOutput = findViewById(R.id.tvOutput)
    }

    private fun setupPresets() {
        val chips = listOf(
            R.id.chipGoogleDns,
            R.id.chipCloudflare,
            R.id.chipGoogle,
            R.id.chipGithub,
            R.id.chipRouter
        )
        chips.forEach { id ->
            findViewById<Chip>(id).setOnClickListener { view ->
                val chip = view as Chip
                etHost.setText(chip.text.toString())
            }
        }
    }

    private fun setupModeSwitcher() {
        toggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            currentMode = when (checkedId) {
                R.id.btnModeIcmp -> Mode.ICMP
                R.id.btnModeScan -> Mode.SCAN
                else -> Mode.TCP
            }

            tilPort.isEnabled = currentMode == Mode.TCP
            tilCount.isEnabled = currentMode != Mode.SCAN
            resetStatsForMode(currentMode)
        }
    }

    private fun setupButtons() {
        btnAction.setOnClickListener {
            if (isRunning) stopTask() else startTask()
        }

        findViewById<MaterialButton>(R.id.btnCopy).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("NetActivity Log", tvOutput.text))
            Toast.makeText(this, "Лог скопирован", Toast.LENGTH_SHORT).show()
        }

        findViewById<MaterialButton>(R.id.btnClear).setOnClickListener {
            if (!isRunning) {
                tvOutput.text = "Консоль очищена."
                resetStatsForMode(currentMode)
            }
        }
    }

    private fun resetStatsForMode(mode: Mode) {
        tvStat1Value.text = "—"
        tvStat2Value.text = "—"
        tvStat3Value.text = "—"
        when (mode) {
            Mode.TCP, Mode.ICMP -> {
                tvStat1Label.text = "AVG (мс)"
                tvStat2Label.text = "MIN / MAX"
                tvStat3Label.text = "ПОТЕРИ"
            }
            Mode.SCAN -> {
                tvStat1Label.text = "ОТКРЫТО"
                tvStat2Label.text = "ЗАКРЫТО"
                tvStat3Label.text = "ВРЕМЯ"
            }
        }
    }

    private fun startTask() {
        val rawHost = etHost.text?.toString()?.trim().orEmpty()
        if (rawHost.isEmpty()) {
            tvOutput.text = "Ошибка: введи IP или домен."
            return
        }

        val host = rawHost
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .replace(Regex("[^a-zA-Z0-9.\\-:]"), "")

        val port = etPort.text?.toString()?.toIntOrNull()?.coerceIn(1, 65535) ?: 443
        val count = etCount.text?.toString()?.toIntOrNull()?.coerceIn(1, 100) ?: 4
        val mode = currentMode

        isRunning = true
        btnAction.text = "Остановить"
        resetStatsForMode(mode)
        tvOutput.text = "> Резолв DNS для $host...\n"

        executor.execute {
            try {
                val ip = InetAddress.getByName(host).hostAddress ?: host
                appendLog("> IP: $ip\n")

                when (mode) {
                    Mode.TCP -> runTcpPing(host, ip, port, count)
                    Mode.ICMP -> runIcmpPing(host, count)
                    Mode.SCAN -> runPortScan(host, ip)
                }
            } catch (e: Exception) {
                appendLog("\n[Ошибка]: ${e.message}")
            } finally {
                finishTask()
            }
        }
    }

    private fun runTcpPing(host: String, ip: String, port: Int, count: Int) {
        appendLog("TCP PING $host ($ip) port $port:")
        val times = mutableListOf<Double>()
        var transmitted = 0

        for (seq in 1..count) {
            if (!isRunning) break
            transmitted++

            try {
                val startNs = System.nanoTime()
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), 2500)
                }
                val ms = (System.nanoTime() - startNs) / 1_000_000.0
                times.add(ms)
                appendLog(String.format(Locale.US, "seq=%d  %s:%d  time=%.1f ms", seq, ip, port, ms))
            } catch (e: Exception) {
                appendLog("seq=%d  timeout/refused (%s)".format(Locale.US, seq, e.javaClass.simpleName))
            }

            updatePingStats(times, transmitted)
            if (seq < count && isRunning) Thread.sleep(600)
        }

        val loss = if (transmitted > 0) ((transmitted - times.size) * 100) / transmitted else 0
        appendLog("\n--- $host:$port summary ---")
        appendLog("$transmitted sent, ${times.size} ok, $loss% loss")
    }

    private fun runIcmpPing(host: String, count: Int) {
        val process = Runtime.getRuntime().exec(arrayOf("/system/bin/ping", "-c", count.toString(), host))
        activeProcess = process

        val times = mutableListOf<Double>()
        var transmitted = 0
        val timeRegex = Regex("time=([0-9.]+)")

        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null && isRunning) {
                val text = line ?: continue
                appendLog(text)

                val match = timeRegex.find(text)
                if (match != null) {
                    transmitted++
                    match.groupValues[1].toDoubleOrNull()?.let { times.add(it) }
                    updatePingStats(times, transmitted)
                }
            }
        }

        val exitCode = process.waitFor()
        if (times.isEmpty()) {
            updateStatsUI("—", "—", "100%")
        }
        appendLog("\n> Завершено (код: $exitCode)")
    }

    private fun runPortScan(host: String, ip: String) {
        val commonPorts = listOf(
            21 to "FTP",
            22 to "SSH",
            53 to "DNS",
            80 to "HTTP",
            443 to "HTTPS",
            3306 to "MySQL",
            8080 to "HTTP-Alt",
            25565 to "Minecraft"
        )

        appendLog("Сканирование ${commonPorts.size} портов на $host ($ip)...\n")
        val startScan = System.currentTimeMillis()
        var openCount = 0
        var closedCount = 0

        val tasks = commonPorts.map { (port, service) ->
            Callable {
                if (!isRunning) return@Callable
                val isOpen = try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(ip, port), 1200)
                        true
                    }
                } catch (_: Exception) {
                    false
                }

                synchronized(this) {
                    if (isOpen) {
                        openCount++
                        appendLog(String.format(Locale.US, "[OPEN]   %-6d (%s)", port, service))
                    } else {
                        closedCount++
                        appendLog(String.format(Locale.US, "[CLOSED] %-6d (%s)", port, service))
                    }
                    val elapsed = "${System.currentTimeMillis() - startScan} мс"
                    updateStatsUI(openCount.toString(), closedCount.toString(), elapsed)
                }
            }
        }

        executor.invokeAll(tasks)
        appendLog("\n> Сканирование завершено: открыто $openCount из ${commonPorts.size}.")
    }

    private fun updatePingStats(times: List<Double>, transmitted: Int) {
        val loss = if (transmitted > 0) ((transmitted - times.size) * 100) / transmitted else 0
        if (times.isNotEmpty()) {
            val avg = String.format(Locale.US, "%.1f", times.average())
            val min = times.minOrNull() ?: 0.0
            val max = times.maxOrNull() ?: 0.0
            val minMax = String.format(Locale.US, "%.0f / %.0f", min, max)
            updateStatsUI(avg, minMax, "$loss%")
        } else {
            updateStatsUI("—", "—", "$loss%")
        }
    }

    private fun updateStatsUI(s1: String, s2: String, s3: String) {
        mainHandler.post {
            tvStat1Value.text = s1
            tvStat2Value.text = s2
            tvStat3Value.text = s3
        }
    }

    private fun stopTask() {
        isRunning = false
        activeProcess?.destroy()
        appendLog("\n> Остановлено пользователем.")
    }

    private fun finishTask() {
        isRunning = false
        activeProcess = null
        mainHandler.post {
            btnAction.text = "Запустить проверку"
        }
    }

    private fun appendLog(text: String) {
        mainHandler.post {
            tvOutput.append("$text\n")
            scrollLog.post {
                scrollLog.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activeProcess?.destroy()
        executor.shutdownNow()
    }
}

package com.acer.batteryinsight.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class RootKeepAliveScriptTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val pkg = "com.acer.batteryinsight"
    private val service = "$pkg/com.acer.batteryinsight.service.BatteryInsightService"

    private fun run(vararg cmd: String, env: Map<String, String> = emptyMap()): Pair<Int, String> {
        val pb = ProcessBuilder(*cmd).redirectErrorStream(true)
        pb.environment().putAll(env)
        val p = pb.start()
        val out = p.inputStream.bufferedReader().readText()
        assertTrue("timed out", p.waitFor(20, TimeUnit.SECONDS))
        return p.exitValue() to out
    }

    private fun syntaxOk(script: String) {
        val f = tmp.newFile()
        f.writeText(script)
        val (code, out) = run("sh", "-n", f.absolutePath)
        assertEquals("sh -n failed: $out\n$script", 0, code)
    }

    @Test
    fun `generated scripts are valid shell`() {
        val dir = tmp.newFolder("files").absolutePath
        syntaxOk(RootKeepAlive.buildScript(dir, pkg, service))
        syntaxOk(RootKeepAlive.buildLaunchCommand(dir))
        syntaxOk(RootKeepAlive.buildStopCommand(dir))
        syntaxOk(RootKeepAlive.buildOemCommand(pkg))
    }

    @Test
    fun `watchdog restarts the service while the app is dead and exits when the flag is removed`() {
        val dir = tmp.newFolder("files")
        val bin = tmp.newFolder("bin")
        val amLog = File(tmp.root, "am.log")
        val flag = File(dir, "keepalive.enabled").apply { createNewFile() }

        // Stubs: app process is not running; `am` records calls; `sleep` ends the loop after two rounds.
        val pidofLog = File(tmp.root, "pidof.log")
        File(bin, "pidof").writeText("#!/bin/sh\necho \"\$@\" >> '${pidofLog.absolutePath}'\nexit 1\n")
        File(bin, "am").writeText("#!/bin/sh\necho \"\$@\" >> '${amLog.absolutePath}'\n")
        File(bin, "sleep").writeText(
            "#!/bin/sh\nn=\$(wc -l < '${amLog.absolutePath}')\n[ \"\$n\" -ge 2 ] && rm -f '${flag.absolutePath}'\nexit 0\n"
        )
        bin.listFiles()!!.forEach { it.setExecutable(true) }

        val script = File(dir, "keepalive.sh").apply { writeText(RootKeepAlive.buildScript(dir.absolutePath, pkg, service)) }
        val (code, out) = run("sh", script.absolutePath, env = mapOf("PATH" to "${bin.absolutePath}:/usr/bin:/bin"))

        assertEquals(out, 0, code)
        val calls = amLog.readLines()
        assertEquals(2, calls.size)
        assertEquals("start-foreground-service -n $service", calls.first())
        // The service lives in the ":monitor" process; checking the UI process would cause restarts.
        assertEquals("$pkg:monitor", pidofLog.readLines().first())
        assertFalse("flag should have been removed by the stub", flag.exists())
        assertFalse("pid file must be cleaned up on exit", File(dir, "keepalive.pid").exists())
    }

    @Test
    fun `watchdog does nothing while the app is alive`() {
        val dir = tmp.newFolder("files")
        val bin = tmp.newFolder("bin")
        val amLog = File(tmp.root, "am.log").apply { createNewFile() }
        val counter = File(tmp.root, "rounds")
        val flag = File(dir, "keepalive.enabled").apply { createNewFile() }

        File(bin, "pidof").writeText("#!/bin/sh\nexit 0\n")
        File(bin, "am").writeText("#!/bin/sh\necho \"\$@\" >> '${amLog.absolutePath}'\n")
        File(bin, "sleep").writeText(
            "#!/bin/sh\necho x >> '${counter.absolutePath}'\n[ \$(wc -l < '${counter.absolutePath}') -ge 3 ] && rm -f '${flag.absolutePath}'\nexit 0\n"
        )
        bin.listFiles()!!.forEach { it.setExecutable(true) }

        val script = File(dir, "keepalive.sh").apply { writeText(RootKeepAlive.buildScript(dir.absolutePath, pkg, service)) }
        val (code, out) = run("sh", script.absolutePath, env = mapOf("PATH" to "${bin.absolutePath}:/usr/bin:/bin"))

        assertEquals(out, 0, code)
        assertTrue("am must not be called while the app runs", amLog.readText().isEmpty())
    }
}

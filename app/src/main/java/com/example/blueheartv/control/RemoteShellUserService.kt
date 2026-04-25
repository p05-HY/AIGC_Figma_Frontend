package com.example.blueheartv.control

import android.os.Bundle
import android.os.Process

class RemoteShellUserService : IRemoteShellService.Stub() {

    override fun execute(command: String): Bundle {
        val process = ProcessBuilder("/system/bin/sh", "-c", command)
            .redirectErrorStream(false)
            .start()

        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        return Bundle().apply {
            putInt(KEY_EXIT_CODE, exitCode)
            putString(KEY_STDOUT, stdout)
            putString(KEY_STDERR, stderr)
        }
    }

    override fun destroy() {
        Process.killProcess(Process.myPid())
    }

    companion object {
        private const val KEY_EXIT_CODE = "exitCode"
        private const val KEY_STDOUT = "stdout"
        private const val KEY_STDERR = "stderr"
    }
}

package com.example.blueheartv.control

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

private const val TAG = "ShizukuAdbExecutor"

class ShizukuAdbExecutor(
    private val packageName: String
) {

    private val mutex = Mutex()

    @Volatile
    private var remoteService: IRemoteShellService? = null

    @Volatile
    private var serviceConnection: ServiceConnection? = null

    suspend fun execute(command: String): ShellResult = withContext(Dispatchers.IO) {
        ensurePermission()
        val service = awaitService()
        var bundle: Bundle? = null
        try {
            bundle = service.execute(command)
        } catch (ex: RemoteException) {
            Log.e(TAG, ex.message, ex)
        }
        ShellResult(
            exitCode = bundle?.getInt(KEY_EXIT_CODE) ?: -1,
            stdout = bundle?.getString(KEY_STDOUT).orEmpty(),
            stderr = bundle?.getString(KEY_STDERR).orEmpty()
        )
    }

    suspend fun destroy() {
        mutex.withLock {
            runCatching { remoteService?.destroy() }
            serviceConnection?.let { connection ->
                runCatching { Shizuku.unbindUserService(userServiceArgs(), connection, true) }
            }
            remoteService = null
            serviceConnection = null
        }
    }

    private suspend fun awaitService(): IRemoteShellService {
        remoteService?.let { return it }
        return mutex.withLock {
            remoteService?.let { return it }
            val deferred = CompletableDeferred<IRemoteShellService>()
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    val remote = IRemoteShellService.Stub.asInterface(service)
                    remoteService = remote
                    deferred.complete(remote)
                }

                override fun onServiceDisconnected(name: ComponentName) {
                    remoteService = null
                }
            }
            serviceConnection = connection
            Shizuku.bindUserService(userServiceArgs(), connection)
            deferred.await()
        }
    }

    private fun userServiceArgs(): Shizuku.UserServiceArgs {
        return Shizuku.UserServiceArgs(
            ComponentName(packageName, RemoteShellUserService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("adb")
            .tag("remote_shell")
            .version(1)
    }

    private fun ensurePermission() {
        check(!Shizuku.isPreV11()) { "Shizuku 版本过低。" }
        check(Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            "Shizuku 权限未授予。"
        }
    }

    companion object {
        private const val KEY_EXIT_CODE = "exitCode"
        private const val KEY_STDOUT = "stdout"
        private const val KEY_STDERR = "stderr"
    }
}

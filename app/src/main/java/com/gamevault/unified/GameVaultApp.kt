package com.gamevault.unified

import android.app.Application
import com.gamevault.unified.data.local.CrashLog
import com.gamevault.unified.data.local.LibraryCache
import com.gamevault.unified.data.local.MissingArtStore
import com.gamevault.unified.data.local.ServerStorage
import com.gamevault.unified.data.local.UiPrefs
import com.gamevault.unified.data.repository.GameRepository

class GameVaultApp : Application() {

    lateinit var serverStorage: ServerStorage
        private set

    lateinit var cache: LibraryCache
        private set

    lateinit var missingArt: MissingArtStore
        private set

    lateinit var uiPrefs: UiPrefs
        private set

    lateinit var repository: GameRepository
        private set

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        serverStorage = ServerStorage(applicationContext)
        cache = LibraryCache(applicationContext)
        missingArt = MissingArtStore(applicationContext)
        uiPrefs = UiPrefs(applicationContext)
        repository = GameRepository(serverStorage, cache, missingArt)
    }

    /**
     * Records uncaught exceptions in the local crash log before handing the
     * crash to the system handler (so Android still shows its dialog).
     */
    private fun installCrashLogger() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                CrashLog.record(applicationContext, thread, error)
            } catch (ignored: Throwable) {
                // Never mask the original crash.
            }
            previous?.uncaughtException(thread, error)
        }
    }
}

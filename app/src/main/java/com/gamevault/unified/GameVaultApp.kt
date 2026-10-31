package com.gamevault.unified

import android.app.Application
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
        serverStorage = ServerStorage(applicationContext)
        cache = LibraryCache(applicationContext)
        missingArt = MissingArtStore(applicationContext)
        uiPrefs = UiPrefs(applicationContext)
        repository = GameRepository(serverStorage, cache, missingArt)
    }
}

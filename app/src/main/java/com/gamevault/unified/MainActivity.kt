package com.gamevault.unified

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import coil.compose.LocalImageLoader
import com.gamevault.unified.data.remote.BridgeClient
import com.gamevault.unified.ui.navigation.AppNav
import com.gamevault.unified.ui.theme.GameVaultTheme
import com.gamevault.unified.ui.theme.appThemeFromName
import com.gamevault.unified.viewmodel.LibraryViewModel
import com.gamevault.unified.viewmodel.LibraryViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            val app = application as GameVaultApp
            val vm: LibraryViewModel = viewModel(
                factory = LibraryViewModelFactory(app.repository, app.serverStorage, app.uiPrefs)
            )
            // Cover art needs the Bearer token too, so the whole tree
            // uses an ImageLoader wired to the saved server config.
            val uiState by vm.ui.collectAsState()
            GameVaultTheme(appTheme = appThemeFromName(uiState.appThemeName), darkTheme = dark) {
                val loader = remember(uiState.server) {
                    val server = uiState.server
                    if (server != null) BridgeClient.imageLoader(this, server)
                    else ImageLoader(this)
                }
                CompositionLocalProvider(LocalImageLoader provides loader) {
                    AppNav(vm)
                }
            }
        }
    }
}

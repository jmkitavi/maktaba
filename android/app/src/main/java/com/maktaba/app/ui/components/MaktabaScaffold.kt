package com.maktaba.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.maktaba.app.ui.theme.MaktabaTheme

/**
 * Screen shell. It owns the background, the window insets and the snackbar host so that
 * individual screens stop improvising all three.
 *
 * The snackbar default is [remember]ed - it used to construct a fresh [SnackbarHostState]
 * on every recomposition, which silently discarded any snackbar shown through it.
 */
@Composable
fun MaktabaScaffold(
    modifier: Modifier = Modifier,
    selectedTab: BottomNavTab? = null,
    onTabSelected: (BottomNavTab) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    topBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaktabaTheme.colors.background,
        topBar = topBar,
        bottomBar = {
            selectedTab?.let { MaktabaBottomNav(it, onTabSelected) }
        },
        floatingActionButton = floatingActionButton,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        content = content
    )
}

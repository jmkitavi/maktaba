package com.maktaba.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.maktaba.app.ui.theme.CreamBackground

@Composable
fun MaktabaScaffold(
    modifier: Modifier = Modifier,
    selectedTab: BottomNavTab? = null,
    onTabSelected: (BottomNavTab) -> Unit = {},
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    topBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        containerColor = CreamBackground,
        topBar = topBar,
        bottomBar = {
            selectedTab?.let { BookHavenBottomNav(it, onTabSelected) }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        content = content
    )
}

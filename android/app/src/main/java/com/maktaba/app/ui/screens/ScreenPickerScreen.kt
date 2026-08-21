package com.maktaba.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.maktaba.app.nav.Routes
import com.maktaba.app.ui.components.MaktabaScaffold
import com.maktaba.app.ui.components.ScreenTopBar
import com.maktaba.app.ui.theme.MaktabaAppTheme
import com.maktaba.app.ui.theme.MaktabaShapes
import com.maktaba.app.ui.theme.MaktabaTheme
import com.maktaba.app.ui.theme.MinTouchTarget

/** Debug helper for jumping straight to a screen while screenshotting. */
@Composable
fun ScreenPickerScreen(navController: NavHostController) {
    val colors = MaktabaTheme.colors
    val spacing = MaktabaTheme.spacing
    MaktabaScaffold(
        topBar = { ScreenTopBar(title = "Screens") }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(colors.background)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(spacing.gutter),
                verticalArrangement = Arrangement.spacedBy(spacing.xs)
            ) {
                items(Routes.all) { route ->
                    Text(
                        route.route,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.ink,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = MinTouchTarget)
                            .clip(MaktabaShapes.small)
                            .background(colors.surface)
                            .clickable(role = Role.Button) { navController.navigate(route.route) }
                            .padding(spacing.sm)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Screen picker")
@Composable
private fun ScreenPickerScreenPreview() {
    MaktabaAppTheme { ScreenPickerScreen(navController = rememberNavController()) }
}

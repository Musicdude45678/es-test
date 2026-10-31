package com.reps.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.reps.app.ui.navigateTab

@Composable
fun RepsBottomBar(nav: NavHostController, current: String) {
    NavigationBar {
        NavigationBarItem(
            selected = current == Routes.HOME,
            onClick = { if (current != Routes.HOME) nav.navigateTab(Routes.HOME) },
            icon = { Icon(if (current == Routes.HOME) Icons.Filled.FitnessCenter else Icons.Outlined.FitnessCenter, contentDescription = null) },
            label = { Text("Train") }
        )
        NavigationBarItem(
            selected = current == Routes.HISTORY,
            onClick = { if (current != Routes.HISTORY) nav.navigateTab(Routes.HISTORY) },
            icon = { Icon(if (current == Routes.HISTORY) Icons.Filled.History else Icons.Outlined.History, contentDescription = null) },
            label = { Text("History") }
        )
        NavigationBarItem(
            selected = current == Routes.PROGRESS,
            onClick = { if (current != Routes.PROGRESS) nav.navigateTab(Routes.PROGRESS) },
            icon = { Icon(if (current == Routes.PROGRESS) Icons.Filled.TrendingUp else Icons.Outlined.TrendingUp, contentDescription = null) },
            label = { Text("Progress") }
        )
    }
}

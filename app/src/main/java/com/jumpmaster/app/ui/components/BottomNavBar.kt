package com.jumpmaster.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jumpmaster.app.ui.theme.AppTypography
import com.jumpmaster.app.ui.theme.BluePrimary
import com.jumpmaster.app.ui.theme.DarkBorder
import com.jumpmaster.app.ui.theme.DarkSurface

data class NavItem(
    val id: String,
    val icon: ImageVector,
    val label: String
)

val navItems = listOf(
    NavItem("home", Icons.Default.Home, "主页"),
    NavItem("debug", Icons.Default.Visibility, "调试"),
    NavItem("settings", Icons.Default.Settings, "设置")
)

@Composable
fun BottomNavBar(
    currentScreen: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkSurface)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        navItems.forEach { item ->
            val selected = currentScreen == item.id
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clickable { onNavigate(item.id) }
                    .padding(horizontal = 16.dp)
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = if (selected) BluePrimary else Color(0xFF666666),
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = item.label,
                    style = AppTypography.monoSmall,
                    color = if (selected) BluePrimary else Color(0xFF666666)
                )
            }
        }
    }
}

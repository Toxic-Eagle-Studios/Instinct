package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.NavTab
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.DarkCharcoal
import com.example.ui.theme.ElectricLime
import com.example.ui.theme.GraphiteBorder
import com.example.ui.theme.JetBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

private data class BottomNavDestination(
  val tab: NavTab,
  val title: String,
  val icon: ImageVector,
  val testTag: String,
  val badgeCount: Int = 0
)

@Composable
fun AppNavBar(
  selectedTab: NavTab,
  onTabSelected: (NavTab) -> Unit,
  activeReminderCount: Int = 0,
  modifier: Modifier = Modifier
) {
  val destinations = listOf(
    BottomNavDestination(
      tab = NavTab.HOME,
      title = "Home",
      icon = Icons.Default.Home,
      testTag = "nav_home"
    ),
    BottomNavDestination(
      tab = NavTab.BRIEFING,
      title = "Briefing",
      icon = Icons.Default.AutoAwesome,
      testTag = "nav_briefing"
    ),
    BottomNavDestination(
      tab = NavTab.REMINDERS,
      title = "At It Hub",
      icon = Icons.Default.NotificationsActive,
      testTag = "nav_reminders",
      badgeCount = activeReminderCount
    ),
    BottomNavDestination(
      tab = NavTab.FIXES,
      title = "Undo IRL",
      icon = Icons.Default.Build,
      testTag = "nav_fixes"
    ),
    BottomNavDestination(
      tab = NavTab.MEMORIES,
      title = "Vault",
      icon = Icons.Default.Bookmark,
      testTag = "nav_memories"
    ),
    BottomNavDestination(
      tab = NavTab.SETTINGS,
      title = "Settings",
      icon = Icons.Default.Settings,
      testTag = "nav_settings"
    )
  )

  NavigationBar(
    modifier = modifier
      .fillMaxWidth()
      .border(0.5.dp, GraphiteBorder)
      .testTag("main_bottom_navigation_bar"),
    containerColor = DarkCharcoal,
    contentColor = TextPrimary,
    tonalElevation = 0.dp
  ) {
    destinations.forEach { dest ->
      val isSelected = selectedTab == dest.tab
      NavigationBarItem(
        selected = isSelected,
        onClick = { onTabSelected(dest.tab) },
        modifier = Modifier.testTag(dest.testTag),
        icon = {
          if (dest.badgeCount > 0) {
            BadgedBox(
              badge = {
                Badge(
                  containerColor = CyanElectric,
                  contentColor = JetBlack
                ) {
                  Text(
                    text = dest.badgeCount.coerceAtMost(99).toString(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                  )
                }
              }
            ) {
              Icon(
                imageVector = dest.icon,
                contentDescription = dest.title,
                modifier = Modifier.size(20.dp)
              )
            }
          } else {
            Icon(
              imageVector = dest.icon,
              contentDescription = dest.title,
              modifier = Modifier.size(20.dp)
            )
          }
        },
        label = {
          Text(
            text = dest.title,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        },
        colors = NavigationBarItemDefaults.colors(
          selectedIconColor = ElectricLime,
          selectedTextColor = TextPrimary,
          indicatorColor = ElectricLime.copy(alpha = 0.16f),
          unselectedIconColor = TextMuted,
          unselectedTextColor = TextMuted
        )
      )
    }
  }
}

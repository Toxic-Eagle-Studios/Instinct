package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AiModelTier
import com.example.ui.components.AppNavBar
import com.example.ui.components.QuickMemoryEntryFab
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.DailyBriefingScreen
import com.example.ui.screens.FixesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MemoriesScreen
import com.example.ui.screens.RemindersScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AmberNotice
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.DarkCharcoal
import com.example.ui.theme.ElectricLime
import com.example.ui.theme.GraphiteBorder
import com.example.ui.theme.GraphiteCard
import com.example.ui.theme.GraphiteSurface
import com.example.ui.theme.JetBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private data class DrawerNavItem(
  val tab: NavTab,
  val label: String,
  val subtitle: String,
  val icon: ImageVector,
  val testTag: String,
  val countBadge: Int? = null
)

@Composable
fun AppNavigation(
  viewModel: InstinctViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

  val currentUser by viewModel.authRepository.currentUser.collectAsState()
  val isGuestMode by viewModel.authRepository.isGuestMode.collectAsState()
  val currentTab by viewModel.currentTab.collectAsState()
  val userMessage by viewModel.userMessage.collectAsState()
  val reminders by viewModel.reminders.collectAsState()
  val preferences by viewModel.preferences.collectAsState()
  val fixes by viewModel.fixes.collectAsState()
  val activeContextSession by viewModel.activeContextSession.collectAsState()
  val selectedAiModel by viewModel.selectedAiModel.collectAsState()
  val searchGroundingEnabled by viewModel.searchGroundingEnabled.collectAsState()
  val mapsGroundingEnabled by viewModel.mapsGroundingEnabled.collectAsState()
  val liveVoiceModeActive by viewModel.liveVoiceModeActive.collectAsState()

  val snackbarHostState = remember { SnackbarHostState() }
  val activeReminderCount = remember(reminders) { reminders.count { !it.isCompleted } }

  LaunchedEffect(userMessage) {
    userMessage?.let {
      snackbarHostState.showSnackbar(it)
      viewModel.dismissUserMessage()
    }
  }

  // Gate feature screens behind authentication or guest mode
  if (currentUser == null && !isGuestMode) {
    AuthScreen(
      viewModel = viewModel,
      onGuestSelected = {
        viewModel.setTab(NavTab.HOME)
      }
    )
  } else {
    // BackHandler: close drawer first if open, otherwise navigate back to Home if on secondary tab
    if (drawerState.isOpen) {
      BackHandler {
        scope.launch { drawerState.close() }
      }
    } else if (currentTab != NavTab.HOME) {
      BackHandler {
        viewModel.setTab(NavTab.HOME)
      }
    }

    val drawerItems = listOf(
      DrawerNavItem(
        tab = NavTab.HOME,
        label = "Shared Brain Home",
        subtitle = "Universal input, AI chat & voice",
        icon = Icons.Default.Home,
        testTag = "drawer_nav_home"
      ),
      DrawerNavItem(
        tab = NavTab.BRIEFING,
        label = "Daily Briefing",
        subtitle = "Smart memory synthesis & upcoming tasks",
        icon = Icons.Default.AutoAwesome,
        testTag = "drawer_nav_briefing",
        countBadge = activeReminderCount
      ),
      DrawerNavItem(
        tab = NavTab.REMINDERS,
        label = "While You're At It Hub",
        subtitle = "Contextual reminders & errand chains",
        icon = Icons.Default.NotificationsActive,
        testTag = "drawer_nav_while_youre_at_it",
        countBadge = activeReminderCount
      ),
      DrawerNavItem(
        tab = NavTab.FIXES,
        label = "Undo IRL · Fix History",
        subtitle = "Digital & physical recovery guides",
        icon = Icons.Default.Build,
        testTag = "drawer_nav_fixes",
        countBadge = fixes.size
      ),
      DrawerNavItem(
        tab = NavTab.MEMORIES,
        label = "Exactly · Memory Vault",
        subtitle = "Saved preferences & unified records",
        icon = Icons.Default.Bookmark,
        testTag = "drawer_nav_memories",
        countBadge = preferences.size
      ),
      DrawerNavItem(
        tab = NavTab.SETTINGS,
        label = "Settings, Sync & Backup",
        subtitle = "SQL, Cloud Sync, JSON Export/Import",
        icon = Icons.Default.Settings,
        testTag = "drawer_nav_settings"
      )
    )

    ModalNavigationDrawer(
      drawerState = drawerState,
      gesturesEnabled = true,
      drawerContent = {
        ModalDrawerSheet(
          drawerContainerColor = DarkCharcoal,
          drawerContentColor = TextPrimary,
          modifier = Modifier
            .width(316.dp)
            .testTag("instinct_navigation_drawer")
        ) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 14.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            // Drawer Brand Header
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ElectricLime.copy(alpha = 0.16f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.Psychology,
                    contentDescription = null,
                    tint = ElectricLime,
                    modifier = Modifier.size(22.dp)
                  )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                      text = "INSTINCT",
                      fontSize = 18.sp,
                      fontWeight = FontWeight.Black,
                      color = TextPrimary,
                      letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                      modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(ElectricLime)
                    )
                  }
                  Text(
                    text = "Shared Brain Navigation",
                    fontSize = 11.sp,
                    color = TextSecondary
                  )
                }
              }

              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(8.dp))
                  .background(CyanElectric.copy(alpha = 0.15f))
                  .padding(horizontal = 8.dp, vertical = 4.dp)
              ) {
                Text(
                  text = selectedAiModel.badge,
                  fontSize = 9.sp,
                  fontWeight = FontWeight.ExtraBold,
                  color = CyanElectric
                )
              }
            }

            HorizontalDivider(color = GraphiteBorder, modifier = Modifier.padding(vertical = 4.dp))

            Text(
              text = "CORE WORKSPACES",
              fontSize = 10.sp,
              fontWeight = FontWeight.Black,
              color = TextMuted,
              letterSpacing = 0.9.sp,
              modifier = Modifier.padding(horizontal = 8.dp)
            )

            drawerItems.forEach { item ->
              val selected = currentTab == item.tab
              NavigationDrawerItem(
                selected = selected,
                onClick = {
                  viewModel.setTab(item.tab)
                  scope.launch { drawerState.close() }
                },
                modifier = Modifier.testTag(item.testTag),
                icon = {
                  Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = if (selected) ElectricLime else TextSecondary
                  )
                },
                label = {
                  Column {
                    Text(
                      text = item.label,
                      fontSize = 13.sp,
                      fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                      color = if (selected) TextPrimary else TextSecondary
                    )
                    Text(
                      text = item.subtitle,
                      fontSize = 10.sp,
                      color = TextMuted
                    )
                  }
                },
                badge = {
                  if (item.countBadge != null && item.countBadge > 0) {
                    Box(
                      modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) ElectricLime else GraphiteSurface)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                      Text(
                        text = item.countBadge.toString(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) JetBlack else TextSecondary
                      )
                    }
                  }
                },
                colors = NavigationDrawerItemDefaults.colors(
                  selectedContainerColor = ElectricLime.copy(alpha = 0.14f),
                  unselectedContainerColor = Color.Transparent
                )
              )
            }

            HorizontalDivider(color = GraphiteBorder, modifier = Modifier.padding(vertical = 6.dp))

            // Quick AI & Grounding Controls inside the Drawer
            Text(
              text = "AI & GROUNDING TOOLS",
              fontSize = 10.sp,
              fontWeight = FontWeight.Black,
              color = TextMuted,
              letterSpacing = 0.9.sp,
              modifier = Modifier.padding(horizontal = 8.dp)
            )

            // Hands-Free Voice Mode (Device Speech + TTS) Quick Launch
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (liveVoiceModeActive) ElectricLime.copy(alpha = 0.18f) else GraphiteCard)
                .border(
                  1.dp,
                  if (liveVoiceModeActive) ElectricLime else GraphiteBorder,
                  RoundedCornerShape(12.dp)
                )
                .clickable {
                  viewModel.setLiveVoiceModeActive(true)
                  if (!viewModel.isChatPanelOpen.value) {
                    viewModel.toggleChatPanel()
                  }
                  viewModel.setTab(NavTab.HOME)
                  scope.launch { drawerState.close() }
                }
                .padding(12.dp)
                .testTag("drawer_launch_live_voice_button")
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = ElectricLime,
                    modifier = Modifier.size(18.dp)
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Column {
                    Text(
                      text = "Hands-Free Voice Mode",
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold,
                      color = TextPrimary
                    )
                    Text(
                      text = "Device Speech-to-Text + Gemini Streaming + TTS",
                      fontSize = 10.sp,
                      color = TextSecondary
                    )
                  }
                }
                Text(
                  text = if (liveVoiceModeActive) "ON" else "START",
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Black,
                  color = ElectricLime
                )
              }
            }

            // Open Active Context in Google Maps
            val activePlaceQuery = activeContextSession?.contextLabel ?: "Grocery Store"
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GraphiteCard)
                .border(1.dp, CyanElectric.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .clickable {
                  openPlaceInGoogleMaps(context, activePlaceQuery)
                  scope.launch { drawerState.close() }
                }
                .padding(12.dp)
                .testTag("drawer_open_google_maps_button")
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.Map,
                    contentDescription = null,
                    tint = CyanElectric,
                    modifier = Modifier.size(18.dp)
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Column {
                    Text(
                      text = "Open Errand in Google Maps",
                      fontSize = 12.sp,
                      fontWeight = FontWeight.Bold,
                      color = TextPrimary
                    )
                    Text(
                      text = "Target: $activePlaceQuery",
                      fontSize = 10.sp,
                      color = CyanElectric
                    )
                  }
                }
                Icon(
                  imageVector = Icons.Default.Place,
                  contentDescription = null,
                  tint = CyanElectric,
                  modifier = Modifier.size(16.dp)
                )
              }
            }

            // Contextual Grounding & Maps Launch Toggles
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GraphiteCard)
                .border(1.dp, GraphiteBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = CyanElectric,
                    modifier = Modifier.size(15.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "Contextual Grounding (Prompt)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                  )
                }
                Switch(
                  checked = searchGroundingEnabled,
                  onCheckedChange = { viewModel.setSearchGroundingEnabled(it) },
                  colors = SwitchDefaults.colors(
                    checkedThumbColor = JetBlack,
                    checkedTrackColor = CyanElectric
                  )
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = AmberNotice,
                    modifier = Modifier.size(15.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "Place & Errand Context Hints",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                  )
                }
                Switch(
                  checked = mapsGroundingEnabled,
                  onCheckedChange = { viewModel.setMapsGroundingEnabled(it) },
                  colors = SwitchDefaults.colors(
                    checkedThumbColor = JetBlack,
                    checkedTrackColor = AmberNotice
                  )
                )
              }
            }
          }
        }
      }
    ) {
      Scaffold(
        modifier = modifier
          .fillMaxSize()
          .background(JetBlack),
        containerColor = JetBlack,
        floatingActionButton = {
          QuickMemoryEntryFab(viewModel = viewModel)
        },
        bottomBar = {
          AppNavBar(
            selectedTab = currentTab,
            onTabSelected = { viewModel.setTab(it) },
            activeReminderCount = activeReminderCount
          )
        },
        snackbarHost = {
          SnackbarHost(hostState = snackbarHostState)
        }
      ) { innerPadding ->
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .background(JetBlack)
        ) {
          val openDrawerAction: () -> Unit = {
            scope.launch { drawerState.open() }
          }

          AnimatedContent(
            targetState = currentTab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "screen_transition"
          ) { tab ->
            when (tab) {
              NavTab.HOME -> HomeScreen(
                viewModel = viewModel,
                onOpenDrawer = openDrawerAction
              )
              NavTab.BRIEFING -> DailyBriefingScreen(
                viewModel = viewModel,
                onOpenDrawer = openDrawerAction
              )
              NavTab.REMINDERS -> RemindersScreen(viewModel = viewModel)
              NavTab.FIXES -> FixesScreen(viewModel = viewModel)
              NavTab.MEMORIES -> MemoriesScreen(viewModel = viewModel)
              NavTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
            }
          }
        }
      }
    }
  }
}

/**
 * Launches Google Maps (or browser fallback) for a given place or errand context query.
 */
fun openPlaceInGoogleMaps(context: android.content.Context, placeQuery: String) {
  val cleanQuery = placeQuery
    .replace(Regex("(?i)^(i['’]?m |i am |heading to |going to |at |visiting )"), "")
    .trim()
    .ifBlank { "Supermarket" }
  val encoded = Uri.encode(cleanQuery)
  val geoIntent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encoded")).apply {
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }
  try {
    context.startActivity(geoIntent)
  } catch (_: Exception) {
    try {
      val webIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://www.google.com/maps/search/?api=1&query=$encoded")
      ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(webIntent)
    } catch (_: Exception) {
      // No external handler available in headless test environment
    }
  }
}

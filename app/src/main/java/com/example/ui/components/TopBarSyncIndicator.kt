package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.SyncStatus
import com.example.ui.theme.AmberNotice
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.ElectricLime
import com.example.ui.theme.GraphiteSurface

/**
 * Subtle, high-legibility status indicator for the Material 3 TopAppBar and headers.
 * Keeps users informed about synchronization state (Online / Offline / Syncing / Pending Outbox).
 */
@Composable
fun TopBarSyncIndicator(
  syncStatus: SyncStatus,
  isSignedIn: Boolean = false,
  onClick: (() -> Unit)? = null,
  testTag: String = "top_app_bar_sync_indicator",
  modifier: Modifier = Modifier
) {
  val uiModel = resolveSyncUiModel(syncStatus = syncStatus, isSignedIn = isSignedIn)

  Box(
    modifier = modifier
      .clip(RoundedCornerShape(10.dp))
      .background(GraphiteSurface)
      .border(1.dp, uiModel.accentColor.copy(alpha = 0.38f), RoundedCornerShape(10.dp))
      .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
      .padding(horizontal = 8.dp, vertical = 5.dp)
      .semantics {
        contentDescription = "Synchronization status: ${uiModel.accessibilityLabel}"
      }
      .testTag(testTag)
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      Box(
        modifier = Modifier
          .size(6.dp)
          .clip(CircleShape)
          .background(uiModel.accentColor)
      )
      Spacer(modifier = Modifier.width(5.dp))
      Icon(
        imageVector = uiModel.icon,
        contentDescription = null,
        tint = uiModel.accentColor,
        modifier = Modifier.size(13.dp)
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = uiModel.badgeLabel,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = uiModel.accentColor,
        letterSpacing = 0.2.sp
      )
    }
  }
}

internal data class SyncIndicatorUiModel(
  val badgeLabel: String,
  val accessibilityLabel: String,
  val icon: ImageVector,
  val accentColor: Color
)

internal fun resolveSyncUiModel(
  syncStatus: SyncStatus,
  isSignedIn: Boolean
): SyncIndicatorUiModel {
  return when (syncStatus) {
    is SyncStatus.Synced -> SyncIndicatorUiModel(
      badgeLabel = "Online",
      accessibilityLabel = "Online and synced (${syncStatus.itemCount} items)",
      icon = Icons.Default.CloudDone,
      accentColor = ElectricLime
    )
    is SyncStatus.Syncing -> SyncIndicatorUiModel(
      badgeLabel = "Syncing",
      accessibilityLabel = "Synchronizing memories with cloud",
      icon = Icons.Default.CloudSync,
      accentColor = AmberNotice
    )
    is SyncStatus.Offline -> SyncIndicatorUiModel(
      badgeLabel = "Offline",
      accessibilityLabel = "Offline — data safely stored on device",
      icon = Icons.Default.CloudOff,
      accentColor = CyanElectric
    )
    is SyncStatus.Error -> SyncIndicatorUiModel(
      badgeLabel = "Offline · Queued",
      accessibilityLabel = "Offline with local changes queued for sync",
      icon = Icons.Default.CloudQueue,
      accentColor = AmberNotice
    )
    is SyncStatus.Idle -> if (isSignedIn) {
      SyncIndicatorUiModel(
        badgeLabel = "Online",
        accessibilityLabel = "Online and ready to sync",
        icon = Icons.Default.CloudDone,
        accentColor = ElectricLime
      )
    } else {
      SyncIndicatorUiModel(
        badgeLabel = "Offline",
        accessibilityLabel = "Offline local device storage active",
        icon = Icons.Default.CloudOff,
        accentColor = CyanElectric
      )
    }
  }
}

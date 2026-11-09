package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.NavTab
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.DarkCharcoal
import com.example.ui.theme.ElectricLime
import com.example.ui.theme.GraphiteBorder
import com.example.ui.theme.GraphiteCard
import com.example.ui.theme.GraphiteSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ToxicGreenGlow

@Composable
fun FeatureCardsRow(
  onSelectTab: (NavTab) -> Unit,
  onPrefillPrompt: (String) -> Unit,
  modifier: Modifier = Modifier,
  remindersCount: Int = 0,
  fixesCount: Int = 0,
  preferencesCount: Int = 0
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    FeatureCardItem(
      title = "WHILE YOU'RE AT IT",
      subtitle = "Remember the right thing at the right moment.",
      badge = "Contextual Reminders",
      countText = if (remindersCount > 0) "$remindersCount Active" else "Ready",
      icon = Icons.Default.NotificationsActive,
      accentColor = ElectricLime,
      onClick = { onSelectTab(NavTab.REMINDERS) },
      exampleText = "Try: \"Heading to Walmart...\"",
      onExampleClick = { onPrefillPrompt("Next time I go to the store, remind me to buy batteries") },
      primaryTestTag = "feature_card_while_youre_at_it"
    )

    FeatureCardItem(
      title = "UNDO IRL",
      subtitle = "When something goes wrong, find your way back.",
      badge = "Problem Recovery",
      countText = if (fixesCount > 0) "$fixesCount Solved" else "Ready",
      icon = Icons.Default.Build,
      accentColor = ToxicGreenGlow,
      onClick = { onSelectTab(NavTab.FIXES) },
      exampleText = "Try: \"Oversalted my soup...\"",
      onExampleClick = { onPrefillPrompt("I accidentally oversalted my soup") },
      primaryTestTag = "feature_card_undo_irl"
    )

    FeatureCardItem(
      title = "EXACTLY",
      subtitle = "Everything you like, exactly how you like it.",
      badge = "Personal Memory",
      countText = if (preferencesCount > 0) "$preferencesCount Stored" else "Ready",
      icon = Icons.Default.Bookmark,
      accentColor = CyanElectric,
      onClick = { onSelectTab(NavTab.MEMORIES) },
      exampleText = "Try: \"My coffee order...\"",
      onExampleClick = { onPrefillPrompt("Remember my haircut: 2 on the sides, scissor cut on top") },
      primaryTestTag = "feature_card_exactly"
    )
  }
}

@Composable
fun FeatureCardItem(
  title: String,
  subtitle: String,
  badge: String,
  countText: String,
  icon: ImageVector,
  accentColor: androidx.compose.ui.graphics.Color,
  onClick: () -> Unit,
  exampleText: String,
  onExampleClick: () -> Unit,
  primaryTestTag: String,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .background(GraphiteCard)
      .border(1.dp, GraphiteBorder, RoundedCornerShape(16.dp))
      .clickable(onClick = onClick)
      .testTag(primaryTestTag)
      .padding(14.dp)
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accentColor.copy(alpha = 0.12f))
            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = icon,
            contentDescription = title,
            tint = accentColor,
            modifier = Modifier.size(24.dp)
          )
        }

        Spacer(modifier = Modifier.size(12.dp))

        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = title,
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary,
              letterSpacing = 0.8.sp
            )

            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(accentColor.copy(alpha = 0.15f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
              Text(
                text = countText,
                fontSize = 10.sp,
                color = accentColor,
                fontWeight = FontWeight.Bold
              )
            }
          }

          Spacer(modifier = Modifier.height(2.dp))

          Text(
            text = subtitle,
            fontSize = 12.sp,
            color = TextSecondary,
            lineHeight = 16.sp
          )
        }

        Spacer(modifier = Modifier.size(8.dp))

        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowForward,
          contentDescription = "Open $title",
          tint = TextMuted,
          modifier = Modifier.size(18.dp)
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Bottom Row with Badge and Quick Example Tap
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(GraphiteSurface)
            .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
          Text(
            text = badge,
            fontSize = 10.sp,
            color = accentColor,
            fontWeight = FontWeight.SemiBold
          )
        }

        Box(
          modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(DarkCharcoal)
            .clickable(onClick = onExampleClick)
            .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
          Text(
            text = exampleText,
            fontSize = 11.sp,
            color = TextMuted
          )
        }
      }
    }
  }
}

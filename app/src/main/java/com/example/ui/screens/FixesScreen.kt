package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Fix
import com.example.ui.InstinctViewModel
import com.example.ui.NavTab
import com.example.ui.theme.DarkCharcoal
import com.example.ui.theme.DeepRedWarning
import com.example.ui.theme.ElectricLime
import com.example.ui.theme.GraphiteBorder
import com.example.ui.theme.GraphiteCard
import com.example.ui.theme.GraphiteSurface
import com.example.ui.theme.JetBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ToxicGreenGlow

@Composable
fun FixesScreen(
  viewModel: InstinctViewModel,
  modifier: Modifier = Modifier
) {
  val fixes by viewModel.fixes.collectAsState()
  var searchQuery by remember { mutableStateOf("") }

  val filteredFixes = fixes.filter {
    searchQuery.isBlank() ||
      it.problemSummary.contains(searchQuery, ignoreCase = true) ||
      it.bestFix.contains(searchQuery, ignoreCase = true) ||
      it.whatHappened.contains(searchQuery, ignoreCase = true)
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(JetBlack)
      .statusBarsPadding()
      .padding(horizontal = 18.dp, vertical = 12.dp)
  ) {
    // Header
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Column {
        Text(
          text = "UNDO IRL",
          fontSize = 20.sp,
          fontWeight = FontWeight.ExtraBold,
          color = ToxicGreenGlow,
          letterSpacing = 1.sp
        )
        Text(
          text = "Everyday problem recovery & verified solutions",
          fontSize = 12.sp,
          color = TextSecondary
        )
      }

      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(ToxicGreenGlow.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Build,
          contentDescription = null,
          tint = ToxicGreenGlow,
          modifier = Modifier.size(20.dp)
        )
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Search bar
    OutlinedTextField(
      value = searchQuery,
      onValueChange = { searchQuery = it },
      leadingIcon = {
        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = TextMuted)
      },
      placeholder = { Text("Search recorded fixes (e.g., 'soup', 'stain')...", fontSize = 13.sp, color = TextMuted) },
      modifier = Modifier
        .fillMaxWidth()
        .testTag("fixes_search_input"),
      shape = RoundedCornerShape(12.dp),
      colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = GraphiteCard,
        unfocusedContainerColor = GraphiteCard,
        focusedBorderColor = ToxicGreenGlow,
        unfocusedBorderColor = GraphiteBorder,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary
      ),
      singleLine = true
    )

    Spacer(modifier = Modifier.height(14.dp))

    // Quick Problem Diagnosis Shortcuts
    Text(
      text = "SOMETHING GO SIDEWAYS? QUICK RECOVERY SHORTCUTS",
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      color = TextMuted,
      letterSpacing = 0.8.sp
    )
    Spacer(modifier = Modifier.height(6.dp))

    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      QuickFixPill(label = "Deleted a photo by accident", onClick = {
        viewModel.setTab(NavTab.HOME)
        viewModel.submitUniversalInput("I deleted a photo by accident")
      })
      QuickFixPill(label = "Sent message to wrong person", onClick = {
        viewModel.setTab(NavTab.HOME)
        viewModel.submitUniversalInput("I sent a message to the wrong person")
      })
      QuickFixPill(label = "Overwrote a document", onClick = {
        viewModel.setTab(NavTab.HOME)
        viewModel.submitUniversalInput("I overwrote a document")
      })
      QuickFixPill(label = "Messed up a setting", onClick = {
        viewModel.setTab(NavTab.HOME)
        viewModel.submitUniversalInput("I messed up a setting")
      })
      QuickFixPill(label = "Oversalted soup", onClick = {
        viewModel.setTab(NavTab.HOME)
        viewModel.submitUniversalInput("I put too much salt in my soup")
      })
      QuickFixPill(label = "Grease stain", onClick = {
        viewModel.setTab(NavTab.HOME)
        viewModel.submitUniversalInput("I spilled grease on my shirt")
      })
      QuickFixPill(label = "Stuck zipper", onClick = {
        viewModel.setTab(NavTab.HOME)
        viewModel.submitUniversalInput("My zipper is stuck")
      })
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Verified Fixes List
    Text(
      text = "RECORDED FIX HISTORY (${filteredFixes.size})",
      fontSize = 11.sp,
      fontWeight = FontWeight.Bold,
      color = TextMuted,
      letterSpacing = 0.8.sp
    )

    Spacer(modifier = Modifier.height(8.dp))

    if (filteredFixes.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Icon(
            imageVector = Icons.Default.Build,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(44.dp)
          )
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "No saved fixes yet",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Whenever a solution works, tap \"That Worked\" and INSTINCT will remember it forever.",
            fontSize = 12.sp,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        items(filteredFixes, key = { it.id }) { fix ->
          SavedFixCard(
            fix = fix,
            onDelete = { viewModel.deleteFix(fix.id) }
          )
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
      }
    }
  }
}

@Composable
fun QuickFixPill(
  label: String,
  onClick: () -> Unit
) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(16.dp))
      .background(GraphiteCard)
      .border(1.dp, GraphiteBorder, RoundedCornerShape(16.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 6.dp)
  ) {
    Text(
      text = label,
      fontSize = 11.sp,
      color = ToxicGreenGlow,
      fontWeight = FontWeight.Medium
    )
  }
}

@Composable
fun SavedFixCard(
  fix: Fix,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier
) {
  var isExpanded by remember { mutableStateOf(false) }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .background(GraphiteCard)
      .border(1.dp, GraphiteBorder, RoundedCornerShape(14.dp))
      .padding(14.dp)
      .testTag("fix_item_${fix.id}")
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = fix.problemSummary,
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = TextPrimary
            )
            if (fix.isVerifiedWorking) {
              Spacer(modifier = Modifier.width(6.dp))
              Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Verified working",
                tint = ElectricLime,
                modifier = Modifier.size(16.dp)
              )
            }
          }

          if (fix.whatHappened.isNotBlank()) {
            Text(
              text = fix.whatHappened,
              fontSize = 12.sp,
              color = TextSecondary,
              modifier = Modifier.padding(top = 2.dp)
            )
          }
        }

        IconButton(
          onClick = onDelete,
          modifier = Modifier.size(28.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Delete fix",
            tint = DeepRedWarning.copy(alpha = 0.8f),
            modifier = Modifier.size(18.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(8.dp))
          .background(DarkCharcoal)
          .padding(10.dp)
      ) {
        Column {
          Text(
            text = "VERIFIED SOLUTION:",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = ToxicGreenGlow
          )
          Text(
            text = fix.bestFix,
            fontSize = 13.sp,
            color = TextPrimary
          )
        }
      }

      if (fix.steps.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = if (isExpanded) "Hide detailed steps" else "Show ${fix.steps.size} step-by-step instructions",
            fontSize = 12.sp,
            color = ToxicGreenGlow,
            fontWeight = FontWeight.Medium
          )
          Icon(
            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = ToxicGreenGlow,
            modifier = Modifier.size(18.dp)
          )
        }

        AnimatedVisibility(visible = isExpanded) {
          Column(modifier = Modifier.padding(top = 8.dp)) {
            fix.steps.forEachIndexed { index, step ->
              Row(
                modifier = Modifier.padding(bottom = 6.dp),
                verticalAlignment = Alignment.Top
              ) {
                Text(
                  text = "${index + 1}. ",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = ToxicGreenGlow
                )
                Text(
                  text = step,
                  fontSize = 12.sp,
                  color = TextPrimary,
                  lineHeight = 16.sp
                )
              }
            }
          }
        }
      }
    }
  }
}

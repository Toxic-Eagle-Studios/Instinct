package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RepairSessionState
import com.example.data.model.RepairStatus
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InteractiveRepairCard(
  sessionState: RepairSessionState,
  onNextStep: () -> Unit,
  onPrevStep: () -> Unit,
  onWorked: () -> Unit,
  onDidNotWork: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  val fix = sessionState.activeFix

  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(18.dp))
      .background(GraphiteCard)
      .border(1.5.dp, ToxicGreenGlow, RoundedCornerShape(18.dp))
      .padding(16.dp)
      .testTag("interactive_repair_card")
  ) {
    Column {
      // Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Default.Build,
            contentDescription = "Undo IRL",
            tint = ToxicGreenGlow,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "UNDO IRL",
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = ToxicGreenGlow,
            letterSpacing = 0.8.sp
          )
        }

        IconButton(
          onClick = onDismiss,
          modifier = Modifier.size(28.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Dismiss fix",
            tint = TextMuted,
            modifier = Modifier.size(18.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(6.dp))

      // Problem Summary
      Text(
        text = fix.problemSummary,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
      )

      // What Happened
      if (fix.whatHappened.isNotBlank()) {
        Text(
          text = fix.whatHappened,
          fontSize = 13.sp,
          color = TextSecondary,
          modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )
      }

      // Best Fix Highlight
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(10.dp))
          .background(DarkCharcoal)
          .border(1.dp, GraphiteBorder, RoundedCornerShape(10.dp))
          .padding(10.dp)
      ) {
        Column {
          Text(
            text = "BEST FIX",
            fontSize = 11.sp,
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

      // Tools Needed
      if (fix.toolsNeeded.isNotEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
          text = "WHAT YOU'LL NEED:",
          fontSize = 11.sp,
          fontWeight = FontWeight.Bold,
          color = TextSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          fix.toolsNeeded.forEach { tool ->
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(GraphiteSurface)
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text(text = tool, fontSize = 11.sp, color = TextPrimary)
            }
          }
        }
      }

      // Interactive Step Mode
      if (fix.steps.isNotEmpty()) {
        Spacer(modifier = Modifier.height(14.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCharcoal)
            .border(1.dp, ToxicGreenGlow.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .padding(14.dp)
        ) {
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "STEP ${sessionState.currentStepIndex + 1} OF ${fix.steps.size}",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = ToxicGreenGlow
              )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
              text = sessionState.currentStep ?: "",
              fontSize = 14.sp,
              fontWeight = FontWeight.Medium,
              color = TextPrimary,
              lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Navigation within steps
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              OutlinedButton(
                onClick = onPrevStep,
                enabled = sessionState.currentStepIndex > 0,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(34.dp)
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                  contentDescription = null,
                  modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Previous", fontSize = 11.sp)
              }

              Button(
                onClick = onNextStep,
                enabled = !sessionState.isLastStep,
                colors = ButtonDefaults.buttonColors(containerColor = GraphiteSurface),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(34.dp)
              ) {
                Text("Next Step", fontSize = 11.sp, color = TextPrimary)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                  contentDescription = null,
                  tint = TextPrimary,
                  modifier = Modifier.size(14.dp)
                )
              }
            }
          }
        }
      }

      // Alternative note if "Didn't Work" was clicked
      if (sessionState.note.isNotBlank()) {
        Spacer(modifier = Modifier.height(10.dp))
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(GraphiteSurface)
            .padding(10.dp)
        ) {
          Text(
            text = sessionState.note,
            fontSize = 12.sp,
            color = TextPrimary
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Verification Buttons: "That Worked" vs "Didn't Work"
      if (sessionState.status != RepairStatus.SAVED) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            onClick = onWorked,
            colors = ButtonDefaults.buttonColors(containerColor = ElectricLime),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .weight(1f)
              .height(40.dp)
              .testTag("repair_worked_button")
          ) {
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = null,
              tint = JetBlack,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("That Worked", color = JetBlack, fontWeight = FontWeight.Bold, fontSize = 13.sp)
          }

          OutlinedButton(
            onClick = onDidNotWork,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .weight(1f)
              .height(40.dp)
              .testTag("repair_did_not_work_button")
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = null,
              tint = TextSecondary,
              modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Didn't Work", color = TextSecondary, fontSize = 13.sp)
          }
        }
      } else {
        // Saved state
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(ElectricLime.copy(alpha = 0.15f))
            .border(1.dp, ElectricLime, RoundedCornerShape(8.dp))
            .padding(10.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = null,
              tint = ElectricLime,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Fix recorded in memory. Ask anytime: \"How did I fix this?\"",
              color = ElectricLime,
              fontSize = 12.sp,
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      }
    }
  }
}

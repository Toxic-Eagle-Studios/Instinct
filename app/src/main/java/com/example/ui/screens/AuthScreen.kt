package com.example.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.InstinctViewModel
import com.example.ui.theme.DarkCharcoal
import com.example.ui.theme.ElectricLime
import com.example.ui.theme.GraphiteBorder
import com.example.ui.theme.GraphiteCard
import com.example.ui.theme.JetBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AuthScreen(
  viewModel: InstinctViewModel,
  onGuestSelected: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(JetBlack)
      .statusBarsPadding()
      .padding(24.dp),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
      modifier = Modifier.fillMaxWidth()
    ) {
      // Emblem Icon
      Box(
        modifier = Modifier
          .size(72.dp)
          .clip(CircleShape)
          .background(DarkCharcoal)
          .border(2.dp, ElectricLime, CircleShape),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Psychology,
          contentDescription = "INSTINCT emblem",
          tint = ElectricLime,
          modifier = Modifier.size(38.dp)
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = "INSTINCT",
        fontSize = 28.sp,
        fontWeight = FontWeight.Black,
        color = TextPrimary,
        letterSpacing = 2.sp
      )

      Text(
        text = "Your life. A little less complicated.",
        fontSize = 14.sp,
        color = TextSecondary,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
      )

      // Feature summary card
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(GraphiteCard)
          .border(1.dp, GraphiteBorder, RoundedCornerShape(16.dp))
          .padding(18.dp)
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          FeatureBullet(title = "While You're At It", desc = "Remembers the right task at the right moment")
          FeatureBullet(title = "Undo IRL", desc = "Immediate recovery when everyday things go wrong")
          FeatureBullet(title = "Exactly", desc = "Stores your exact personal preferences and settings")
        }
      }

      if (errorMessage != null) {
        Spacer(modifier = Modifier.height(14.dp))
        Text(
          text = errorMessage!!,
          fontSize = 12.sp,
          color = androidx.compose.ui.graphics.Color(0xFFFF334B),
          textAlign = TextAlign.Center
        )
      }

      Spacer(modifier = Modifier.height(28.dp))

      // Google Sign-In Button
      Button(
        onClick = {
          val activity = context as? Activity ?: return@Button
          isLoading = true
          errorMessage = null
          viewModel.authRepository.signInWithGoogle(
            activity = activity,
            onSuccess = {
              isLoading = false
            },
            onError = { err ->
              isLoading = false
              errorMessage = err
            },
            onCancelled = {
              isLoading = false
            },
            scope = coroutineScope
          )
        },
        enabled = !isLoading,
        colors = ButtonDefaults.buttonColors(containerColor = ElectricLime),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("google_sign_in_button")
      ) {
        if (isLoading) {
          CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = JetBlack,
            strokeWidth = 2.dp
          )
        } else {
          Text(
            text = "Sign in with Google",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = JetBlack
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Continue as Guest Button
      OutlinedButton(
        onClick = {
          viewModel.authRepository.setGuestMode(true)
          onGuestSelected()
        },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("continue_as_guest_button")
      ) {
        Text(
          text = "Continue as Guest (Local Only)",
          fontSize = 14.sp,
          color = TextSecondary
        )
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Privacy note
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
      ) {
        Icon(
          imageVector = Icons.Default.Lock,
          contentDescription = null,
          tint = TextMuted,
          modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = "Zero-trust privacy. Memories are private to you.",
          fontSize = 11.sp,
          color = TextMuted
        )
      }
    }
  }
}

@Composable
private fun FeatureBullet(title: String, desc: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
      modifier = Modifier
        .size(6.dp)
        .clip(CircleShape)
        .background(ElectricLime)
    )
    Spacer(modifier = Modifier.width(10.dp))
    Column {
      Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary
      )
      Text(
        text = desc,
        fontSize = 11.sp,
        color = TextSecondary
      )
    }
  }
}

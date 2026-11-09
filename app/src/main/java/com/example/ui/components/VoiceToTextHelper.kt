package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.MemoryEntry
import com.example.ui.theme.CyanElectric
import com.example.ui.theme.DarkCharcoal
import com.example.ui.theme.DeepRedWarning
import com.example.ui.theme.ElectricLime
import com.example.ui.theme.GraphiteBorder
import com.example.ui.theme.GraphiteSurface
import com.example.ui.theme.JetBlack
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ToxicGreenGlow
import java.util.Locale

/**
 * Pure helper function that combines existing text in an input field with newly
 * transcribed voice-to-text speech. Preserves existing content and normalizes spacing.
 */
fun appendSpokenTranscript(existingText: String, spokenTranscript: String): String {
  val trimmedExisting = existingText.trim()
  val trimmedSpoken = spokenTranscript.trim()
  if (trimmedSpoken.isEmpty()) return trimmedExisting
  if (trimmedExisting.isEmpty()) {
    return trimmedSpoken.replaceFirstChar {
      if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
    }
  }
  val needsSpace = !trimmedExisting.endsWith(" ") && !trimmedExisting.endsWith("\n")
  return if (needsSpace) "$trimmedExisting $trimmedSpoken" else "$trimmedExisting$trimmedSpoken"
}

/**
 * Optional voice metadata hint inferred from natural speech prefixes
 * (e.g., "Work reminder: call the client" or "Shopping: buy AA batteries").
 */
data class VoiceMemoryHint(
  val cleanedText: String,
  val suggestedType: String? = null,
  val suggestedCategory: String? = null
)

/**
 * Inspects a spoken transcript for explicit category or memory type prefixes
 * while keeping the full thought intact if no prefix is used.
 */
fun inferVoiceMemoryHint(rawTranscript: String): VoiceMemoryHint {
  val trimmed = rawTranscript.trim()
  if (trimmed.isEmpty()) return VoiceMemoryHint(cleanedText = "")

  var workingText = trimmed
  var detectedCategory: String? = null
  var detectedType: String? = null

  // Check if the user started with "<Category> category:" or "<Category>:"
  for (category in MemoryEntry.CATEGORIES) {
    val catPrefixColon = Regex("^${Regex.escape(category)}(?:\\s+category)?\\s*:\\s*(.+)$", RegexOption.IGNORE_CASE)
    val match = catPrefixColon.find(workingText)
    if (match != null) {
      detectedCategory = category
      workingText = match.groupValues[1].trim()
      break
    }
  }

  // Check if the user started with "Reminder:", "Remind me to", "Preference:", or "Fix:"
  val reminderPrefix = Regex("^(?:reminder\\s*:\\s*|remind\\s+me\\s+to\\s+)(.+)$", RegexOption.IGNORE_CASE)
  val fixPrefix = Regex("^(?:fix\\s*:\\s*|how\\s+I\\s+fixed\\s*:\\s*)(.+)$", RegexOption.IGNORE_CASE)
  val preferencePrefix = Regex("^(?:preference\\s*:\\s*|exactly\\s*:\\s*)(.+)$", RegexOption.IGNORE_CASE)

  when {
    reminderPrefix.containsMatchIn(workingText) -> {
      detectedType = MemoryEntry.TYPE_REMINDER
      workingText = reminderPrefix.find(workingText)!!.groupValues[1].trim()
    }
    fixPrefix.containsMatchIn(workingText) -> {
      detectedType = MemoryEntry.TYPE_FIX_HISTORY
      workingText = fixPrefix.find(workingText)!!.groupValues[1].trim()
    }
    preferencePrefix.containsMatchIn(workingText) -> {
      detectedType = MemoryEntry.TYPE_PREFERENCE
      workingText = preferencePrefix.find(workingText)!!.groupValues[1].trim()
    }
  }

  val formatted = workingText.replaceFirstChar {
    if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
  }

  return VoiceMemoryHint(
    cleanedText = formatted,
    suggestedType = detectedType,
    suggestedCategory = detectedCategory
  )
}

/**
 * State holder for the reusable voice-to-text controller.
 */
class VoiceToTextController(
  val isListening: Boolean,
  val activeTargetKey: String,
  val partialTranscript: String,
  val rmsLevel: Float,
  val errorMessage: String?,
  private val onToggleListening: (targetKey: String, prompt: String) -> Unit,
  private val onStopListening: () -> Unit,
  private val onClearError: () -> Unit
) {
  fun toggleListening(
    targetKey: String = "primary",
    prompt: String = "Speak your memory entry..."
  ) {
    onToggleListening(targetKey, prompt)
  }

  fun stopListening() {
    onStopListening()
  }

  fun clearError() {
    onClearError()
  }

  fun isListeningFor(targetKey: String): Boolean = isListening && activeTargetKey == targetKey
}

/**
 * Remembers a lifecycle-aware [VoiceToTextController] that handles:
 * 1. Runtime `RECORD_AUDIO` permission requests.
 * 2. Live streaming `SpeechRecognizer` with real-time partial transcription and RMS audio levels.
 * 3. Automatic fallback to system `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` activity if on-device
 *    streaming speech recognition is unavailable or reports a service error.
 */
@Composable
fun rememberVoiceToTextController(
  onSpokenTextReceived: (targetKey: String, spokenText: String) -> Unit
): VoiceToTextController {
  val context = LocalContext.current
  val currentOnSpokenText by rememberUpdatedState(onSpokenTextReceived)

  var isListening by remember { mutableStateOf(false) }
  var activeTargetKey by remember { mutableStateOf("primary") }
  var pendingPrompt by remember { mutableStateOf("Speak your memory entry...") }
  var partialTranscript by remember { mutableStateOf("") }
  var rmsLevel by remember { mutableFloatStateOf(0f) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  val speechRecognizerRef = remember { mutableStateOf<SpeechRecognizer?>(null) }

  // Fallback ActivityResultLauncher for RecognizerIntent.ACTION_RECOGNIZE_SPEECH
  val fallbackSpeechIntentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    isListening = false
    partialTranscript = ""
    rmsLevel = 0f
    val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
    val spoken = matches?.firstOrNull()?.trim()
    if (!spoken.isNullOrEmpty()) {
      errorMessage = null
      currentOnSpokenText(activeTargetKey, spoken)
    }
  }

  val launchIntentFallback: (String) -> Unit = { promptText ->
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
      putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
      putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
      putExtra(RecognizerIntent.EXTRA_PROMPT, promptText)
    }
    try {
      isListening = true
      partialTranscript = ""
      fallbackSpeechIntentLauncher.launch(intent)
    } catch (e: Exception) {
      isListening = false
      errorMessage = "Voice-to-text service is not available on this device."
    }
  }

  val startSpeechRecognition: (Context, String) -> Unit = { ctx, promptText ->
    errorMessage = null
    partialTranscript = ""
    rmsLevel = 0f

    if (SpeechRecognizer.isRecognitionAvailable(ctx)) {
      try {
        speechRecognizerRef.value?.destroy()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(ctx)
        speechRecognizerRef.value = recognizer

        recognizer.setRecognitionListener(object : RecognitionListener {
          override fun onReadyForSpeech(params: Bundle?) {
            isListening = true
            errorMessage = null
          }

          override fun onBeginningOfSpeech() {
            isListening = true
          }

          override fun onRmsChanged(rmsdB: Float) {
            rmsLevel = rmsdB.coerceIn(0f, 12f)
          }

          override fun onBufferReceived(buffer: ByteArray?) {}

          override fun onEndOfSpeech() {
            isListening = false
            rmsLevel = 0f
          }

          override fun onError(error: Int) {
            isListening = false
            rmsLevel = 0f
            // If we already captured a partial transcript before timeout/end, commit it
            val leftoverPartial = partialTranscript.trim()
            partialTranscript = ""
            if (leftoverPartial.isNotEmpty() &&
              (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
            ) {
              currentOnSpokenText(activeTargetKey, leftoverPartial)
              return
            }

            when (error) {
              SpeechRecognizer.ERROR_NO_MATCH -> {
                errorMessage = "No speech detected. Tap the microphone and speak clearly."
              }
              SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                errorMessage = "Listening timed out. Tap the mic to try again."
              }
              SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                errorMessage = "Microphone permission is required for voice-to-text."
              }
              SpeechRecognizer.ERROR_CLIENT,
              SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
              SpeechRecognizer.ERROR_SERVER,
              SpeechRecognizer.ERROR_NETWORK -> {
                // Fallback to system RecognizerIntent dialog
                launchIntentFallback(promptText)
              }
              else -> {
                launchIntentFallback(promptText)
              }
            }
          }

          override fun onResults(results: Bundle?) {
            isListening = false
            rmsLevel = 0f
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val bestText = matches?.firstOrNull()?.trim().orEmpty()
              .ifEmpty { partialTranscript.trim() }
            partialTranscript = ""
            if (bestText.isNotEmpty()) {
              errorMessage = null
              currentOnSpokenText(activeTargetKey, bestText)
            }
          }

          override fun onPartialResults(partialResults: Bundle?) {
            val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val latest = partials?.firstOrNull()?.trim().orEmpty()
            if (latest.isNotEmpty()) {
              partialTranscript = latest
            }
          }

          override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
          putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
          putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
          putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
          putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        isListening = true
        recognizer.startListening(recognizerIntent)
      } catch (e: Exception) {
        launchIntentFallback(promptText)
      }
    } else {
      launchIntentFallback(promptText)
    }
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      startSpeechRecognition(context, pendingPrompt)
    } else {
      isListening = false
      errorMessage = "Microphone permission is required to speak memory entries."
    }
  }

  val stopListeningAction: () -> Unit = {
    try {
      speechRecognizerRef.value?.stopListening()
      speechRecognizerRef.value?.cancel()
    } catch (_: Exception) {
    }
    val capturedPartial = partialTranscript.trim()
    isListening = false
    partialTranscript = ""
    rmsLevel = 0f
    if (capturedPartial.isNotEmpty()) {
      currentOnSpokenText(activeTargetKey, capturedPartial)
    }
  }

  val toggleListeningAction: (String, String) -> Unit = { targetKey, promptText ->
    if (isListening && activeTargetKey == targetKey) {
      stopListeningAction()
    } else {
      if (isListening) {
        stopListeningAction()
      }
      activeTargetKey = targetKey
      pendingPrompt = promptText
      val hasPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
      ) == PackageManager.PERMISSION_GRANTED
      if (hasPermission) {
        startSpeechRecognition(context, promptText)
      } else {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
      }
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      try {
        speechRecognizerRef.value?.destroy()
      } catch (_: Exception) {
      }
      speechRecognizerRef.value = null
    }
  }

  return remember(
    isListening,
    activeTargetKey,
    partialTranscript,
    rmsLevel,
    errorMessage
  ) {
    VoiceToTextController(
      isListening = isListening,
      activeTargetKey = activeTargetKey,
      partialTranscript = partialTranscript,
      rmsLevel = rmsLevel,
      errorMessage = errorMessage,
      onToggleListening = toggleListeningAction,
      onStopListening = stopListeningAction,
      onClearError = { errorMessage = null }
    )
  }
}

/**
 * Reusable Microphone IconButton with pulsing active listening state and 48.dp accessibility target.
 */
@Composable
fun VoiceInputMicButton(
  isListening: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  accentColor: Color = CyanElectric,
  buttonSize: Dp = 48.dp,
  iconSize: Dp = 20.dp,
  contentDescription: String = "Speak memory entry with voice-to-text",
  testTag: String = "voice_input_mic_button"
) {
  val infiniteTransition = rememberInfiniteTransition(label = "voice_mic_pulse")
  val pulseScale by infiniteTransition.animateFloat(
    initialValue = 1f,
    targetValue = if (isListening) 1.12f else 1f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 550),
      repeatMode = RepeatMode.Reverse
    ),
    label = "voice_mic_scale"
  )

  IconButton(
    onClick = onClick,
    modifier = modifier
      .size(buttonSize)
      .scale(if (isListening) pulseScale else 1f)
      .clip(CircleShape)
      .background(
        if (isListening) DeepRedWarning.copy(alpha = 0.22f) else GraphiteSurface
      )
      .border(
        width = if (isListening) 1.5.dp else 1.dp,
        color = if (isListening) DeepRedWarning else accentColor.copy(alpha = 0.55f),
        shape = CircleShape
      )
      .testTag(testTag)
  ) {
    Icon(
      imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
      contentDescription = if (isListening) "Stop voice-to-text listening" else contentDescription,
      tint = if (isListening) DeepRedWarning else accentColor,
      modifier = Modifier.size(iconSize)
    )
  }
}

/**
 * Banner displayed while voice-to-text is actively listening or when a voice error occurs.
 * Shows live partial speech transcription in real time and a Stop / Dismiss control.
 */
@Composable
fun VoiceListeningStatusBanner(
  controller: VoiceToTextController,
  accentColor: Color = ElectricLime,
  listeningLabel: String = "LISTENING — SPEAK YOUR MEMORY",
  testTag: String = "voice_listening_status_banner",
  modifier: Modifier = Modifier
) {
  AnimatedVisibility(visible = controller.isListening || controller.errorMessage != null) {
    if (controller.isListening) {
      Box(
        modifier = modifier
          .fillMaxWidth()
          .padding(top = 8.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(DarkCharcoal)
          .border(1.dp, accentColor.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
          .padding(horizontal = 12.dp, vertical = 10.dp)
          .testTag(testTag)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.18f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.GraphicEq,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(16.dp)
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
              Text(
                text = listeningLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = accentColor,
                letterSpacing = 0.7.sp
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = if (controller.partialTranscript.isNotBlank()) {
                  "\"${controller.partialTranscript}...\""
                } else {
                  "Listening now... speak naturally or tap Done when finished"
                },
                fontSize = 12.sp,
                fontStyle = if (controller.partialTranscript.isNotBlank()) FontStyle.Italic else FontStyle.Normal,
                color = if (controller.partialTranscript.isNotBlank()) TextPrimary else TextSecondary,
                modifier = Modifier.testTag("${testTag}_partial_transcript")
              )
            }
          }

          Spacer(modifier = Modifier.width(8.dp))

          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(DeepRedWarning.copy(alpha = 0.18f))
              .border(1.dp, DeepRedWarning.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
              .clickable { controller.stopListening() }
              .padding(horizontal = 10.dp, vertical = 6.dp)
              .testTag("${testTag}_stop_button")
          ) {
            Text(
              text = "DONE",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = DeepRedWarning
            )
          }
        }
      }
    } else if (controller.errorMessage != null) {
      Box(
        modifier = modifier
          .fillMaxWidth()
          .padding(top = 8.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(DeepRedWarning.copy(alpha = 0.12f))
          .border(1.dp, DeepRedWarning.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
          .padding(horizontal = 12.dp, vertical = 8.dp)
          .testTag("${testTag}_error")
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Text(
            text = controller.errorMessage,
            fontSize = 11.sp,
            color = DeepRedWarning,
            modifier = Modifier.weight(1f)
          )
          IconButton(
            onClick = { controller.clearError() },
            modifier = Modifier
              .size(24.dp)
              .testTag("${testTag}_dismiss_error")
          ) {
            Icon(
              imageVector = Icons.Default.Close,
              contentDescription = "Dismiss voice error",
              tint = TextMuted,
              modifier = Modifier.size(14.dp)
            )
          }
        }
      }
    }
  }
}

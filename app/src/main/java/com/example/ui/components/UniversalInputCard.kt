package com.example.ui.components

import android.Manifest
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.ui.theme.ToxicGreenGlow

@Composable
fun UniversalInputCard(
  inputText: String,
  onInputTextChanged: (String) -> Unit,
  attachedImage: Bitmap?,
  onImageChanged: (Bitmap?) -> Unit,
  isProcessing: Boolean,
  onSubmit: () -> Unit,
  selectedCategory: String = com.example.data.model.MemoryEntry.DEFAULT_CATEGORY,
  onCategorySelected: ((String) -> Unit)? = null,
  dueAt: String = "",
  onDueAtChanged: ((String) -> Unit)? = null,
  onQuickTrySubmit: ((String) -> Unit)? = null,
  onAiTranscribeSpoken: ((String) -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var inputError by remember { mutableStateOf<String?>(null) }
  var showDueDateInput by remember { mutableStateOf(dueAt.isNotBlank()) }

  // Helper to scale down large bitmaps safely for Gemini multimodal inference
  val normalizeBitmap: (Bitmap) -> Bitmap = { bitmap ->
    if (bitmap.width > 1024 || bitmap.height > 1024) {
      val ratio = 1024f / maxOf(bitmap.width, bitmap.height)
      Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * ratio).toInt().coerceAtLeast(1),
        (bitmap.height * ratio).toInt().coerceAtLeast(1),
        true
      )
    } else {
      bitmap
    }
  }

  // Live Voice-to-Text controller with SpeechRecognizer streaming + RecognizerIntent fallback
  val voiceController = rememberVoiceToTextController { _, spokenText ->
    val hint = inferVoiceMemoryHint(spokenText)
    if (hint.suggestedCategory != null && onCategorySelected != null) {
      onCategorySelected(hint.suggestedCategory)
    }
    if (onAiTranscribeSpoken != null) {
      onAiTranscribeSpoken(spokenText)
    } else {
      onInputTextChanged(appendSpokenTranscript(inputText, spokenText))
    }
  }

  // Zero-permission Android Photo Picker launcher
  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    if (uri != null) {
      try {
        val rawBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
          val source = ImageDecoder.createSource(context.contentResolver, uri)
          ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.isMutableRequired = true
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
          }
        } else {
          @Suppress("DEPRECATION")
          MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
        onImageChanged(normalizeBitmap(rawBitmap))
        inputError = null
      } catch (e: Exception) {
        inputError = "Could not load selected image."
      }
    }
  }

  // Live Camera Capture launcher (returns Bitmap preview directly)
  val cameraCaptureLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicturePreview()
  ) { capturedBitmap: Bitmap? ->
    if (capturedBitmap != null) {
      onImageChanged(normalizeBitmap(capturedBitmap))
      inputError = null
    }
  }

  // Camera runtime permission launcher
  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      try {
        cameraCaptureLauncher.launch(null)
      } catch (e: Exception) {
        photoPickerLauncher.launch(
          PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
      }
    } else {
      inputError = "Camera permission denied. Opening photo picker instead."
      photoPickerLauncher.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
      )
    }
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(20.dp))
      .background(GraphiteCard)
      .border(
        width = 1.dp,
        color = if (attachedImage != null) CyanElectric.copy(alpha = 0.7f) else GraphiteBorder,
        shape = RoundedCornerShape(20.dp)
      )
      .padding(16.dp)
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "REMEMBER LESS. GET MORE DONE.",
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            color = ElectricLime,
            letterSpacing = 0.8.sp
          )
          Text(
            text = "What's on your mind?",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
          )
        }

        if (attachedImage != null) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(CyanElectric.copy(alpha = 0.15f))
              .border(1.dp, CyanElectric.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
              .padding(horizontal = 8.dp, vertical = 3.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = CyanElectric,
                modifier = Modifier.size(12.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "VISION READY",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = CyanElectric
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "Tell INSTINCT something to remember, ask what it knows, say where you're going, or explain what went wrong.",
        fontSize = 13.sp,
        color = TextSecondary,
        lineHeight = 18.sp
      )

      Spacer(modifier = Modifier.height(12.dp))

      // Multimodal Attached Image Preview + Quick Vision Intent Selector
      AnimatedVisibility(visible = attachedImage != null) {
        if (attachedImage != null) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 12.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(DarkCharcoal)
              .border(1.dp, CyanElectric.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
              .padding(10.dp)
              .testTag("attached_image_preview_container")
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Box(
                modifier = Modifier
                  .size(76.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .border(1.dp, CyanElectric, RoundedCornerShape(10.dp))
              ) {
                Image(
                  bitmap = attachedImage.asImageBitmap(),
                  contentDescription = "Attached photo for Gemini multimodal analysis",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.matchParentSize()
                )
                IconButton(
                  onClick = { onImageChanged(null) },
                  modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .background(JetBlack.copy(alpha = 0.78f), CircleShape)
                    .testTag("remove_attached_image_button")
                ) {
                  Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove photo",
                    tint = TextPrimary,
                    modifier = Modifier.size(14.dp)
                  )
                }
              }

              Spacer(modifier = Modifier.width(12.dp))

              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Multimodal Image Attached",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Bold,
                  color = TextPrimary
                )
                Text(
                  text = "Choose how Gemini should analyze this image or type below:",
                  fontSize = 11.sp,
                  color = TextSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                  VisionIntentPill(
                    label = "Diagnose Issue",
                    icon = Icons.Default.Build,
                    accent = ToxicGreenGlow,
                    onClick = {
                      onInputTextChanged("Identify this problem or damage and show me step-by-step how to fix it")
                    },
                    testTag = "vision_mode_repair_chip"
                  )
                  VisionIntentPill(
                    label = "Save Preference",
                    icon = Icons.Default.Bookmark,
                    accent = CyanElectric,
                    onClick = {
                      onInputTextChanged("Save this item and its details to my personal preferences")
                    },
                    testTag = "vision_mode_preference_chip"
                  )
                }
              }
            }
          }
        }
      }

      if (onCategorySelected != null) {
        MemoryCategorySelectorRow(
          selectedCategory = selectedCategory,
          onCategorySelected = onCategorySelected,
          accentColor = CyanElectric,
          testTagPrefix = "universal_input_category"
        )
        Spacer(modifier = Modifier.height(10.dp))
      }

      // Input TextField
      OutlinedTextField(
        value = inputText,
        onValueChange = onInputTextChanged,
        placeholder = {
          Text(
            text = if (attachedImage != null) {
              "Add optional context for this photo, or tap Send to analyze..."
            } else {
              "Remind me to ask Dad about Friday..."
            },
            fontSize = 14.sp,
            color = TextMuted
          )
        },
        trailingIcon = {
          if (inputText.isNotEmpty()) {
            IconButton(
              onClick = { onInputTextChanged("") },
              modifier = Modifier
                .size(36.dp)
                .testTag("clear_input_button")
            ) {
              Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear input text",
                tint = TextMuted,
                modifier = Modifier.size(18.dp)
              )
            }
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .testTag("universal_input_field"),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedContainerColor = GraphiteSurface,
          unfocusedContainerColor = GraphiteSurface,
          focusedBorderColor = ElectricLime,
          unfocusedBorderColor = GraphiteBorder,
          focusedTextColor = TextPrimary,
          unfocusedTextColor = TextPrimary,
          cursorColor = ElectricLime
        ),
        maxLines = 4
      )

      // Live Voice-to-Text Listening Status & Partial Transcript Banner
      VoiceListeningStatusBanner(
        controller = voiceController,
        accentColor = CyanElectric,
        listeningLabel = "LISTENING — SPEAK YOUR THOUGHT OR MEMORY (AI TRANSCRIPTION READY)",
        testTag = "universal_voice_listening_banner"
      )

      if (inputError != null) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = inputError!!,
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.error
        )
      }

      // Expandable Optional Reminder Date (`dueAt`)
      if (onDueAtChanged != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { showDueDateInput = !showDueDateInput }
            .padding(vertical = 4.dp)
            .testTag("toggle_due_date_button"),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Default.Event,
            contentDescription = null,
            tint = if (dueAt.isNotBlank()) ElectricLime else TextSecondary,
            modifier = Modifier.size(14.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = if (dueAt.isNotBlank()) "Reminder date: $dueAt" else "Optional reminder date",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (dueAt.isNotBlank()) ElectricLime else TextSecondary
          )
          Spacer(modifier = Modifier.width(4.dp))
          Icon(
            imageVector = if (showDueDateInput) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(15.dp)
          )
        }

        AnimatedVisibility(visible = showDueDateInput) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 6.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(DarkCharcoal)
              .border(1.dp, GraphiteBorder, RoundedCornerShape(10.dp))
              .padding(10.dp)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              listOf("Today 6:00 PM", "Tomorrow 9:00 AM", "Friday 5:00 PM", "This Weekend").forEach { preset ->
                QuickPromptChip(
                  label = preset,
                  onClick = { onDueAtChanged(preset) }
                )
              }
              if (dueAt.isNotBlank()) {
                QuickPromptChip(
                  label = "Clear date",
                  onClick = { onDueAtChanged("") }
                )
              }
            }
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedTextField(
              value = dueAt,
              onValueChange = onDueAtChanged,
              placeholder = {
                Text("Or enter date/time (e.g., 2026-11-15 17:00 or Friday 5 PM)", fontSize = 11.sp, color = TextMuted)
              },
              singleLine = true,
              shape = RoundedCornerShape(8.dp),
              colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = GraphiteSurface,
                unfocusedContainerColor = GraphiteSurface,
                focusedBorderColor = ElectricLime,
                unfocusedBorderColor = GraphiteBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
              ),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("reminder_due_date_input")
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Quick-Try Chips (matching App.jsx QUICK + INSTINCT flows)
      Text(
        text = "TRY IT:",
        fontSize = 9.sp,
        fontWeight = FontWeight.ExtraBold,
        color = TextMuted,
        letterSpacing = 0.8.sp
      )
      Spacer(modifier = Modifier.height(5.dp))
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        val triggerChip: (String) -> Unit = { prompt ->
          onInputTextChanged(prompt)
          onQuickTrySubmit?.invoke(prompt)
        }
        QuickPromptChip(
          label = "Remind me to ask Dad about Friday",
          onClick = { triggerChip("Remind me to ask Dad about Friday") },
          modifier = Modifier.testTag("quick_chip_ask_dad")
        )
        QuickPromptChip(
          label = "I'm heading to Dad's",
          onClick = { triggerChip("I'm heading to Dad's") },
          modifier = Modifier.testTag("quick_chip_heading_dads")
        )
        QuickPromptChip(
          label = "My normal pizza order is pepperoni and mushrooms",
          onClick = { triggerChip("My normal pizza order is pepperoni and mushrooms") },
          modifier = Modifier.testTag("quick_chip_save_pizza")
        )
        QuickPromptChip(
          label = "What is my normal pizza order?",
          onClick = { triggerChip("What is my normal pizza order?") },
          modifier = Modifier.testTag("quick_chip_ask_pizza")
        )
        QuickPromptChip(
          label = "I accidentally deleted a file",
          onClick = { triggerChip("I accidentally deleted a file") },
          modifier = Modifier.testTag("quick_chip_deleted_file")
        )
        QuickPromptChip(
          label = "🛒 Going to Walmart",
          onClick = { triggerChip("I'm heading to Walmart") }
        )
        QuickPromptChip(
          label = "🥣 Oversalted soup",
          onClick = { triggerChip("I accidentally oversalted my soup") }
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Bottom Row: Voice mic, Live Camera Capture, Gallery Photo Picker, and Send
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // 1. Voice Input Button
          VoiceInputMicButton(
            isListening = voiceController.isListening,
            onClick = {
              inputError = null
              voiceController.toggleListening(
                targetKey = "universal_input",
                prompt = "Tell INSTINCT what's going on or what to remember..."
              )
            },
            accentColor = CyanElectric,
            buttonSize = 48.dp,
            iconSize = 20.dp,
            contentDescription = "Speak input via microphone",
            testTag = "voice_input_button"
          )

          // 2. Live Camera Capture Button
          IconButton(
            onClick = {
              inputError = null
              cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            },
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(
                if (attachedImage != null) ElectricLime.copy(alpha = 0.16f) else GraphiteSurface
              )
              .border(
                1.dp,
                if (attachedImage != null) ElectricLime else GraphiteBorder,
                CircleShape
              )
              .testTag("camera_capture_button")
          ) {
            Icon(
              imageVector = Icons.Default.PhotoCamera,
              contentDescription = "Capture photo with camera for Gemini analysis",
              tint = if (attachedImage != null) ElectricLime else CyanElectric,
              modifier = Modifier.size(20.dp)
            )
          }

          // 3. Photo Gallery Picker Button
          IconButton(
            onClick = {
              inputError = null
              photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(GraphiteSurface)
              .border(1.dp, GraphiteBorder, CircleShape)
              .testTag("photo_upload_button")
          ) {
            Icon(
              imageVector = Icons.Default.Image,
              contentDescription = "Pick photo from gallery for Gemini analysis",
              tint = CyanElectric,
              modifier = Modifier.size(20.dp)
            )
          }
        }

        // Send / Analyze Button
        IconButton(
          onClick = onSubmit,
          enabled = !isProcessing && (inputText.isNotBlank() || attachedImage != null),
          modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
              if (inputText.isNotBlank() || attachedImage != null) ElectricLime else GraphiteSurface
            )
            .testTag("universal_submit_button")
        ) {
          if (isProcessing) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              color = JetBlack,
              strokeWidth = 2.dp
            )
          } else {
            Icon(
              imageVector = Icons.Default.Send,
              contentDescription = "Send to INSTINCT",
              tint = if (inputText.isNotBlank() || attachedImage != null) JetBlack else TextMuted,
              modifier = Modifier.size(20.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun VisionIntentPill(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  accent: androidx.compose.ui.graphics.Color,
  onClick: () -> Unit,
  testTag: String
) {
  Box(
    modifier = Modifier
      .clip(RoundedCornerShape(8.dp))
      .background(accent.copy(alpha = 0.14f))
      .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 8.dp, vertical = 4.dp)
      .testTag(testTag)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = accent,
        modifier = Modifier.size(12.dp)
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = label,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = accent
      )
    }
  }
}

@Composable
fun QuickPromptChip(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(8.dp))
      .background(GraphiteSurface)
      .border(1.dp, GraphiteBorder, RoundedCornerShape(8.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 10.dp, vertical = 6.dp)
  ) {
    Text(
      text = label,
      fontSize = 12.sp,
      color = TextSecondary,
      fontWeight = FontWeight.Medium
    )
  }
}

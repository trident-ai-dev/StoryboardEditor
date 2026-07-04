package com.storyboard.editor.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.storyboard.editor.domain.model.ClipPosition
import com.storyboard.editor.domain.model.ClipTransition
import com.storyboard.editor.domain.model.CropRect
import com.storyboard.editor.domain.model.TransitionType
import com.storyboard.editor.domain.model.VideoClip
import com.storyboard.editor.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineEditorScreen(
    onNavigateBack: () -> Unit,
    onNavigateToExport: (String) -> Unit,
    viewModel: TimelineViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TimelineTopBar(
                canUndo = uiState.canUndo,
                canRedo = uiState.canRedo,
                onNavigateBack = onNavigateBack,
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onExport = { onNavigateToExport(uiState.project.id) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.project.videoClips.isEmpty()) {
                EmptyTimelineMessage(
                    modifier = Modifier.weight(1f)
                )
            } else {
                // Timeline Canvas
                TimelineCanvas(
                    clips = uiState.project.videoClips,
                    selectedClipId = uiState.selectedClipId,
                    currentTimeMs = uiState.currentTimeMs,
                    zoom = uiState.timelineZoom,
                    onSelectClip = { viewModel.selectClip(it) },
                    onMoveClip = { from, to -> viewModel.moveClip(from, to) },
                    onUpdateCurrentTime = { viewModel.updateCurrentTime(it) },
                    modifier = Modifier.weight(1f)
                )

                // Timeline Controls
                TimelineControls(
                    zoom = uiState.timelineZoom,
                    isPlaying = uiState.isPlaying,
                    onZoomChange = { viewModel.updateZoom(it) },
                    onPlayPause = { viewModel.setPlaying(!uiState.isPlaying) },
                    onSplit = {
                        uiState.selectedClipId?.let {
                            viewModel.splitClip(it, uiState.currentTimeMs)
                        }
                    },
                    onDuplicate = {
                        uiState.selectedClipId?.let {
                            viewModel.duplicateClip(it)
                        }
                    },
                    onDelete = {
                        uiState.selectedClipId?.let {
                            viewModel.removeClip(it)
                        }
                    }
                )
            }
        }
    }

    // Clip Inspector
    if (uiState.showInspector) {
        val selectedClip = uiState.project.videoClips.find { it.id == uiState.selectedClipId }
        if (selectedClip != null) {
            ClipInspector(
                clip = selectedClip,
                onDismiss = { viewModel.selectClip(null) },
                onUpdateDuration = { viewModel.updateClipDuration(selectedClip.id, it) },
                onUpdateSpeed = { viewModel.updateClipSpeed(selectedClip.id, it) },
                onUpdatePosition = { viewModel.updateClipPosition(selectedClip.id, it) },
                onUpdateTransition = { viewModel.updateClipTransition(selectedClip.id, it) },
                onChangeClipImage = { newUri ->
                    viewModel.selectClip(null)
                },
                onSplit = { viewModel.splitClip(selectedClip.id, uiState.currentTimeMs) },
                onDuplicate = { viewModel.duplicateClip(selectedClip.id) },
                onDelete = { viewModel.removeClip(selectedClip.id) },
                onPickTransition = { viewModel.toggleTransitionPicker() }
            )
        }
    }

    // Transition Picker Dialog
    if (uiState.showTransitionPicker) {
        TransitionPickerDialog(
            onDismiss = { viewModel.toggleTransitionPicker() },
            onSelect = { viewModel.setTransitionType(it) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineTopBar(
    canUndo: Boolean,
    canRedo: Boolean,
    onNavigateBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onExport: () -> Unit
) {
    TopAppBar(
        title = { Text("Timeline Editor", fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(Icons.Default.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(Icons.Default.Redo, contentDescription = "Redo")
            }
            IconButton(onClick = onExport) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Export")
            }
        }
    )
}

@Composable
private fun EmptyTimelineMessage(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.Movie,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No clips in timeline",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Import images and assign timestamps on the Home screen",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TimelineCanvas(
    clips: List<VideoClip>,
    selectedClipId: String?,
    currentTimeMs: Long,
    zoom: Float,
    onSelectClip: (String?) -> Unit,
    onMoveClip: (Int, Int) -> Unit,
    onUpdateCurrentTime: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalDuration = clips.maxOfOrNull { it.startTimeMs + it.durationMs } ?: 1L
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    var canvasWidth by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(TimelineBackground)
            .clickable { onSelectClip(null) }
    ) {
        Column {
            // Time ruler
            TimeRuler(
                totalDurationMs = totalDuration,
                zoom = zoom,
                currentTimeMs = currentTimeMs
            )

            // Clips row
            LazyRow(
                state = rememberLazyListState(),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
            ) {
                itemsIndexed(clips, key = { _, clip -> clip.id }) { index, clip ->
                    ClipCard(
                        clip = clip,
                        isSelected = clip.id == selectedClipId,
                        zoom = zoom,
                        onSelect = { onSelectClip(clip.id) },
                        onDragStart = { onMoveClip(index, index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeRuler(
    totalDurationMs: Long,
    zoom: Float,
    currentTimeMs: Long
) {
    val density = LocalDensity.current
    val pixelsPerMs = 0.1f * zoom
    val totalWidth = (totalDurationMs * pixelsPerMs).dp

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(TimelineTrackBackground)
    ) {
        val totalPx = totalDurationMs * pixelsPerMs * density.density
        val stepMs = when {
            totalDurationMs <= 5000 -> 500L
            totalDurationMs <= 30000 -> 1000L
            else -> 5000L
        }

        // Ruler ticks
        var tickMs = 0L
        while (tickMs <= totalDurationMs) {
            val x = (tickMs * pixelsPerMs * density.density).toFloat()
            val isMajor = tickMs % (stepMs * 5) == 0L || tickMs == 0L

            drawLine(
                color = TimelineRulerColor,
                start = Offset(x, if (isMajor) 0f else size.height * 0.5f),
                end = Offset(x, size.height),
                strokeWidth = if (isMajor) 2f else 1f
            )

            if (isMajor && tickMs > 0) {
                val seconds = tickMs / 1000
                drawContext.canvas.nativeCanvas.drawText(
                    "${seconds}s",
                    x + 4f,
                    size.height * 0.7f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#666666")
                        textSize = 24f
                    }
                )
            }
            tickMs += stepMs
        }

        // Playhead cursor
        val cursorX = (currentTimeMs * pixelsPerMs * density.density).toFloat()
        drawLine(
            color = PlayheadColor,
            start = Offset(cursorX, 0f),
            end = Offset(cursorX, size.height),
            strokeWidth = 2f
        )
    }
}

@Composable
private fun ClipCard(
    clip: VideoClip,
    isSelected: Boolean,
    zoom: Float,
    onSelect: () -> Unit,
    onDragStart: () -> Unit
) {
    val density = LocalDensity.current
    val pixelsPerMs = 0.1f * zoom
    val clipWidth = (clip.durationMs * pixelsPerMs).dp
    val clipOffset = (clip.startTimeMs * pixelsPerMs).dp

    Card(
        modifier = Modifier
            .width(clipWidth)
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .clickable { onSelect() },
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) TimelineClipSelected else TimelineClipColor
        )
    ) {
        AsyncImage(
            model = clip.imageUri,
            contentDescription = clip.imageFileName,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            contentScale = ContentScale.Crop
        )
        Text(
            text = clip.imageFileName,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(4.dp),
            color = Color.White
        )
        Text(
            text = "${clip.effectiveDurationMs / 1000}.${(clip.effectiveDurationMs % 1000) / 100}s",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun TimelineControls(
    zoom: Float,
    isPlaying: Boolean,
    onZoomChange: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onSplit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Zoom controls
            IconButton(onClick = { onZoomChange(zoom - 0.25f) }) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
            }
            Text("${(zoom * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
            IconButton(onClick = { onZoomChange(zoom + 0.25f) }) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
            }

            // Edit controls
            IconButton(onClick = onSplit) {
                Icon(Icons.Default.ContentCut, contentDescription = "Split Clip")
            }
            IconButton(onClick = onDuplicate) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Clip")
            }

            // Play/Pause
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play"
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClipInspector(
    clip: VideoClip,
    onDismiss: () -> Unit,
    onUpdateDuration: (Long) -> Unit,
    onUpdateSpeed: (Float) -> Unit,
    onUpdatePosition: (ClipPosition) -> Unit,
    onUpdateTransition: (ClipTransition) -> Unit,
    onChangeClipImage: (Uri) -> Unit,
    onSplit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onPickTransition: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Clip Inspector",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(clip.imageFileName, style = MaterialTheme.typography.bodyMedium)

            // Duration slider
            Text("Duration: ${clip.effectiveDurationMs / 1000}.${(clip.effectiveDurationMs % 1000) / 100}s")
            var durationSlider by remember { mutableFloatStateOf(clip.durationMs.toFloat()) }
            Slider(
                value = durationSlider,
                onValueChange = { durationSlider = it },
                valueRange = 500f..30000f,
                onValueChangeFinished = { onUpdateDuration(durationSlider.toLong()) }
            )

            // Speed slider
            Text("Speed: ${clip.speed}x")
            var speedSlider by remember { mutableFloatStateOf(clip.speed) }
            Slider(
                value = speedSlider,
                onValueChange = { speedSlider = it },
                valueRange = 0.25f..4f,
                onValueChangeFinished = { onUpdateSpeed(speedSlider) }
            )

            // Current transition
            Text(
                "Transition: ${clip.transition?.type?.displayName ?: TransitionType.NONE.displayName}",
                modifier = Modifier.clickable { onPickTransition() }
            )

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AssistChip(onClick = onSplit, label = { Text("Split") })
                AssistChip(onClick = onDuplicate, label = { Text("Duplicate") })
                AssistChip(onClick = onDelete, label = { Text("Delete") })
            }
        }
    }
}

@Composable
private fun TransitionPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (TransitionType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Transition") },
        text = {
            Column {
                TransitionType.entries.forEach { type ->
                    TextButton(
                        onClick = { onSelect(type) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            type.displayName,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

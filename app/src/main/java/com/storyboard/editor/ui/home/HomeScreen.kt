package com.storyboard.editor.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToTimeline: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val voiceover = uiState.voiceover
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importImages(uris)
        }
    }

    var replaceIndex by remember { mutableIntStateOf(-1) }
    val replaceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty() && replaceIndex >= 0) {
            viewModel.replaceImage(replaceIndex, uris.first())
            replaceIndex = -1
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importVoiceover(uris.first())
        }
    }

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
            TopAppBar(
                title = { Text("Storyboard Editor", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        audioPickerLauncher.launch("audio/*")
                    }) {
                        Icon(Icons.Outlined.AudioFile, "Import Audio")
                    }
                    IconButton(onClick = {
                        imagePickerLauncher.launch("image/*")
                    }) {
                        Icon(Icons.Outlined.AddPhotoAlternate, "Add Images")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Project name
            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.projectName,
                    onValueChange = { },
                    label = { Text("Project Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Imported images preview
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Imported Images (${uiState.importedImages.size})",
                                style = MaterialTheme.typography.titleMedium
                            )
                            TextButton(onClick = {
                                imagePickerLauncher.launch("image/*")
                            }) {
                                Text("Add")
                            }
                        }

                        if (uiState.importedImages.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No images imported yet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(
                                    uiState.importedImages,
                                    key = { index, _ -> index }
                                ) { index, image ->
                                    Card(
                                        modifier = Modifier.width(100.dp)
                                    ) {
                                        Box {
                                            AsyncImage(
                                                model = image.uri,
                                                contentDescription = image.fileName,
                                                modifier = Modifier
                                                    .width(100.dp)
                                                    .height(100.dp),
                                                contentScale = ContentScale.Crop
                                            )
                                            IconButton(
                                                onClick = {
                                                    viewModel.removeImage(index)
                                                },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(24.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    "Remove",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                        Text(
                                            text = image.fileName,
                                            modifier = Modifier.padding(4.dp),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Voiceover section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Audio Track",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (voiceover == null) {
                            TextButton(onClick = {
                                audioPickerLauncher.launch("audio/*")
                            }) {
                                Icon(
                                    Icons.Outlined.AudioFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Import voiceover/audio")
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.AudioFile,
                                    contentDescription = null
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        voiceover.fileName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        "${voiceover.durationMs / 1000}s",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                IconButton(onClick = { viewModel.removeVoiceover() }) {
                                    Icon(Icons.Default.Close, "Remove audio")
                                }
                            }
                        }
                    }
                }
            }

            // Timestamp input
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "Timestamps",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Enter one timestamp per line (e.g., 0-5s or 00:00-00:05)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = uiState.timestampInput,
                            onValueChange = { viewModel.updateTimestampInput(it) },
                            label = { Text("Timestamps") },
                            placeholder = { Text("0-5s\n5-10s\n10-15s") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                            maxLines = 10
                        )
                    }
                }
            }

            // Mismatch warning
            item {
                AnimatedVisibility(visible = uiState.mismatchMessage != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                uiState.mismatchMessage ?: "",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(onClick = { viewModel.clearMismatch() }) {
                                    Text("Cancel")
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(onClick = {
                                    if (viewModel.forceBuildTimeline() != null) onNavigateToTimeline()
                                }) {
                                    Text("Force Build")
                                }
                            }
                        }
                    }
                }
            }

            // Build & Edit button
            item {
                Button(
                    onClick = {
                        val project = viewModel.autoBuildTimeline()
                        if (project != null) {
                            onNavigateToTimeline()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    enabled = uiState.importedImages.isNotEmpty() && uiState.timestampInput.isNotBlank()
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Build Timeline & Edit",
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

package com.storyboard.editor.ui.export

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.storyboard.editor.domain.model.ExportResolution
import com.storyboard.editor.domain.model.FrameRate
import com.storyboard.editor.media.renderer.RenderState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    onNavigateBack: () -> Unit,
    onNavigateHome: () -> Unit,
    viewModel: ExportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.renderProgress.state) {
        when (uiState.renderProgress.state) {
            RenderState.COMPLETED -> {
                snackbarHostState.showSnackbar("Video saved to Gallery!")
            }
            RenderState.FAILED -> {
                snackbarHostState.showSnackbar(
                    "Export failed: ${uiState.renderProgress.error ?: "Unknown error"}"
                )
            }
            else -> {}
        }
    }

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
                title = { Text("Export", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Project info
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(uiState.project.name, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${uiState.project.videoClips.size} clips | ${uiState.project.durationMs / 1000}s total",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Resolution selector
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Resolution", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    ExportResolution.entries.forEach { resolution ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.selectedResolution == resolution,
                                onClick = { viewModel.setResolution(resolution) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "${resolution.width}x${resolution.height} (${resolution.label})",
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            // Frame rate selector
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Frame Rate", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    FrameRate.entries.forEach { fps ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.selectedFrameRate == fps,
                                onClick = { viewModel.setFrameRate(fps) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(fps.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            // Export button
            Button(
                onClick = { viewModel.startExport() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = uiState.renderProgress.state != RenderState.RENDERING &&
                         uiState.renderProgress.state != RenderState.PREPARING
            ) {
                Icon(Icons.Default.Videocam, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Export", fontSize = 16.sp)
            }

            // Progress section
            AnimatedVisibility(
                visible = uiState.renderProgress.state == RenderState.RENDERING ||
                          uiState.renderProgress.state == RenderState.PREPARING
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            uiState.renderProgress.message,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { uiState.renderProgress.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (uiState.renderProgress.totalClips > 0) {
                            Text(
                                "Clip ${uiState.renderProgress.currentClip + 1} of ${uiState.renderProgress.totalClips}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.cancelExport() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            }

            // Complete state
            AnimatedVisibility(
                visible = uiState.renderProgress.state == RenderState.COMPLETED ||
                          uiState.renderProgress.state == RenderState.FAILED
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.renderProgress.state == RenderState.COMPLETED)
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            if (uiState.renderProgress.state == RenderState.COMPLETED)
                                Icons.Default.CheckCircle
                            else Icons.Default.Error,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            if (uiState.renderProgress.state == RenderState.COMPLETED)
                                "Export Complete!"
                            else "Export Failed",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (uiState.renderProgress.state == RenderState.COMPLETED) {
                            Button(
                                onClick = onNavigateHome,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Back to Home")
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

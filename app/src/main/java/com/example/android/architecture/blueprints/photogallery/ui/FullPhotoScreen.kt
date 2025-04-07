package com.example.android.architecture.blueprints.photogallery.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.android.architecture.blueprints.photogallery.viewmodel.PhotoGalleryViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPhotoScreen(
    viewModel: PhotoGalleryViewModel,
    onNavigateBack: () -> Unit
) {
    val photos = viewModel.photos
    val currentIndex = viewModel.currentPhotoIndex.value

    if (photos.isEmpty()) {
        onNavigateBack()
        return
    }

    val currentPhoto = photos[currentIndex]
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var initialOffsetX by remember { mutableStateOf(0f) }
    var swipeProgress by remember { mutableStateOf(0f) }

    // Control UI visibility
    var showControls by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    var controlsJob: Job? = remember { null }

    // Auto-hide controls after a delay
    fun resetControlsTimer() {
        controlsJob?.cancel()
        showControls = true
        controlsJob = coroutineScope.launch {
            delay(3000)
            showControls = false
        }
    }

    // Initial setup
    LaunchedEffect(currentIndex) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        resetControlsTimer()
    }

    // For swipe navigation animation
    val dragProgress by animateFloatAsState(
        targetValue = swipeProgress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        )
    )

    // For controls fade animation
    val controlsAlpha by animateFloatAsState(
        targetValue = if (showControls) 1f else 0f,
        animationSpec = tween(300)
    )

    Box(modifier = Modifier
        .fillMaxSize()
        .background(Color.Black)
        // Detect tap to toggle controls
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = {
                    showControls = !showControls
                    if (showControls) resetControlsTimer()
                },
                onDoubleTap = {
                    scale = if (scale > 1f) 1f else 2.5f
                    if (scale == 1f) {
                        offsetX = 0f
                        offsetY = 0f
                    }
                    resetControlsTimer()
                }
            )
        }
    ) {
        // Main photo with gestures
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(scale) {
                    detectDragGestures(
                        onDragStart = {
                            initialOffsetX = offsetX
                            resetControlsTimer()
                        },
                        onDragEnd = {
                            // If we're at normal scale and significant horizontal drag
                            if (scale <= 1.05f && abs(swipeProgress) > 0.3f) {
                                coroutineScope.launch {
                                    if (swipeProgress > 0 && currentIndex > 0) {
                                        viewModel.previousPhoto()
                                    } else if (swipeProgress < 0 && currentIndex < photos.size - 1) {
                                        viewModel.nextPhoto()
                                    }
                                    // Reset values
                                    swipeProgress = 0f
                                    offsetX = 0f
                                }
                            } else {
                                // Spring back if not triggering navigation
                                coroutineScope.launch {
                                    swipeProgress = 0f
                                    if (scale <= 1f) offsetX = 0f
                                }
                            }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            resetControlsTimer()

                            // If zoomed in, allow panning
                            if (scale > 1f) {
                                offsetX += dragAmount.x
                                offsetY += dragAmount.y
                                // Limit panning based on zoom level
                                val maxOffset = (scale - 1f) * size.width / 2f
                                offsetX = offsetX.coerceIn(-maxOffset, maxOffset)
                                offsetY = offsetY.coerceIn(-maxOffset, maxOffset)
                            } else {
                                // If at normal scale, track horizontal swipe for navigation
                                offsetX += dragAmount.x
                                swipeProgress = offsetX / size.width
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    // Pinch to zoom
                    detectTransformGestures { _, pan, zoom, _ ->
                        resetControlsTimer()
                        scale = (scale * zoom).coerceIn(1f, 4f)

                        if (scale > 1f) {
                            // Adjust offset for zoom - this makes zooming feel more natural
                            offsetX += pan.x
                            offsetY += pan.y

                            // Limit panning based on zoom level
                            val maxOffset = (scale - 1f) * size.width / 2f
                            offsetX = offsetX.coerceIn(-maxOffset, maxOffset)
                            offsetY = offsetY.coerceIn(-maxOffset, maxOffset)
                        } else {
                            // Reset offset when at normal scale
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                }
        ) {
            // Background image (for swipe visual effect)
            if (currentIndex > 0 && swipeProgress > 0) {
                val previousPhoto = photos[currentIndex - 1]
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(previousPhoto.url)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    loading = {
                        Box(Modifier.fillMaxSize()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = -size.width + (dragProgress * size.width)
                        }
                )
            }

            if (currentIndex < photos.size - 1 && swipeProgress < 0) {
                val nextPhoto = photos[currentIndex + 1]
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(nextPhoto.url)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    loading = {
                        Box(Modifier.fillMaxSize()) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = size.width + (dragProgress * size.width)
                        }
                )
            }

            // Current photo
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(currentPhoto.url)
                    .crossfade(true)
                    .build(),
                contentDescription = currentPhoto.title,
                contentScale = ContentScale.Fit,
                loading = {
                    Box(Modifier.fillMaxSize()) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = if (scale > 1f) offsetX else (dragProgress * size.width)
                        translationY = offsetY
                    }
            )

            // Navigation indicators (when swiping)
            if (swipeProgress != 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    // Left arrow indicator
                    if (swipeProgress > 0.05f && currentIndex > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .size(48.dp)
                                .alpha(swipeProgress * 2f)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(24.dp)
                            )
                        }
                    }

                    // Right arrow indicator
                    if (swipeProgress < -0.05f && currentIndex < photos.size - 1) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .size(48.dp)
                                .alpha(abs(swipeProgress) * 2f)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(24.dp)
                            )
                        }
                    }
                }
            }

            // Top app bar with fade animation
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                // Gradient overlay for better visibility
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.7f),
                                    Color.Transparent
                                )
                            )
                        )
                ) {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                text = currentPhoto.title,
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    Icons.Default.ArrowBack,
                                    contentDescription = "Back to Grid",
                                    tint = Color.White
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { viewModel.toggleFavorite(currentPhoto.id) }) {
                                Icon(
                                    imageVector = if (currentPhoto.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                    contentDescription = if (currentPhoto.isFavorite) "Remove from favorites" else "Add to favorites",
                                    tint = if (currentPhoto.isFavorite) Color.Red else Color.White
                                )
                            }

                            // Zoom indicator
                            if (scale > 1f) {
                                IconButton(
                                    onClick = {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                ) {
                                    Icon(
                                        Icons.Outlined.ZoomOut,
                                        contentDescription = "Reset Zoom",
                                        tint = Color.White
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = { scale = 2.5f }
                                ) {
                                    Icon(
                                        Icons.Outlined.ZoomIn,
                                        contentDescription = "Zoom In",
                                        tint = Color.White
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                }
            }

            // Bottom navigation with fade animation
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                // Gradient overlay for better visibility
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.7f)
                                )
                            )
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                    ) {
                        // Photo counter
                        Text(
                            text = "${currentIndex + 1} / ${photos.size}",
                            color = Color.White,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        // Navigation buttons
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.previousPhoto() },
                                enabled = currentIndex > 0,
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White,
                                    disabledContentColor = Color.Gray
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = SolidColor(if (currentIndex > 0) Color.White else Color.Gray)
                                ),
                                modifier = Modifier.width(150.dp)
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Previous")
                            }

                            OutlinedButton(
                                onClick = { viewModel.nextPhoto() },
                                enabled = currentIndex < photos.size - 1,
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White,
                                    disabledContentColor = Color.Gray
                                ),
                                border = ButtonDefaults.outlinedButtonBorder.copy(
                                    brush = SolidColor(if (currentIndex < photos.size - 1) Color.White else Color.Gray)
                                ),
                                modifier = Modifier.width(150.dp)
                            ) {
                                Text("Next")
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(Icons.Default.ArrowForward, contentDescription = null)
                            }
                        }
                    }
                }
            }

            // Gesture instructions (shown only once)
            var showInstructions by remember { mutableStateOf(true) }
            if (showInstructions) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .clickable {
                            showInstructions = false
                            resetControlsTimer()
                        }
                ) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Gesture Controls",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwipeLeft,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                "Swipe left/right to navigate",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ZoomIn,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                "Pinch to zoom in/out",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TouchApp,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                "Double-tap to toggle zoom",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                "Tap to toggle controls",
                                color = Color.White,
                                fontSize = 16.sp
                            )
                        }

                        Text(
                            "Tap anywhere to dismiss",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            }
        }
    }
}
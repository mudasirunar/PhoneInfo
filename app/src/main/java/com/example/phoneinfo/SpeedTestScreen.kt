package com.example.phoneinfo

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.phoneinfo.ui.theme.*
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedTestScreen(onNavigateBack: () -> Unit) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf("https://www.speakeasy.net/speedtest") }
    val progressAnimatable = remember { Animatable(0f) }
    var isVisible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Helper function to reset the bar instantly
    val resetProgressBar = {
        scope.launch {
            isVisible = false
            progressAnimatable.snapTo(0f) // Teleport back to 0 (No sliding!)
        }
    }

    // Handle system back press for WebView navigation
    BackHandler {
        if (webViewRef?.canGoBack() == true) {
            resetProgressBar()
            webViewRef?.goBack()
        } else {
            onNavigateBack()
        }
    }


    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The 'X' closes the whole screen as typical for modal in-app browsers
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextPrimary)
                }
                Spacer(modifier = Modifier.width(12.dp))
                
                // Creative Progress Bar: The URL pill fills up with color!
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(GlassBackgroundHighlight)
                ) {
                    if (isVisible) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressAnimatable.value)
                                .fillMaxHeight()
                                .background(Color.White.copy(alpha = 0.2f))
                        )
                    }

                    // The URL Content
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Secure",
                            tint = AccentGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isVisible) currentUrl.removePrefix("https://") else getDisplayUrl(currentUrl),
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        },
        containerColor = Color.Transparent, // Let the global background show through
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                factory = { context ->
                    val swipeLayout = SwipeRefreshLayout(context).apply {
                        // Colorful Swipe Loader as requested
                        setColorSchemeColors(
                            AccentCyan.toArgb(),
                            AccentPurple.toArgb(),
                            AccentPink.toArgb(),
                            AccentGreen.toArgb(),
                            AccentOrange.toArgb()
                        )
                        setProgressBackgroundColorSchemeColor(BgGradientMiddle.toArgb())
                        
                        // Let the SwipeRefreshLayout use its default start/rest positions.
                        // Since it's padded below the TopBar, 0 is right underneath it.
                        // It will rest perfectly in view.
                        setProgressViewOffset(false, 0, (40 * context.resources.displayMetrics.density).toInt())
                    }
                    
                    val webView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        
                        // Mobile-optimized view settings
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                    "Chrome/124.0.0.0 Mobile Safari/537.36"
                        settings.useWideViewPort = false
                        settings.loadWithOverviewMode = false
                        settings.setSupportZoom(false)
                        
                        settings.builtInZoomControls = false
                        settings.displayZoomControls = false
                        settings.mediaPlaybackRequiresUserGesture = false

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                url?.let { currentUrl = it }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                url?.let { currentUrl = it }
                            }
                        }
                        
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                super.onProgressChanged(view, newProgress)
                                val targetProgress = newProgress / 100f

                                scope.launch {
                                    // If new loading starts (progress drops), snap to 0
                                    if (targetProgress < progressAnimatable.value) {
                                        isVisible = false
                                        progressAnimatable.snapTo(0f)
                                    }

                                    if (targetProgress > 0f && targetProgress < 1f) {
                                        isVisible = true
                                        // Animate forward smoothly
                                        progressAnimatable.animateTo(
                                            targetValue = targetProgress,
                                            animationSpec = tween(400)
                                        )
                                    } else if (targetProgress >= 1f) {
                                        // Finish the line before hiding
                                        progressAnimatable.animateTo(1f, tween(200))
                                        isVisible = false
                                        progressAnimatable.snapTo(0f)
                                    }
                                }
                                
                                // Keep the pull-to-refresh spinner going until 50% loaded
                                if (newProgress >= 50) {
                                    swipeLayout.isRefreshing = false
                                }
                            }
                        }
                        
                        webViewRef = this
                        loadUrl("https://www.speakeasy.net/speedtest")
                    }
                    
                    swipeLayout.addView(webView)
                    
                    // Swipe to refresh triggers the reload and keeps the spinner active
                    swipeLayout.setOnRefreshListener {
                        resetProgressBar()
                        webView.reload()
                    }
                    
                    swipeLayout
                },
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            )
        }
    }
}


fun getDisplayUrl(url: String): String {
    return url.replaceFirst("https://", "")
        .replaceFirst("http://", "")
        .replaceFirst("www.", "")
        .split("/")
        .firstOrNull() ?: url
}
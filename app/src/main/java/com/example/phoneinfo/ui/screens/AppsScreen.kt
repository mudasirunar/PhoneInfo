package com.example.phoneinfo.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.phoneinfo.R
import com.example.phoneinfo.data.AppDetail
import com.example.phoneinfo.ui.theme.*
import com.example.phoneinfo.viewmodel.PhoneInfoViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

enum class SortType(val title: String) {
    DEFAULT("Name (Ascending)"),
    NAME_DESC("Name (Descending)"),
    SIZE_ASC("App Size (Smallest)"),
    SIZE_DESC("App Size (Largest)"),
    DATE_ASC("Install Date (Oldest)"),
    DATE_DESC("Install Date (Newest)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    onNavigateBack: () -> Unit,
    phoneInfoViewModel: PhoneInfoViewModel = viewModel(
        factory = PhoneInfoViewModel.PhoneInfoViewModelFactory(LocalContext.current.applicationContext)
    )
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val apps by phoneInfoViewModel.appDetails.collectAsState()
    val isLoading by phoneInfoViewModel.isLoadingApps.collectAsState()
    val allAppsSize by phoneInfoViewModel.allAppsSize.collectAsState()
    val installedAppsSize by phoneInfoViewModel.installedAppsSize.collectAsState()
    val systemAppsSize by phoneInfoViewModel.systemAppsSize.collectAsState()
    val deviceInfo by phoneInfoViewModel.deviceInfo.collectAsState()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 1. Check what our UI is currently showing
                val isUiCurrentlyBlocked = apps.any { it.isPermissionDenied }

                // 2. Check the actual system permission right now
                val hasPermissionNow = phoneInfoViewModel.hasUsagePermission()

                // 3. ONLY refresh if the state changed (e.g., from Blocked to Granted)
                if (isUiCurrentlyBlocked && hasPermissionNow) {
                    phoneInfoViewModel.loadApps(forceRefresh = true)
                }
                // Optional: Handle the case where they revoke permission while away
                else if (!isUiCurrentlyBlocked && !hasPermissionNow && apps.isNotEmpty()) {
                    phoneInfoViewModel.loadApps(forceRefresh = true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        phoneInfoViewModel.loadApps()
    }

    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val tabs = listOf(
        "All Apps (${apps.size})", 
        "Installed (${apps.count { !it.isSystemApp }})", 
        "System (${apps.count { it.isSystemApp }})"
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    // Search state
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    // Snackbar & List states
    val snackbarHostState = remember { SnackbarHostState() }
    val pageListStates = listOf(
        rememberLazyListState(),
        rememberLazyListState(),
        rememberLazyListState()
    )

    // Sort state per page
    var allAppsSort by rememberSaveable { mutableStateOf(SortType.DEFAULT.name) }
    var installedAppsSort by rememberSaveable { mutableStateOf(SortType.DEFAULT.name) }
    var systemAppsSort by rememberSaveable { mutableStateOf(SortType.DEFAULT.name) }

    fun getCurrentSort(): SortType {
        return SortType.valueOf(
            when (pagerState.currentPage) {
                0 -> allAppsSort
                1 -> installedAppsSort
                else -> systemAppsSort
            }
        )
    }

    fun setCurrentSort(sort: SortType) {
        when (pagerState.currentPage) {
            0 -> allAppsSort = sort.name
            1 -> installedAppsSort = sort.name
            2 -> systemAppsSort = sort.name
        }
    }

    var previousSort by remember { mutableStateOf(SortType.DEFAULT) }
    var showSortSheet by remember { mutableStateOf(false) }

    val currentSort = getCurrentSort()

    // Clear search on page change
    LaunchedEffect(pagerState.currentPage) {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        }
        snackbarHostState.currentSnackbarData?.dismiss()
    }

    // Reset scroll on sort change or search query change
    LaunchedEffect(currentSort, searchQuery) {
        pageListStates[pagerState.currentPage].scrollToItem(0)
    }

    fun applySearchAndSort(source: List<AppDetail>, query: String, sortOption: String): List<AppDetail> {
        val isSearching = query.isNotBlank()
        val searched = if (isSearching) {
            source.filter { it.name.contains(query, ignoreCase = true) }
        } else {
            source
        }
        
        val sorted = when (SortType.valueOf(sortOption)) {
            SortType.DEFAULT -> searched.sortedBy { it.name.lowercase() }
            SortType.NAME_DESC -> searched.sortedByDescending { it.name.lowercase() }
            SortType.SIZE_ASC -> searched.sortedBy { it.size }
            SortType.SIZE_DESC -> searched.sortedByDescending { it.size }
            SortType.DATE_ASC -> searched.sortedBy { it.installTime }
            SortType.DATE_DESC -> searched.sortedByDescending { it.installTime }
        }
        
        return if (isSearching) {
            sorted.sortedByDescending { it.name.startsWith(query, ignoreCase = true) }
        } else {
            sorted
        }
    }

    val installedAppsList = remember(apps) { apps.filter { !it.isSystemApp } }
    val systemAppsList = remember(apps) { apps.filter { it.isSystemApp } }

    val allAppsFiltered = remember(apps, searchQuery, allAppsSort) {
        applySearchAndSort(apps, searchQuery, allAppsSort)
    }
    
    val installedAppsFiltered = remember(installedAppsList, searchQuery, installedAppsSort) {
        applySearchAndSort(installedAppsList, searchQuery, installedAppsSort)
    }
    
    val systemAppsFiltered = remember(systemAppsList, searchQuery, systemAppsSort) {
        applySearchAndSort(systemAppsList, searchQuery, systemAppsSort)
    }

    val currentTabColor = when (pagerState.currentPage) {
        0 -> AccentTeal
        1 -> AccentPurple
        else -> AccentOrange
    }

    Scaffold(
        snackbarHost = { 
            SnackbarHost(snackbarHostState) { data ->
                androidx.compose.material3.Snackbar(
                    snackbarData = data,
                    containerColor = currentTabColor,
                    contentColor = Color.White,
                    actionColor = Color.White
                )
            }
        },
        topBar = {
            AnimatedContent(
                targetState = isSearchActive,
                label = "SearchBarTransition",
                transitionSpec = {
                    (fadeIn(animationSpec = tween(300)) + slideInHorizontally { it }).togetherWith(
                        fadeOut(animationSpec = tween(300)) + slideOutHorizontally { -it }
                    )
                }
            ) { searchActive ->
                if (searchActive) {
                    TopAppBar(
                        title = {
                            androidx.compose.foundation.text.BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 15.sp),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(AccentCyan),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(GlassBackgroundHighlight)
                                    .focusRequester(focusRequester),
                                decorationBox = { innerTextField ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.height(40.dp).padding(horizontal = 16.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier.weight(1f),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (searchQuery.isEmpty()) {
                                                Text("Search apps...", color = TextSecondary, fontSize = 15.sp)
                                            }
                                            innerTextField()
                                        }
                                    }
                                }
                            )
                            LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        },
                        navigationIcon = {
                            IconButton(onClick = { 
                                isSearchActive = false
                                searchQuery = ""
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Search", tint = TextPrimary)
                            }
                        },
                        actions = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear Text", tint = TextPrimary)
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                } else {
                    TopAppBar(
                        title = { Text("Applications", color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                            }
                        },
                        actions = {
                            IconButton(onClick = { isSearchActive = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = TextPrimary)
                            }
                            IconButton(onClick = { showSortSheet = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort", tint = TextPrimary)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            }
        },
        containerColor = AppBackground,
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Custom Tab Indicator
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(GlassBackgroundHighlight)
                    .height(46.dp)
            ) {
                val tabWidth = maxWidth / tabs.size
                val indicatorOffset by animateDpAsState(
                    targetValue = tabWidth * pagerState.currentPage + (tabWidth * pagerState.currentPageOffsetFraction),
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "IndicatorOffset"
                )
                
                val indicatorColor by animateColorAsState(
                    targetValue = when (pagerState.currentPage) {
                        0 -> AccentTeal
                        1 -> AccentPurple
                        else -> AccentOrange
                    },
                    animationSpec = tween(300),
                    label = "IndicatorColor"
                )

                val isMoving = pagerState.isScrollInProgress || pagerState.currentPageOffsetFraction != 0f
                val chipScale by animateFloatAsState(
                    targetValue = if (isMoving) 1.10f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "ChipScale"
                )
                val glowAlpha by animateFloatAsState(
                    targetValue = if (isMoving) 0.8f else 0f,
                    animationSpec = tween(300),
                    label = "GlowAlpha"
                )
                val glowRadius by animateDpAsState(
                    targetValue = if (isMoving) 24.dp else 0.dp,
                    animationSpec = tween(300),
                    label = "GlowRadius"
                )

                // Indicator Pill
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .padding(4.dp)
                        .graphicsLayer {
                            scaleX = chipScale
                            scaleY = chipScale
                        }
                        .drawBehind {
                            if (glowRadius.toPx() > 0f) {
                                val frameworkPaint = android.graphics.Paint().apply {
                                    color = indicatorColor.toArgb()
                                    alpha = (glowAlpha * 255).toInt()
                                    maskFilter = android.graphics.BlurMaskFilter(
                                        glowRadius.toPx(),
                                        android.graphics.BlurMaskFilter.Blur.NORMAL
                                    )
                                }
                                drawContext.canvas.nativeCanvas.drawRoundRect(
                                    0f, 0f, size.width, size.height,
                                    20.dp.toPx(), 20.dp.toPx(),
                                    frameworkPaint
                                )
                            }
                        }
                        .clip(RoundedCornerShape(20.dp))
                        .background(indicatorColor)
                )

                // Tab Texts
                Row(modifier = Modifier.fillMaxSize()) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = pagerState.currentPage == index
                        // Using a simple threshold for color change since fraction can cause midway states
                        val distance = Math.abs(pagerState.currentPage + pagerState.currentPageOffsetFraction - index)
                        val textColor = if (distance < 0.5f) Color.White else TextSecondary
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Storage Summary Section
            val targetStorageSize = when (pagerState.currentPage) {
                1 -> installedAppsSize
                2 -> systemAppsSize
                else -> allAppsSize
            }
            
            val totalStorageSize = maxOf(1L, deviceInfo.internalStorage?.total ?: 1L)
            val fillFraction = (targetStorageSize.toFloat() / totalStorageSize.toFloat()).coerceIn(0f, 1f)
            
            val animatedStorageFillFraction by animateFloatAsState(
                targetValue = fillFraction,
                animationSpec = tween(500),
                label = "StorageFillFraction"
            )
            
            val storageIndicatorColor = when (pagerState.currentPage) {
                0 -> AccentTeal
                1 -> AccentPurple
                else -> AccentOrange
            }

            val isPermissionMissing = apps.any { it.isPermissionDenied }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                if (isPermissionMissing) {
                    // --- PERMISSION REQUIRED BANNER ---
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(AccentRed.copy(alpha = 0.1f))
                            .border(1.dp, AccentRed.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Usage Access Required",
                                color = AccentRed,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Grant permission to see accurate app sizes.",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }

                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier.height(36.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Grant", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // --- STANDARD STORAGE STATS (Visible only when permission is granted) ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Storage Occupied",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${phoneInfoViewModel.formatBytes(targetStorageSize)} / ${phoneInfoViewModel.formatBytes(totalStorageSize)}",
                            color = storageIndicatorColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(GlassBackgroundHighlight)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedStorageFillFraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(storageIndicatorColor)
                        )
                    }
                }
            }

            // Pager Content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val currentTabApps = when (page) {
                    1 -> installedAppsFiltered
                    2 -> systemAppsFiltered
                    else -> allAppsFiltered
                }
                
                val currentTabSort = when (page) {
                    1 -> SortType.valueOf(installedAppsSort)
                    2 -> SortType.valueOf(systemAppsSort)
                    else -> SortType.valueOf(allAppsSort)
                }
                
                if (isLoading && apps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentTeal)
                    }
                } else if (currentTabApps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No applications found", color = TextSecondary, fontSize = 16.sp)
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        LazyColumn(
                            state = pageListStates[page],
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp, start = 20.dp, end = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(currentTabApps, key = { it.packageName }) { app ->
                                AppListItem(
                                    app = app, 
                                    phoneInfoViewModel = phoneInfoViewModel, 
                                    currentSort = currentTabSort,
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                        CustomScrollbar(
                            listState = pageListStates[page],
                            color = currentTabColor,
                            modifier = Modifier.align(Alignment.CenterEnd).padding(top = 12.dp, bottom = 40.dp, end = 4.dp)
                        )
                    }
                }
            }
        }

        // Sort Bottom Sheet
        if (showSortSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSortSheet = false },
                containerColor = AppBackground,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecondary) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = 40.dp)
                ) {
                    Text(
                        text = "Sort Applications",
                        color = TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    SortType.entries.forEach { sortOption ->
                        val isSelected = currentSort == sortOption
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) AccentTeal.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable {
                                    previousSort = currentSort
                                    setCurrentSort(sortOption)
                                    showSortSheet = false
                                    coroutineScope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Sorted by ${sortOption.title}",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            setCurrentSort(previousSort)
                                        }
                                    }
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sortOption.title,
                                color = if (isSelected) AccentTeal else TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = AccentTeal)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppListItem(app: AppDetail, phoneInfoViewModel: PhoneInfoViewModel, currentSort: SortType = SortType.DEFAULT, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var iconBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    
    LaunchedEffect(app.packageName) {
        withContext(Dispatchers.IO) {
            val bitmap = try {
                val drawable = context.packageManager.getApplicationIcon(app.packageName)
                drawable.toBitmap().asImageBitmap()
            } catch (e: Exception) {
                ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap()?.asImageBitmap()
            }
            withContext(Dispatchers.Main) {
                iconBitmap = bitmap
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(GlassBackground)
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Icon
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap!!,
                contentDescription = app.name,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(GlassBackgroundHighlight)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // App Details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.name,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = app.packageName,
                color = TextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "v${app.versionName}",
                    color = AccentCyan,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Column(horizontalAlignment = Alignment.End) {
                    val displaySize = when {
                        app.isPermissionDenied -> "Permission Required"
                        app.totalSize == -1L  -> "Calculating..."
                        else -> phoneInfoViewModel.formatBytes(app.totalSize)
                    }

                    val textColor = if (app.isPermissionDenied) AccentRed else TextPrimary

                    Text(
                        text = displaySize,
                        color = textColor,
                        fontSize = 12.sp
                    )
                    if (currentSort == SortType.DATE_ASC || currentSort == SortType.DATE_DESC) {
                        Text(
                            text = phoneInfoViewModel.formatTimestampToDate(app.installTime),
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CustomScrollbar(listState: androidx.compose.foundation.lazy.LazyListState, modifier: Modifier = Modifier, color: Color) {
    var isDragging by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }

    val isVisible = listState.isScrollInProgress || isDragging
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isVisible) 150 else 500,
            delayMillis = if (isVisible) 0 else 1000
        ),
        label = "ScrollbarAlpha"
    )
    
    LaunchedEffect(isDragging) {
        if (isDragging) {
            isExpanded = true
        } else {
            kotlinx.coroutines.delay(500)
            isExpanded = false
        }
    }

    val scrollbarWidth by animateDpAsState(
        targetValue = if (isExpanded) 8.dp else 4.dp,
        animationSpec = tween(300),
        label = "ScrollbarWidth"
    )
    
    val coroutineScope = rememberCoroutineScope()
    
    if (alpha > 0f) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxHeight()
                .width(24.dp) // wide touch target for easier dragging
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val viewPortHeight = size.height.toFloat()
                            val layoutInfo = listState.layoutInfo
                            if (layoutInfo.totalItemsCount > 0) {
                                val fraction = (change.position.y / viewPortHeight).coerceIn(0f, 1f)
                                val maxIndex = maxOf(0, layoutInfo.totalItemsCount - layoutInfo.visibleItemsInfo.size)
                                val targetItem = (fraction * maxIndex).toInt()
                                coroutineScope.launch {
                                    listState.scrollToItem(targetItem)
                                }
                            }
                        }
                    )
                }
        ) {
            val viewPortHeight = maxHeight.value
            val layoutInfo = listState.layoutInfo
            if (layoutInfo.totalItemsCount == 0) return@BoxWithConstraints

            val visibleItemsCount = layoutInfo.visibleItemsInfo.size
            
            // To make the scroll fluid, factor in the pixel offset of the first visible item
            val firstVisibleItem = layoutInfo.visibleItemsInfo.firstOrNull()
            val itemOffset = firstVisibleItem?.offset ?: 0
            val itemSize = maxOf(1, firstVisibleItem?.size ?: 1)
            
            val exactIndex = listState.firstVisibleItemIndex.toFloat() + (Math.abs(itemOffset).toFloat() / itemSize.toFloat())
            val maxExactIndex = maxOf(0.1f, layoutInfo.totalItemsCount.toFloat() - visibleItemsCount.toFloat())
            
            val scrollFraction = (exactIndex / maxExactIndex).coerceIn(0f, 1f)
            
            val scrollbarHeight = maxOf(20f, viewPortHeight * (visibleItemsCount.toFloat() / layoutInfo.totalItemsCount))
            val scrollbarY = scrollFraction * (viewPortHeight - scrollbarHeight)

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = scrollbarY.dp)
                    .width(scrollbarWidth)
                    .height(scrollbarHeight.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color.copy(alpha = alpha))
            )
        }
    }
}

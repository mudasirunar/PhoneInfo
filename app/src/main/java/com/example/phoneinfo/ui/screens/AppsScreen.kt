package com.example.phoneinfo.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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

enum class SortType(val title: String) {
    DEFAULT("Name (Default)"),
    NAME_DESC("Name (Descending)"),
    SIZE_ASC("App Size (Ascending)"),
    SIZE_DESC("App Size (Descending)"),
    DATE_ASC("Install Date (Ascending)"),
    DATE_DESC("Install Date (Descending)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    onNavigateBack: () -> Unit,
    phoneInfoViewModel: PhoneInfoViewModel = viewModel(
        factory = PhoneInfoViewModel.PhoneInfoViewModelFactory(LocalContext.current.applicationContext)
    )
) {
    val apps by phoneInfoViewModel.appDetails.collectAsState()
    val isLoading by phoneInfoViewModel.isLoadingApps.collectAsState()

    LaunchedEffect(Unit) {
        phoneInfoViewModel.loadApps()
    }

    val coroutineScope = rememberCoroutineScope()
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
    val listState = rememberLazyListState()
    var previousSort by remember { mutableStateOf(SortType.DEFAULT) }

    // Clear search on page change
    LaunchedEffect(pagerState.currentPage) {
        if (isSearchActive) {
            isSearchActive = false
            searchQuery = ""
        }
        snackbarHostState.currentSnackbarData?.dismiss()
    }

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

    var showSortSheet by remember { mutableStateOf(false) }

    val currentSort = getCurrentSort()

    // Reset scroll on sort change
    LaunchedEffect(currentSort, pagerState.currentPage) {
        listState.scrollToItem(0)
    }

    val filteredApps = remember(apps, pagerState.currentPage, searchQuery, currentSort) {
        val sectionApps = when (pagerState.currentPage) {
            1 -> apps.filter { !it.isSystemApp }
            2 -> apps.filter { it.isSystemApp }
            else -> apps
        }

        val searched = if (searchQuery.isNotBlank()) {
            sectionApps.filter { it.name.contains(searchQuery, ignoreCase = true) }
        } else {
            sectionApps
        }

        when (currentSort) {
            SortType.DEFAULT -> searched.sortedBy { it.name.lowercase() }
            SortType.NAME_DESC -> searched.sortedByDescending { it.name.lowercase() }
            SortType.SIZE_ASC -> searched.sortedBy { it.size }
            SortType.SIZE_DESC -> searched.sortedByDescending { it.size }
            SortType.DATE_ASC -> searched.sortedBy { it.installTime }
            SortType.DATE_DESC -> searched.sortedByDescending { it.installTime }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search apps...", color = TextSecondary, fontSize = 15.sp) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = GlassBackgroundHighlight,
                                    unfocusedContainerColor = GlassBackgroundHighlight,
                                    disabledContainerColor = Color.Transparent,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    cursorColor = AccentCyan
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .focusRequester(focusRequester),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { /* Close keyboard */ })
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

                // Indicator Pill
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth)
                        .fillMaxHeight()
                        .padding(4.dp)
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

            // Pager Content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                if (isLoading && apps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AccentTeal)
                    }
                } else if (filteredApps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No applications found", color = TextSecondary, fontSize = 16.sp)
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp, start = 20.dp, end = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(filteredApps) { app ->
                            AppListItem(app = app, phoneInfoViewModel = phoneInfoViewModel)
                        }
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
fun AppListItem(app: AppDetail, phoneInfoViewModel: PhoneInfoViewModel) {
    val context = LocalContext.current
    var iconBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    
    LaunchedEffect(app.packageName) {
        try {
            val drawable = context.packageManager.getApplicationIcon(app.packageName)
            iconBitmap = drawable.toBitmap().asImageBitmap()
        } catch (e: Exception) {
            iconBitmap = ContextCompat.getDrawable(context, R.mipmap.ic_launcher)?.toBitmap()?.asImageBitmap()
        }
    }

    Row(
        modifier = Modifier
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
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "v${app.versionName}",
                    color = AccentCyan,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = phoneInfoViewModel.formatBytes(app.size),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

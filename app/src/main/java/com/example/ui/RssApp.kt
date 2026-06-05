package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.RssFeedEntity
import com.example.data.RssItemEntity
import com.example.service.DownloadState
import com.example.service.PlaybackInfo
import com.example.service.PodcastDownloadService
import com.example.service.PodcastPlayerManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssApp(
    viewModel: RssViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf("Articles") }
    
    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val articles by viewModel.articlesList.collectAsState()
    val savedArticles by viewModel.savedArticles.collectAsState()
    val feeds by viewModel.feedsList.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedArticle by viewModel.selectedArticle.collectAsState()
    val availableFlairs by viewModel.availableFlairs.collectAsState()
    val selectedFlair by viewModel.selectedFlair.collectAsState()
    val playbackState by viewModel.playerManager.playbackState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = when (currentTab) {
                                "Articles" -> "RSS // ${selectedCategory.uppercase(Locale.ROOT)}"
                                "Saved" -> "SAVED // READING LIST"
                                "Podcasts" -> "MEDIA // PODCASTS"
                                "Feeds" -> "CONFIG // FEEDS"
                                else -> "READER"
                            },
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.refreshAll() },
                            modifier = Modifier.testTag("refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Feeds"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )

                // Category Chips (Only on Articles screen)
                if (currentTab == "Articles") {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp, top = 2.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(categories) { category ->
                            val isSelected = selectedCategory == category
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectCategory(category) },
                                label = {
                                    Text(
                                        text = if (category == "All") "All Stories" else category,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 11.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.testTag("category_chip_$category")
                            )
                        }
                    }

                    // Reddit Flair Filter Chips (Only on Articles screen when available)
                    if (availableFlairs.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item {
                                Text(
                                    text = "Filter:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(end = 2.dp)
                                )
                            }
                            items(availableFlairs) { flair ->
                                val isSelected = selectedFlair == flair
                                ElevatedFilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.selectedFlair.value = flair },
                                    label = {
                                        Text(
                                            text = flair,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp
                                        )
                                    },
                                    colors = FilterChipDefaults.elevatedFilterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                    ),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.testTag("flair_chip_$flair")
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                NavigationBarItem(
                    selected = currentTab == "Articles",
                    onClick = { currentTab = "Articles" },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Articles") },
                    label = { Text("Feed") },
                    modifier = Modifier.testTag("nav_feed_tab")
                )
                NavigationBarItem(
                    selected = currentTab == "Saved",
                    onClick = { currentTab = "Saved" },
                    icon = { Icon(Icons.Default.Favorite, contentDescription = "Saved Articles") },
                    label = { Text("Saved") },
                    modifier = Modifier.testTag("nav_saved_tab")
                )
                NavigationBarItem(
                    selected = currentTab == "Podcasts",
                    onClick = { currentTab = "Podcasts" },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Podcasts & Media") },
                    label = { Text("Podcasts") },
                    modifier = Modifier.testTag("nav_podcasts_tab")
                )
                NavigationBarItem(
                    selected = currentTab == "Feeds",
                    onClick = { currentTab = "Feeds" },
                    icon = { Icon(Icons.Default.List, contentDescription = "Manage Feeds") },
                    label = { Text("Feeds") },
                    modifier = Modifier.testTag("nav_feeds_tab")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                "Articles" -> {
                    ArticlesScreen(
                        articles = articles,
                        isRefreshing = isRefreshing,
                        viewModel = viewModel,
                        onArticleClick = { viewModel.selectArticle(it) },
                        onSaveToggle = { viewModel.toggleSaveArticle(it) },
                        onRefresh = { viewModel.refreshAll() }
                    )
                }
                "Saved" -> {
                    SavedScreen(
                        savedArticles = savedArticles,
                        viewModel = viewModel,
                        onArticleClick = { viewModel.selectArticle(it) },
                        onRemoveSave = { viewModel.toggleSaveArticle(it) }
                    )
                }
                "Podcasts" -> {
                    PodcastScreen(
                        viewModel = viewModel,
                        onPlayEpisode = { item -> viewModel.playerManager.playEpisode(item) },
                        onDownloadEpisode = { item -> viewModel.downloadService.downloadEpisode(item) },
                        onDeleteEpisode = { item -> viewModel.downloadService.deleteEpisode(item) }
                    )
                }
                "Feeds" -> {
                    FeedsScreen(
                        feeds = feeds,
                        viewModel = viewModel,
                        onAddFeed = { url, name, cat -> viewModel.addCustomFeed(url, name, cat) },
                        onDeleteFeed = { url -> viewModel.deleteFeed(url) }
                    )
                }
            }

            if (isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            AnimatedVisibility(
                visible = playbackState.item != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                MiniPlayerCard(
                    playbackState = playbackState,
                    playerManager = viewModel.playerManager,
                    downloadService = viewModel.downloadService
                )
            }
        }
    }

    // Article Detail Overlay / Dialog
    selectedArticle?.let { article ->
        ArticleDetailDialog(
            article = article,
            onDismiss = { viewModel.selectArticle(null) },
            onSaveToggle = { viewModel.toggleSaveArticle(article) },
            onReportClick = {
                viewModel.reportArticle(article.guid)
                viewModel.selectArticle(null)
            }
        )
    }
}

@Composable
fun ArticlesScreen(
    articles: List<RssItemEntity>,
    isRefreshing: Boolean,
    viewModel: RssViewModel,
    onArticleClick: (RssItemEntity) -> Unit,
    onSaveToggle: (RssItemEntity) -> Unit,
    onRefresh: () -> Unit
) {
    if (articles.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Articles Found",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Try pulling to refresh or subscribing to more feeds.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("retry_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Refresh Feeds")
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(articles, key = { it.guid }) { article ->
                ArticleCard(
                    article = article,
                    viewModel = viewModel,
                    onClick = { onArticleClick(article) },
                    onSaveToggle = { onSaveToggle(article) }
                )
            }
        }
    }
}

@Composable
fun SavedScreen(
    savedArticles: List<RssItemEntity>,
    viewModel: RssViewModel,
    onArticleClick: (RssItemEntity) -> Unit,
    onRemoveSave: (RssItemEntity) -> Unit
) {
    if (savedArticles.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Your Reading List is Empty",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Save interesting articles for offline quick reading by clicking the heart button in cards or detail views.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(savedArticles, key = { it.guid }) { article ->
                ArticleCard(
                    article = article,
                    viewModel = viewModel,
                    onClick = { onArticleClick(article) },
                    onSaveToggle = { onRemoveSave(article) }
                )
            }
        }
    }
}

@Composable
fun ArticleCard(
    article: RssItemEntity,
    viewModel: RssViewModel,
    onClick: () -> Unit,
    onSaveToggle: () -> Unit
) {
    val context = LocalContext.current
    val sourceAbbrev = when {
        article.feedUrl.contains("thedailystar", ignoreCase = true) -> "DS"
        article.feedUrl.contains("dhakatribune", ignoreCase = true) -> "DT"
        article.feedUrl.contains("prothomalo", ignoreCase = true) -> "PA"
        article.feedUrl.contains("tbsnews", ignoreCase = true) -> "BS"
        article.feedUrl.contains("r/bangladesh", ignoreCase = true) -> "BD"
        article.feedUrl.contains("r/dhaka", ignoreCase = true) -> "DK"
        article.feedUrl.contains("r/BangladeshMedia", ignoreCase = true) -> "BM"
        article.feedUrl.contains("UCthidp7_fIIn6_pPLaY3sBg", ignoreCase = true) -> "SY"
        article.feedUrl.contains("UCp6ZfV9Mh6f7gS8_T", ignoreCase = true) -> "JM"
        article.feedUrl.contains("UCvclA86D9P7VnU8m0S_Vj9g", ignoreCase = true) -> "KF"
        article.feedUrl.contains("reddit", ignoreCase = true) -> "RD"
        article.feedUrl.contains("youtube", ignoreCase = true) -> "YT"
        else -> article.category.take(2).uppercase(Locale.ROOT)
    }

    val sourceLabel = when {
        article.feedUrl.contains("thedailystar", ignoreCase = true) -> "The Daily Star"
        article.feedUrl.contains("dhakatribune", ignoreCase = true) -> "Dhaka Tribune"
        article.feedUrl.contains("prothomalo", ignoreCase = true) -> "Prothom Alo"
        article.feedUrl.contains("tbsnews", ignoreCase = true) -> "The Business Standard"
        article.feedUrl.contains("r/bangladesh", ignoreCase = true) -> "r/Bangladesh"
        article.feedUrl.contains("r/dhaka", ignoreCase = true) -> "r/Dhaka"
        article.feedUrl.contains("r/BangladeshMedia", ignoreCase = true) -> "r/BangladeshMedia"
        article.feedUrl.contains("UCthidp7_fIIn6_pPLaY3sBg", ignoreCase = true) -> "Somoy TV"
        article.feedUrl.contains("UCp6ZfV9Mh6f7gS8_T", ignoreCase = true) -> "Jamuna TV"
        article.feedUrl.contains("UCvclA86D9P7VnU8m0S_Vj9g", ignoreCase = true) -> "Khalid Farhan"
        article.feedUrl.contains("reddit", ignoreCase = true) -> "Reddit"
        article.feedUrl.contains("youtube", ignoreCase = true) -> "YouTube"
        else -> "RSS Feed"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                1.dp,
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(4.dp)
            )
            .testTag("article_card_${article.guid}"),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Header Source info with circle badge logo and content moderation controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(MaterialTheme.colorScheme.onBackground, RoundedCornerShape(2.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = sourceAbbrev,
                                color = MaterialTheme.colorScheme.background,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "$sourceLabel • ${formatTimeAgo(article.pubDateLong)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box {
                        var menuExpanded by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(20.dp).testTag("more_options_${article.guid}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Report Content", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.reportArticle(article.guid)
                                    Toast.makeText(context, "Under auditing: content has been reported and hidden.", Toast.LENGTH_LONG).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Block publisher", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.muteFeed(article.feedUrl)
                                    Toast.makeText(context, "$sourceLabel has been blocked and hidden.", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Heading matching Bold Typography
                Text(
                    text = article.title,
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Short summary / preview
                if (article.description.isNotBlank()) {
                    Text(
                        text = article.description,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (article.audioUrl != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    val downloadStates by viewModel.downloadService.downloadStates.collectAsState()
                    val playbackState by viewModel.playerManager.playbackState.collectAsState()
                    
                    val isDownloaded = viewModel.downloadService.isDownloaded(article)
                    val downloadState = downloadStates[article.guid] ?: DownloadState.Idle
                    val isCurrentPlaying = playbackState.item?.guid == article.guid && playbackState.isPlaying

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "🎙️",
                                fontSize = 12.sp
                            )
                            Column {
                                Text(
                                    text = "Podcast",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (isDownloaded) "Offline" else "Stream",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 8.sp
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            when (downloadState) {
                                is DownloadState.Downloading -> {
                                    CircularProgressIndicator(
                                        progress = { downloadState.progress / 100f },
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 1.5.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                else -> {
                                    if (isDownloaded) {
                                        IconButton(
                                            onClick = { viewModel.downloadService.deleteEpisode(article) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete downloaded episode",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    } else {
                                        IconButton(
                                            onClick = { viewModel.downloadService.downloadEpisode(article) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Download episode",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = { viewModel.playerManager.playEpisode(article) },
                                contentPadding = PaddingValues(horizontal = 6.dp),
                                modifier = Modifier.height(24.dp),
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isCurrentPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = if (isCurrentPlaying) Icons.Default.Clear else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (isCurrentPlaying) "Pause" else "Play",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Footer actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        // Flair Badge if present
                        article.flair?.let { flair ->
                            Row(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        RoundedCornerShape(2.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = flair.uppercase(Locale.ROOT),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onSaveToggle,
                            modifier = Modifier
                                .size(28.dp)
                                .testTag("save_article_${article.guid}")
                        ) {
                            Icon(
                                imageVector = if (article.isSaved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = if (article.isSaved) "Remove from Saved" else "Save Article",
                                tint = if (article.isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            if (article.thumbnailUrl != null) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(LocalContext.current)
                        .data(article.thumbnailUrl)
                        .crossfade(true)
                        .precision(coil.size.Precision.EXACT)
                        .build(),
                    contentDescription = "Article image thumbnail",
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .align(Alignment.CenterVertically),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
fun FeedsScreen(
    feeds: List<RssFeedEntity>,
    viewModel: RssViewModel,
    onAddFeed: (String, String, String) -> Unit,
    onDeleteFeed: (String) -> Unit
) {
    val context = LocalContext.current
    val blockedFeeds by viewModel.blockedFeeds.collectAsState()
    var inputUrl by remember { mutableStateOf("") }
    var inputTitle by remember { mutableStateOf("") }
    var inputCategory by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Form Section Info
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        RoundedCornerShape(4.dp)
                    ),
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.02f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Subscribe to RSS Feed",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.SansSerif,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add custom XML/RSS links from blogs and news platforms to expand your news timeline.",
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        label = { Text("Feed URL") },
                        placeholder = { Text("https://example.com/feed.xml") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("feed_url_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(4.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = inputTitle,
                            onValueChange = { inputTitle = it },
                            label = { Text("Feed Name") },
                            placeholder = { Text("Tech News Spot") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("feed_name_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(4.dp)
                        )
                        OutlinedTextField(
                            value = inputCategory,
                            onValueChange = { inputCategory = it },
                            label = { Text("Category") },
                            placeholder = { Text("Technology") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("feed_category_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (inputUrl.isBlank() || !inputUrl.startsWith("http")) {
                                Toast.makeText(context, "Please enter a valid HTTP/HTTPS URL", Toast.LENGTH_LONG).show()
                            } else {
                                onAddFeed(inputUrl, inputTitle, inputCategory)
                                inputUrl = ""
                                inputTitle = ""
                                inputCategory = ""
                                Toast.makeText(context, "Subscribed successfully!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("subscribe_button"),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Add Subscribed Feed", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Active Subscribed Feeds Head
        item {
            Text(
                text = "Active Subscriptions",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(top = 8.dp),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        items(feeds) { feed ->
            val isBlocked = blockedFeeds.contains(feed.url)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (isBlocked) MaterialTheme.colorScheme.error.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(4.dp)
                    ),
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isBlocked) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = feed.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isBlocked) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "MUTED",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.errorContainer,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = feed.url,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Category: ${feed.category}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isBlocked) {
                            TextButton(
                                onClick = {
                                    viewModel.unmuteFeed(feed.url)
                                    Toast.makeText(context, "${feed.title} is now unblocked.", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("unmute_feed_${feed.url}")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Unblock", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    viewModel.muteFeed(feed.url)
                                    Toast.makeText(context, "${feed.title} is now muted.", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("mute_feed_${feed.url}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Mute publisher feed",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }

                        if (!feed.isPredefined) {
                            IconButton(
                                onClick = { onDeleteFeed(feed.url) },
                                modifier = Modifier.testTag("delete_feed_${feed.url}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Feed",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            Text(
                                text = "SYSTEM",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // Compliance & Publisher credentials
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(4.dp)
                    ),
                shape = RoundedCornerShape(4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Publisher & Compliance Info",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.SansSerif,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This app aggregates public RSS news feeds in compliance with Google Play Developer Policies. Content moderation controls are available on each news card.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("PUBLISHER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Dispatch Media Group", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("CONTACT EMAIL", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("contact@dispatch-rss.com", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Divider(color = MaterialTheme.colorScheme.surfaceVariant)
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Privacy Policy & Terms of Service",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        var showPolicyDialog by remember { mutableStateOf(false) }
                        TextButton(
                            onClick = { showPolicyDialog = true },
                            modifier = Modifier.testTag("view_privacy_policy_button")
                        ) {
                            Text("View Policy", fontSize = 12.sp, fontWeight = FontWeight.Black)
                        }
                        
                        if (showPolicyDialog) {
                            PrivacyPolicyDialog(onDismiss = { showPolicyDialog = false })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Privacy Policy",
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Effective Date: June 4, 2026\n\n" +
                            "This Privacy Policy describes how the Dispatch application manages your privacy. " +
                            "Please read carefully to understand how your data is treated.\n\n" +
                            "1. DATA COLLECTION\n" +
                            "Dispatch operates as a client-side RSS reader. We do NOT collect, transmit, share, " +
                            "or sell any personally identifiable information (PII). All feed subscriptions, " +
                            "content moderation blocks, and saved articles are stored securely and locally on your device " +
                            "using an offline Room SQLite database.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "2. NETWORK USAGE\n" +
                            "To refresh and fetch new stories, the application connects directly via the internet to " +
                            "public feed URLs provided by respective creators or configured by the user. These requests " +
                            "go directly to external news servers; no proxy or intermediary analytics servers are used.\n\n" +
                            "3. USER RIGHTS & MODERATION\n" +
                            "You are in full control of your feeds. You can add or unsubscribe from custom feeds at any " +
                            "time. You can flag inappropriate content or block publishers using the card controls. " +
                            "All moderation acts instantly on your local database.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "4. CONTACT DETAILS\n" +
                            "If you have inquiries regarding compliance, content copyrights, or data rights, contact us at:\n" +
                            "Email: contact@dispatch-rss.com\n" +
                            "Dispatch Media Group, Dhaka, Bangladesh.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(4.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false)
    )
}

@Composable
fun ArticleDetailDialog(
    article: RssItemEntity,
    onDismiss: () -> Unit,
    onSaveToggle: () -> Unit,
    onReportClick: () -> Unit
) {
    val context = LocalContext.current

    val sourceLabel = when {
        article.feedUrl.contains("thedailystar", ignoreCase = true) -> "The Daily Star"
        article.feedUrl.contains("dhakatribune", ignoreCase = true) -> "Dhaka Tribune"
        article.feedUrl.contains("prothomalo", ignoreCase = true) -> "Prothom Alo"
        article.feedUrl.contains("tbsnews", ignoreCase = true) -> "The Business Standard"
        article.feedUrl.contains("r/bangladesh", ignoreCase = true) -> "r/Bangladesh"
        article.feedUrl.contains("r/dhaka", ignoreCase = true) -> "r/Dhaka"
        article.feedUrl.contains("r/BangladeshMedia", ignoreCase = true) -> "r/BangladeshMedia"
        article.feedUrl.contains("UCthidp7_fIIn6_pPLaY3sBg", ignoreCase = true) -> "Somoy TV"
        article.feedUrl.contains("UCp6ZfV9Mh6f7gS8_T", ignoreCase = true) -> "Jamuna TV"
        article.feedUrl.contains("UCvclA86D9P7VnU8m0S_Vj9g", ignoreCase = true) -> "Khalid Farhan"
        article.feedUrl.contains("reddit", ignoreCase = true) -> "Reddit"
        article.feedUrl.contains("youtube", ignoreCase = true) -> "YouTube"
        else -> "RSS Feed"
    }

    val sourceAbbrev = when {
        article.feedUrl.contains("thedailystar", ignoreCase = true) -> "DS"
        article.feedUrl.contains("dhakatribune", ignoreCase = true) -> "DT"
        article.feedUrl.contains("prothomalo", ignoreCase = true) -> "PA"
        article.feedUrl.contains("tbsnews", ignoreCase = true) -> "BS"
        article.feedUrl.contains("r/bangladesh", ignoreCase = true) -> "BD"
        article.feedUrl.contains("r/dhaka", ignoreCase = true) -> "DK"
        article.feedUrl.contains("r/BangladeshMedia", ignoreCase = true) -> "BM"
        article.feedUrl.contains("UCthidp7_fIIn6_pPLaY3sBg", ignoreCase = true) -> "SY"
        article.feedUrl.contains("UCp6ZfV9Mh6f7gS8_T", ignoreCase = true) -> "JM"
        article.feedUrl.contains("UCvclA86D9P7VnU8m0S_Vj9g", ignoreCase = true) -> "KF"
        article.feedUrl.contains("reddit", ignoreCase = true) -> "RD"
        article.feedUrl.contains("youtube", ignoreCase = true) -> "YT"
        else -> article.category.take(2).uppercase(Locale.ROOT)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .statusBarsPadding()
                .navigationBarsPadding()
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(4.dp)
                ),
            shape = RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Close Detail")
                    }

                    Row {
                        IconButton(
                            onClick = {
                                onReportClick()
                                Toast.makeText(context, "Thank you. This content has been reported and hidden.", Toast.LENGTH_LONG).show()
                            },
                            modifier = Modifier.testTag("report_article_dialog_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Report content",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }

                        IconButton(onClick = onSaveToggle) {
                            Icon(
                                imageVector = if (article.isSaved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Save toggling",
                                tint = if (article.isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = {
                                try {
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, "${article.title}\n\nRead more at: ${article.link}")
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Share Article")
                                    context.startActivity(shareIntent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Error sharing link", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share Link")
                        }
                    }
                }

                // Scrollable Content
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    item {
                        // Source and Date row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(MaterialTheme.colorScheme.onBackground, RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = sourceAbbrev,
                                    color = MaterialTheme.colorScheme.background,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Text(
                                text = "$sourceLabel • ${formatTimeAgo(article.pubDateLong)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Title matching Bold Typography (H1 heavy design)
                        Text(
                            text = article.title,
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            lineHeight = 30.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Status pill row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = article.category,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )

                            // Flair pill if present
                            article.flair?.let { flair ->
                                Text(
                                    text = flair,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.secondaryContainer,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "OFFLINE STORED",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    // Large Top Image
                    if (article.thumbnailUrl != null) {
                        item {
                            AsyncImage(
                                model = coil.request.ImageRequest.Builder(LocalContext.current)
                                    .data(article.thumbnailUrl)
                                    .crossfade(true)
                                    .precision(coil.size.Precision.EXACT)
                                    .build(),
                                contentDescription = "Article main image",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(4.dp)
                                    ),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }

                    // Content / Summary Plain view
                    item {
                        Text(
                            text = article.description,
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            fontFamily = FontFamily.SansSerif,
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(36.dp))

                        // Deep Link Button / Full read in Browser
                        Button(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(article.link))
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open web browser", Toast.LENGTH_LONG).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("open_browser_button"),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text("Open Full Story", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}

fun formatTimeAgo(timeMs: Long): String {
    val diff = System.currentTimeMillis() - timeMs
    if (diff < 0) return "Just now"
    val diffMinutes = diff / (60 * 1000)
    if (diffMinutes < 1) return "Just now"
    if (diffMinutes < 60) return "${diffMinutes}m ago"
    val diffHours = diffMinutes / 60
    if (diffHours < 24) return "${diffHours}h ago"
    val diffDays = diffHours / 24
    if (diffDays < 7) return "${diffDays}d ago"
    
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timeMs))
}

@Composable
fun PodcastScreen(
    viewModel: RssViewModel,
    onPlayEpisode: (RssItemEntity) -> Unit,
    onDownloadEpisode: (RssItemEntity) -> Unit,
    onDeleteEpisode: (RssItemEntity) -> Unit
) {
    val articles by viewModel.articlesList.collectAsState()
    val downloadStates by viewModel.downloadService.downloadStates.collectAsState()
    val playbackState by viewModel.playerManager.playbackState.collectAsState()

    val podcastEpisodes = remember(articles) {
        articles.filter { it.audioUrl != null }
    }

    var showOnlyDownloaded by remember { mutableStateOf(false) }

    val displayedEpisodes = remember(podcastEpisodes, showOnlyDownloaded, downloadStates) {
        if (showOnlyDownloaded) {
            podcastEpisodes.filter { viewModel.downloadService.isDownloaded(it) }
        } else {
            podcastEpisodes
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = { showOnlyDownloaded = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!showOnlyDownloaded) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (!showOnlyDownloaded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("All Podcasts", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { showOnlyDownloaded = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showOnlyDownloaded) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (showOnlyDownloaded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Downloaded", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (displayedEpisodes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (showOnlyDownloaded) "No Offline Downloads" else "No Podcast Episodes Found",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (showOnlyDownloaded) "Any podcasts you download will appear here so you can play them without an internet connection."
                               else "Check back later! Or trigger a refresh of RSS feeds to load the latest episodes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 96.dp)
            ) {
                items(displayedEpisodes, key = { it.guid }) { item ->
                    PodcastEpisodeCard(
                        item = item,
                        viewModel = viewModel,
                        downloadState = downloadStates[item.guid] ?: DownloadState.Idle,
                        isCurrentPlaying = playbackState.item?.guid == item.guid && playbackState.isPlaying,
                        onPlayClick = { onPlayEpisode(item) },
                        onDownloadClick = { onDownloadEpisode(item) },
                        onDeleteClick = { onDeleteEpisode(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun PodcastEpisodeCard(
    item: RssItemEntity,
    viewModel: RssViewModel,
    downloadState: DownloadState,
    isCurrentPlaying: Boolean,
    onPlayClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isDownloaded = viewModel.downloadService.isDownloaded(item)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(4.dp)
            ),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (item.thumbnailUrl != null) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = "Podcast Thumbnail",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                RoundedCornerShape(4.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📻",
                            fontSize = 20.sp
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Published • ${formatTimeAgo(item.pubDateLong)}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (item.description.isNotEmpty()) {
                Text(
                    text = item.description,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when (downloadState) {
                        is DownloadState.Downloading -> {
                            CircularProgressIndicator(
                                progress = { downloadState.progress / 100f },
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Downloading ${downloadState.progress}%",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        else -> {
                            if (isDownloaded) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Downloaded offline",
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Offline Ready",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                Text(
                                    text = "Online Stream",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isDownloaded && downloadState !is DownloadState.Downloading) {
                        IconButton(
                            onClick = onDeleteClick,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete download",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else if (!isDownloaded && downloadState !is DownloadState.Downloading) {
                        IconButton(
                            onClick = onDownloadClick,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Download to local device",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Button(
                        onClick = onPlayClick,
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp),
                        modifier = Modifier.height(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCurrentPlaying) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (isCurrentPlaying) Icons.Default.Clear else Icons.Default.PlayArrow,
                            contentDescription = if (isCurrentPlaying) "Pause" else "Play",
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isCurrentPlaying) "Pause" else "Play",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MiniPlayerCard(
    playbackState: PlaybackInfo,
    playerManager: PodcastPlayerManager,
    downloadService: PodcastDownloadService
) {
    val item = playbackState.item ?: return
    var showFullPlayer by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showFullPlayer = true }
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            .testTag("mini_player_bar"),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (item.thumbnailUrl != null) {
                        AsyncImage(
                            model = item.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(4.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📻", fontSize = 18.sp)
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (downloadService.isDownloaded(item)) "Playing offline" else "Streaming online",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { playerManager.skipBackward() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Skip back 15s",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            if (playbackState.isPlaying) {
                                playerManager.pause()
                            } else {
                                playerManager.resume()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Clear else Icons.Default.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = { playerManager.stop() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Stop",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (playbackState.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { playbackState.progressMs.toFloat() / playbackState.durationMs.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                )
            }
        }
    }

    if (showFullPlayer) {
        FullPlayerDialog(
            playbackState = playbackState,
            playerManager = playerManager,
            downloadService = downloadService,
            onDismiss = { showFullPlayer = false }
        )
    }
}

@Composable
fun FullPlayerDialog(
    playbackState: PlaybackInfo,
    playerManager: PodcastPlayerManager,
    downloadService: PodcastDownloadService,
    onDismiss: () -> Unit
) {
    val item = playbackState.item ?: return
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val progressValue = if (isDragging) dragValue else {
        if (playbackState.durationMs > 0) {
            playbackState.progressMs.toFloat() / playbackState.durationMs.toFloat()
        } else 0f
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Minimize Player",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        text = "Now Playing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { playerManager.stop(); onDismiss() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Player",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 16.dp)
                ) {
                    if (item.thumbnailUrl != null) {
                        AsyncImage(
                            model = item.thumbnailUrl,
                            contentDescription = "Episode Cover",
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .aspectRatio(1f)
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ),
                                    RoundedCornerShape(4.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("📻", fontSize = 72.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        minLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val author = if (item.feedUrl.contains("ted", ignoreCase = true)) "TED Talks" else "Podcast Episode"
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = progressValue,
                        onValueChange = { newVal ->
                            isDragging = true
                            dragValue = newVal
                        },
                        onValueChangeFinished = {
                            playerManager.seekTo((dragValue * playbackState.durationMs).toInt())
                            isDragging = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatMillis(playbackState.progressMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatMillis(playbackState.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { playerManager.skipBackward() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Skip Back 15 seconds",
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .clickable {
                                if (playbackState.isPlaying) {
                                    playerManager.pause()
                                } else {
                                    playerManager.resume()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Clear else Icons.Default.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(40.dp),
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = { playerManager.skipForward() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Skip Forward 15 seconds",
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

fun formatMillis(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}


package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.RssFeedEntity
import com.example.data.RssItemEntity
import com.example.data.RssRepository
import com.example.service.PodcastDownloadService
import com.example.service.PodcastPlayerManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RssViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: RssRepository
    private val prefs = application.getSharedPreferences("dispatch_compliance_prefs", Context.MODE_PRIVATE)

    val reportedArticles = MutableStateFlow<Set<String>>(emptySet())
    val blockedFeeds = MutableStateFlow<Set<String>>(emptySet())

    val downloadService: PodcastDownloadService
    val playerManager: PodcastPlayerManager

    init {
        val database = AppDatabase.getDatabase(application)
        repository = RssRepository(database)
        downloadService = PodcastDownloadService(application, repository)
        playerManager = PodcastPlayerManager(application)
        
        reportedArticles.value = prefs.getStringSet("reported_articles_guids", emptySet())?.toSet() ?: emptySet()
        blockedFeeds.value = prefs.getStringSet("blocked_feeds_urls", emptySet())?.toSet() ?: emptySet()
        
        // Check prepopulate feeds and perform background refresh on launch
        viewModelScope.launch {
            repository.checkAndPrepopulateFeeds()
            refreshAll()
        }
    }

    val feedsList: StateFlow<List<RssFeedEntity>> = repository.feeds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedArticles: StateFlow<List<RssItemEntity>> = repository.savedItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val selectedCategory = MutableStateFlow("All")
    val searchQuery = MutableStateFlow("")
    val isRefreshing = MutableStateFlow(false)
    val selectedArticle = MutableStateFlow<RssItemEntity?>(null)

    // Dynamic categories computed from active feeds
    val categories: StateFlow<List<String>> = repository.feeds.map { feeds ->
        val list = mutableListOf("All")
        val uniqueCats = feeds.map { it.category }.distinct().sorted()
        list.addAll(uniqueCats)
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("All"))

    val selectedFlair = MutableStateFlow<String?>("All")

    @Suppress("OPT_IN_USAGE")
    val rawArticlesList: StateFlow<List<RssItemEntity>> = selectedCategory
        .flatMapLatest { category ->
            repository.getItemsByCategory(category)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableFlairs: StateFlow<List<String>> = rawArticlesList.map { items ->
        val flairs = items.mapNotNull { it.flair }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        if (flairs.isNotEmpty()) {
            listOf("All") + flairs
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val articlesList: StateFlow<List<RssItemEntity>> = combine(
        rawArticlesList,
        selectedFlair,
        reportedArticles,
        blockedFeeds
    ) { items, flair, reported, blocked ->
        val filtered = items.filter { it.guid !in reported && it.feedUrl !in blocked }
        if (flair == null || flair == "All") {
            filtered
        } else {
            filtered.filter { it.flair == flair }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectCategory(category: String) {
        selectedCategory.value = category
        selectedFlair.value = "All"
    }

    fun searchArticles(query: String) {
        searchQuery.value = query
    }

    fun selectArticle(article: RssItemEntity?) {
        selectedArticle.value = article
    }

    fun refreshAll() {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                repository.refreshAllFeeds()
            } catch (e: Exception) {
                // Handle or log error
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun toggleSaveArticle(article: RssItemEntity) {
        viewModelScope.launch {
            repository.toggleSaveArticle(article.guid, !article.isSaved)
            // Update selected article state if open to show the actual saved status immediately
            val currentSelected = selectedArticle.value
            if (currentSelected != null && currentSelected.guid == article.guid) {
                selectedArticle.value = currentSelected.copy(isSaved = !article.isSaved)
            }
        }
    }

    fun addCustomFeed(url: String, title: String, category: String) {
        viewModelScope.launch {
            isRefreshing.value = true
            try {
                repository.addFeed(url, title, category)
            } catch (e: Exception) {
                // Log or handle error
            } finally {
                isRefreshing.value = false
            }
        }
    }

    fun deleteFeed(url: String) {
        viewModelScope.launch {
            repository.deleteFeed(url)
        }
    }

    fun reportArticle(guid: String) {
        val updated = reportedArticles.value.toMutableSet().apply { add(guid) }
        reportedArticles.value = updated
        prefs.edit().putStringSet("reported_articles_guids", updated).apply()
    }

    fun muteFeed(feedUrl: String) {
        val updated = blockedFeeds.value.toMutableSet().apply { add(feedUrl) }
        blockedFeeds.value = updated
        prefs.edit().putStringSet("blocked_feeds_urls", updated).apply()
    }

    fun unmuteFeed(feedUrl: String) {
        val updated = blockedFeeds.value.toMutableSet().apply { remove(feedUrl) }
        blockedFeeds.value = updated
        prefs.edit().putStringSet("blocked_feeds_urls", updated).apply()
    }

    fun clearCachedItems() {
        viewModelScope.launch {
            repository.clearCache()
        }
    }

    override fun onCleared() {
        super.onCleared()
        playerManager.stop()
    }
}

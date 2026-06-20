package xyz.mpv.rex.cinemine.viewmodel

import androidx.compose.runtime.*
import xyz.mpv.rex.cinemine.model.MineTab
import xyz.mpv.rex.features.cinehub.model.MovieItem
import xyz.mpv.rex.features.cinehub.model.TvShowItem
import xyz.mpv.rex.features.cinetube.model.YoutubeVideo

class CineMineViewModel {
    var searchQuery by mutableStateOf("")
    var activeTab by mutableStateOf(MineTab.UNIFIED)

    // --- NEW STABLE ARCHITECTURE STATES ---
    // Reactive hooks to cleanly drive the Season-Wise dynamic TV bottom sheets[span_2](start_span)[span_2](end_span)
    var selectedTvShowForSheet by mutableStateOf<TvShowItem?>(null)

    // Reactive State Lists for direct unified rows injection mapping
    val filteredLocalMovies = mutableStateListOf<MovieItem>()
    val filteredLocalShows = mutableStateListOf<TvShowItem>()
    val filteredTubeVideos = mutableStateListOf<YoutubeVideo>()
    val filteredOnlineCloud = mutableStateListOf<MovieItem>()

    /**
     * Deep Global Filtration across all 3 integrated subsystems simultaneously
     */
    fun updateSearchAndFilter(
        query: String,
        movies: List<MovieItem>,
        shows: List<TvShowItem>,
        tubeVideos: List<YoutubeVideo>,
        cloudItems: List<MovieItem>
    ) {
        searchQuery = query
        val target = query.trim().lowercase()

        if (target.isEmpty()) {
            resetFeeds(movies, shows, tubeVideos, cloudItems)
            return
        }

        // 1. CineHub Local Movies Filtration Pipeline
        filteredLocalMovies.clear()
        filteredLocalMovies.addAll(movies.filter { 
            it.title.lowercase().contains(target) || it.genre.lowercase().contains(target) 
        })

        // 2. CineHub Local TV Series Filtration Pipeline
        filteredLocalShows.clear()
        filteredLocalShows.addAll(shows.filter { 
            it.title.lowercase().contains(target) || it.genre.lowercase().contains(target) 
        })

        // 3. CineTube (Invidious Streaming Node) Filtration Pipeline
        // UPDATE: Expanded capability to query through creator handles/authors seamlessly[span_3](start_span)[span_3](end_span)
        filteredTubeVideos.clear()
        filteredTubeVideos.addAll(tubeVideos.filter { 
            it.title.lowercase().contains(target) || it.author.lowercase().contains(target)[span_4](start_span)[span_4](end_span)
        })

        // 4. CineMax Online (Cloud Server Channels) Filtration Pipeline
        filteredOnlineCloud.clear()
        filteredOnlineCloud.addAll(cloudItems.filter { 
            it.title.lowercase().contains(target) || it.genre.lowercase().contains(target) 
        })
    }

    /**
     * Restores dynamic datasets back to their pristine row structures instantly
     */
    fun resetFeeds(
        movies: List<MovieItem>,
        shows: List<TvShowItem>,
        tubeVideos: List<YoutubeVideo>,
        cloudItems: List<MovieItem>
    ) {
        filteredLocalMovies.clear(); filteredLocalMovies.addAll(movies)
        filteredLocalShows.clear(); filteredLocalShows.addAll(shows)
        filteredTubeVideos.clear(); filteredTubeVideos.addAll(tubeVideos)
        filteredOnlineCloud.clear(); filteredOnlineCloud.addAll(cloudItems)
    }

    // --- SHEET STATE HANDLERS ---
    fun openTvShowDetails(show: TvShowItem) {
        selectedTvShowForSheet = show
    }

    fun closeTvShowDetails() {
        selectedTvShowForSheet = null
    }
}

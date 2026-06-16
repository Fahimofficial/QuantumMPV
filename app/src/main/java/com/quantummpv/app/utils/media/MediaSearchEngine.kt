package com.quantummpv.app.utils.media

import com.quantummpv.app.domain.media.model.VideoFolder
import com.quantummpv.app.domain.media.model.Video

data class VideoIndex(
    val video: Video,
    val nameLower: String,
    val tokens: List<String>
)

data class FolderIndex(
    val folder: VideoFolder,
    val nameLower: String,
    val tokens: List<String>
)

object MediaSearchEngine {
    // FAST LOOKUPS
    private val videoMap = mutableMapOf<String, VideoIndex>()
    private val folderMap = mutableMapOf<String, FolderIndex>()

    // -------------------------
    // INDEXING (BUILD + SYNC)
    // -------------------------
    fun buildIndex(
        folders: List<VideoFolder>,
        videosByFolder: Map<String, List<Video>>
    ) {
        videoMap.clear()
        folderMap.clear()

        for (folder in folders) {
            val folderIndex = FolderIndex(
                folder = folder,
                nameLower = folder.name.lowercase(),
                tokens = tokenize(folder.name)
            )

            folderMap[folder.path] = folderIndex

            val videos = videosByFolder[folder.bucketId].orEmpty()

            for (video in videos) {
                val index = VideoIndex(
                    video = video,
                    nameLower = video.displayName.lowercase(),
                    tokens = tokenize(video.displayName)
                )
                videoMap[video.path] = index
            }
        }
    }

    // -------------------------
    // REAL-TIME UPDATES
    // -------------------------
    fun upsertVideo(video: Video) {
        videoMap[video.path] = VideoIndex(
            video = video,
            nameLower = video.displayName.lowercase(),
            tokens = tokenize(video.displayName)
        )
    }

    fun deleteVideo(videoPath: String) {
        videoMap.remove(videoPath)
    }

    fun upsertFolder(folder: VideoFolder) {
        folderMap[folder.path] = FolderIndex(
            folder = folder,
            nameLower = folder.name.lowercase(),
            tokens = tokenize(folder.name)
        )
    }

    fun deleteFolder(folderPath: String) {
        folderMap.remove(folderPath)
        // remove videos under folder path
        videoMap.entries.removeIf {
            it.value.video.path.startsWith(folderPath)
        }
    }

    // -------------------------
    // SEARCH
    // -------------------------
    fun search(query: String, limit: Int = 50): List<Any> {
        if (query.isBlank()) return emptyList()

        val q = query.lowercase()
        val qTokens = tokenize(query)

        val results = mutableListOf<Pair<Any, Int>>()

        // folders
        for (folder in folderMap.values) {
            val score = score(folder.nameLower, folder.tokens, q, qTokens)            if (score > 0) {
                results.add(folder.folder to score)
            }
        }

        // videos
        for (video in videoMap.values) {
            val score = score(video.nameLower, video.tokens, q, qTokens)
            if (score > 0) {
                results.add(video.video to score)
            }
        }

        return results
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    // -------------------------
    // SCORING ENGINE
    // -------------------------
    private fun score(
        text: String,
        tokens: List<String>,
        query: String,
        qTokens: List<String>
    ): Int {
        var score = 0

        // exact match (Spotify top priority)
        if (text == query) return 1000

        // prefix match (very strong)
        if (text.startsWith(query)) score += 200

        // substring match
        if (text.contains(query)) score += 120

        // token matching
        for (qt in qTokens) {
            for (t in tokens) {
                if (t == qt) score += 80
                else if (t.startsWith(qt)) score += 40
            }
        }

        // sequential fuzzy match (lightweight)
        if (isSequentialMatch(text, query)) score += 60
        return score
    }

    // -------------------------
    // HELPERS
    // -------------------------
    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .split(" ", "_", "-", ".", "/")
            .filter { it.isNotBlank() }
    }

    private fun isSequentialMatch(text: String, query: String): Boolean {
        var i = 0
        for (c in text) {
            if (i < query.length && c == query[i]) {
                i++
            }
        }
        return i == query.length
    }
}

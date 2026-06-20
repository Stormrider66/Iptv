package com.iptv.player.data

/** A live, VOD or series category. */
data class Category(
    val id: String,
    val name: String,
)

/** A live TV channel. */
data class LiveChannel(
    val streamId: String,
    val name: String,
    val icon: String?,
    val epgChannelId: String?,
    val categoryId: String?,
    val number: Int?,
)

/** A VOD movie. */
data class Movie(
    val streamId: String,
    val name: String,
    val icon: String?,
    val categoryId: String?,
    val containerExtension: String?,
    val rating: String?,
)

/** A series (show). */
data class Series(
    val seriesId: String,
    val name: String,
    val cover: String?,
    val categoryId: String?,
    val plot: String?,
    val rating: String?,
)

/** A single season of a series. */
data class Season(
    val seasonNumber: Int,
    val episodes: List<Episode>,
)

/** A single episode of a series. */
data class Episode(
    val id: String,
    val title: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val containerExtension: String?,
    val plot: String?,
    val cover: String?,
)

/** A single EPG programme entry. */
data class EpgEntry(
    val title: String,
    val description: String,
    val startMillis: Long,
    val endMillis: Long,
)

/** Kinds of playable content. */
enum class StreamKind { LIVE, MOVIE, EPISODE }

/** Everything needed to start playback. */
data class PlaybackItem(
    val kind: StreamKind,
    val id: String,
    val title: String,
    val containerExtension: String? = null,
)

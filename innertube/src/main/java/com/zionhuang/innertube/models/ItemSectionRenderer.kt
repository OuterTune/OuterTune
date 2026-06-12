package com.zionhuang.innertube.models

import kotlinx.serialization.Serializable

/**
 * Search summary responses now wrap individual results in [ItemSectionRenderer]s instead of
 * grouping them into titled [MusicShelfRenderer] shelves.
 */
@Serializable
data class ItemSectionRenderer(
    val contents: List<MusicShelfRenderer.Content>?,
)

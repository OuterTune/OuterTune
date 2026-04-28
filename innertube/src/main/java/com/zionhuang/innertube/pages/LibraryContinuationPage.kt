package com.zionhuang.innertube.pages

import com.zionhuang.innertube.models.YTItem

data class LibraryContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
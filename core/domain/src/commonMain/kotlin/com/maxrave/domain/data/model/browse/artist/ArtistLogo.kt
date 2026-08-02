package com.maxrave.domain.data.model.browse.artist

import kotlinx.serialization.Serializable

@Serializable
data class ArtistLogo(
    val logoUrl: String,
    val bgColorHex: String?,
    val width: Int,
    val height: Int,
)

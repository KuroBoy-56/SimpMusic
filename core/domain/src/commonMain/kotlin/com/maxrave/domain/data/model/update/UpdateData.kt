package com.maxrave.domain.data.model.update

data class UpdateData(
    val tagName: String,
    val releaseTime: String? = null,
    val body: String,
    val htmlUrl: String? = null,
)

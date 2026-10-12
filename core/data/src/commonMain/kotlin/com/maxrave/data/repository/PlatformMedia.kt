package com.maxrave.data.repository

/**
 * True where the system player only decodes AAC audio (iOS/AVPlayer), so stream selection must
 * never hand it a WebM/Opus itag.
 */
internal expect val onlyAacAudioPlayable: Boolean

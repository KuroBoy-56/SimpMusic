package com.maxrave.data.repository

import com.maxrave.domain.data.model.update.UpdateData
import com.maxrave.domain.repository.UpdateRepository
import com.maxrave.domain.utils.Resource
import com.maxrave.kotlinytmusicscraper.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

internal class UpdateRepositoryImpl(
    @Suppress("UNUSED_PARAMETER") private val youTube: YouTube,
) : UpdateRepository {
    override fun checkForGithubReleaseUpdate(): Flow<Resource<UpdateData>> =
        flow {
            emit(Resource.Error<UpdateData>("Updates disabled"))
        }.flowOn(Dispatchers.Default)

    override fun checkForFdroidUpdate(): Flow<Resource<UpdateData>> =
        flow {
            emit(Resource.Error<UpdateData>("Updates disabled"))
        }.flowOn(Dispatchers.Default)
}
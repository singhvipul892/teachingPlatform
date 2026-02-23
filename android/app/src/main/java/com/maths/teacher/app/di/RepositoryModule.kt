package com.maths.teacher.app.di

import com.maths.teacher.app.data.repository.AuthRepository
import com.maths.teacher.app.data.repository.DefaultAuthRepository
import com.maths.teacher.app.data.repository.DefaultVideoRepository
import com.maths.teacher.app.data.repository.VideoRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindVideoRepository(impl: DefaultVideoRepository): VideoRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: DefaultAuthRepository): AuthRepository
}

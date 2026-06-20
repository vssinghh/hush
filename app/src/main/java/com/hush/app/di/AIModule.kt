package com.hush.app.di

import com.hush.app.data.repository.AIEngineImpl
import com.hush.app.data.repository.PackageResolverImpl
import com.hush.app.data.repository.SpeechRecognizerWrapperImpl
import com.hush.app.domain.repository.AIEngine
import com.hush.app.domain.repository.PackageResolver
import com.hush.app.domain.repository.SpeechRecognizerWrapper
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AIModule {

    @Binds
    @Singleton
    abstract fun bindAIEngine(
        aiEngineImpl: AIEngineImpl
    ): AIEngine

    @Binds
    @Singleton
    abstract fun bindSpeechRecognizerWrapper(
        speechRecognizerWrapperImpl: SpeechRecognizerWrapperImpl
    ): SpeechRecognizerWrapper

    @Binds
    @Singleton
    abstract fun bindPackageResolver(
        packageResolverImpl: PackageResolverImpl
    ): PackageResolver
}

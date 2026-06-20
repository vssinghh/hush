package com.hush.app.di

import com.hush.app.domain.repository.AIEngine
import com.hush.app.domain.repository.PackageResolver
import com.hush.app.domain.repository.SpeechRecognizerWrapper
import com.hush.app.mock.FakeAIEngine
import com.hush.app.mock.FakePackageResolver
import com.hush.app.mock.FakeSpeechRecognizerWrapper
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AIModule::class]
)
interface TestAIModule {
    @Binds
    @Singleton
    fun bindAIEngine(fake: FakeAIEngine): AIEngine

    @Binds
    @Singleton
    fun bindSpeechRecognizerWrapper(fake: FakeSpeechRecognizerWrapper): SpeechRecognizerWrapper

    @Binds
    @Singleton
    fun bindPackageResolver(fake: FakePackageResolver): PackageResolver
}

package com.hush.app.di

import com.hush.app.data.repository.HistoryRepositoryImpl
import com.hush.app.data.repository.RuleRepositoryImpl
import com.hush.app.domain.repository.HistoryRepository
import com.hush.app.domain.repository.RuleRepository
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
    abstract fun bindRuleRepository(
        ruleRepositoryImpl: RuleRepositoryImpl
    ): RuleRepository

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(
        historyRepositoryImpl: HistoryRepositoryImpl
    ): HistoryRepository
}

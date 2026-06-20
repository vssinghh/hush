package com.hush.app.di

import android.content.Context
import com.hush.app.data.pref.OnboardingPrefs
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    @Provides
    @Singleton
    fun provideOnboardingPrefs(@ApplicationContext context: Context): OnboardingPrefs {
        return OnboardingPrefs(context)
    }
}

package com.hush.app.di

import com.hush.app.domain.permission.PermissionManager
import com.hush.app.mock.FakePermissionManager
import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [PermissionModule::class]
)
interface TestPermissionModule {

    @Binds
    @Singleton
    fun bindPermissionManager(fake: FakePermissionManager): PermissionManager
}

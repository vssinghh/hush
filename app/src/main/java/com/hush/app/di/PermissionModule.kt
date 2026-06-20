package com.hush.app.di

import com.hush.app.data.repository.PermissionManagerImpl
import com.hush.app.domain.permission.PermissionManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PermissionModule {

    @Binds
    @Singleton
    abstract fun bindPermissionManager(
        permissionManagerImpl: PermissionManagerImpl
    ): PermissionManager
}

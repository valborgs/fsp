package dev.comon.fsp.core.database.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.comon.fsp.core.database.FspDatabase
import dev.comon.fsp.core.database.dao.LocalSessionDao
import dev.comon.fsp.core.database.dao.OutboxDao
import dev.comon.fsp.core.database.dao.SurveyDefinitionDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FspDatabase = FspDatabase.create(context)

    @Provides
    fun provideSurveyDefinitionDao(database: FspDatabase): SurveyDefinitionDao = database.surveyDefinitionDao()

    @Provides
    fun provideLocalSessionDao(database: FspDatabase): LocalSessionDao = database.localSessionDao()

    @Provides
    fun provideOutboxDao(database: FspDatabase): OutboxDao = database.outboxDao()
}

package com.storyboard.editor.di

import android.content.Context
import com.storyboard.editor.data.converter.TimestampParser
import com.storyboard.editor.data.repository.ProjectRepository
import com.storyboard.editor.media.renderer.VideoRenderer
import com.storyboard.editor.media.player.PreviewManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideProjectRepository(
        @ApplicationContext context: Context
    ): ProjectRepository {
        return ProjectRepository(context)
    }

    @Provides
    @Singleton
    fun provideTimestampParser(): TimestampParser {
        return TimestampParser()
    }

    @Provides
    @Singleton
    fun provideVideoRenderer(
        @ApplicationContext context: Context
    ): VideoRenderer {
        return VideoRenderer(context)
    }

    @Provides
    @Singleton
    fun providePreviewManager(
        @ApplicationContext context: Context
    ): PreviewManager {
        return PreviewManager(context)
    }
}

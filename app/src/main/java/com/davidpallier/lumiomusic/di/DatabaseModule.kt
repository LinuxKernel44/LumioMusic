package com.davidpallier.lumiomusic.di

import android.content.Context
import androidx.room.Room
import com.davidpallier.lumiomusic.data.db.AppDatabase
import com.davidpallier.lumiomusic.data.db.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "lumiomusic.db")
            // Pre-release: schema is still evolving, freely drop/recreate rather than
            // hand-writing migrations for every phase. Add real migrations before shipping.
            .fallbackToDestructiveMigration(true)
            .build()

    @Provides
    fun provideTrackDao(db: AppDatabase): TrackDao = db.trackDao()
}

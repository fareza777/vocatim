package com.vocatim.app.di

import android.content.Context
import androidx.room.Room
import com.vocatim.app.data.db.TranscriptDao
import com.vocatim.app.data.db.VocatimDatabase
import com.vocatim.app.data.model.ModelManager
import com.vocatim.app.data.transcribe.WhisperTranscriber
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): VocatimDatabase =
        Room.databaseBuilder(context, VocatimDatabase::class.java, "vocatim.db").build()

    @Provides
    fun provideTranscriptDao(db: VocatimDatabase): TranscriptDao = db.transcriptDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        // Model downloads are long-running streams; no read deadline, but
        // detect dead connections via readTimeout between packets.
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideModelManager(
        @ApplicationContext context: Context,
        client: OkHttpClient,
    ): ModelManager = ModelManager(
        modelsDir = File(context.filesDir, "models"),
        client = client,
    )

    @Provides
    @Singleton
    fun provideWhisperTranscriber(modelManager: ModelManager): WhisperTranscriber =
        WhisperTranscriber(modelManager)
}

package com.vocatim.app.di

import android.content.Context
import androidx.room.Room
import com.vocatim.app.data.audio.AudioImporter
import com.vocatim.app.data.db.TranscriptDao
import com.vocatim.app.data.db.VocatimDatabase
import com.vocatim.app.data.model.ModelManager
import com.vocatim.app.data.prefs.RtfStore
import com.vocatim.app.data.prefs.UserPrefs
import com.vocatim.app.data.repository.ImportCoordinator
import com.vocatim.app.data.repository.StartupRecovery
import com.vocatim.app.data.repository.TranscriptRepository
import com.vocatim.app.data.transcribe.ThreadPolicy
import com.vocatim.app.data.transcribe.TranscriptionProgressHolder
import com.vocatim.app.data.transcribe.TranscriptionRunner
import com.vocatim.app.data.transcribe.WhisperTranscriber
import com.vocatim.app.service.RecordingStateHolder
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
        Room.databaseBuilder(context, VocatimDatabase::class.java, "vocatim.db")
            .addMigrations(VocatimDatabase.MIGRATION_3_4)
            // Only for pre-v3 leftovers; from v3 on, real migrations apply.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideTranscriptDao(db: VocatimDatabase): TranscriptDao = db.transcriptDao()

    @Provides
    @Singleton
    fun provideTranscriptRepository(dao: TranscriptDao): TranscriptRepository =
        TranscriptRepository(dao)

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

    @Provides
    @Singleton
    fun provideRtfStore(@ApplicationContext context: Context): RtfStore = RtfStore(context)

    @Provides
    @Singleton
    fun provideUserPrefs(@ApplicationContext context: Context): UserPrefs = UserPrefs(context)

    @Provides
    @Singleton
    fun provideAudioImporter(@ApplicationContext context: Context): AudioImporter =
        AudioImporter(context)

    @Provides
    @Singleton
    fun provideImportCoordinator(
        @ApplicationContext context: Context,
        repository: TranscriptRepository,
        userPrefs: UserPrefs,
    ): ImportCoordinator = ImportCoordinator(context, repository, userPrefs)

    @Provides
    @Singleton
    fun provideRecordingStateHolder(): RecordingStateHolder = RecordingStateHolder()

    @Provides
    @Singleton
    fun provideTranscriptionProgressHolder(): TranscriptionProgressHolder =
        TranscriptionProgressHolder()

    @Provides
    @Singleton
    fun provideTranscriptionRunner(
        @ApplicationContext context: Context,
        repository: TranscriptRepository,
        transcriber: WhisperTranscriber,
        importer: AudioImporter,
        rtfStore: RtfStore,
        progressHolder: TranscriptionProgressHolder,
        userPrefs: UserPrefs,
    ): TranscriptionRunner = TranscriptionRunner(
        repository = repository,
        transcriber = transcriber,
        importer = importer,
        rtfStore = rtfStore,
        progressHolder = progressHolder,
        userPrefs = userPrefs,
        threadPolicy = ThreadPolicy(context),
        importDir = File(context.filesDir, "imports"),
    )

    @Provides
    @Singleton
    fun provideStartupRecovery(
        @ApplicationContext context: Context,
        repository: TranscriptRepository,
    ): StartupRecovery = StartupRecovery(context, repository)
}

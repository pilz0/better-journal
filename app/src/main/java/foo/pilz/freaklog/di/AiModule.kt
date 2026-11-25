package foo.pilz.freaklog.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import foo.pilz.freaklog.data.ai.profiles.AiKeyCipher
import foo.pilz.freaklog.data.ai.profiles.KeystoreAiKeyCipher
import foo.pilz.freaklog.data.ai.vitals.HealthConnectVitalsDataSource
import foo.pilz.freaklog.data.ai.vitals.VitalsDataSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

  @Binds @Singleton abstract fun bindAiKeyCipher(cipher: KeystoreAiKeyCipher): AiKeyCipher

  @Binds
  @Singleton
  abstract fun bindVitalsDataSource(source: HealthConnectVitalsDataSource): VitalsDataSource
}

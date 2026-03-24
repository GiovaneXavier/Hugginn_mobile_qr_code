package com.srbr.huginn.di

import com.srbr.huginn.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Chave HMAC para validação do QR de cadastro — injetada via BuildConfig.
     * BuildConfig lê de local.properties ou variáveis de ambiente do CI/CD.
     * Nunca hardcoded no código-fonte.
     */
    @Provides
    @Singleton
    @Named("qrHmacKey")
    fun provideQrHmacKey(): String = BuildConfig.QR_HMAC_KEY

    /**
     * Chave HMAC para geração dos tokens QR dinâmicos — mesmo formato do NFC.
     */
    @Provides
    @Singleton
    @Named("tokenHmacKey")
    fun provideTokenHmacKey(): String = BuildConfig.TOKEN_HMAC_KEY
}

package com.quantummpv.app.di

import com.quantummpv.app.domain.anime4k.Anime4KManager
import com.quantummpv.app.domain.hdr.HdrToysManager
import com.quantummpv.app.network.AndroidCookieJar
import com.quantummpv.app.repository.IntroDbRepository
import com.quantummpv.app.repository.subtitle.OnlineSubtitleFileStore
import com.quantummpv.app.repository.subtitle.OnlineSubtitleOrchestrator
import com.quantummpv.app.repository.subtitlehub.MpvRxSubtitleHubRepository
import com.quantummpv.app.repository.wyzie.WyzieSearchRepository
import com.quantummpv.app.repository.ai.AiClient
import com.quantummpv.app.repository.ai.AiService
import com.quantummpv.app.repository.ai.OpenCodeClient
import com.quantummpv.app.repository.ai.AnthropicClient
import com.quantummpv.app.repository.ai.GroqClient
import com.quantummpv.app.repository.ai.GroqSpeechClient
import com.quantummpv.app.repository.ai.LlamaCppInference
import com.quantummpv.app.repository.ai.LocalAiClient
import com.quantummpv.app.repository.ai.ModelDownloadManager
import com.quantummpv.app.repository.ai.OpenAiClient
import com.quantummpv.app.repository.ai.OpenRouterClient
import com.quantummpv.app.repository.ai.OpenRouterSpeechClient
import com.quantummpv.app.repository.ai.RealtimeSubtitleService
import com.quantummpv.app.repository.ai.SubtitleGenerationService
import com.quantummpv.app.repository.ai.TogetherClient
import com.quantummpv.app.preferences.AiPreferences
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val domainModule = module {
    single {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .cookieJar(AndroidCookieJar())
            .build()
    }
    single { Anime4KManager(androidContext()) }
    single { HdrToysManager(androidContext()) }
    single { OnlineSubtitleFileStore(androidContext(), get()) }
    single { WyzieSearchRepository(androidContext(), get(), get(), get(), get()) }
    single { MpvRxSubtitleHubRepository(get(), get(), get(), get()) }
    single { OnlineSubtitleOrchestrator(get<WyzieSearchRepository>(), get<MpvRxSubtitleHubRepository>()) }
    single { IntroDbRepository(get(), get()) }
    single { OpenCodeClient(get(), get()) }
    single { GroqClient(get(), get()) }
    single { OpenAiClient(get(), get()) }
    single { AnthropicClient(get(), get()) }
    single { OpenRouterClient(get(), get()) }
    single { TogetherClient(get(), get()) }
    single { GroqSpeechClient(get(), get()) }
    single { OpenRouterSpeechClient(get(), get()) }
    single<com.quantummpv.app.repository.ai.LlmInference> { com.quantummpv.app.repository.ai.LlamaCppInference() }
    single<AiClient>(named("opencode")) { OpenCodeClient(get(), get()) }
    single<AiClient>(named("groq")) { GroqClient(get(), get()) }
    single<AiClient>(named("openai")) { OpenAiClient(get(), get()) }
    single<AiClient>(named("anthropic")) { AnthropicClient(get(), get()) }
    single<AiClient>(named("openrouter")) { OpenRouterClient(get(), get()) }
    single<AiClient>(named("together")) { TogetherClient(get(), get()) }
    single { LocalAiClient(get()) }
    single { ModelDownloadManager(get()) }
    single { SubtitleGenerationService(androidContext(), get(), get(), get(), get(), get()) }
    single { RealtimeSubtitleService(androidContext(), get(), get(), get(), get(), get()) }
    single {
        AiService(
            androidContext(),
            get<AiPreferences>(),
            get<AiClient>(named("opencode")),
            get<AiClient>(named("groq")),
            get<AiClient>(named("openai")),
            get<AiClient>(named("anthropic")),
            get<AiClient>(named("openrouter")),
            get<AiClient>(named("together")),
            get<LocalAiClient>(),
            get<ModelDownloadManager>(),
            get<Json>()
        )
    }
}

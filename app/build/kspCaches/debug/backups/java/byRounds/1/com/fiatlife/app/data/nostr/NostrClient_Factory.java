package com.fiatlife.app.data.nostr;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import okhttp3.OkHttpClient;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast"
})
public final class NostrClient_Factory implements Factory<NostrClient> {
  private final Provider<OkHttpClient> okHttpClientProvider;

  public NostrClient_Factory(Provider<OkHttpClient> okHttpClientProvider) {
    this.okHttpClientProvider = okHttpClientProvider;
  }

  @Override
  public NostrClient get() {
    return newInstance(okHttpClientProvider.get());
  }

  public static NostrClient_Factory create(Provider<OkHttpClient> okHttpClientProvider) {
    return new NostrClient_Factory(okHttpClientProvider);
  }

  public static NostrClient newInstance(OkHttpClient okHttpClient) {
    return new NostrClient(okHttpClient);
  }
}

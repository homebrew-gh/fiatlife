package com.fiatlife.app.data.blossom;

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
public final class BlossomClient_Factory implements Factory<BlossomClient> {
  private final Provider<OkHttpClient> okHttpClientProvider;

  public BlossomClient_Factory(Provider<OkHttpClient> okHttpClientProvider) {
    this.okHttpClientProvider = okHttpClientProvider;
  }

  @Override
  public BlossomClient get() {
    return newInstance(okHttpClientProvider.get());
  }

  public static BlossomClient_Factory create(Provider<OkHttpClient> okHttpClientProvider) {
    return new BlossomClient_Factory(okHttpClientProvider);
  }

  public static BlossomClient newInstance(OkHttpClient okHttpClient) {
    return new BlossomClient(okHttpClient);
  }
}

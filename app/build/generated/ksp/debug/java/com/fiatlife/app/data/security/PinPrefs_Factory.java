package com.fiatlife.app.data.security;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class PinPrefs_Factory implements Factory<PinPrefs> {
  private final Provider<Context> contextProvider;

  public PinPrefs_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public PinPrefs get() {
    return newInstance(contextProvider.get());
  }

  public static PinPrefs_Factory create(Provider<Context> contextProvider) {
    return new PinPrefs_Factory(contextProvider);
  }

  public static PinPrefs newInstance(Context context) {
    return new PinPrefs(context);
  }
}

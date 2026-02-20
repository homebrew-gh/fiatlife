package com.fiatlife.app.data.notification;

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
public final class BillNotificationManager_Factory implements Factory<BillNotificationManager> {
  private final Provider<Context> contextProvider;

  public BillNotificationManager_Factory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public BillNotificationManager get() {
    return newInstance(contextProvider.get());
  }

  public static BillNotificationManager_Factory create(Provider<Context> contextProvider) {
    return new BillNotificationManager_Factory(contextProvider);
  }

  public static BillNotificationManager newInstance(Context context) {
    return new BillNotificationManager(context);
  }
}

package com.fiatlife.app.di;

import android.content.Context;
import com.fiatlife.app.data.notification.BillNotificationManager;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class AppModule_ProvideBillNotificationManagerFactory implements Factory<BillNotificationManager> {
  private final Provider<Context> contextProvider;

  public AppModule_ProvideBillNotificationManagerFactory(Provider<Context> contextProvider) {
    this.contextProvider = contextProvider;
  }

  @Override
  public BillNotificationManager get() {
    return provideBillNotificationManager(contextProvider.get());
  }

  public static AppModule_ProvideBillNotificationManagerFactory create(
      Provider<Context> contextProvider) {
    return new AppModule_ProvideBillNotificationManagerFactory(contextProvider);
  }

  public static BillNotificationManager provideBillNotificationManager(Context context) {
    return Preconditions.checkNotNullFromProvides(AppModule.INSTANCE.provideBillNotificationManager(context));
  }
}

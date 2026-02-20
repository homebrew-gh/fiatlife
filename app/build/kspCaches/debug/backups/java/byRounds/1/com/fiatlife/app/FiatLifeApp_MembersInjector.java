package com.fiatlife.app;

import androidx.hilt.work.HiltWorkerFactory;
import com.fiatlife.app.data.notification.BillNotificationManager;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class FiatLifeApp_MembersInjector implements MembersInjector<FiatLifeApp> {
  private final Provider<HiltWorkerFactory> workerFactoryProvider;

  private final Provider<BillNotificationManager> billNotificationManagerProvider;

  public FiatLifeApp_MembersInjector(Provider<HiltWorkerFactory> workerFactoryProvider,
      Provider<BillNotificationManager> billNotificationManagerProvider) {
    this.workerFactoryProvider = workerFactoryProvider;
    this.billNotificationManagerProvider = billNotificationManagerProvider;
  }

  public static MembersInjector<FiatLifeApp> create(
      Provider<HiltWorkerFactory> workerFactoryProvider,
      Provider<BillNotificationManager> billNotificationManagerProvider) {
    return new FiatLifeApp_MembersInjector(workerFactoryProvider, billNotificationManagerProvider);
  }

  @Override
  public void injectMembers(FiatLifeApp instance) {
    injectWorkerFactory(instance, workerFactoryProvider.get());
    injectBillNotificationManager(instance, billNotificationManagerProvider.get());
  }

  @InjectedFieldSignature("com.fiatlife.app.FiatLifeApp.workerFactory")
  public static void injectWorkerFactory(FiatLifeApp instance, HiltWorkerFactory workerFactory) {
    instance.workerFactory = workerFactory;
  }

  @InjectedFieldSignature("com.fiatlife.app.FiatLifeApp.billNotificationManager")
  public static void injectBillNotificationManager(FiatLifeApp instance,
      BillNotificationManager billNotificationManager) {
    instance.billNotificationManager = billNotificationManager;
  }
}

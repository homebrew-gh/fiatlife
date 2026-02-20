package com.fiatlife.app.data.notification;

import android.content.Context;
import androidx.work.WorkerParameters;
import dagger.internal.DaggerGenerated;
import dagger.internal.InstanceFactory;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class BillReminderWorker_AssistedFactory_Impl implements BillReminderWorker_AssistedFactory {
  private final BillReminderWorker_Factory delegateFactory;

  BillReminderWorker_AssistedFactory_Impl(BillReminderWorker_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public BillReminderWorker create(Context p0, WorkerParameters p1) {
    return delegateFactory.get(p0, p1);
  }

  public static Provider<BillReminderWorker_AssistedFactory> create(
      BillReminderWorker_Factory delegateFactory) {
    return InstanceFactory.create(new BillReminderWorker_AssistedFactory_Impl(delegateFactory));
  }

  public static dagger.internal.Provider<BillReminderWorker_AssistedFactory> createFactoryProvider(
      BillReminderWorker_Factory delegateFactory) {
    return InstanceFactory.create(new BillReminderWorker_AssistedFactory_Impl(delegateFactory));
  }
}

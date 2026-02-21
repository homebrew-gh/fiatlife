package com.fiatlife.app.data.notification;

import android.content.Context;
import androidx.work.WorkerParameters;
import com.fiatlife.app.data.local.dao.BillDao;
import com.fiatlife.app.data.local.dao.CreditAccountDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import kotlinx.serialization.json.Json;

@ScopeMetadata
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
public final class BillReminderWorker_Factory {
  private final Provider<BillDao> billDaoProvider;

  private final Provider<CreditAccountDao> creditAccountDaoProvider;

  private final Provider<BillNotificationManager> notificationManagerProvider;

  private final Provider<Json> jsonProvider;

  public BillReminderWorker_Factory(Provider<BillDao> billDaoProvider,
      Provider<CreditAccountDao> creditAccountDaoProvider,
      Provider<BillNotificationManager> notificationManagerProvider, Provider<Json> jsonProvider) {
    this.billDaoProvider = billDaoProvider;
    this.creditAccountDaoProvider = creditAccountDaoProvider;
    this.notificationManagerProvider = notificationManagerProvider;
    this.jsonProvider = jsonProvider;
  }

  public BillReminderWorker get(Context appContext, WorkerParameters workerParams) {
    return newInstance(appContext, workerParams, billDaoProvider.get(), creditAccountDaoProvider.get(), notificationManagerProvider.get(), jsonProvider.get());
  }

  public static BillReminderWorker_Factory create(Provider<BillDao> billDaoProvider,
      Provider<CreditAccountDao> creditAccountDaoProvider,
      Provider<BillNotificationManager> notificationManagerProvider, Provider<Json> jsonProvider) {
    return new BillReminderWorker_Factory(billDaoProvider, creditAccountDaoProvider, notificationManagerProvider, jsonProvider);
  }

  public static BillReminderWorker newInstance(Context appContext, WorkerParameters workerParams,
      BillDao billDao, CreditAccountDao creditAccountDao,
      BillNotificationManager notificationManager, Json json) {
    return new BillReminderWorker(appContext, workerParams, billDao, creditAccountDao, notificationManager, json);
  }
}

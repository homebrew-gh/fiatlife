package com.fiatlife.app.data.repository;

import com.fiatlife.app.data.blossom.BlossomClient;
import com.fiatlife.app.data.local.dao.BillDao;
import com.fiatlife.app.data.nostr.NostrClient;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;
import kotlinx.serialization.json.Json;

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
public final class BillRepository_Factory implements Factory<BillRepository> {
  private final Provider<BillDao> billDaoProvider;

  private final Provider<NostrClient> nostrClientProvider;

  private final Provider<BlossomClient> blossomClientProvider;

  private final Provider<Json> jsonProvider;

  public BillRepository_Factory(Provider<BillDao> billDaoProvider,
      Provider<NostrClient> nostrClientProvider, Provider<BlossomClient> blossomClientProvider,
      Provider<Json> jsonProvider) {
    this.billDaoProvider = billDaoProvider;
    this.nostrClientProvider = nostrClientProvider;
    this.blossomClientProvider = blossomClientProvider;
    this.jsonProvider = jsonProvider;
  }

  @Override
  public BillRepository get() {
    return newInstance(billDaoProvider.get(), nostrClientProvider.get(), blossomClientProvider.get(), jsonProvider.get());
  }

  public static BillRepository_Factory create(Provider<BillDao> billDaoProvider,
      Provider<NostrClient> nostrClientProvider, Provider<BlossomClient> blossomClientProvider,
      Provider<Json> jsonProvider) {
    return new BillRepository_Factory(billDaoProvider, nostrClientProvider, blossomClientProvider, jsonProvider);
  }

  public static BillRepository newInstance(BillDao billDao, NostrClient nostrClient,
      BlossomClient blossomClient, Json json) {
    return new BillRepository(billDao, nostrClient, blossomClient, json);
  }
}

package com.fiatlife.app.data.repository;

import com.fiatlife.app.data.local.dao.CypherLogSubscriptionDao;
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
public final class CypherLogSubscriptionRepository_Factory implements Factory<CypherLogSubscriptionRepository> {
  private final Provider<CypherLogSubscriptionDao> daoProvider;

  private final Provider<NostrClient> nostrClientProvider;

  private final Provider<Json> jsonProvider;

  public CypherLogSubscriptionRepository_Factory(Provider<CypherLogSubscriptionDao> daoProvider,
      Provider<NostrClient> nostrClientProvider, Provider<Json> jsonProvider) {
    this.daoProvider = daoProvider;
    this.nostrClientProvider = nostrClientProvider;
    this.jsonProvider = jsonProvider;
  }

  @Override
  public CypherLogSubscriptionRepository get() {
    return newInstance(daoProvider.get(), nostrClientProvider.get(), jsonProvider.get());
  }

  public static CypherLogSubscriptionRepository_Factory create(
      Provider<CypherLogSubscriptionDao> daoProvider, Provider<NostrClient> nostrClientProvider,
      Provider<Json> jsonProvider) {
    return new CypherLogSubscriptionRepository_Factory(daoProvider, nostrClientProvider, jsonProvider);
  }

  public static CypherLogSubscriptionRepository newInstance(CypherLogSubscriptionDao dao,
      NostrClient nostrClient, Json json) {
    return new CypherLogSubscriptionRepository(dao, nostrClient, json);
  }
}

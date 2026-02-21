package com.fiatlife.app.data.repository;

import com.fiatlife.app.data.blossom.BlossomClient;
import com.fiatlife.app.data.local.dao.CreditAccountDao;
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
public final class CreditAccountRepository_Factory implements Factory<CreditAccountRepository> {
  private final Provider<CreditAccountDao> creditAccountDaoProvider;

  private final Provider<NostrClient> nostrClientProvider;

  private final Provider<BlossomClient> blossomClientProvider;

  private final Provider<Json> jsonProvider;

  public CreditAccountRepository_Factory(Provider<CreditAccountDao> creditAccountDaoProvider,
      Provider<NostrClient> nostrClientProvider, Provider<BlossomClient> blossomClientProvider,
      Provider<Json> jsonProvider) {
    this.creditAccountDaoProvider = creditAccountDaoProvider;
    this.nostrClientProvider = nostrClientProvider;
    this.blossomClientProvider = blossomClientProvider;
    this.jsonProvider = jsonProvider;
  }

  @Override
  public CreditAccountRepository get() {
    return newInstance(creditAccountDaoProvider.get(), nostrClientProvider.get(), blossomClientProvider.get(), jsonProvider.get());
  }

  public static CreditAccountRepository_Factory create(
      Provider<CreditAccountDao> creditAccountDaoProvider,
      Provider<NostrClient> nostrClientProvider, Provider<BlossomClient> blossomClientProvider,
      Provider<Json> jsonProvider) {
    return new CreditAccountRepository_Factory(creditAccountDaoProvider, nostrClientProvider, blossomClientProvider, jsonProvider);
  }

  public static CreditAccountRepository newInstance(CreditAccountDao creditAccountDao,
      NostrClient nostrClient, BlossomClient blossomClient, Json json) {
    return new CreditAccountRepository(creditAccountDao, nostrClient, blossomClient, json);
  }
}

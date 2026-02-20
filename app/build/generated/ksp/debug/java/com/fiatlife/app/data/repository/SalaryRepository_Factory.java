package com.fiatlife.app.data.repository;

import com.fiatlife.app.data.local.dao.SalaryDao;
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
public final class SalaryRepository_Factory implements Factory<SalaryRepository> {
  private final Provider<SalaryDao> salaryDaoProvider;

  private final Provider<NostrClient> nostrClientProvider;

  private final Provider<Json> jsonProvider;

  public SalaryRepository_Factory(Provider<SalaryDao> salaryDaoProvider,
      Provider<NostrClient> nostrClientProvider, Provider<Json> jsonProvider) {
    this.salaryDaoProvider = salaryDaoProvider;
    this.nostrClientProvider = nostrClientProvider;
    this.jsonProvider = jsonProvider;
  }

  @Override
  public SalaryRepository get() {
    return newInstance(salaryDaoProvider.get(), nostrClientProvider.get(), jsonProvider.get());
  }

  public static SalaryRepository_Factory create(Provider<SalaryDao> salaryDaoProvider,
      Provider<NostrClient> nostrClientProvider, Provider<Json> jsonProvider) {
    return new SalaryRepository_Factory(salaryDaoProvider, nostrClientProvider, jsonProvider);
  }

  public static SalaryRepository newInstance(SalaryDao salaryDao, NostrClient nostrClient,
      Json json) {
    return new SalaryRepository(salaryDao, nostrClient, json);
  }
}

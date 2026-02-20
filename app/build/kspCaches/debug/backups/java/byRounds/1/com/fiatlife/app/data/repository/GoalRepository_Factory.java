package com.fiatlife.app.data.repository;

import com.fiatlife.app.data.local.dao.GoalDao;
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
public final class GoalRepository_Factory implements Factory<GoalRepository> {
  private final Provider<GoalDao> goalDaoProvider;

  private final Provider<NostrClient> nostrClientProvider;

  private final Provider<Json> jsonProvider;

  public GoalRepository_Factory(Provider<GoalDao> goalDaoProvider,
      Provider<NostrClient> nostrClientProvider, Provider<Json> jsonProvider) {
    this.goalDaoProvider = goalDaoProvider;
    this.nostrClientProvider = nostrClientProvider;
    this.jsonProvider = jsonProvider;
  }

  @Override
  public GoalRepository get() {
    return newInstance(goalDaoProvider.get(), nostrClientProvider.get(), jsonProvider.get());
  }

  public static GoalRepository_Factory create(Provider<GoalDao> goalDaoProvider,
      Provider<NostrClient> nostrClientProvider, Provider<Json> jsonProvider) {
    return new GoalRepository_Factory(goalDaoProvider, nostrClientProvider, jsonProvider);
  }

  public static GoalRepository newInstance(GoalDao goalDao, NostrClient nostrClient, Json json) {
    return new GoalRepository(goalDao, nostrClient, json);
  }
}

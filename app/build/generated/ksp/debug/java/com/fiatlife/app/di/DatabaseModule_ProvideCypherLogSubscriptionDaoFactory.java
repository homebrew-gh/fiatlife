package com.fiatlife.app.di;

import com.fiatlife.app.data.local.FiatLifeDatabase;
import com.fiatlife.app.data.local.dao.CypherLogSubscriptionDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class DatabaseModule_ProvideCypherLogSubscriptionDaoFactory implements Factory<CypherLogSubscriptionDao> {
  private final Provider<FiatLifeDatabase> databaseProvider;

  public DatabaseModule_ProvideCypherLogSubscriptionDaoFactory(
      Provider<FiatLifeDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public CypherLogSubscriptionDao get() {
    return provideCypherLogSubscriptionDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideCypherLogSubscriptionDaoFactory create(
      Provider<FiatLifeDatabase> databaseProvider) {
    return new DatabaseModule_ProvideCypherLogSubscriptionDaoFactory(databaseProvider);
  }

  public static CypherLogSubscriptionDao provideCypherLogSubscriptionDao(
      FiatLifeDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideCypherLogSubscriptionDao(database));
  }
}

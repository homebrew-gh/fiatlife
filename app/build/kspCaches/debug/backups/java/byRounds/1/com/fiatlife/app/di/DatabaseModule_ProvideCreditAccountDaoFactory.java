package com.fiatlife.app.di;

import com.fiatlife.app.data.local.FiatLifeDatabase;
import com.fiatlife.app.data.local.dao.CreditAccountDao;
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
public final class DatabaseModule_ProvideCreditAccountDaoFactory implements Factory<CreditAccountDao> {
  private final Provider<FiatLifeDatabase> databaseProvider;

  public DatabaseModule_ProvideCreditAccountDaoFactory(
      Provider<FiatLifeDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public CreditAccountDao get() {
    return provideCreditAccountDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideCreditAccountDaoFactory create(
      Provider<FiatLifeDatabase> databaseProvider) {
    return new DatabaseModule_ProvideCreditAccountDaoFactory(databaseProvider);
  }

  public static CreditAccountDao provideCreditAccountDao(FiatLifeDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideCreditAccountDao(database));
  }
}

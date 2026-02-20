package com.fiatlife.app.di;

import com.fiatlife.app.data.local.FiatLifeDatabase;
import com.fiatlife.app.data.local.dao.SalaryDao;
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
public final class DatabaseModule_ProvideSalaryDaoFactory implements Factory<SalaryDao> {
  private final Provider<FiatLifeDatabase> databaseProvider;

  public DatabaseModule_ProvideSalaryDaoFactory(Provider<FiatLifeDatabase> databaseProvider) {
    this.databaseProvider = databaseProvider;
  }

  @Override
  public SalaryDao get() {
    return provideSalaryDao(databaseProvider.get());
  }

  public static DatabaseModule_ProvideSalaryDaoFactory create(
      Provider<FiatLifeDatabase> databaseProvider) {
    return new DatabaseModule_ProvideSalaryDaoFactory(databaseProvider);
  }

  public static SalaryDao provideSalaryDao(FiatLifeDatabase database) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideSalaryDao(database));
  }
}

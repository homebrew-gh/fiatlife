package com.fiatlife.app.ui.viewmodel;

import com.fiatlife.app.data.repository.BillRepository;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
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
public final class BillsViewModel_Factory implements Factory<BillsViewModel> {
  private final Provider<BillRepository> repositoryProvider;

  public BillsViewModel_Factory(Provider<BillRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public BillsViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static BillsViewModel_Factory create(Provider<BillRepository> repositoryProvider) {
    return new BillsViewModel_Factory(repositoryProvider);
  }

  public static BillsViewModel newInstance(BillRepository repository) {
    return new BillsViewModel(repository);
  }
}

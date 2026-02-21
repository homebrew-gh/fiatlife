package com.fiatlife.app.ui.viewmodel;

import androidx.lifecycle.SavedStateHandle;
import com.fiatlife.app.data.repository.BillRepository;
import com.fiatlife.app.data.repository.CypherLogSubscriptionRepository;
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
public final class BillDetailViewModel_Factory implements Factory<BillDetailViewModel> {
  private final Provider<SavedStateHandle> savedStateHandleProvider;

  private final Provider<BillRepository> repositoryProvider;

  private final Provider<CypherLogSubscriptionRepository> cypherLogSubscriptionRepositoryProvider;

  public BillDetailViewModel_Factory(Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<BillRepository> repositoryProvider,
      Provider<CypherLogSubscriptionRepository> cypherLogSubscriptionRepositoryProvider) {
    this.savedStateHandleProvider = savedStateHandleProvider;
    this.repositoryProvider = repositoryProvider;
    this.cypherLogSubscriptionRepositoryProvider = cypherLogSubscriptionRepositoryProvider;
  }

  @Override
  public BillDetailViewModel get() {
    return newInstance(savedStateHandleProvider.get(), repositoryProvider.get(), cypherLogSubscriptionRepositoryProvider.get());
  }

  public static BillDetailViewModel_Factory create(
      Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<BillRepository> repositoryProvider,
      Provider<CypherLogSubscriptionRepository> cypherLogSubscriptionRepositoryProvider) {
    return new BillDetailViewModel_Factory(savedStateHandleProvider, repositoryProvider, cypherLogSubscriptionRepositoryProvider);
  }

  public static BillDetailViewModel newInstance(SavedStateHandle savedStateHandle,
      BillRepository repository, CypherLogSubscriptionRepository cypherLogSubscriptionRepository) {
    return new BillDetailViewModel(savedStateHandle, repository, cypherLogSubscriptionRepository);
  }
}

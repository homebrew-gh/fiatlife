package com.fiatlife.app.ui.viewmodel;

import androidx.lifecycle.SavedStateHandle;
import com.fiatlife.app.data.repository.BillRepository;
import com.fiatlife.app.data.repository.CreditAccountRepository;
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
public final class DebtDetailViewModel_Factory implements Factory<DebtDetailViewModel> {
  private final Provider<SavedStateHandle> savedStateHandleProvider;

  private final Provider<CreditAccountRepository> repositoryProvider;

  private final Provider<BillRepository> billRepositoryProvider;

  public DebtDetailViewModel_Factory(Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<CreditAccountRepository> repositoryProvider,
      Provider<BillRepository> billRepositoryProvider) {
    this.savedStateHandleProvider = savedStateHandleProvider;
    this.repositoryProvider = repositoryProvider;
    this.billRepositoryProvider = billRepositoryProvider;
  }

  @Override
  public DebtDetailViewModel get() {
    return newInstance(savedStateHandleProvider.get(), repositoryProvider.get(), billRepositoryProvider.get());
  }

  public static DebtDetailViewModel_Factory create(
      Provider<SavedStateHandle> savedStateHandleProvider,
      Provider<CreditAccountRepository> repositoryProvider,
      Provider<BillRepository> billRepositoryProvider) {
    return new DebtDetailViewModel_Factory(savedStateHandleProvider, repositoryProvider, billRepositoryProvider);
  }

  public static DebtDetailViewModel newInstance(SavedStateHandle savedStateHandle,
      CreditAccountRepository repository, BillRepository billRepository) {
    return new DebtDetailViewModel(savedStateHandle, repository, billRepository);
  }
}

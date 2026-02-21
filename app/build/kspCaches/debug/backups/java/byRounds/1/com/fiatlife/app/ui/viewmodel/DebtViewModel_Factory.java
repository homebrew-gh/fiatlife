package com.fiatlife.app.ui.viewmodel;

import com.fiatlife.app.data.nostr.NostrClient;
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
public final class DebtViewModel_Factory implements Factory<DebtViewModel> {
  private final Provider<CreditAccountRepository> repositoryProvider;

  private final Provider<NostrClient> nostrClientProvider;

  public DebtViewModel_Factory(Provider<CreditAccountRepository> repositoryProvider,
      Provider<NostrClient> nostrClientProvider) {
    this.repositoryProvider = repositoryProvider;
    this.nostrClientProvider = nostrClientProvider;
  }

  @Override
  public DebtViewModel get() {
    return newInstance(repositoryProvider.get(), nostrClientProvider.get());
  }

  public static DebtViewModel_Factory create(Provider<CreditAccountRepository> repositoryProvider,
      Provider<NostrClient> nostrClientProvider) {
    return new DebtViewModel_Factory(repositoryProvider, nostrClientProvider);
  }

  public static DebtViewModel newInstance(CreditAccountRepository repository,
      NostrClient nostrClient) {
    return new DebtViewModel(repository, nostrClient);
  }
}

package com.fiatlife.app.ui.viewmodel;

import com.fiatlife.app.data.nostr.NostrClient;
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

  private final Provider<NostrClient> nostrClientProvider;

  public BillsViewModel_Factory(Provider<BillRepository> repositoryProvider,
      Provider<NostrClient> nostrClientProvider) {
    this.repositoryProvider = repositoryProvider;
    this.nostrClientProvider = nostrClientProvider;
  }

  @Override
  public BillsViewModel get() {
    return newInstance(repositoryProvider.get(), nostrClientProvider.get());
  }

  public static BillsViewModel_Factory create(Provider<BillRepository> repositoryProvider,
      Provider<NostrClient> nostrClientProvider) {
    return new BillsViewModel_Factory(repositoryProvider, nostrClientProvider);
  }

  public static BillsViewModel newInstance(BillRepository repository, NostrClient nostrClient) {
    return new BillsViewModel(repository, nostrClient);
  }
}

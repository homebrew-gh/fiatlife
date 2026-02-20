package com.fiatlife.app.ui.viewmodel;

import com.fiatlife.app.data.nostr.NostrClient;
import com.fiatlife.app.data.repository.SalaryRepository;
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
public final class SalaryViewModel_Factory implements Factory<SalaryViewModel> {
  private final Provider<SalaryRepository> repositoryProvider;

  private final Provider<NostrClient> nostrClientProvider;

  public SalaryViewModel_Factory(Provider<SalaryRepository> repositoryProvider,
      Provider<NostrClient> nostrClientProvider) {
    this.repositoryProvider = repositoryProvider;
    this.nostrClientProvider = nostrClientProvider;
  }

  @Override
  public SalaryViewModel get() {
    return newInstance(repositoryProvider.get(), nostrClientProvider.get());
  }

  public static SalaryViewModel_Factory create(Provider<SalaryRepository> repositoryProvider,
      Provider<NostrClient> nostrClientProvider) {
    return new SalaryViewModel_Factory(repositoryProvider, nostrClientProvider);
  }

  public static SalaryViewModel newInstance(SalaryRepository repository, NostrClient nostrClient) {
    return new SalaryViewModel(repository, nostrClient);
  }
}

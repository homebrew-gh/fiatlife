package com.fiatlife.app.ui.viewmodel;

import com.fiatlife.app.data.nostr.NostrClient;
import com.fiatlife.app.data.repository.BillRepository;
import com.fiatlife.app.data.repository.CypherLogSubscriptionRepository;
import com.fiatlife.app.data.repository.GoalRepository;
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
public final class MainAppViewModel_Factory implements Factory<MainAppViewModel> {
  private final Provider<NostrClient> nostrClientProvider;

  private final Provider<SalaryRepository> salaryRepositoryProvider;

  private final Provider<BillRepository> billRepositoryProvider;

  private final Provider<CypherLogSubscriptionRepository> cypherLogSubscriptionRepositoryProvider;

  private final Provider<GoalRepository> goalRepositoryProvider;

  public MainAppViewModel_Factory(Provider<NostrClient> nostrClientProvider,
      Provider<SalaryRepository> salaryRepositoryProvider,
      Provider<BillRepository> billRepositoryProvider,
      Provider<CypherLogSubscriptionRepository> cypherLogSubscriptionRepositoryProvider,
      Provider<GoalRepository> goalRepositoryProvider) {
    this.nostrClientProvider = nostrClientProvider;
    this.salaryRepositoryProvider = salaryRepositoryProvider;
    this.billRepositoryProvider = billRepositoryProvider;
    this.cypherLogSubscriptionRepositoryProvider = cypherLogSubscriptionRepositoryProvider;
    this.goalRepositoryProvider = goalRepositoryProvider;
  }

  @Override
  public MainAppViewModel get() {
    return newInstance(nostrClientProvider.get(), salaryRepositoryProvider.get(), billRepositoryProvider.get(), cypherLogSubscriptionRepositoryProvider.get(), goalRepositoryProvider.get());
  }

  public static MainAppViewModel_Factory create(Provider<NostrClient> nostrClientProvider,
      Provider<SalaryRepository> salaryRepositoryProvider,
      Provider<BillRepository> billRepositoryProvider,
      Provider<CypherLogSubscriptionRepository> cypherLogSubscriptionRepositoryProvider,
      Provider<GoalRepository> goalRepositoryProvider) {
    return new MainAppViewModel_Factory(nostrClientProvider, salaryRepositoryProvider, billRepositoryProvider, cypherLogSubscriptionRepositoryProvider, goalRepositoryProvider);
  }

  public static MainAppViewModel newInstance(NostrClient nostrClient,
      SalaryRepository salaryRepository, BillRepository billRepository,
      CypherLogSubscriptionRepository cypherLogSubscriptionRepository,
      GoalRepository goalRepository) {
    return new MainAppViewModel(nostrClient, salaryRepository, billRepository, cypherLogSubscriptionRepository, goalRepository);
  }
}

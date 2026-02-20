package com.fiatlife.app.ui.viewmodel;

import com.fiatlife.app.data.nostr.NostrClient;
import com.fiatlife.app.data.repository.BillRepository;
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
public final class DashboardViewModel_Factory implements Factory<DashboardViewModel> {
  private final Provider<SalaryRepository> salaryRepositoryProvider;

  private final Provider<BillRepository> billRepositoryProvider;

  private final Provider<GoalRepository> goalRepositoryProvider;

  private final Provider<NostrClient> nostrClientProvider;

  public DashboardViewModel_Factory(Provider<SalaryRepository> salaryRepositoryProvider,
      Provider<BillRepository> billRepositoryProvider,
      Provider<GoalRepository> goalRepositoryProvider, Provider<NostrClient> nostrClientProvider) {
    this.salaryRepositoryProvider = salaryRepositoryProvider;
    this.billRepositoryProvider = billRepositoryProvider;
    this.goalRepositoryProvider = goalRepositoryProvider;
    this.nostrClientProvider = nostrClientProvider;
  }

  @Override
  public DashboardViewModel get() {
    return newInstance(salaryRepositoryProvider.get(), billRepositoryProvider.get(), goalRepositoryProvider.get(), nostrClientProvider.get());
  }

  public static DashboardViewModel_Factory create(
      Provider<SalaryRepository> salaryRepositoryProvider,
      Provider<BillRepository> billRepositoryProvider,
      Provider<GoalRepository> goalRepositoryProvider, Provider<NostrClient> nostrClientProvider) {
    return new DashboardViewModel_Factory(salaryRepositoryProvider, billRepositoryProvider, goalRepositoryProvider, nostrClientProvider);
  }

  public static DashboardViewModel newInstance(SalaryRepository salaryRepository,
      BillRepository billRepository, GoalRepository goalRepository, NostrClient nostrClient) {
    return new DashboardViewModel(salaryRepository, billRepository, goalRepository, nostrClient);
  }
}

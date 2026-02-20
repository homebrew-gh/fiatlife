package com.fiatlife.app.ui.viewmodel;

import com.fiatlife.app.data.repository.GoalRepository;
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
public final class GoalsViewModel_Factory implements Factory<GoalsViewModel> {
  private final Provider<GoalRepository> repositoryProvider;

  public GoalsViewModel_Factory(Provider<GoalRepository> repositoryProvider) {
    this.repositoryProvider = repositoryProvider;
  }

  @Override
  public GoalsViewModel get() {
    return newInstance(repositoryProvider.get());
  }

  public static GoalsViewModel_Factory create(Provider<GoalRepository> repositoryProvider) {
    return new GoalsViewModel_Factory(repositoryProvider);
  }

  public static GoalsViewModel newInstance(GoalRepository repository) {
    return new GoalsViewModel(repository);
  }
}

package com.fiatlife.app;

import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import com.fiatlife.app.data.blossom.BlossomClient;
import com.fiatlife.app.data.nostr.NostrClient;
import com.fiatlife.app.data.repository.BillRepository;
import com.fiatlife.app.data.repository.CreditAccountRepository;
import com.fiatlife.app.data.repository.GoalRepository;
import com.fiatlife.app.data.repository.SalaryRepository;
import com.fiatlife.app.data.security.PinPrefs;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

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
public final class MainActivity_MembersInjector implements MembersInjector<MainActivity> {
  private final Provider<DataStore<Preferences>> dataStoreProvider;

  private final Provider<NostrClient> nostrClientProvider;

  private final Provider<BlossomClient> blossomClientProvider;

  private final Provider<PinPrefs> pinPrefsProvider;

  private final Provider<SalaryRepository> salaryRepositoryProvider;

  private final Provider<BillRepository> billRepositoryProvider;

  private final Provider<GoalRepository> goalRepositoryProvider;

  private final Provider<CreditAccountRepository> creditAccountRepositoryProvider;

  public MainActivity_MembersInjector(Provider<DataStore<Preferences>> dataStoreProvider,
      Provider<NostrClient> nostrClientProvider, Provider<BlossomClient> blossomClientProvider,
      Provider<PinPrefs> pinPrefsProvider, Provider<SalaryRepository> salaryRepositoryProvider,
      Provider<BillRepository> billRepositoryProvider,
      Provider<GoalRepository> goalRepositoryProvider,
      Provider<CreditAccountRepository> creditAccountRepositoryProvider) {
    this.dataStoreProvider = dataStoreProvider;
    this.nostrClientProvider = nostrClientProvider;
    this.blossomClientProvider = blossomClientProvider;
    this.pinPrefsProvider = pinPrefsProvider;
    this.salaryRepositoryProvider = salaryRepositoryProvider;
    this.billRepositoryProvider = billRepositoryProvider;
    this.goalRepositoryProvider = goalRepositoryProvider;
    this.creditAccountRepositoryProvider = creditAccountRepositoryProvider;
  }

  public static MembersInjector<MainActivity> create(
      Provider<DataStore<Preferences>> dataStoreProvider, Provider<NostrClient> nostrClientProvider,
      Provider<BlossomClient> blossomClientProvider, Provider<PinPrefs> pinPrefsProvider,
      Provider<SalaryRepository> salaryRepositoryProvider,
      Provider<BillRepository> billRepositoryProvider,
      Provider<GoalRepository> goalRepositoryProvider,
      Provider<CreditAccountRepository> creditAccountRepositoryProvider) {
    return new MainActivity_MembersInjector(dataStoreProvider, nostrClientProvider, blossomClientProvider, pinPrefsProvider, salaryRepositoryProvider, billRepositoryProvider, goalRepositoryProvider, creditAccountRepositoryProvider);
  }

  @Override
  public void injectMembers(MainActivity instance) {
    injectDataStore(instance, dataStoreProvider.get());
    injectNostrClient(instance, nostrClientProvider.get());
    injectBlossomClient(instance, blossomClientProvider.get());
    injectPinPrefs(instance, pinPrefsProvider.get());
    injectSalaryRepository(instance, salaryRepositoryProvider.get());
    injectBillRepository(instance, billRepositoryProvider.get());
    injectGoalRepository(instance, goalRepositoryProvider.get());
    injectCreditAccountRepository(instance, creditAccountRepositoryProvider.get());
  }

  @InjectedFieldSignature("com.fiatlife.app.MainActivity.dataStore")
  public static void injectDataStore(MainActivity instance, DataStore<Preferences> dataStore) {
    instance.dataStore = dataStore;
  }

  @InjectedFieldSignature("com.fiatlife.app.MainActivity.nostrClient")
  public static void injectNostrClient(MainActivity instance, NostrClient nostrClient) {
    instance.nostrClient = nostrClient;
  }

  @InjectedFieldSignature("com.fiatlife.app.MainActivity.blossomClient")
  public static void injectBlossomClient(MainActivity instance, BlossomClient blossomClient) {
    instance.blossomClient = blossomClient;
  }

  @InjectedFieldSignature("com.fiatlife.app.MainActivity.pinPrefs")
  public static void injectPinPrefs(MainActivity instance, PinPrefs pinPrefs) {
    instance.pinPrefs = pinPrefs;
  }

  @InjectedFieldSignature("com.fiatlife.app.MainActivity.salaryRepository")
  public static void injectSalaryRepository(MainActivity instance,
      SalaryRepository salaryRepository) {
    instance.salaryRepository = salaryRepository;
  }

  @InjectedFieldSignature("com.fiatlife.app.MainActivity.billRepository")
  public static void injectBillRepository(MainActivity instance, BillRepository billRepository) {
    instance.billRepository = billRepository;
  }

  @InjectedFieldSignature("com.fiatlife.app.MainActivity.goalRepository")
  public static void injectGoalRepository(MainActivity instance, GoalRepository goalRepository) {
    instance.goalRepository = goalRepository;
  }

  @InjectedFieldSignature("com.fiatlife.app.MainActivity.creditAccountRepository")
  public static void injectCreditAccountRepository(MainActivity instance,
      CreditAccountRepository creditAccountRepository) {
    instance.creditAccountRepository = creditAccountRepository;
  }
}

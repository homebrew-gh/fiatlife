package com.fiatlife.app;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.view.View;
import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import androidx.fragment.app.Fragment;
import androidx.hilt.work.HiltWorkerFactory;
import androidx.hilt.work.WorkerAssistedFactory;
import androidx.hilt.work.WorkerFactoryModule_ProvideFactoryFactory;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;
import androidx.work.ListenableWorker;
import androidx.work.WorkerParameters;
import com.fiatlife.app.data.blossom.BlossomClient;
import com.fiatlife.app.data.local.FiatLifeDatabase;
import com.fiatlife.app.data.local.dao.BillDao;
import com.fiatlife.app.data.local.dao.CreditAccountDao;
import com.fiatlife.app.data.local.dao.CypherLogSubscriptionDao;
import com.fiatlife.app.data.local.dao.GoalDao;
import com.fiatlife.app.data.local.dao.SalaryDao;
import com.fiatlife.app.data.nostr.NostrClient;
import com.fiatlife.app.data.notification.BillNotificationManager;
import com.fiatlife.app.data.notification.BillReminderWorker;
import com.fiatlife.app.data.notification.BillReminderWorker_AssistedFactory;
import com.fiatlife.app.data.repository.BillRepository;
import com.fiatlife.app.data.repository.CreditAccountRepository;
import com.fiatlife.app.data.repository.CypherLogSubscriptionRepository;
import com.fiatlife.app.data.repository.GoalRepository;
import com.fiatlife.app.data.repository.SalaryRepository;
import com.fiatlife.app.data.security.PinPrefs;
import com.fiatlife.app.di.AppModule_ProvideBillNotificationManagerFactory;
import com.fiatlife.app.di.AppModule_ProvideDataStoreFactory;
import com.fiatlife.app.di.AppModule_ProvideJsonFactory;
import com.fiatlife.app.di.AppModule_ProvidePinPrefsFactory;
import com.fiatlife.app.di.DatabaseModule_ProvideBillDaoFactory;
import com.fiatlife.app.di.DatabaseModule_ProvideCreditAccountDaoFactory;
import com.fiatlife.app.di.DatabaseModule_ProvideCypherLogSubscriptionDaoFactory;
import com.fiatlife.app.di.DatabaseModule_ProvideDatabaseFactory;
import com.fiatlife.app.di.DatabaseModule_ProvideGoalDaoFactory;
import com.fiatlife.app.di.DatabaseModule_ProvideSalaryDaoFactory;
import com.fiatlife.app.di.NetworkModule_ProvideOkHttpClientFactory;
import com.fiatlife.app.ui.viewmodel.BillDetailViewModel;
import com.fiatlife.app.ui.viewmodel.BillDetailViewModel_HiltModules;
import com.fiatlife.app.ui.viewmodel.BillsViewModel;
import com.fiatlife.app.ui.viewmodel.BillsViewModel_HiltModules;
import com.fiatlife.app.ui.viewmodel.DashboardViewModel;
import com.fiatlife.app.ui.viewmodel.DashboardViewModel_HiltModules;
import com.fiatlife.app.ui.viewmodel.DebtDetailViewModel;
import com.fiatlife.app.ui.viewmodel.DebtDetailViewModel_HiltModules;
import com.fiatlife.app.ui.viewmodel.DebtViewModel;
import com.fiatlife.app.ui.viewmodel.DebtViewModel_HiltModules;
import com.fiatlife.app.ui.viewmodel.GoalsViewModel;
import com.fiatlife.app.ui.viewmodel.GoalsViewModel_HiltModules;
import com.fiatlife.app.ui.viewmodel.MainAppViewModel;
import com.fiatlife.app.ui.viewmodel.MainAppViewModel_HiltModules;
import com.fiatlife.app.ui.viewmodel.SalaryViewModel;
import com.fiatlife.app.ui.viewmodel.SalaryViewModel_HiltModules;
import com.fiatlife.app.ui.viewmodel.SettingsViewModel;
import com.fiatlife.app.ui.viewmodel.SettingsViewModel_HiltModules;
import dagger.hilt.android.ActivityRetainedLifecycle;
import dagger.hilt.android.ViewModelLifecycle;
import dagger.hilt.android.internal.builders.ActivityComponentBuilder;
import dagger.hilt.android.internal.builders.ActivityRetainedComponentBuilder;
import dagger.hilt.android.internal.builders.FragmentComponentBuilder;
import dagger.hilt.android.internal.builders.ServiceComponentBuilder;
import dagger.hilt.android.internal.builders.ViewComponentBuilder;
import dagger.hilt.android.internal.builders.ViewModelComponentBuilder;
import dagger.hilt.android.internal.builders.ViewWithFragmentComponentBuilder;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories;
import dagger.hilt.android.internal.lifecycle.DefaultViewModelFactories_InternalFactoryFactory_Factory;
import dagger.hilt.android.internal.managers.ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory;
import dagger.hilt.android.internal.managers.SavedStateHandleHolder;
import dagger.hilt.android.internal.modules.ApplicationContextModule;
import dagger.hilt.android.internal.modules.ApplicationContextModule_ProvideContextFactory;
import dagger.internal.DaggerGenerated;
import dagger.internal.DoubleCheck;
import dagger.internal.IdentifierNameString;
import dagger.internal.KeepFieldType;
import dagger.internal.LazyClassKeyMap;
import dagger.internal.MapBuilder;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.SingleCheck;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;
import kotlinx.serialization.json.Json;
import okhttp3.OkHttpClient;

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
public final class DaggerFiatLifeApp_HiltComponents_SingletonC {
  private DaggerFiatLifeApp_HiltComponents_SingletonC() {
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private ApplicationContextModule applicationContextModule;

    private Builder() {
    }

    public Builder applicationContextModule(ApplicationContextModule applicationContextModule) {
      this.applicationContextModule = Preconditions.checkNotNull(applicationContextModule);
      return this;
    }

    public FiatLifeApp_HiltComponents.SingletonC build() {
      Preconditions.checkBuilderRequirement(applicationContextModule, ApplicationContextModule.class);
      return new SingletonCImpl(applicationContextModule);
    }
  }

  private static final class ActivityRetainedCBuilder implements FiatLifeApp_HiltComponents.ActivityRetainedC.Builder {
    private final SingletonCImpl singletonCImpl;

    private SavedStateHandleHolder savedStateHandleHolder;

    private ActivityRetainedCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ActivityRetainedCBuilder savedStateHandleHolder(
        SavedStateHandleHolder savedStateHandleHolder) {
      this.savedStateHandleHolder = Preconditions.checkNotNull(savedStateHandleHolder);
      return this;
    }

    @Override
    public FiatLifeApp_HiltComponents.ActivityRetainedC build() {
      Preconditions.checkBuilderRequirement(savedStateHandleHolder, SavedStateHandleHolder.class);
      return new ActivityRetainedCImpl(singletonCImpl, savedStateHandleHolder);
    }
  }

  private static final class ActivityCBuilder implements FiatLifeApp_HiltComponents.ActivityC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private Activity activity;

    private ActivityCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ActivityCBuilder activity(Activity activity) {
      this.activity = Preconditions.checkNotNull(activity);
      return this;
    }

    @Override
    public FiatLifeApp_HiltComponents.ActivityC build() {
      Preconditions.checkBuilderRequirement(activity, Activity.class);
      return new ActivityCImpl(singletonCImpl, activityRetainedCImpl, activity);
    }
  }

  private static final class FragmentCBuilder implements FiatLifeApp_HiltComponents.FragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private Fragment fragment;

    private FragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public FragmentCBuilder fragment(Fragment fragment) {
      this.fragment = Preconditions.checkNotNull(fragment);
      return this;
    }

    @Override
    public FiatLifeApp_HiltComponents.FragmentC build() {
      Preconditions.checkBuilderRequirement(fragment, Fragment.class);
      return new FragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragment);
    }
  }

  private static final class ViewWithFragmentCBuilder implements FiatLifeApp_HiltComponents.ViewWithFragmentC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private View view;

    private ViewWithFragmentCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;
    }

    @Override
    public ViewWithFragmentCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public FiatLifeApp_HiltComponents.ViewWithFragmentC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewWithFragmentCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl, view);
    }
  }

  private static final class ViewCBuilder implements FiatLifeApp_HiltComponents.ViewC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private View view;

    private ViewCBuilder(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
    }

    @Override
    public ViewCBuilder view(View view) {
      this.view = Preconditions.checkNotNull(view);
      return this;
    }

    @Override
    public FiatLifeApp_HiltComponents.ViewC build() {
      Preconditions.checkBuilderRequirement(view, View.class);
      return new ViewCImpl(singletonCImpl, activityRetainedCImpl, activityCImpl, view);
    }
  }

  private static final class ViewModelCBuilder implements FiatLifeApp_HiltComponents.ViewModelC.Builder {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private SavedStateHandle savedStateHandle;

    private ViewModelLifecycle viewModelLifecycle;

    private ViewModelCBuilder(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
    }

    @Override
    public ViewModelCBuilder savedStateHandle(SavedStateHandle handle) {
      this.savedStateHandle = Preconditions.checkNotNull(handle);
      return this;
    }

    @Override
    public ViewModelCBuilder viewModelLifecycle(ViewModelLifecycle viewModelLifecycle) {
      this.viewModelLifecycle = Preconditions.checkNotNull(viewModelLifecycle);
      return this;
    }

    @Override
    public FiatLifeApp_HiltComponents.ViewModelC build() {
      Preconditions.checkBuilderRequirement(savedStateHandle, SavedStateHandle.class);
      Preconditions.checkBuilderRequirement(viewModelLifecycle, ViewModelLifecycle.class);
      return new ViewModelCImpl(singletonCImpl, activityRetainedCImpl, savedStateHandle, viewModelLifecycle);
    }
  }

  private static final class ServiceCBuilder implements FiatLifeApp_HiltComponents.ServiceC.Builder {
    private final SingletonCImpl singletonCImpl;

    private Service service;

    private ServiceCBuilder(SingletonCImpl singletonCImpl) {
      this.singletonCImpl = singletonCImpl;
    }

    @Override
    public ServiceCBuilder service(Service service) {
      this.service = Preconditions.checkNotNull(service);
      return this;
    }

    @Override
    public FiatLifeApp_HiltComponents.ServiceC build() {
      Preconditions.checkBuilderRequirement(service, Service.class);
      return new ServiceCImpl(singletonCImpl, service);
    }
  }

  private static final class ViewWithFragmentCImpl extends FiatLifeApp_HiltComponents.ViewWithFragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl;

    private final ViewWithFragmentCImpl viewWithFragmentCImpl = this;

    private ViewWithFragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        FragmentCImpl fragmentCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;
      this.fragmentCImpl = fragmentCImpl;


    }
  }

  private static final class FragmentCImpl extends FiatLifeApp_HiltComponents.FragmentC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final FragmentCImpl fragmentCImpl = this;

    private FragmentCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, ActivityCImpl activityCImpl,
        Fragment fragmentParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return activityCImpl.getHiltInternalFactoryFactory();
    }

    @Override
    public ViewWithFragmentComponentBuilder viewWithFragmentComponentBuilder() {
      return new ViewWithFragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl, fragmentCImpl);
    }
  }

  private static final class ViewCImpl extends FiatLifeApp_HiltComponents.ViewC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl;

    private final ViewCImpl viewCImpl = this;

    private ViewCImpl(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
        ActivityCImpl activityCImpl, View viewParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.activityCImpl = activityCImpl;


    }
  }

  private static final class ActivityCImpl extends FiatLifeApp_HiltComponents.ActivityC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ActivityCImpl activityCImpl = this;

    private ActivityCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, Activity activityParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;


    }

    @Override
    public void injectMainActivity(MainActivity mainActivity) {
      injectMainActivity2(mainActivity);
    }

    @Override
    public DefaultViewModelFactories.InternalFactoryFactory getHiltInternalFactoryFactory() {
      return DefaultViewModelFactories_InternalFactoryFactory_Factory.newInstance(getViewModelKeys(), new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl));
    }

    @Override
    public Map<Class<?>, Boolean> getViewModelKeys() {
      return LazyClassKeyMap.<Boolean>of(MapBuilder.<String, Boolean>newMapBuilder(9).put(LazyClassKeyProvider.com_fiatlife_app_ui_viewmodel_BillDetailViewModel, BillDetailViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_fiatlife_app_ui_viewmodel_BillsViewModel, BillsViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_fiatlife_app_ui_viewmodel_DashboardViewModel, DashboardViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_fiatlife_app_ui_viewmodel_DebtDetailViewModel, DebtDetailViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_fiatlife_app_ui_viewmodel_DebtViewModel, DebtViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_fiatlife_app_ui_viewmodel_GoalsViewModel, GoalsViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_fiatlife_app_ui_viewmodel_MainAppViewModel, MainAppViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_fiatlife_app_ui_viewmodel_SalaryViewModel, SalaryViewModel_HiltModules.KeyModule.provide()).put(LazyClassKeyProvider.com_fiatlife_app_ui_viewmodel_SettingsViewModel, SettingsViewModel_HiltModules.KeyModule.provide()).build());
    }

    @Override
    public ViewModelComponentBuilder getViewModelComponentBuilder() {
      return new ViewModelCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public FragmentComponentBuilder fragmentComponentBuilder() {
      return new FragmentCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    @Override
    public ViewComponentBuilder viewComponentBuilder() {
      return new ViewCBuilder(singletonCImpl, activityRetainedCImpl, activityCImpl);
    }

    private MainActivity injectMainActivity2(MainActivity instance) {
      MainActivity_MembersInjector.injectDataStore(instance, singletonCImpl.provideDataStoreProvider.get());
      MainActivity_MembersInjector.injectNostrClient(instance, singletonCImpl.nostrClientProvider.get());
      MainActivity_MembersInjector.injectBlossomClient(instance, singletonCImpl.blossomClientProvider.get());
      MainActivity_MembersInjector.injectPinPrefs(instance, singletonCImpl.providePinPrefsProvider.get());
      MainActivity_MembersInjector.injectSalaryRepository(instance, singletonCImpl.salaryRepositoryProvider.get());
      MainActivity_MembersInjector.injectBillRepository(instance, singletonCImpl.billRepositoryProvider.get());
      MainActivity_MembersInjector.injectGoalRepository(instance, singletonCImpl.goalRepositoryProvider.get());
      MainActivity_MembersInjector.injectCreditAccountRepository(instance, singletonCImpl.creditAccountRepositoryProvider.get());
      MainActivity_MembersInjector.injectCypherLogSubscriptionRepository(instance, singletonCImpl.cypherLogSubscriptionRepositoryProvider.get());
      return instance;
    }

    @IdentifierNameString
    private static final class LazyClassKeyProvider {
      static String com_fiatlife_app_ui_viewmodel_BillDetailViewModel = "com.fiatlife.app.ui.viewmodel.BillDetailViewModel";

      static String com_fiatlife_app_ui_viewmodel_DebtDetailViewModel = "com.fiatlife.app.ui.viewmodel.DebtDetailViewModel";

      static String com_fiatlife_app_ui_viewmodel_GoalsViewModel = "com.fiatlife.app.ui.viewmodel.GoalsViewModel";

      static String com_fiatlife_app_ui_viewmodel_SalaryViewModel = "com.fiatlife.app.ui.viewmodel.SalaryViewModel";

      static String com_fiatlife_app_ui_viewmodel_BillsViewModel = "com.fiatlife.app.ui.viewmodel.BillsViewModel";

      static String com_fiatlife_app_ui_viewmodel_DebtViewModel = "com.fiatlife.app.ui.viewmodel.DebtViewModel";

      static String com_fiatlife_app_ui_viewmodel_DashboardViewModel = "com.fiatlife.app.ui.viewmodel.DashboardViewModel";

      static String com_fiatlife_app_ui_viewmodel_MainAppViewModel = "com.fiatlife.app.ui.viewmodel.MainAppViewModel";

      static String com_fiatlife_app_ui_viewmodel_SettingsViewModel = "com.fiatlife.app.ui.viewmodel.SettingsViewModel";

      @KeepFieldType
      BillDetailViewModel com_fiatlife_app_ui_viewmodel_BillDetailViewModel2;

      @KeepFieldType
      DebtDetailViewModel com_fiatlife_app_ui_viewmodel_DebtDetailViewModel2;

      @KeepFieldType
      GoalsViewModel com_fiatlife_app_ui_viewmodel_GoalsViewModel2;

      @KeepFieldType
      SalaryViewModel com_fiatlife_app_ui_viewmodel_SalaryViewModel2;

      @KeepFieldType
      BillsViewModel com_fiatlife_app_ui_viewmodel_BillsViewModel2;

      @KeepFieldType
      DebtViewModel com_fiatlife_app_ui_viewmodel_DebtViewModel2;

      @KeepFieldType
      DashboardViewModel com_fiatlife_app_ui_viewmodel_DashboardViewModel2;

      @KeepFieldType
      MainAppViewModel com_fiatlife_app_ui_viewmodel_MainAppViewModel2;

      @KeepFieldType
      SettingsViewModel com_fiatlife_app_ui_viewmodel_SettingsViewModel2;
    }
  }

  private static final class ViewModelCImpl extends FiatLifeApp_HiltComponents.ViewModelC {
    private final SavedStateHandle savedStateHandle;

    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl;

    private final ViewModelCImpl viewModelCImpl = this;

    private Provider<BillDetailViewModel> billDetailViewModelProvider;

    private Provider<BillsViewModel> billsViewModelProvider;

    private Provider<DashboardViewModel> dashboardViewModelProvider;

    private Provider<DebtDetailViewModel> debtDetailViewModelProvider;

    private Provider<DebtViewModel> debtViewModelProvider;

    private Provider<GoalsViewModel> goalsViewModelProvider;

    private Provider<MainAppViewModel> mainAppViewModelProvider;

    private Provider<SalaryViewModel> salaryViewModelProvider;

    private Provider<SettingsViewModel> settingsViewModelProvider;

    private ViewModelCImpl(SingletonCImpl singletonCImpl,
        ActivityRetainedCImpl activityRetainedCImpl, SavedStateHandle savedStateHandleParam,
        ViewModelLifecycle viewModelLifecycleParam) {
      this.singletonCImpl = singletonCImpl;
      this.activityRetainedCImpl = activityRetainedCImpl;
      this.savedStateHandle = savedStateHandleParam;
      initialize(savedStateHandleParam, viewModelLifecycleParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandle savedStateHandleParam,
        final ViewModelLifecycle viewModelLifecycleParam) {
      this.billDetailViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 0);
      this.billsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 1);
      this.dashboardViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 2);
      this.debtDetailViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 3);
      this.debtViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 4);
      this.goalsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 5);
      this.mainAppViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 6);
      this.salaryViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 7);
      this.settingsViewModelProvider = new SwitchingProvider<>(singletonCImpl, activityRetainedCImpl, viewModelCImpl, 8);
    }

    @Override
    public Map<Class<?>, javax.inject.Provider<ViewModel>> getHiltViewModelMap() {
      return LazyClassKeyMap.<javax.inject.Provider<ViewModel>>of(MapBuilder.<String, javax.inject.Provider<ViewModel>>newMapBuilder(9).put(LazyClassKeyProvider.com_fiatlife_app_ui_viewmodel_BillDetailViewModel, ((Provider) billDetailViewModelProvider)).put(LazyClassKeyProvider.com_fiatlife_app_ui_viewmodel_BillsViewModel, ((Provider) billsViewModelProvider)).put(LazyClassKeyProvider.com_fiatlife_app_ui_viewmodel_DashboardViewModel, ((Provider) dashboardViewModelProvider)).put(LazyClassKeyProvider.com_fiatlife_app_ui_viewmodel_DebtDetailViewModel, ((Provider) debtDetailViewModelProvider)).put(LazyClassKeyProvider.com_fiatlife_app_ui_viewmodel_DebtViewModel, ((Provider) debtViewModelProvider)).put(LazyClassKeyProvider.com_fiatlife_app_ui_viewmodel_GoalsViewModel, ((Provider) goalsViewModelProvider)).put(LazyClassKeyProvider.com_fiatlife_app_ui_viewmodel_MainAppViewModel, ((Provider) mainAppViewModelProvider)).put(LazyClassKeyProvider.com_fiatlife_app_ui_viewmodel_SalaryViewModel, ((Provider) salaryViewModelProvider)).put(LazyClassKeyProvider.com_fiatlife_app_ui_viewmodel_SettingsViewModel, ((Provider) settingsViewModelProvider)).build());
    }

    @Override
    public Map<Class<?>, Object> getHiltViewModelAssistedMap() {
      return Collections.<Class<?>, Object>emptyMap();
    }

    @IdentifierNameString
    private static final class LazyClassKeyProvider {
      static String com_fiatlife_app_ui_viewmodel_BillDetailViewModel = "com.fiatlife.app.ui.viewmodel.BillDetailViewModel";

      static String com_fiatlife_app_ui_viewmodel_BillsViewModel = "com.fiatlife.app.ui.viewmodel.BillsViewModel";

      static String com_fiatlife_app_ui_viewmodel_DashboardViewModel = "com.fiatlife.app.ui.viewmodel.DashboardViewModel";

      static String com_fiatlife_app_ui_viewmodel_DebtDetailViewModel = "com.fiatlife.app.ui.viewmodel.DebtDetailViewModel";

      static String com_fiatlife_app_ui_viewmodel_SalaryViewModel = "com.fiatlife.app.ui.viewmodel.SalaryViewModel";

      static String com_fiatlife_app_ui_viewmodel_GoalsViewModel = "com.fiatlife.app.ui.viewmodel.GoalsViewModel";

      static String com_fiatlife_app_ui_viewmodel_DebtViewModel = "com.fiatlife.app.ui.viewmodel.DebtViewModel";

      static String com_fiatlife_app_ui_viewmodel_MainAppViewModel = "com.fiatlife.app.ui.viewmodel.MainAppViewModel";

      static String com_fiatlife_app_ui_viewmodel_SettingsViewModel = "com.fiatlife.app.ui.viewmodel.SettingsViewModel";

      @KeepFieldType
      BillDetailViewModel com_fiatlife_app_ui_viewmodel_BillDetailViewModel2;

      @KeepFieldType
      BillsViewModel com_fiatlife_app_ui_viewmodel_BillsViewModel2;

      @KeepFieldType
      DashboardViewModel com_fiatlife_app_ui_viewmodel_DashboardViewModel2;

      @KeepFieldType
      DebtDetailViewModel com_fiatlife_app_ui_viewmodel_DebtDetailViewModel2;

      @KeepFieldType
      SalaryViewModel com_fiatlife_app_ui_viewmodel_SalaryViewModel2;

      @KeepFieldType
      GoalsViewModel com_fiatlife_app_ui_viewmodel_GoalsViewModel2;

      @KeepFieldType
      DebtViewModel com_fiatlife_app_ui_viewmodel_DebtViewModel2;

      @KeepFieldType
      MainAppViewModel com_fiatlife_app_ui_viewmodel_MainAppViewModel2;

      @KeepFieldType
      SettingsViewModel com_fiatlife_app_ui_viewmodel_SettingsViewModel2;
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final ViewModelCImpl viewModelCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          ViewModelCImpl viewModelCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.viewModelCImpl = viewModelCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // com.fiatlife.app.ui.viewmodel.BillDetailViewModel 
          return (T) new BillDetailViewModel(viewModelCImpl.savedStateHandle, singletonCImpl.billRepositoryProvider.get(), singletonCImpl.creditAccountRepositoryProvider.get(), singletonCImpl.cypherLogSubscriptionRepositoryProvider.get());

          case 1: // com.fiatlife.app.ui.viewmodel.BillsViewModel 
          return (T) new BillsViewModel(singletonCImpl.billRepositoryProvider.get(), singletonCImpl.cypherLogSubscriptionRepositoryProvider.get(), singletonCImpl.creditAccountRepositoryProvider.get(), singletonCImpl.nostrClientProvider.get());

          case 2: // com.fiatlife.app.ui.viewmodel.DashboardViewModel 
          return (T) new DashboardViewModel(singletonCImpl.salaryRepositoryProvider.get(), singletonCImpl.billRepositoryProvider.get(), singletonCImpl.cypherLogSubscriptionRepositoryProvider.get(), singletonCImpl.goalRepositoryProvider.get(), singletonCImpl.nostrClientProvider.get());

          case 3: // com.fiatlife.app.ui.viewmodel.DebtDetailViewModel 
          return (T) new DebtDetailViewModel(viewModelCImpl.savedStateHandle, singletonCImpl.creditAccountRepositoryProvider.get(), singletonCImpl.billRepositoryProvider.get());

          case 4: // com.fiatlife.app.ui.viewmodel.DebtViewModel 
          return (T) new DebtViewModel(singletonCImpl.creditAccountRepositoryProvider.get(), singletonCImpl.billRepositoryProvider.get(), singletonCImpl.nostrClientProvider.get());

          case 5: // com.fiatlife.app.ui.viewmodel.GoalsViewModel 
          return (T) new GoalsViewModel(singletonCImpl.goalRepositoryProvider.get());

          case 6: // com.fiatlife.app.ui.viewmodel.MainAppViewModel 
          return (T) new MainAppViewModel(singletonCImpl.nostrClientProvider.get(), singletonCImpl.salaryRepositoryProvider.get(), singletonCImpl.billRepositoryProvider.get(), singletonCImpl.cypherLogSubscriptionRepositoryProvider.get(), singletonCImpl.goalRepositoryProvider.get());

          case 7: // com.fiatlife.app.ui.viewmodel.SalaryViewModel 
          return (T) new SalaryViewModel(singletonCImpl.salaryRepositoryProvider.get(), singletonCImpl.nostrClientProvider.get());

          case 8: // com.fiatlife.app.ui.viewmodel.SettingsViewModel 
          return (T) new SettingsViewModel(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule), singletonCImpl.provideDataStoreProvider.get(), singletonCImpl.nostrClientProvider.get(), singletonCImpl.blossomClientProvider.get(), singletonCImpl.salaryRepositoryProvider.get(), singletonCImpl.billRepositoryProvider.get(), singletonCImpl.goalRepositoryProvider.get(), singletonCImpl.providePinPrefsProvider.get());

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ActivityRetainedCImpl extends FiatLifeApp_HiltComponents.ActivityRetainedC {
    private final SingletonCImpl singletonCImpl;

    private final ActivityRetainedCImpl activityRetainedCImpl = this;

    private Provider<ActivityRetainedLifecycle> provideActivityRetainedLifecycleProvider;

    private ActivityRetainedCImpl(SingletonCImpl singletonCImpl,
        SavedStateHandleHolder savedStateHandleHolderParam) {
      this.singletonCImpl = singletonCImpl;

      initialize(savedStateHandleHolderParam);

    }

    @SuppressWarnings("unchecked")
    private void initialize(final SavedStateHandleHolder savedStateHandleHolderParam) {
      this.provideActivityRetainedLifecycleProvider = DoubleCheck.provider(new SwitchingProvider<ActivityRetainedLifecycle>(singletonCImpl, activityRetainedCImpl, 0));
    }

    @Override
    public ActivityComponentBuilder activityComponentBuilder() {
      return new ActivityCBuilder(singletonCImpl, activityRetainedCImpl);
    }

    @Override
    public ActivityRetainedLifecycle getActivityRetainedLifecycle() {
      return provideActivityRetainedLifecycleProvider.get();
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final ActivityRetainedCImpl activityRetainedCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, ActivityRetainedCImpl activityRetainedCImpl,
          int id) {
        this.singletonCImpl = singletonCImpl;
        this.activityRetainedCImpl = activityRetainedCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // dagger.hilt.android.ActivityRetainedLifecycle 
          return (T) ActivityRetainedComponentManager_LifecycleModule_ProvideActivityRetainedLifecycleFactory.provideActivityRetainedLifecycle();

          default: throw new AssertionError(id);
        }
      }
    }
  }

  private static final class ServiceCImpl extends FiatLifeApp_HiltComponents.ServiceC {
    private final SingletonCImpl singletonCImpl;

    private final ServiceCImpl serviceCImpl = this;

    private ServiceCImpl(SingletonCImpl singletonCImpl, Service serviceParam) {
      this.singletonCImpl = singletonCImpl;


    }
  }

  private static final class SingletonCImpl extends FiatLifeApp_HiltComponents.SingletonC {
    private final ApplicationContextModule applicationContextModule;

    private final SingletonCImpl singletonCImpl = this;

    private Provider<FiatLifeDatabase> provideDatabaseProvider;

    private Provider<BillNotificationManager> provideBillNotificationManagerProvider;

    private Provider<Json> provideJsonProvider;

    private Provider<BillReminderWorker_AssistedFactory> billReminderWorker_AssistedFactoryProvider;

    private Provider<DataStore<Preferences>> provideDataStoreProvider;

    private Provider<OkHttpClient> provideOkHttpClientProvider;

    private Provider<NostrClient> nostrClientProvider;

    private Provider<BlossomClient> blossomClientProvider;

    private Provider<PinPrefs> providePinPrefsProvider;

    private Provider<SalaryRepository> salaryRepositoryProvider;

    private Provider<BillRepository> billRepositoryProvider;

    private Provider<GoalRepository> goalRepositoryProvider;

    private Provider<CreditAccountRepository> creditAccountRepositoryProvider;

    private Provider<CypherLogSubscriptionRepository> cypherLogSubscriptionRepositoryProvider;

    private SingletonCImpl(ApplicationContextModule applicationContextModuleParam) {
      this.applicationContextModule = applicationContextModuleParam;
      initialize(applicationContextModuleParam);

    }

    private BillDao billDao() {
      return DatabaseModule_ProvideBillDaoFactory.provideBillDao(provideDatabaseProvider.get());
    }

    private CreditAccountDao creditAccountDao() {
      return DatabaseModule_ProvideCreditAccountDaoFactory.provideCreditAccountDao(provideDatabaseProvider.get());
    }

    private Map<String, javax.inject.Provider<WorkerAssistedFactory<? extends ListenableWorker>>> mapOfStringAndProviderOfWorkerAssistedFactoryOf(
        ) {
      return Collections.<String, javax.inject.Provider<WorkerAssistedFactory<? extends ListenableWorker>>>singletonMap("com.fiatlife.app.data.notification.BillReminderWorker", ((Provider) billReminderWorker_AssistedFactoryProvider));
    }

    private HiltWorkerFactory hiltWorkerFactory() {
      return WorkerFactoryModule_ProvideFactoryFactory.provideFactory(mapOfStringAndProviderOfWorkerAssistedFactoryOf());
    }

    private SalaryDao salaryDao() {
      return DatabaseModule_ProvideSalaryDaoFactory.provideSalaryDao(provideDatabaseProvider.get());
    }

    private GoalDao goalDao() {
      return DatabaseModule_ProvideGoalDaoFactory.provideGoalDao(provideDatabaseProvider.get());
    }

    private CypherLogSubscriptionDao cypherLogSubscriptionDao() {
      return DatabaseModule_ProvideCypherLogSubscriptionDaoFactory.provideCypherLogSubscriptionDao(provideDatabaseProvider.get());
    }

    @SuppressWarnings("unchecked")
    private void initialize(final ApplicationContextModule applicationContextModuleParam) {
      this.provideDatabaseProvider = DoubleCheck.provider(new SwitchingProvider<FiatLifeDatabase>(singletonCImpl, 1));
      this.provideBillNotificationManagerProvider = DoubleCheck.provider(new SwitchingProvider<BillNotificationManager>(singletonCImpl, 2));
      this.provideJsonProvider = DoubleCheck.provider(new SwitchingProvider<Json>(singletonCImpl, 3));
      this.billReminderWorker_AssistedFactoryProvider = SingleCheck.provider(new SwitchingProvider<BillReminderWorker_AssistedFactory>(singletonCImpl, 0));
      this.provideDataStoreProvider = DoubleCheck.provider(new SwitchingProvider<DataStore<Preferences>>(singletonCImpl, 4));
      this.provideOkHttpClientProvider = DoubleCheck.provider(new SwitchingProvider<OkHttpClient>(singletonCImpl, 6));
      this.nostrClientProvider = DoubleCheck.provider(new SwitchingProvider<NostrClient>(singletonCImpl, 5));
      this.blossomClientProvider = DoubleCheck.provider(new SwitchingProvider<BlossomClient>(singletonCImpl, 7));
      this.providePinPrefsProvider = DoubleCheck.provider(new SwitchingProvider<PinPrefs>(singletonCImpl, 8));
      this.salaryRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<SalaryRepository>(singletonCImpl, 9));
      this.billRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<BillRepository>(singletonCImpl, 10));
      this.goalRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<GoalRepository>(singletonCImpl, 11));
      this.creditAccountRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<CreditAccountRepository>(singletonCImpl, 12));
      this.cypherLogSubscriptionRepositoryProvider = DoubleCheck.provider(new SwitchingProvider<CypherLogSubscriptionRepository>(singletonCImpl, 13));
    }

    @Override
    public void injectFiatLifeApp(FiatLifeApp fiatLifeApp) {
      injectFiatLifeApp2(fiatLifeApp);
    }

    @Override
    public Set<Boolean> getDisableFragmentGetContextFix() {
      return Collections.<Boolean>emptySet();
    }

    @Override
    public ActivityRetainedComponentBuilder retainedComponentBuilder() {
      return new ActivityRetainedCBuilder(singletonCImpl);
    }

    @Override
    public ServiceComponentBuilder serviceComponentBuilder() {
      return new ServiceCBuilder(singletonCImpl);
    }

    private FiatLifeApp injectFiatLifeApp2(FiatLifeApp instance) {
      FiatLifeApp_MembersInjector.injectWorkerFactory(instance, hiltWorkerFactory());
      FiatLifeApp_MembersInjector.injectBillNotificationManager(instance, provideBillNotificationManagerProvider.get());
      return instance;
    }

    private static final class SwitchingProvider<T> implements Provider<T> {
      private final SingletonCImpl singletonCImpl;

      private final int id;

      SwitchingProvider(SingletonCImpl singletonCImpl, int id) {
        this.singletonCImpl = singletonCImpl;
        this.id = id;
      }

      @SuppressWarnings("unchecked")
      @Override
      public T get() {
        switch (id) {
          case 0: // com.fiatlife.app.data.notification.BillReminderWorker_AssistedFactory 
          return (T) new BillReminderWorker_AssistedFactory() {
            @Override
            public BillReminderWorker create(Context appContext, WorkerParameters workerParams) {
              return new BillReminderWorker(appContext, workerParams, singletonCImpl.billDao(), singletonCImpl.creditAccountDao(), singletonCImpl.provideBillNotificationManagerProvider.get(), singletonCImpl.provideJsonProvider.get());
            }
          };

          case 1: // com.fiatlife.app.data.local.FiatLifeDatabase 
          return (T) DatabaseModule_ProvideDatabaseFactory.provideDatabase(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 2: // com.fiatlife.app.data.notification.BillNotificationManager 
          return (T) AppModule_ProvideBillNotificationManagerFactory.provideBillNotificationManager(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 3: // kotlinx.serialization.json.Json 
          return (T) AppModule_ProvideJsonFactory.provideJson();

          case 4: // androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> 
          return (T) AppModule_ProvideDataStoreFactory.provideDataStore(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 5: // com.fiatlife.app.data.nostr.NostrClient 
          return (T) new NostrClient(singletonCImpl.provideOkHttpClientProvider.get());

          case 6: // okhttp3.OkHttpClient 
          return (T) NetworkModule_ProvideOkHttpClientFactory.provideOkHttpClient();

          case 7: // com.fiatlife.app.data.blossom.BlossomClient 
          return (T) new BlossomClient(singletonCImpl.provideOkHttpClientProvider.get());

          case 8: // com.fiatlife.app.data.security.PinPrefs 
          return (T) AppModule_ProvidePinPrefsFactory.providePinPrefs(ApplicationContextModule_ProvideContextFactory.provideContext(singletonCImpl.applicationContextModule));

          case 9: // com.fiatlife.app.data.repository.SalaryRepository 
          return (T) new SalaryRepository(singletonCImpl.salaryDao(), singletonCImpl.nostrClientProvider.get(), singletonCImpl.provideJsonProvider.get());

          case 10: // com.fiatlife.app.data.repository.BillRepository 
          return (T) new BillRepository(singletonCImpl.billDao(), singletonCImpl.nostrClientProvider.get(), singletonCImpl.blossomClientProvider.get(), singletonCImpl.provideJsonProvider.get());

          case 11: // com.fiatlife.app.data.repository.GoalRepository 
          return (T) new GoalRepository(singletonCImpl.goalDao(), singletonCImpl.nostrClientProvider.get(), singletonCImpl.provideJsonProvider.get());

          case 12: // com.fiatlife.app.data.repository.CreditAccountRepository 
          return (T) new CreditAccountRepository(singletonCImpl.creditAccountDao(), singletonCImpl.nostrClientProvider.get(), singletonCImpl.blossomClientProvider.get(), singletonCImpl.provideJsonProvider.get());

          case 13: // com.fiatlife.app.data.repository.CypherLogSubscriptionRepository 
          return (T) new CypherLogSubscriptionRepository(singletonCImpl.cypherLogSubscriptionDao(), singletonCImpl.nostrClientProvider.get(), singletonCImpl.provideJsonProvider.get());

          default: throw new AssertionError(id);
        }
      }
    }
  }
}

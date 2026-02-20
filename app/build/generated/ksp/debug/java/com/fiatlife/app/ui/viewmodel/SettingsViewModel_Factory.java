package com.fiatlife.app.ui.viewmodel;

import android.content.Context;
import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import com.fiatlife.app.data.blossom.BlossomClient;
import com.fiatlife.app.data.nostr.NostrClient;
import com.fiatlife.app.data.security.PinPrefs;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class SettingsViewModel_Factory implements Factory<SettingsViewModel> {
  private final Provider<Context> appContextProvider;

  private final Provider<DataStore<Preferences>> dataStoreProvider;

  private final Provider<NostrClient> nostrClientProvider;

  private final Provider<BlossomClient> blossomClientProvider;

  private final Provider<PinPrefs> pinPrefsProvider;

  public SettingsViewModel_Factory(Provider<Context> appContextProvider,
      Provider<DataStore<Preferences>> dataStoreProvider, Provider<NostrClient> nostrClientProvider,
      Provider<BlossomClient> blossomClientProvider, Provider<PinPrefs> pinPrefsProvider) {
    this.appContextProvider = appContextProvider;
    this.dataStoreProvider = dataStoreProvider;
    this.nostrClientProvider = nostrClientProvider;
    this.blossomClientProvider = blossomClientProvider;
    this.pinPrefsProvider = pinPrefsProvider;
  }

  @Override
  public SettingsViewModel get() {
    return newInstance(appContextProvider.get(), dataStoreProvider.get(), nostrClientProvider.get(), blossomClientProvider.get(), pinPrefsProvider.get());
  }

  public static SettingsViewModel_Factory create(Provider<Context> appContextProvider,
      Provider<DataStore<Preferences>> dataStoreProvider, Provider<NostrClient> nostrClientProvider,
      Provider<BlossomClient> blossomClientProvider, Provider<PinPrefs> pinPrefsProvider) {
    return new SettingsViewModel_Factory(appContextProvider, dataStoreProvider, nostrClientProvider, blossomClientProvider, pinPrefsProvider);
  }

  public static SettingsViewModel newInstance(Context appContext, DataStore<Preferences> dataStore,
      NostrClient nostrClient, BlossomClient blossomClient, PinPrefs pinPrefs) {
    return new SettingsViewModel(appContext, dataStore, nostrClient, blossomClient, pinPrefs);
  }
}

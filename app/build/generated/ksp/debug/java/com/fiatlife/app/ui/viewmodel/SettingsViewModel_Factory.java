package com.fiatlife.app.ui.viewmodel;

import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import com.fiatlife.app.data.blossom.BlossomClient;
import com.fiatlife.app.data.nostr.NostrClient;
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
public final class SettingsViewModel_Factory implements Factory<SettingsViewModel> {
  private final Provider<DataStore<Preferences>> dataStoreProvider;

  private final Provider<NostrClient> nostrClientProvider;

  private final Provider<BlossomClient> blossomClientProvider;

  public SettingsViewModel_Factory(Provider<DataStore<Preferences>> dataStoreProvider,
      Provider<NostrClient> nostrClientProvider, Provider<BlossomClient> blossomClientProvider) {
    this.dataStoreProvider = dataStoreProvider;
    this.nostrClientProvider = nostrClientProvider;
    this.blossomClientProvider = blossomClientProvider;
  }

  @Override
  public SettingsViewModel get() {
    return newInstance(dataStoreProvider.get(), nostrClientProvider.get(), blossomClientProvider.get());
  }

  public static SettingsViewModel_Factory create(Provider<DataStore<Preferences>> dataStoreProvider,
      Provider<NostrClient> nostrClientProvider, Provider<BlossomClient> blossomClientProvider) {
    return new SettingsViewModel_Factory(dataStoreProvider, nostrClientProvider, blossomClientProvider);
  }

  public static SettingsViewModel newInstance(DataStore<Preferences> dataStore,
      NostrClient nostrClient, BlossomClient blossomClient) {
    return new SettingsViewModel(dataStore, nostrClient, blossomClient);
  }
}

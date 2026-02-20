package com.fiatlife.app;

import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.Preferences;
import com.fiatlife.app.data.blossom.BlossomClient;
import com.fiatlife.app.data.nostr.NostrClient;
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

  public MainActivity_MembersInjector(Provider<DataStore<Preferences>> dataStoreProvider,
      Provider<NostrClient> nostrClientProvider, Provider<BlossomClient> blossomClientProvider) {
    this.dataStoreProvider = dataStoreProvider;
    this.nostrClientProvider = nostrClientProvider;
    this.blossomClientProvider = blossomClientProvider;
  }

  public static MembersInjector<MainActivity> create(
      Provider<DataStore<Preferences>> dataStoreProvider, Provider<NostrClient> nostrClientProvider,
      Provider<BlossomClient> blossomClientProvider) {
    return new MainActivity_MembersInjector(dataStoreProvider, nostrClientProvider, blossomClientProvider);
  }

  @Override
  public void injectMembers(MainActivity instance) {
    injectDataStore(instance, dataStoreProvider.get());
    injectNostrClient(instance, nostrClientProvider.get());
    injectBlossomClient(instance, blossomClientProvider.get());
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
}

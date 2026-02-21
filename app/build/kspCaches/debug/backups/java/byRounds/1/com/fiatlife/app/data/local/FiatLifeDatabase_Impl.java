package com.fiatlife.app.data.local;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.fiatlife.app.data.local.dao.BillDao;
import com.fiatlife.app.data.local.dao.BillDao_Impl;
import com.fiatlife.app.data.local.dao.CreditAccountDao;
import com.fiatlife.app.data.local.dao.CreditAccountDao_Impl;
import com.fiatlife.app.data.local.dao.CypherLogSubscriptionDao;
import com.fiatlife.app.data.local.dao.CypherLogSubscriptionDao_Impl;
import com.fiatlife.app.data.local.dao.GoalDao;
import com.fiatlife.app.data.local.dao.GoalDao_Impl;
import com.fiatlife.app.data.local.dao.SalaryDao;
import com.fiatlife.app.data.local.dao.SalaryDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class FiatLifeDatabase_Impl extends FiatLifeDatabase {
  private volatile SalaryDao _salaryDao;

  private volatile BillDao _billDao;

  private volatile GoalDao _goalDao;

  private volatile CypherLogSubscriptionDao _cypherLogSubscriptionDao;

  private volatile CreditAccountDao _creditAccountDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(4) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `salary_configs` (`id` TEXT NOT NULL, `jsonData` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `bills` (`id` TEXT NOT NULL, `jsonData` TEXT NOT NULL, `category` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `goals` (`id` TEXT NOT NULL, `jsonData` TEXT NOT NULL, `category` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `cypherlog_subscriptions` (`dTag` TEXT NOT NULL, `eventId` TEXT NOT NULL, `tagsJson` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `contentDecryptedJson` TEXT, PRIMARY KEY(`dTag`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `credit_accounts` (`id` TEXT NOT NULL, `jsonData` TEXT NOT NULL, `type` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '8b7d15ee503512740e0094feeaab811a')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `salary_configs`");
        db.execSQL("DROP TABLE IF EXISTS `bills`");
        db.execSQL("DROP TABLE IF EXISTS `goals`");
        db.execSQL("DROP TABLE IF EXISTS `cypherlog_subscriptions`");
        db.execSQL("DROP TABLE IF EXISTS `credit_accounts`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsSalaryConfigs = new HashMap<String, TableInfo.Column>(3);
        _columnsSalaryConfigs.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSalaryConfigs.put("jsonData", new TableInfo.Column("jsonData", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSalaryConfigs.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSalaryConfigs = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSalaryConfigs = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSalaryConfigs = new TableInfo("salary_configs", _columnsSalaryConfigs, _foreignKeysSalaryConfigs, _indicesSalaryConfigs);
        final TableInfo _existingSalaryConfigs = TableInfo.read(db, "salary_configs");
        if (!_infoSalaryConfigs.equals(_existingSalaryConfigs)) {
          return new RoomOpenHelper.ValidationResult(false, "salary_configs(com.fiatlife.app.data.local.entity.SalaryEntity).\n"
                  + " Expected:\n" + _infoSalaryConfigs + "\n"
                  + " Found:\n" + _existingSalaryConfigs);
        }
        final HashMap<String, TableInfo.Column> _columnsBills = new HashMap<String, TableInfo.Column>(4);
        _columnsBills.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBills.put("jsonData", new TableInfo.Column("jsonData", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBills.put("category", new TableInfo.Column("category", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsBills.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysBills = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesBills = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoBills = new TableInfo("bills", _columnsBills, _foreignKeysBills, _indicesBills);
        final TableInfo _existingBills = TableInfo.read(db, "bills");
        if (!_infoBills.equals(_existingBills)) {
          return new RoomOpenHelper.ValidationResult(false, "bills(com.fiatlife.app.data.local.entity.BillEntity).\n"
                  + " Expected:\n" + _infoBills + "\n"
                  + " Found:\n" + _existingBills);
        }
        final HashMap<String, TableInfo.Column> _columnsGoals = new HashMap<String, TableInfo.Column>(4);
        _columnsGoals.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGoals.put("jsonData", new TableInfo.Column("jsonData", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGoals.put("category", new TableInfo.Column("category", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsGoals.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysGoals = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesGoals = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoGoals = new TableInfo("goals", _columnsGoals, _foreignKeysGoals, _indicesGoals);
        final TableInfo _existingGoals = TableInfo.read(db, "goals");
        if (!_infoGoals.equals(_existingGoals)) {
          return new RoomOpenHelper.ValidationResult(false, "goals(com.fiatlife.app.data.local.entity.GoalEntity).\n"
                  + " Expected:\n" + _infoGoals + "\n"
                  + " Found:\n" + _existingGoals);
        }
        final HashMap<String, TableInfo.Column> _columnsCypherlogSubscriptions = new HashMap<String, TableInfo.Column>(5);
        _columnsCypherlogSubscriptions.put("dTag", new TableInfo.Column("dTag", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCypherlogSubscriptions.put("eventId", new TableInfo.Column("eventId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCypherlogSubscriptions.put("tagsJson", new TableInfo.Column("tagsJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCypherlogSubscriptions.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCypherlogSubscriptions.put("contentDecryptedJson", new TableInfo.Column("contentDecryptedJson", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysCypherlogSubscriptions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesCypherlogSubscriptions = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoCypherlogSubscriptions = new TableInfo("cypherlog_subscriptions", _columnsCypherlogSubscriptions, _foreignKeysCypherlogSubscriptions, _indicesCypherlogSubscriptions);
        final TableInfo _existingCypherlogSubscriptions = TableInfo.read(db, "cypherlog_subscriptions");
        if (!_infoCypherlogSubscriptions.equals(_existingCypherlogSubscriptions)) {
          return new RoomOpenHelper.ValidationResult(false, "cypherlog_subscriptions(com.fiatlife.app.data.local.entity.CypherLogSubscriptionEntity).\n"
                  + " Expected:\n" + _infoCypherlogSubscriptions + "\n"
                  + " Found:\n" + _existingCypherlogSubscriptions);
        }
        final HashMap<String, TableInfo.Column> _columnsCreditAccounts = new HashMap<String, TableInfo.Column>(4);
        _columnsCreditAccounts.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCreditAccounts.put("jsonData", new TableInfo.Column("jsonData", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCreditAccounts.put("type", new TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsCreditAccounts.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysCreditAccounts = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesCreditAccounts = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoCreditAccounts = new TableInfo("credit_accounts", _columnsCreditAccounts, _foreignKeysCreditAccounts, _indicesCreditAccounts);
        final TableInfo _existingCreditAccounts = TableInfo.read(db, "credit_accounts");
        if (!_infoCreditAccounts.equals(_existingCreditAccounts)) {
          return new RoomOpenHelper.ValidationResult(false, "credit_accounts(com.fiatlife.app.data.local.entity.CreditAccountEntity).\n"
                  + " Expected:\n" + _infoCreditAccounts + "\n"
                  + " Found:\n" + _existingCreditAccounts);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "8b7d15ee503512740e0094feeaab811a", "5702e172eddf782880e4b01fc1c78f1b");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "salary_configs","bills","goals","cypherlog_subscriptions","credit_accounts");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `salary_configs`");
      _db.execSQL("DELETE FROM `bills`");
      _db.execSQL("DELETE FROM `goals`");
      _db.execSQL("DELETE FROM `cypherlog_subscriptions`");
      _db.execSQL("DELETE FROM `credit_accounts`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(SalaryDao.class, SalaryDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(BillDao.class, BillDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(GoalDao.class, GoalDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(CypherLogSubscriptionDao.class, CypherLogSubscriptionDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(CreditAccountDao.class, CreditAccountDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public SalaryDao salaryDao() {
    if (_salaryDao != null) {
      return _salaryDao;
    } else {
      synchronized(this) {
        if(_salaryDao == null) {
          _salaryDao = new SalaryDao_Impl(this);
        }
        return _salaryDao;
      }
    }
  }

  @Override
  public BillDao billDao() {
    if (_billDao != null) {
      return _billDao;
    } else {
      synchronized(this) {
        if(_billDao == null) {
          _billDao = new BillDao_Impl(this);
        }
        return _billDao;
      }
    }
  }

  @Override
  public GoalDao goalDao() {
    if (_goalDao != null) {
      return _goalDao;
    } else {
      synchronized(this) {
        if(_goalDao == null) {
          _goalDao = new GoalDao_Impl(this);
        }
        return _goalDao;
      }
    }
  }

  @Override
  public CypherLogSubscriptionDao cypherLogSubscriptionDao() {
    if (_cypherLogSubscriptionDao != null) {
      return _cypherLogSubscriptionDao;
    } else {
      synchronized(this) {
        if(_cypherLogSubscriptionDao == null) {
          _cypherLogSubscriptionDao = new CypherLogSubscriptionDao_Impl(this);
        }
        return _cypherLogSubscriptionDao;
      }
    }
  }

  @Override
  public CreditAccountDao creditAccountDao() {
    if (_creditAccountDao != null) {
      return _creditAccountDao;
    } else {
      synchronized(this) {
        if(_creditAccountDao == null) {
          _creditAccountDao = new CreditAccountDao_Impl(this);
        }
        return _creditAccountDao;
      }
    }
  }
}

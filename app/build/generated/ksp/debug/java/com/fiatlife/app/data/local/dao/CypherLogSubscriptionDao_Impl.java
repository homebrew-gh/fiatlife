package com.fiatlife.app.data.local.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.EntityUpsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.fiatlife.app.data.local.entity.CypherLogSubscriptionEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class CypherLogSubscriptionDao_Impl implements CypherLogSubscriptionDao {
  private final RoomDatabase __db;

  private final EntityDeletionOrUpdateAdapter<CypherLogSubscriptionEntity> __deletionAdapterOfCypherLogSubscriptionEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteByDTag;

  private final SharedSQLiteStatement __preparedStmtOfDeleteAll;

  private final EntityUpsertionAdapter<CypherLogSubscriptionEntity> __upsertionAdapterOfCypherLogSubscriptionEntity;

  public CypherLogSubscriptionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__deletionAdapterOfCypherLogSubscriptionEntity = new EntityDeletionOrUpdateAdapter<CypherLogSubscriptionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `cypherlog_subscriptions` WHERE `dTag` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CypherLogSubscriptionEntity entity) {
        statement.bindString(1, entity.getDTag());
      }
    };
    this.__preparedStmtOfDeleteByDTag = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM cypherlog_subscriptions WHERE dTag = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM cypherlog_subscriptions";
        return _query;
      }
    };
    this.__upsertionAdapterOfCypherLogSubscriptionEntity = new EntityUpsertionAdapter<CypherLogSubscriptionEntity>(new EntityInsertionAdapter<CypherLogSubscriptionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT INTO `cypherlog_subscriptions` (`dTag`,`eventId`,`tagsJson`,`createdAt`,`contentDecryptedJson`) VALUES (?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CypherLogSubscriptionEntity entity) {
        statement.bindString(1, entity.getDTag());
        statement.bindString(2, entity.getEventId());
        statement.bindString(3, entity.getTagsJson());
        statement.bindLong(4, entity.getCreatedAt());
        if (entity.getContentDecryptedJson() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getContentDecryptedJson());
        }
      }
    }, new EntityDeletionOrUpdateAdapter<CypherLogSubscriptionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE `cypherlog_subscriptions` SET `dTag` = ?,`eventId` = ?,`tagsJson` = ?,`createdAt` = ?,`contentDecryptedJson` = ? WHERE `dTag` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final CypherLogSubscriptionEntity entity) {
        statement.bindString(1, entity.getDTag());
        statement.bindString(2, entity.getEventId());
        statement.bindString(3, entity.getTagsJson());
        statement.bindLong(4, entity.getCreatedAt());
        if (entity.getContentDecryptedJson() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getContentDecryptedJson());
        }
        statement.bindString(6, entity.getDTag());
      }
    });
  }

  @Override
  public Object delete(final CypherLogSubscriptionEntity entity,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfCypherLogSubscriptionEntity.handle(entity);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteByDTag(final String dTag, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteByDTag.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, dTag);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteByDTag.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteAll.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object upsert(final CypherLogSubscriptionEntity entity,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __upsertionAdapterOfCypherLogSubscriptionEntity.upsert(entity);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<CypherLogSubscriptionEntity>> getAll() {
    final String _sql = "SELECT * FROM cypherlog_subscriptions ORDER BY createdAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"cypherlog_subscriptions"}, new Callable<List<CypherLogSubscriptionEntity>>() {
      @Override
      @NonNull
      public List<CypherLogSubscriptionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfDTag = CursorUtil.getColumnIndexOrThrow(_cursor, "dTag");
          final int _cursorIndexOfEventId = CursorUtil.getColumnIndexOrThrow(_cursor, "eventId");
          final int _cursorIndexOfTagsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "tagsJson");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfContentDecryptedJson = CursorUtil.getColumnIndexOrThrow(_cursor, "contentDecryptedJson");
          final List<CypherLogSubscriptionEntity> _result = new ArrayList<CypherLogSubscriptionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final CypherLogSubscriptionEntity _item;
            final String _tmpDTag;
            _tmpDTag = _cursor.getString(_cursorIndexOfDTag);
            final String _tmpEventId;
            _tmpEventId = _cursor.getString(_cursorIndexOfEventId);
            final String _tmpTagsJson;
            _tmpTagsJson = _cursor.getString(_cursorIndexOfTagsJson);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpContentDecryptedJson;
            if (_cursor.isNull(_cursorIndexOfContentDecryptedJson)) {
              _tmpContentDecryptedJson = null;
            } else {
              _tmpContentDecryptedJson = _cursor.getString(_cursorIndexOfContentDecryptedJson);
            }
            _item = new CypherLogSubscriptionEntity(_tmpDTag,_tmpEventId,_tmpTagsJson,_tmpCreatedAt,_tmpContentDecryptedJson);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getByDTag(final String dTag,
      final Continuation<? super CypherLogSubscriptionEntity> $completion) {
    final String _sql = "SELECT * FROM cypherlog_subscriptions WHERE dTag = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, dTag);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<CypherLogSubscriptionEntity>() {
      @Override
      @Nullable
      public CypherLogSubscriptionEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfDTag = CursorUtil.getColumnIndexOrThrow(_cursor, "dTag");
          final int _cursorIndexOfEventId = CursorUtil.getColumnIndexOrThrow(_cursor, "eventId");
          final int _cursorIndexOfTagsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "tagsJson");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfContentDecryptedJson = CursorUtil.getColumnIndexOrThrow(_cursor, "contentDecryptedJson");
          final CypherLogSubscriptionEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpDTag;
            _tmpDTag = _cursor.getString(_cursorIndexOfDTag);
            final String _tmpEventId;
            _tmpEventId = _cursor.getString(_cursorIndexOfEventId);
            final String _tmpTagsJson;
            _tmpTagsJson = _cursor.getString(_cursorIndexOfTagsJson);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpContentDecryptedJson;
            if (_cursor.isNull(_cursorIndexOfContentDecryptedJson)) {
              _tmpContentDecryptedJson = null;
            } else {
              _tmpContentDecryptedJson = _cursor.getString(_cursorIndexOfContentDecryptedJson);
            }
            _result = new CypherLogSubscriptionEntity(_tmpDTag,_tmpEventId,_tmpTagsJson,_tmpCreatedAt,_tmpContentDecryptedJson);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<CypherLogSubscriptionEntity> getByDTagAsFlow(final String dTag) {
    final String _sql = "SELECT * FROM cypherlog_subscriptions WHERE dTag = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, dTag);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"cypherlog_subscriptions"}, new Callable<CypherLogSubscriptionEntity>() {
      @Override
      @Nullable
      public CypherLogSubscriptionEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfDTag = CursorUtil.getColumnIndexOrThrow(_cursor, "dTag");
          final int _cursorIndexOfEventId = CursorUtil.getColumnIndexOrThrow(_cursor, "eventId");
          final int _cursorIndexOfTagsJson = CursorUtil.getColumnIndexOrThrow(_cursor, "tagsJson");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfContentDecryptedJson = CursorUtil.getColumnIndexOrThrow(_cursor, "contentDecryptedJson");
          final CypherLogSubscriptionEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpDTag;
            _tmpDTag = _cursor.getString(_cursorIndexOfDTag);
            final String _tmpEventId;
            _tmpEventId = _cursor.getString(_cursorIndexOfEventId);
            final String _tmpTagsJson;
            _tmpTagsJson = _cursor.getString(_cursorIndexOfTagsJson);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpContentDecryptedJson;
            if (_cursor.isNull(_cursorIndexOfContentDecryptedJson)) {
              _tmpContentDecryptedJson = null;
            } else {
              _tmpContentDecryptedJson = _cursor.getString(_cursorIndexOfContentDecryptedJson);
            }
            _result = new CypherLogSubscriptionEntity(_tmpDTag,_tmpEventId,_tmpTagsJson,_tmpCreatedAt,_tmpContentDecryptedJson);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}

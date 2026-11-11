package com.cmyksoda.savepoint;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import java.util.List;

@Dao
public abstract class GameDao {
    @Query("SELECT * FROM games")
    public abstract List<GameRow> all();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insert(List<GameRow> rows);

    @Query("DELETE FROM games")
    public abstract void clear();

    @Transaction
    public void replace(List<GameRow> rows) {
        clear();
        insert(rows);
    }
}

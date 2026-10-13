package com.cmyksoda.savepoint;

import androidx.room.Database;
import androidx.room.RoomDatabase;

@Database(entities = {GameRow.class}, version = 1, exportSchema = false)
public abstract class LibraryDatabase extends RoomDatabase {
    public abstract GameDao games();
}

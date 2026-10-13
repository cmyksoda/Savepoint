package com.cmyksoda.savepoint;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "games")
public class GameRow {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String payload = "";
}

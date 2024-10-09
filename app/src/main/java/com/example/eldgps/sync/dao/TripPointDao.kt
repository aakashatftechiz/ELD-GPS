package com.example.eldgps.sync.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.eldgps.sync.entity.DatabaseTripPoint

@Dao
interface TripPointDao {
    @Query("select * from databasetrippoint")
    fun getTripPoints(): LiveData<List<DatabaseTripPoint>>

    @Query("DELETE FROM databasetrippoint")
    fun truncateTable()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll( videos: List<DatabaseTripPoint>)
}
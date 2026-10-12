package com.splitwise.app

import android.app.Application
import androidx.room.Room
import com.splitwise.app.data.ApiClients
import com.splitwise.app.data.OverviewCache
import com.splitwise.app.data.DataCache
import com.splitwise.app.data.OfflineWriteQueue
import com.splitwise.app.data.SplitEaseDatabase
import com.splitwise.app.data.Repository
import com.splitwise.app.data.TokenStore

class SplitwiseApp : Application() {
    private val database by lazy {
        Room.databaseBuilder(this, SplitEaseDatabase::class.java, "splitease.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    /** Manual DI: one repository shared by every ViewModel. */
    val repository: Repository by lazy {
        val tokens = TokenStore(this)
        Repository(tokens, ApiClients(tokens), OverviewCache(database.overviewCache()), DataCache(database.cachedData()), OfflineWriteQueue(database.pendingWrites()))
    }
}

package com.splitwise.app

import android.app.Application
import com.splitwise.app.data.ApiClients
import com.splitwise.app.data.Repository
import com.splitwise.app.data.TokenStore

class SplitwiseApp : Application() {
    /** Manual DI: one repository shared by every ViewModel. */
    val repository: Repository by lazy {
        val tokens = TokenStore(this)
        Repository(tokens, ApiClients(tokens))
    }
}

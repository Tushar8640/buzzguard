package com.buzzguard.app

import android.app.Application
import com.buzzguard.app.data.BuzzGuardRepository
import com.buzzguard.app.service.ActionExecutor

class BuzzGuardApp : Application() {
    lateinit var repository: BuzzGuardRepository
        private set
    lateinit var actionExecutor: ActionExecutor
        private set

    override fun onCreate() {
        super.onCreate()
        repository = BuzzGuardRepository(this)
        actionExecutor = ActionExecutor(this)
    }
}

package com.ajay902188.fmhy

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class FMHYPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FMHYProvider())
    }
}

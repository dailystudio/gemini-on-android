package com.dailystudio.gemini.core

import com.dailystudio.devbricksx.app.DevBricksApplication
import com.dailystudio.devbricksx.development.Logger
import com.facebook.stetho.Stetho
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class GeminiOnAndroidApplication : DevBricksApplication() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.USE_STETHO) {
            Stetho.initializeWithDefaults(this)
        }

        Logger.info("application is running in [%s] mode.",
            if (BuildConfig.DEBUG) "DEBUG" else "RELEASE")

        PDFBoxResourceLoader.init(this)
        
    }

    override fun isDebugBuild(): Boolean {
        return BuildConfig.DEBUG
    }

}
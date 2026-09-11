package com.example.muamaizingbot

import android.app.Application
import android.content.Context
import com.example.muamaizingbot.content.MapContentSync
import com.example.muamaizingbot.license.LicenseGate
import com.example.muamaizingbot.license.LicenseStore
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.settings.AppSettingsStore
import com.example.muamaizingbot.settings.LocaleHelper
import com.example.muamaizingbot.telegram.TelegramStore
import com.example.muamaizingbot.vision.focus.FocusPortraitClassifier
import com.example.muamaizingbot.vision.opencv.OpenCVInitializer
import com.example.muamaizingbot.vision.template.TemplateRepository

class MuBotApplication : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        OpenCVInitializer.init()
        TemplateRepository.init(this)
        FocusPortraitClassifier.init(this)
        MapDefinitionRepository.init(this)
        MapContentSync.init(this)
        ProfileRepository.init(this)
        LocationRepository.init(this)
        LocationRepository.refreshForCurrentProfile()
        LicenseStore.init(this)
        LicenseGate.init(this)
        TelegramStore.init(this)
        AppSettingsStore.init(this)
        com.example.muamaizingbot.settings.UiStrings.init(this)
        com.example.muamaizingbot.bot.BotDiagnosticJournal.init(this)
        com.example.muamaizingbot.bot.maintenance.ElfBuffDebugDump.init(this)
    }
}

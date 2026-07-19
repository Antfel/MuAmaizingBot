package com.example.muamaizingbot

import android.app.Application
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.vision.opencv.OpenCVInitializer
import com.example.muamaizingbot.vision.template.TemplateRepository

class MuBotApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        OpenCVInitializer.init()
        TemplateRepository.init(this)
        MapDefinitionRepository.init(this)
        ProfileRepository.init(this)
        LocationRepository.init(this)
        LocationRepository.refreshForCurrentProfile()
        com.example.muamaizingbot.bot.BotDiagnosticJournal.init(this)
        com.example.muamaizingbot.bot.maintenance.ElfBuffDebugDump.init(this)
    }
}

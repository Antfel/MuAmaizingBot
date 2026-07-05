package com.example.muamaizingbot

import android.app.Application
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.calibration.CalibrationRepository
import com.example.muamaizingbot.settings.ResolutionSettingsRepository
import com.example.muamaizingbot.vision.opencv.OpenCVInitializer
import com.example.muamaizingbot.vision.template.TemplateRepository

class MuBotApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        OpenCVInitializer.init()
        ResolutionSettingsRepository.init(this)
        CalibrationRepository.init(this)
        TemplateRepository.init(this)
        MapDefinitionRepository.init(this)
        ProfileRepository.init(this)
        LocationRepository.init(this)
        LocationRepository.refreshForCurrentProfile()
    }
}

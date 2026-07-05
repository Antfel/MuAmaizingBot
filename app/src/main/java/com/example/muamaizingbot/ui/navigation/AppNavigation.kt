package com.example.muamaizingbot.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.muamaizingbot.maps.MapDefinitionRepository
import com.example.muamaizingbot.profile.LocationRepository
import com.example.muamaizingbot.profile.ProfileRepository
import com.example.muamaizingbot.ui.home.HomeScreen
import com.example.muamaizingbot.ui.picker.LocationPickerType
import com.example.muamaizingbot.ui.picker.SpotPickerScreen
import com.example.muamaizingbot.ui.settings.PotionConfigScreen
import com.example.muamaizingbot.ui.settings.ProfileConfigureScreen
import com.example.muamaizingbot.ui.settings.ProfileListScreen
import com.example.muamaizingbot.ui.settings.ResolutionSettingsScreen
import com.example.muamaizingbot.ui.shell.ConfigDrawerContent
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val PROFILES = "profiles"
    const val PROFILE_CONFIGURE = "profile_configure/{profileStem}"
    const val SPOT_PICKER = "spot_picker/{profileStem}/{locationType}"
    const val POTION_CONFIG = "potion_config/{profileStem}"
    const val RESOLUTION = "resolution"

    fun profileConfigure(profileStem: String) = "profile_configure/$profileStem"

    fun spotPicker(profileStem: String, locationType: LocationPickerType): String {
        return "spot_picker/$profileStem/${locationType.name}"
    }

    fun potionConfig(profileStem: String) = "potion_config/$profileStem"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(
    onRequestCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val currentProfile by ProfileRepository.currentProfile.collectAsState()
    val farmSpot by LocationRepository.farmSpot.collectAsState()

    val profileLabel = currentProfile?.displayName ?: "Sin perfil activo"
    val farmSpotLabel = farmSpot?.summaryLabel(
        MapDefinitionRepository.getById(farmSpot?.map.orEmpty())?.name
    ) ?: "Farm spot sin configurar"

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                ConfigDrawerContent(
                    profileLabel = profileLabel,
                    farmSpotLabel = farmSpotLabel,
                    onOpenProfiles = {
                        scope.launch {
                            drawerState.close()
                            navController.navigate(Routes.PROFILES)
                        }
                    },
                    onOpenResolution = {
                        scope.launch {
                            drawerState.close()
                            navController.navigate(Routes.RESOLUTION)
                        }
                    },
                )
            }
        },
        modifier = modifier,
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("MU Amaizing Bot") },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Menú")
                        }
                    },
                )
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(innerPadding),
            ) {
                composable(Routes.HOME) {
                    HomeScreen(onRequestCapture = onRequestCapture)
                }
                composable(Routes.PROFILES) {
                    ProfileListScreen(
                        onConfigureProfile = { profileStem ->
                            navController.navigate(Routes.profileConfigure(profileStem))
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.RESOLUTION) {
                    ResolutionSettingsScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = Routes.PROFILE_CONFIGURE,
                    arguments = listOf(navArgument("profileStem") { type = NavType.StringType }),
                ) { entry ->
                    val profileStem = entry.arguments?.getString("profileStem").orEmpty()
                    ProfileConfigureScreen(
                        profileStem = profileStem,
                        onOpenFarmSpot = {
                            navController.navigate(
                                Routes.spotPicker(profileStem, LocationPickerType.FARM_SPOT)
                            )
                        },
                        onOpenElfBuff = {
                            navController.navigate(
                                Routes.spotPicker(profileStem, LocationPickerType.ELF_BUFF)
                            )
                        },
                        onOpenPotionConfig = {
                            navController.navigate(Routes.potionConfig(profileStem))
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = Routes.SPOT_PICKER,
                    arguments = listOf(
                        navArgument("profileStem") { type = NavType.StringType },
                        navArgument("locationType") { type = NavType.StringType },
                    ),
                ) { entry ->
                    val profileStem = entry.arguments?.getString("profileStem").orEmpty()
                    val locationType = entry.arguments?.getString("locationType")
                        ?.let { runCatching { LocationPickerType.valueOf(it) }.getOrNull() }
                        ?: LocationPickerType.FARM_SPOT
                    SpotPickerScreen(
                        profileStem = profileStem,
                        locationType = locationType,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    route = Routes.POTION_CONFIG,
                    arguments = listOf(navArgument("profileStem") { type = NavType.StringType }),
                ) { entry ->
                    val profileStem = entry.arguments?.getString("profileStem").orEmpty()
                    PotionConfigScreen(
                        profileStem = profileStem,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

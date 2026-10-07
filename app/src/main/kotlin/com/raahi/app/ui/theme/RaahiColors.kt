package com.raahi.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.raahi.app.domain.model.PlaceCategory

@Immutable
data class RaahiColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val offline: Color,
    val onOffline: Color,
    val offlineContainer: Color,
    val onOfflineContainer: Color,
    val emergency: Color,
    val onEmergency: Color,
    val emergencyContainer: Color,
    val onEmergencyContainer: Color,
    val saffron: Color,
    val categoryFuel: Color,
    val categoryEv: Color,
    val categoryHospital: Color,
    val categoryPharmacy: Color,
    val categoryPolice: Color,
    val categoryAtm: Color,
    val categoryRestaurant: Color,
    val categoryHotel: Color,
    val categoryParking: Color,
    val categoryToilet: Color,
) {
    fun categoryColor(category: PlaceCategory?): Color = when (category) {
        PlaceCategory.FUEL -> categoryFuel
        PlaceCategory.EV_CHARGING -> categoryEv
        PlaceCategory.HOSPITAL -> categoryHospital
        PlaceCategory.PHARMACY -> categoryPharmacy
        PlaceCategory.POLICE -> categoryPolice
        PlaceCategory.ATM -> categoryAtm
        PlaceCategory.RESTAURANT -> categoryRestaurant
        PlaceCategory.HOTEL -> categoryHotel
        PlaceCategory.PARKING -> categoryParking
        PlaceCategory.TOILET -> categoryToilet
        null -> saffron
    }
}

val LightRaahiColors = RaahiColors(
    success = SuccessLight,
    onSuccess = OnSuccessLight,
    successContainer = SuccessContainerLight,
    onSuccessContainer = OnSuccessContainerLight,
    offline = OfflineAmberLight,
    onOffline = OnOfflineAmberLight,
    offlineContainer = OfflineAmberContainerLight,
    onOfflineContainer = OnOfflineAmberContainerLight,
    emergency = EmergencyErrorLight,
    onEmergency = EmergencyOnErrorLight,
    emergencyContainer = EmergencyErrorContainerLight,
    onEmergencyContainer = EmergencyOnErrorContainerLight,
    saffron = SaffronAccentLight,
    categoryFuel = CategoryFuelLight,
    categoryEv = CategoryEvLight,
    categoryHospital = CategoryHospitalLight,
    categoryPharmacy = CategoryPharmacyLight,
    categoryPolice = CategoryPoliceLight,
    categoryAtm = CategoryAtmLight,
    categoryRestaurant = CategoryRestaurantLight,
    categoryHotel = CategoryHotelLight,
    categoryParking = CategoryParkingLight,
    categoryToilet = CategoryToiletLight,
)

val DarkRaahiColors = RaahiColors(
    success = SuccessDark,
    onSuccess = OnSuccessDark,
    successContainer = SuccessContainerDark,
    onSuccessContainer = OnSuccessContainerDark,
    offline = OfflineAmberDark,
    onOffline = OnOfflineAmberDark,
    offlineContainer = OfflineAmberContainerDark,
    onOfflineContainer = OnOfflineAmberContainerDark,
    emergency = EmergencyErrorDark,
    onEmergency = EmergencyOnErrorDark,
    emergencyContainer = EmergencyErrorContainerDark,
    onEmergencyContainer = EmergencyOnErrorContainerDark,
    saffron = SaffronAccentDark,
    categoryFuel = CategoryFuelDark,
    categoryEv = CategoryEvDark,
    categoryHospital = CategoryHospitalDark,
    categoryPharmacy = CategoryPharmacyDark,
    categoryPolice = CategoryPoliceDark,
    categoryAtm = CategoryAtmDark,
    categoryRestaurant = CategoryRestaurantDark,
    categoryHotel = CategoryHotelDark,
    categoryParking = CategoryParkingDark,
    categoryToilet = CategoryToiletDark,
)

val LocalRaahiColors = staticCompositionLocalOf { LightRaahiColors }

object RaahiTheme {
    val colors: RaahiColors
        @androidx.compose.runtime.Composable
        @androidx.compose.runtime.ReadOnlyComposable
        get() = LocalRaahiColors.current
}

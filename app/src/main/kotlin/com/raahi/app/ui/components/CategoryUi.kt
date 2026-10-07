package com.raahi.app.ui.components

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Atm
import androidx.compose.material.icons.outlined.EvStation
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.LocalPolice
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Wc
import androidx.compose.ui.graphics.vector.ImageVector
import com.raahi.app.R
import com.raahi.app.domain.model.PlaceCategory

@StringRes
fun PlaceCategory?.labelRes(): Int = when (this) {
    PlaceCategory.FUEL -> R.string.category_fuel
    PlaceCategory.EV_CHARGING -> R.string.category_ev_charging
    PlaceCategory.HOSPITAL -> R.string.category_hospital
    PlaceCategory.PHARMACY -> R.string.category_pharmacy
    PlaceCategory.POLICE -> R.string.category_police
    PlaceCategory.ATM -> R.string.category_atm
    PlaceCategory.RESTAURANT -> R.string.category_restaurant
    PlaceCategory.HOTEL -> R.string.category_hotel
    PlaceCategory.PARKING -> R.string.category_parking
    PlaceCategory.TOILET -> R.string.category_toilet
    null -> R.string.category_place
}

fun PlaceCategory?.icon(): ImageVector = when (this) {
    PlaceCategory.FUEL -> Icons.Outlined.LocalGasStation
    PlaceCategory.EV_CHARGING -> Icons.Outlined.EvStation
    PlaceCategory.HOSPITAL -> Icons.Outlined.LocalHospital
    PlaceCategory.PHARMACY -> Icons.Outlined.LocalPharmacy
    PlaceCategory.POLICE -> Icons.Outlined.LocalPolice
    PlaceCategory.ATM -> Icons.Outlined.Atm
    PlaceCategory.RESTAURANT -> Icons.Outlined.Restaurant
    PlaceCategory.HOTEL -> Icons.Outlined.Hotel
    PlaceCategory.PARKING -> Icons.Outlined.LocalParking
    PlaceCategory.TOILET -> Icons.Outlined.Wc
    null -> Icons.Outlined.Place
}

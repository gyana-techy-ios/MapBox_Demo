package com.raahi.app.domain.usecase

import com.raahi.app.domain.model.Route
import java.util.concurrent.ConcurrentHashMap

class ActiveRouteStore {
    private val routesByTrip = ConcurrentHashMap<Long, List<Route>>()

    fun put(tripId: Long, routes: List<Route>) {
        routesByTrip[tripId] = routes
    }

    fun get(tripId: Long): List<Route>? = routesByTrip[tripId]

    fun clear(tripId: Long) {
        routesByTrip.remove(tripId)
    }
}

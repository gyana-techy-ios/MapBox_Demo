package com.raahi.app.feature.guidance

enum class ManeuverGlyph {
    Depart, Arrive, Straight, Left, Right, SlightLeft, SlightRight, SharpLeft, SharpRight,
    UTurn, RoundaboutLeft, RoundaboutRight, ForkLeft, ForkRight, Merge,
}

object ManeuverGlyphs {
    fun glyphFor(type: String, modifier: String?): ManeuverGlyph = when (type) {
        "arrive" -> ManeuverGlyph.Arrive
        "depart" -> ManeuverGlyph.Depart
        "roundabout", "rotary", "roundabout turn", "exit roundabout", "exit rotary" ->
            if (modifier?.contains("right") == true) ManeuverGlyph.RoundaboutRight else ManeuverGlyph.RoundaboutLeft
        "fork" -> if (modifier?.contains("right") == true) ManeuverGlyph.ForkRight else ManeuverGlyph.ForkLeft
        "merge" -> ManeuverGlyph.Merge
        else -> byModifier(modifier)
    }

    private fun byModifier(modifier: String?): ManeuverGlyph = when (modifier) {
        "left" -> ManeuverGlyph.Left
        "right" -> ManeuverGlyph.Right
        "slight left" -> ManeuverGlyph.SlightLeft
        "slight right" -> ManeuverGlyph.SlightRight
        "sharp left" -> ManeuverGlyph.SharpLeft
        "sharp right" -> ManeuverGlyph.SharpRight
        "uturn" -> ManeuverGlyph.UTurn
        else -> ManeuverGlyph.Straight
    }
}

package cx.aswin.boxcast.feature.home.settings

internal enum class ProfileSettingsDestination(
    val title: String,
) {
    Hub("Settings"),
    Library("Library"),
    Appearance("Appearance"),
    Playback("Playback"),
    Downloads("Downloads"),
    Privacy("Privacy"),
    About("About boxlore"),
}

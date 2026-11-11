package com.guus.photocleaner.service

/**
 * How much of the photo library the app may read. Android 14+ lets the user grant access to
 * selected photos only, which the app treats as [PARTIAL] (works on those, offers full access).
 */
enum class MediaAccess {
    FULL,
    PARTIAL,
    DENIED,
    ;

    val canReadPhotos: Boolean get() = this != DENIED

    companion object {
        private const val READ_EXTERNAL = "android.permission.READ_EXTERNAL_STORAGE"
        private const val READ_IMAGES = "android.permission.READ_MEDIA_IMAGES"
        private const val READ_VIDEO = "android.permission.READ_MEDIA_VIDEO"
        private const val READ_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"

        private const val TIRAMISU = 33
        private const val UPSIDE_DOWN_CAKE = 34

        /** Runtime permissions to request on this [sdk] level. */
        fun permissionsToRequest(sdk: Int): List<String> = when {
            sdk >= UPSIDE_DOWN_CAKE -> listOf(READ_IMAGES, READ_VIDEO, READ_SELECTED)
            sdk >= TIRAMISU -> listOf(READ_IMAGES, READ_VIDEO)
            else -> listOf(READ_EXTERNAL)
        }

        /** Access level from the permissions currently [granted] on this [sdk] level. */
        fun resolve(sdk: Int, granted: (String) -> Boolean): MediaAccess = when {
            sdk >= TIRAMISU && granted(READ_IMAGES) && granted(READ_VIDEO) -> FULL
            sdk >= UPSIDE_DOWN_CAKE && granted(READ_SELECTED) -> PARTIAL
            sdk < TIRAMISU && granted(READ_EXTERNAL) -> FULL
            else -> DENIED
        }
    }
}

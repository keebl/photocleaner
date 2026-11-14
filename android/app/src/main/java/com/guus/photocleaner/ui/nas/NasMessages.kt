package com.guus.photocleaner.ui.nas

import android.content.Context
import com.guus.photocleaner.R
import com.guus.photocleaner.source.nas.NasError

/** The user-facing message for each NAS failure (specs/android-nas.md AC 4–6). */
fun nasErrorMessage(context: Context, error: NasError): String = context.getString(
    when (error) {
        NasError.AUTH_FAILED -> R.string.nas_error_auth
        NasError.UNREACHABLE -> R.string.nas_error_unreachable
        NasError.NOT_LOCAL -> R.string.nas_error_not_local
        NasError.SHARE_NOT_FOUND -> R.string.nas_error_share
        NasError.SMB1_ONLY -> R.string.nas_error_smb1
        NasError.BAD_SERVER -> R.string.nas_error_bad_server
        NasError.NOT_CONFIGURED -> R.string.nas_error_not_configured
        NasError.OTHER -> R.string.nas_error_other
    },
)

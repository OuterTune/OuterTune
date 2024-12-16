package com.dd3boh.outertune.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpotifyAuthResponse(
    @SerialName("access_token")
    val accessToken: String

    /*
    only `access_token` is required for the import process.
    the spotify servers will be triggered again if a new import needs to be made.
    */

)

package top.kagg886.pixko.internal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * ================================================
 * Author:     886kagg
 * Created on: 2026/8/15 10:42
 * ================================================
 */

@Serializable
internal data class TokenRefreshAccountInfo(
    @SerialName("profile_image_urls")
    val profileImageUrls: TokenRefreshProfileImageUrls,
    val id: String,
    val name: String,
    val account: String,
    @SerialName("mail_address")
    val mailAddress: String,
    @SerialName("is_premium")
    val isPremium: Boolean,
    @SerialName("x_restrict")
    val xRestrict: Int,
    @SerialName("is_mail_authorized")
    val isMailAuthorized: Boolean,
    @SerialName("require_policy_agreement")
    val requirePolicyAgreement: Boolean,
)

@Serializable
internal data class TokenRefreshProfileImageUrls(
    @SerialName("px_16x16")
    val px16x16: String,
    @SerialName("px_50x50")
    val px50x50: String,
    @SerialName("px_170x170")
    val px170x170: String,
)

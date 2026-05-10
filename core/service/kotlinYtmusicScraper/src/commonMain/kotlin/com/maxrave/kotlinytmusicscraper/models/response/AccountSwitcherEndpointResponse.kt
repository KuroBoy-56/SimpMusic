package com.maxrave.kotlinytmusicscraper.models.response

import com.maxrave.kotlinytmusicscraper.models.AccountInfo
import com.maxrave.kotlinytmusicscraper.models.Run
import com.maxrave.kotlinytmusicscraper.models.Thumbnail
import kotlinx.serialization.Serializable

@Serializable
data class AccountSwitcherEndpointResponse(
    val code: String?,
    val data: AccountSwitcherData?,
)

@Serializable
data class AccountSwitcherData(
    val actions: List<AccountSwitcherAction?>?,
    val contents: List<AccountSwitcherSection>?,
    val responseContext: AccountSwitcherResponseContext?,
    val selectText: AccountSwitcherSelectText?,
)

@Serializable
data class AccountSwitcherAction(
    val getMultiPageMenuAction: GetMultiPageMenuAction?,
)

@Serializable
data class GetMultiPageMenuAction(
    val menu: AccountSwitcherMenu?,
)

@Serializable
data class AccountSwitcherMenu(
    val multiPageMenuRenderer: MultiPageMenuRenderer?,
)

@Serializable
data class MultiPageMenuRenderer(
    val footer: AccountSwitcherFooter?,
    val header: AccountSwitcherHeader?,
    val sections: List<AccountSwitcherSection?>?,
    val style: String?,
)

@Serializable
data class AccountSwitcherFooter(
    val multiPageMenuSectionRenderer: MultiPageMenuSectionRenderer?,
)

@Serializable
data class MultiPageMenuSectionRenderer(
    val items: List<AccountSwitcherItem?>?,
)

@Serializable
data class AccountSwitcherItem(
    val compactLinkRenderer: CompactLinkRenderer?,
)

@Serializable
data class CompactLinkRenderer(
    val icon: AccountSwitcherIcon?,
    val navigationEndpoint: AccountSwitcherNavigationEndpoint?,
    val style: String?,
    val title: AccountSwitcherTitle?,
)

@Serializable
data class AccountSwitcherIcon(
    val iconType: String?,
)

@Serializable
data class AccountSwitcherNavigationEndpoint(
    val signOutEndpoint: SignOutEndpoint?,
    val urlEndpoint: UrlEndpoint?,
)

@Serializable
data class SignOutEndpoint(
    val hack: Boolean?,
)

@Serializable
data class UrlEndpoint(
    val url: String?,
)

@Serializable
data class AccountSwitcherTitle(
    val runs: List<Run?>?,
)

@Serializable
data class AccountSwitcherHeader(
    val simpleMenuHeaderRenderer: SimpleMenuHeaderRenderer?,
)

@Serializable
data class SimpleMenuHeaderRenderer(
    val backButton: AccountSwitcherBackButton?,
    val title: AccountSwitcherTitle?,
)

@Serializable
data class AccountSwitcherBackButton(
    val buttonRenderer: AccountSwitcherButtonRenderer?,
)

@Serializable
data class AccountSwitcherButtonRenderer(
    val accessibility: AccountSwitcherAccessibility?,
    val accessibilityData: AccountSwitcherAccessibilityData?,
    val icon: AccountSwitcherIcon?,
    val isDisabled: Boolean?,
    val size: String?,
    val style: String?,
)

@Serializable
data class AccountSwitcherAccessibility(
    val label: String?,
)

@Serializable
data class AccountSwitcherAccessibilityData(
    val accessibilityData: AccountSwitcherAccessibilityDataInner?,
)

@Serializable
data class AccountSwitcherAccessibilityDataInner(
    val label: String?,
)

@Serializable
data class AccountSwitcherSection(
    val accountSectionListRenderer: AccountSectionListRenderer?,
)

@Serializable
data class AccountSectionListRenderer(
    val contents: List<AccountSwitcherContent?>?,
    val header: AccountSwitcherSectionHeader?,
)

@Serializable
data class AccountSwitcherContent(
    val accountItemSectionRenderer: AccountItemSectionRenderer?,
)

@Serializable
data class AccountItemSectionRenderer(
    val contents: List<AccountItemContent?>?,
    val header: AccountItemSectionHeader?,
)

@Serializable
data class AccountItemContent(
    val accountItem: AccountItem?,
)

@Serializable
data class AccountItem(
    val onBehalfOfParameter: String?,
    val accountByline: AccountByline?,
    val accountLogDirectiveInts: List<Int?>?,
    val accountName: AccountName?,
    val accountPhoto: AccountPhoto?,
    val channelHandle: ChannelHandle?,
    val hasChannel: Boolean?,
    val isDisabled: Boolean?,
    val isSelected: Boolean?,
    val mobileBanner: MobileBanner?,
    val serviceEndpoint: AccountItemServiceEndpoint?,
    val unlimitedStatus: List<UnlimitedStatus?>?,
) {
    fun toAccountInfo(email: String): AccountInfo? {
        return AccountInfo(
            name = accountName?.simpleText ?: return null,
            email = email,
            pageId =
            onBehalfOfParameter
                ?: serviceEndpoint
                    ?.selectActiveIdentityEndpoint
                    ?.supportedTokens
                    ?.firstOrNull { it?.pageIdToken != null }
                    ?.pageIdToken
                    ?.pageId,
            thumbnails = accountPhoto?.thumbnails?.filterNotNull() ?: emptyList(),
        )
    }
}

@Serializable
data class AccountByline(
    val simpleText: String,
)

@Serializable
data class AccountName(
    val simpleText: String,
)

@Serializable
data class AccountPhoto(
    val thumbnails: List<Thumbnail?>?,
)

@Serializable
data class ChannelHandle(
    val simpleText: String,
)

@Serializable
data class MobileBanner(
    val thumbnails: List<Thumbnail?>?,
)

@Serializable
data class AccountItemServiceEndpoint(
    val selectActiveIdentityEndpoint: SelectActiveIdentityEndpoint?,
)

@Serializable
data class UnlimitedStatus(
    val runs: List<Run?>?,
)

@Serializable
data class AccountItemSectionHeader(
    val accountItemSectionHeaderRenderer: AccountItemSectionHeaderRenderer?,
)

@Serializable
data class AccountItemSectionHeaderRenderer(
    val title: AccountSwitcherTitle?,
)

@Serializable
data class AccountSwitcherSectionHeader(
    val accountsDialogHeaderRenderer: AccountsDialogHeaderRenderer?,
    val googleAccountHeaderRenderer: GoogleAccountHeaderRenderer?,
)

@Serializable
data class AccountsDialogHeaderRenderer(
    val text: AccountSwitcherText?,
)

@Serializable
data class AccountSwitcherText(
    val runs: List<Run?>?,
)

@Serializable
data class GoogleAccountHeaderRenderer(
    val email: AccountSwitcherEmail?,
    val name: AccountSwitcherName?,
)

@Serializable
data class AccountSwitcherEmail(
    val runs: List<Run?>?,
)

@Serializable
data class AccountSwitcherName(
    val runs: List<Run?>?,
)

@Serializable
data class AccountSwitcherResponseContext(
    val serviceTrackingParams: List<AccountSwitcherServiceTrackingParam?>?,
)

@Serializable
data class AccountSwitcherServiceTrackingParam(
    val params: List<AccountSwitcherParam?>?,
    val service: String?,
)

@Serializable
data class AccountSwitcherParam(
    val key: String?,
    val value: String?,
)

@Serializable
data class AccountSwitcherSelectText(
    val runs: List<Run?>?,
)

@Serializable
data class SelectActiveIdentityEndpoint(
    val supportedTokens: List<SupportedToken?>?,
)

@Serializable
data class SupportedToken(
    val accountSigninToken: AccountSigninToken?,
    val accountStateToken: AccountStateToken?,
    val datasyncIdToken: DatasyncIdToken?,
    val offlineCacheKeyToken: OfflineCacheKeyToken?,
    val pageIdToken: PageIdToken?,
)

@Serializable
data class AccountSigninToken(
    val signinUrl: String?,
)

@Serializable
data class AccountStateToken(
    val hasChannel: Boolean?,
    val isMerged: Boolean?,
    val obfuscatedGaiaId: String?,
)

@Serializable
data class DatasyncIdToken(
    val datasyncIdToken: String?,
)

@Serializable
data class OfflineCacheKeyToken(
    val clientCacheKey: String?,
)

@Serializable
data class PageIdToken(
    val pageId: String?,
)

fun AccountSwitcherEndpointResponse.toListAccountInfo(): List<AccountInfo> {
    if (this.code == "SUCCESS" && this.data != null) {
        val list = mutableListOf<AccountInfo>()
        this.data.contents
            ?.firstOrNull()
            ?.accountSectionListRenderer
            ?.contents
            ?.firstOrNull()
            ?.accountItemSectionRenderer
            ?.contents
            ?.forEach { content ->
                content?.accountItem?.let { accountItem ->
                    accountItem
                        .toAccountInfo(
                            email =
                            accountItem.channelHandle
                                ?.simpleText ?: "",
                        )?.let {
                            list.add(it)
                        }
                }
            }
        return list
    } else {
        return emptyList()
    }
}

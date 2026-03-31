package io.github.hiro.lime

import android.content.Context

class LimeOptions {

    // 使用 data class 自動生成 toString, equals, hashCode 等方法
    data class Option(
        val name: String,
        var id: Int,
        var checked: Boolean,
        val category: OptionCategory
    )

    enum class OptionCategory(private val resourceId: Int) {
        GENERAL(R.string.general),
        Ad(R.string.Ad),
        NOTIFICATIONS(R.string.notifications),
        Theme(R.string.Theme),
        CHAT(R.string.chat),
        CALL(R.string.call),
        OTHER(R.string.other);

        fun getResourceId(): Int = resourceId

        fun getName(context: Context): String = context.getString(resourceId)
    }

    // --- 實體化選項 ---

    val removeAllServices = Option("remove_Services", R.string.RemoveService, false, OptionCategory.GENERAL)
    val removeOption = Option("removeOption", R.string.switch_unembed_options, false, OptionCategory.GENERAL)
    val removeVoom = Option("remove_voom", R.string.switch_remove_voom, false, OptionCategory.GENERAL)
    val removeWallet = Option("remove_wallet", R.string.switch_remove_wallet, false, OptionCategory.GENERAL)
    val removeNewsOrCall = Option("remove_news_or_call", R.string.switch_remove_news_or_call, false, OptionCategory.GENERAL)
    val distributeEvenly = Option("distribute_evenly", R.string.switch_distribute_evenly, false, OptionCategory.GENERAL)
    val extendClickableArea = Option("extend_clickable_area", R.string.switch_extend_clickable_area, false, OptionCategory.GENERAL)
    val removeIconLabels = Option("remove_icon_labels", R.string.switch_remove_icon_labels, false, OptionCategory.GENERAL)
    val removeServiceLabels = Option("remove_service_labels", R.string.switch_remove_service_labels, false, OptionCategory.GENERAL)
    val removeSearchBar = Option("removeSearchBar", R.string.removeSearchBar, false, OptionCategory.GENERAL)
    val removeNaviAlbum = Option("removeNaviAlbum", R.string.removeNaviAlbum, false, OptionCategory.GENERAL)
    val removeNaviOpenchat = Option("removeNaviOpenchat", R.string.removeNaviOpenchat, false, OptionCategory.GENERAL)

    val redirectWebView = Option("redirect_webview", R.string.switch_redirect_webview, false, OptionCategory.GENERAL)
    val openInBrowser = Option("open_in_browser", R.string.switch_open_in_browser, false, OptionCategory.GENERAL)
    val removeNotification = Option("RemoveProfileNotification", R.string.removeNotification, false, OptionCategory.GENERAL)

    val removeAds = Option("remove_ads", R.string.switch_remove_ads, true, OptionCategory.Ad)
    val removeRecommendation = Option("remove_recommendation", R.string.switch_remove_recommendation, false, OptionCategory.Ad)
    val removePremiumRecommendation = Option("remove_premium_recommendation", R.string.switch_remove_premium_recommendation, false, OptionCategory.Ad)
    val blockTracking = Option("block_tracking", R.string.switch_block_tracking, false, OptionCategory.Ad)

    val preventMarkAsRead = Option("prevent_mark_as_read", R.string.switch_prevent_mark_as_read, false, OptionCategory.CHAT)
    val preventUnsendMessage = Option("prevent_unsend_message", R.string.switch_prevent_unsend_message, true, OptionCategory.CHAT)
    val sendMuteMessage = Option("mute_message", R.string.switch_send_mute_message, false, OptionCategory.CHAT)
    val removeKeepUnread = Option("remove_keep_unread", R.string.switch_remove_keep_unread, false, OptionCategory.CHAT)
    val keepUnreadLSpatch = Option("Keep_UnreadLSpatch", R.string.switch_KeepUnreadLSpatch, false, OptionCategory.CHAT)
    val archived = Option("Archived_message", R.string.switch_archived, true, OptionCategory.CHAT)
    val readChecker = Option("ReadChecker", R.string.ReadChecker, false, OptionCategory.CHAT)
    val readCheckerChatdataDelete = Option("ReadCheckerChatdataDelete", R.string.ReadCheckerChatdataDelete, false, OptionCategory.CHAT)
    val mySendMessage = Option("MySendMessage", R.string.MySendMessage, false, OptionCategory.CHAT)
    val reactionCount = Option("ReactionCount", R.string.ReactionCount, false, OptionCategory.CHAT)

    val blockCheck = Option("BlockCheck", R.string.BlockCheck, false, OptionCategory.CHAT)

    val cansellNotification = Option("CansellNotification", R.string.CansellNotification, false, OptionCategory.NOTIFICATIONS)
    val blockUpdateProfileNotification = Option("BlockUpdateProfileNotification", R.string.switch_BlockUpdateProfileNotification, false, OptionCategory.NOTIFICATIONS)

    val muteGroup = Option("Disabled_Group_notification", R.string.MuteGroup, false, OptionCategory.NOTIFICATIONS)
    val photoAddNotification = Option("PhotoAddNotification", R.string.PhotoAddNotification, false, OptionCategory.NOTIFICATIONS)
    val groupNotification = Option("GroupNotification", R.string.GroupNotification, false, OptionCategory.NOTIFICATIONS)
    val addCopyAction = Option("AddCopyAction", R.string.AddCopyAction, false, OptionCategory.NOTIFICATIONS)
    val removeReplyMute = Option("remove_reply_mute", R.string.switch_remove_reply_mute, false, OptionCategory.NOTIFICATIONS)
    val originalId = Option("original_ID", R.string.original_ID, false, OptionCategory.NOTIFICATIONS)
    val disableSilentMessage = Option("DisableSilentMessage", R.string.DisableSilentMessage, false, OptionCategory.NOTIFICATIONS)

    val notificationNull = Option("NotificationNull", R.string.NotificationNull, false, OptionCategory.NOTIFICATIONS)
    val notificationReaction = Option("NotificationReaction", R.string.NotificationReaction, false, OptionCategory.NOTIFICATIONS)

    val darkColor = Option("DarkColor", R.string.DarkColor, false, OptionCategory.Theme)
    val darkModSync = Option("DarkModSync", R.string.DarkkModSync, false, OptionCategory.Theme)

    val callTone = Option("callTone", R.string.callTone, false, OptionCategory.CALL)
    val muteTone = Option("MuteTone", R.string.MuteTone, false, OptionCategory.CALL)
    val dialTone = Option("DialTone", R.string.DialTone, false, OptionCategory.CALL)
    val ringtonevolume = Option("ringtonevolume", R.string.ringtonevolume, false, OptionCategory.CALL)
    val silentCheck = Option("SilentCheck", R.string.SilentCheck, false, OptionCategory.CALL)

    val removeVoiceRecord = Option("RemoveVoiceRecord", R.string.RemoveVoiceRecord, false, OptionCategory.CHAT)
    val callOpenApplication = Option("CallOpenApplication", R.string.CallOpenApplication, false, OptionCategory.CALL)
    val settingClick = Option("SettingClick", R.string.SettingClick, false, OptionCategory.OTHER)
    val photoboothButtonOption = Option("photoboothButtonOption", R.string.photoboothButtonOption, false, OptionCategory.CHAT)
    val voiceButtonOption = Option("voiceButtonOption", R.string.voiceButtonOption, false, OptionCategory.CHAT)
    val videoButtonOption = Option("videoButtonOption", R.string.videoButtonOption, false, OptionCategory.CHAT)
    val videoSingleButtonOption = Option("videoSingleButtonOption", R.string.videoSingleButtonOption, false, OptionCategory.CHAT)

    val pinList = Option("PinList", R.string.PinList, false, OptionCategory.CHAT)

    val outputCommunication = Option("output_communication", R.string.switch_output_communication, false, OptionCategory.OTHER)
    val newOption = Option("NewOption", R.string.NewOption, false, OptionCategory.OTHER)
    val ageCheckSkip = Option("AgeCheckSkip", R.string.AgeCheckSkip, false, OptionCategory.OTHER)
    val autoUpDateCheck = Option("AutoUpDateCheck", R.string.AutoUpDateCheck, false, OptionCategory.OTHER)

    val photoSave = Option("PhotoSave", R.string.PhotoSave, false, OptionCategory.CHAT)
    val stopVersionCheck = Option("stop_version_check", R.string.switch_stop_version_check, false, OptionCategory.OTHER)
    val whiteToDark = Option("WhiteToDark", R.string.WhiteToDark, false, OptionCategory.Theme)

    val stopCallTone = Option("StopCallTone", R.string.StopCallTone, false, OptionCategory.CALL)
    val fastShare = Option("fast_share", R.string.fast_share, false, OptionCategory.CHAT)

    // 使用 arrayOf 定義 options 陣列
    val options = arrayOf(
        removeOption,
        fastShare,
        removeVoom,
        removeWallet,
        removeNewsOrCall,
        distributeEvenly,
        extendClickableArea,
        removeIconLabels,
        removeAds,
        removeRecommendation,
        removePremiumRecommendation,
        removeAllServices,
        removeServiceLabels,
        removeNotification,
        removeNaviOpenchat,
        removeNaviAlbum,
        removeSearchBar,
        removeReplyMute,
        redirectWebView,
        openInBrowser,
        preventMarkAsRead,
        preventUnsendMessage,
        sendMuteMessage,
        archived,
        readChecker, mySendMessage, readCheckerChatdataDelete,
        removeKeepUnread,
        keepUnreadLSpatch,
        stopVersionCheck,
        outputCommunication,
        callTone, ringtonevolume,
        muteTone,
        dialTone, silentCheck,
        darkColor, darkModSync,
        muteGroup,
        photoAddNotification, groupNotification, cansellNotification, addCopyAction, originalId, notificationNull, disableSilentMessage,
        removeVoiceRecord,
        ageCheckSkip,
        callOpenApplication,
        blockCheck, settingClick,
        photoboothButtonOption, voiceButtonOption, videoButtonOption, videoSingleButtonOption,
        autoUpDateCheck, pinList,
        newOption, photoSave,
        reactionCount, whiteToDark, notificationReaction, stopCallTone
    )
}

package jp.juggler.subwaytooter

import android.content.Context
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.JustifyContent
import jp.juggler.subwaytooter.action.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.util.CustomShare
import jp.juggler.subwaytooter.util.CustomShareTarget
import jp.juggler.subwaytooter.util.emptyCallback
import jp.juggler.subwaytooter.util.startMargin
import jp.juggler.subwaytooter.view.CountImageButton
import jp.juggler.util.*
import org.jetbrains.anko.*
import org.jetbrains.anko.custom.customView

class StatusButtons(
    private val activity: ActMain,
    private val column: Column,
    private val bSimpleList: Boolean,

    private val holder: StatusButtonsViewHolder,
    private val itemViewHolder: ItemViewHolder

) : View.OnClickListener, View.OnLongClickListener {

    companion object {

        val log = LogCategory("StatusButtons")
    }

    private val access_info: SavedAccount
    private var relation: UserRelation? = null
    private var status: TootStatus? = null
    private var notification: TootNotification? = null

    var close_window: PopupWindow? = null

    private val btnConversation = holder.btnConversation
    private val btnReply = holder.btnReply
    private val btnBoost = holder.btnBoost
    private val btnFavourite = holder.btnFavourite
    private val btnBookmark = holder.btnBookmark
    private val btnQuote = holder.btnQuote
    private val btnReaction = holder.btnReaction
    private val llFollow2 = holder.llFollow2
    private val btnFollow2 = holder.btnFollow2
    private val ivFollowedBy2 = holder.ivFollowedBy2
    private val btnTranslate = holder.btnTranslate
    private val btnCustomShare1 = holder.btnCustomShare1
    private val btnCustomShare2 = holder.btnCustomShare2
    private val btnCustomShare3 = holder.btnCustomShare3
    private val btnMore = holder.btnMore

    private val color_normal = column.getContentColor()

    private val color_accent: Int
        get() = activity.attrColor(R.attr.colorImageButtonAccent)

    init {
        this.access_info = column.access_info

        arrayOf(
            btnBoost,
            btnFavourite,
            btnBookmark,
            btnQuote,
            btnReaction,
            btnFollow2,
            btnConversation,
            btnReply,
            btnTranslate,
            btnCustomShare1,
            btnCustomShare2,
            btnCustomShare3,
        ).forEach {
            it.setOnClickListener(this)
            it.setOnLongClickListener(this)
        }

        // moreボタンだけ長押しがない
        btnMore.setOnClickListener(this)
    }

    fun hide() {
        holder.viewRoot.visibility = View.GONE
    }

    fun bind(status: TootStatus, notification: TootNotification?) {
        holder.viewRoot.visibility = View.VISIBLE
        this.status = status
        this.notification = notification

        val pref = activity.pref

        setIconDrawableId(
            activity,
            btnConversation,
            R.drawable.ic_forum,
            color = color_normal,
            alphaMultiplier = Styler.boost_alpha
        )

        setIconDrawableId(
            activity,
            btnMore,
            R.drawable.ic_more,
            color = color_normal,
            alphaMultiplier = Styler.boost_alpha
        )

        setButton(
            btnReply,
            true,
            color_normal,
            R.drawable.ic_reply,
            when (val replies_count = status.replies_count) {
                null -> ""
                else -> when (Pref.ipRepliesCount(activity.pref)) {
                    Pref.RC_SIMPLE -> when {
                        replies_count >= 2L -> "1+"
                        replies_count == 1L -> "1"
                        else -> ""
                    }
                    Pref.RC_ACTUAL -> replies_count.toString()
                    else -> ""
                }
            },
            activity.getString(R.string.reply)
        )

        // ブーストボタン
        when {
            // マストドンではDirectはブーストできない (Misskeyはできる)
            (!access_info.isMisskey && status.visibility.order <= TootVisibility.DirectSpecified.order) ->
                setButton(
                    btnBoost,
                    false,
                    color_accent,
                    R.drawable.ic_mail,
                    "",
                    activity.getString(R.string.boost)
                )

            activity.app_state.isBusyBoost(access_info, status) ->
                setButton(
                    btnBoost,
                    false,
                    color_normal,
                    R.drawable.ic_refresh,
                    "?",
                    activity.getString(R.string.boost)
                )

            else -> setButton(
                btnBoost,
                true,
                if (status.reblogged)
                    Pref.ipButtonBoostedColor(pref).notZero() ?: color_accent
                else
                    color_normal,
                R.drawable.ic_repeat,
                when (val boosts_count = status.reblogs_count) {
                    null -> ""
                    else -> when (Pref.ipBoostsCount(activity.pref)) {
                        Pref.RC_SIMPLE -> when {
                            boosts_count >= 2L -> "1+"
                            boosts_count == 1L -> "1"
                            else -> ""
                        }
                        Pref.RC_ACTUAL -> boosts_count.toString()
                        else -> ""
                    }
                },
                activity.getString(R.string.boost)
            )
        }

        val ti = TootInstance.getCached(access_info)
        btnQuote.vg(ti?.feature_quote == true)?.let {
            setButton(
                it,
                true,
                color_normal,
                R.drawable.ic_quote,
                activity.getString(R.string.quote)
            )
        }

        btnReaction.vg(TootReaction.canReaction(access_info, ti))?.let {
            val canMultipleReaction = InstanceCapability.canMultipleReaction(access_info,ti)
            val hasMyReaction = status.reactionSet?.hasMyReaction() == true
            val bRemoveButton = hasMyReaction &&!canMultipleReaction
            setButton(
                it,
                true,
                color_normal,
                if (bRemoveButton)
                    R.drawable.ic_remove
                else
                    R.drawable.ic_add,
                activity.getString(
                    if (bRemoveButton)
                        R.string.reaction_remove
                    else
                        R.string.reaction_add
                )
            )
        }

        // お気に入りボタン
        val fav_icon_drawable = when {
            access_info.isNicoru(status.account) -> R.drawable.ic_nicoru
            else -> R.drawable.ic_star
        }
        when {
            activity.app_state.isBusyFav(access_info, status) -> setButton(
                btnFavourite,
                false,
                color_normal,
                R.drawable.ic_refresh,
                "?",
                activity.getString(R.string.favourite)
            )

            else -> setButton(
                btnFavourite,
                true,
                if (status.favourited)
                    Pref.ipButtonFavoritedColor(pref).notZero() ?: color_accent
                else
                    color_normal,
                fav_icon_drawable,
                when (val favourites_count = status.favourites_count) {
                    null -> ""
                    else -> when (Pref.ipFavouritesCount(activity.pref)) {
                        Pref.RC_SIMPLE -> when {
                            favourites_count >= 2L -> "1+"
                            favourites_count == 1L -> "1"
                            else -> ""
                        }
                        Pref.RC_ACTUAL -> favourites_count.toString()
                        else -> ""
                    }
                },
                activity.getString(R.string.favourite)
            )
        }

        // ブックマークボタン
        when {
            !Pref.bpShowBookmarkButton(activity.pref) -> btnBookmark.vg(false)

            activity.app_state.isBusyBookmark(access_info, status) -> setButton(
                btnBookmark,
                false,
                color_normal,
                R.drawable.ic_refresh,
                activity.getString(R.string.bookmark)
            )

            else -> setButton(
                btnBookmark,
                true,
                if (status.bookmarked)
                    Pref.ipButtonBookmarkedColor(pref).notZero() ?: color_accent
                else
                    color_normal,
                R.drawable.ic_bookmark,
                activity.getString(R.string.bookmark)
            )
        }

        val account = status.account

        this.relation = if (!Pref.bpShowFollowButtonInButtonBar(activity.pref)) {
            llFollow2.visibility = View.GONE
            null
        } else {
            llFollow2.visibility = View.VISIBLE
            val relation = UserRelation.load(access_info.db_id, account.id)
            Styler.setFollowIcon(
                activity,
                btnFollow2,
                ivFollowedBy2,
                relation,
                account,
                color_normal,
                alphaMultiplier = Styler.boost_alpha
            )
            relation
        }

        var optionalButtonFirst: View? = null
        var optionalButtonCount = 0

        fun ImageButton.showCustomShare(target: CustomShareTarget) {
            val (label, icon) = CustomShare.getCache(target)
                ?: error("showCustomShare: invalid target")

            vg(label != null || icon != null)?.apply {
                isEnabled = true
                contentDescription = label ?: "?"
                setImageDrawable(
                    icon ?: createColoredDrawable(
                        this@StatusButtons.activity,
                        R.drawable.ic_question,
                        color_normal,
                        Styler.boost_alpha
                    )
                )
                ++optionalButtonCount
                if (optionalButtonFirst == null) {
                    optionalButtonFirst = this
                }
            }
        }

        btnTranslate.vg(Pref.bpShowTranslateButton(activity.pref))
            ?.showCustomShare(CustomShareTarget.Translate)

        btnCustomShare1.showCustomShare(CustomShareTarget.CustomShare1)
        btnCustomShare2.showCustomShare(CustomShareTarget.CustomShare2)
        btnCustomShare3.showCustomShare(CustomShareTarget.CustomShare3)

        val lpConversation = btnConversation.layoutParams as? FlexboxLayout.LayoutParams
        val updateAdditionalButton: (btn: ImageButton) -> Unit
        when (Pref.ipAdditionalButtonsPosition(activity.pref)) {
            Pref.ABP_TOP -> {
                // 1行目に追加ボタンが並ぶ
                updateAdditionalButton = { btn ->
                    (btn.layoutParams as? FlexboxLayout.LayoutParams)?.let { lp ->
                        lp.isWrapBefore = false
                        lp.startMargin = when (btn) {
                            optionalButtonFirst -> 0
                            else -> holder.marginBetween
                        }
                    }
                }
                // 2行目は通常ボタンが並ぶ
                // 2行目最初のボタンのstartMarginは追加ボタンの有無で変化する
                lpConversation?.startMargin = 0
                lpConversation?.isWrapBefore = (optionalButtonCount != 0)
            }

            Pref.ABP_START -> {
                // 始端に追加ボタンが並ぶ
                updateAdditionalButton = { btn ->
                    (btn.layoutParams as? FlexboxLayout.LayoutParams)?.let { lp ->
                        lp.isWrapBefore = false
                        lp.startMargin = when (btn) {
                            optionalButtonFirst -> 0
                            else -> holder.marginBetween
                        }
                    }
                }
                // 続いて通常ボタンが並ぶ
                lpConversation?.startMargin = holder.marginBetween
                lpConversation?.isWrapBefore = false
            }

            Pref.ABP_END -> {
                // 始端に通常ボタンが並ぶ
                lpConversation?.startMargin = 0
                lpConversation?.isWrapBefore = false
                // 続いて追加ボタンが並ぶ
                updateAdditionalButton = { btn ->
                    (btn.layoutParams as? FlexboxLayout.LayoutParams)?.let { lp ->
                        lp.isWrapBefore = false
                        lp.startMargin = holder.marginBetween
                    }
                }
            }

            else /* Pref.ABP_BOTTOM */ -> {
                // 1行目は通常ボタンが並ぶ
                lpConversation?.startMargin = 0
                lpConversation?.isWrapBefore = false
                // 2行目は追加ボタンが並ぶ
                updateAdditionalButton = { btn ->
                    (btn.layoutParams as? FlexboxLayout.LayoutParams)?.let { lp ->
                        lp.isWrapBefore = btn == optionalButtonFirst
                        lp.startMargin = when (btn) {
                            optionalButtonFirst -> 0
                            else -> holder.marginBetween
                        }
                    }
                }
            }

        }

        updateAdditionalButton(btnTranslate)
        updateAdditionalButton(btnCustomShare1)
        updateAdditionalButton(btnCustomShare2)
        updateAdditionalButton(btnCustomShare3)
    }

    private fun setButton(
        b: CountImageButton,
        enabled: Boolean,
        color: Int,
        drawableId: Int,
        count: String,
        contentDescription: String
    ) {
        val alpha = Styler.boost_alpha
        val d = createColoredDrawable(
            activity,
            drawableId,
            color,
            alpha
        )
        b.setImageDrawable(d)
        b.setPaddingAndText(holder.paddingH, holder.paddingV, count, 14f, holder.compoundPaddingDp)
        b.setTextColor(color.applyAlphaMultiplier(alpha))
        b.contentDescription = contentDescription + count
        b.isEnabled = enabled
    }

    private fun setButton(
        b: ImageButton,
        enabled: Boolean,
        color: Int,
        drawableId: Int,
        contentDescription: String
    ) {
        val alpha = Styler.boost_alpha
        val d = createColoredDrawable(
            activity,
            drawableId,
            color,
            alpha
        )
        b.setImageDrawable(d)
        b.contentDescription = contentDescription
        b.isEnabled = enabled
    }

    override fun onClick(v: View) {

        close_window?.dismiss()
        close_window = null

        val status = this.status ?: return

        when (v) {

            btnConversation -> {

                val cs = status.conversationSummary
                if( activity.conversationUnreadClear(access_info, cs) ){
                    // 表示の更新
                    itemViewHolder.list_adapter.notifyChange(
                        reason = "ConversationSummary reset unread",
                        reset = true
                    )
                }

                activity.conversation(
                    activity.nextPosition(column),
                    access_info,
                    status
                )

            }

            btnReply -> if (!access_info.isPseudo) {
                activity.reply(access_info, status)
            } else {
                activity.replyFromAnotherAccount(access_info, status)
            }

            btnQuote -> if (!access_info.isPseudo) {
                activity.reply(access_info, status,quote = true)
            } else {
                activity.quoteFromAnotherAccount(access_info, status)
            }

            btnBoost -> {
                if (access_info.isPseudo) {
                    activity.boostFromAnotherAccount(access_info, status)
                } else {

                    // トグル動作
                    val bSet = !status.reblogged

                    activity.boost(

                        access_info,
                        status,
                        access_info.getFullAcct(status.account),
                        CrossAccountMode.SameAccount,
                        bSet = bSet,
                        callback = when {
                            !bSimpleList -> emptyCallback
                            // 簡略表示なら結果をトースト表示
                            bSet -> activity.boost_complete_callback
                            else -> activity.unboost_complete_callback
                        },
                    )
                }
            }

            btnFavourite -> {
                if (access_info.isPseudo) {
                    activity.favouriteFromAnotherAccount(access_info, status)
                } else {

                    // トグル動作
                    val bSet = !status.favourited

                    activity.favourite(
                        access_info,
                        status,
                        CrossAccountMode.SameAccount,
                        bSet = bSet,
                        callback = when {
                            !bSimpleList -> emptyCallback
                            // 簡略表示なら結果をトースト表示
                            bSet -> activity.favourite_complete_callback
                            else -> activity.unfavourite_complete_callback
                        },
                    )
                }
            }

            btnBookmark -> {
                if (access_info.isPseudo) {
                    activity.bookmarkFromAnotherAccount(access_info, status)
                } else {

                    // トグル動作
                    val bSet = !status.bookmarked

                    activity.bookmark(
                        access_info,
                        status,
                        CrossAccountMode.SameAccount,
                        bSet = bSet,
                        callback = when {
                            !bSimpleList -> emptyCallback
                            // 簡略表示なら結果をトースト表示
                            bSet -> activity.bookmark_complete_callback
                            else -> activity.unbookmark_complete_callback
                        },
                    )
                }
            }

            btnReaction ->{
                val canMultipleReaction = InstanceCapability.canMultipleReaction(access_info)
                val hasMyReaction = status.reactionSet?.hasMyReaction() == true
                val bRemoveButton = hasMyReaction &&!canMultipleReaction
                when {
                    !TootReaction.canReaction(access_info) ->
                        activity.reactionFromAnotherAccount(
                            access_info,
                            status
                        )
                    bRemoveButton ->
                        activity.reactionRemove(column, status)
                    else ->
                        activity.reactionAdd(column, status)
                }
            }


            btnFollow2 -> {
                val accountRef = status.accountRef
                val account = accountRef.get()
                val relation = this.relation ?: return

                when {
                    access_info.isPseudo -> {
                        // 別アカでフォロー
                        activity.followFromAnotherAccount(

                            activity.nextPosition(column),
                            access_info,
                            account
                        )
                    }

                    relation.blocking || relation.muting -> {
                        // 何もしない
                    }

                    access_info.isMisskey && relation.getRequested(account) && !relation.getFollowing(
                        account
                    ) ->
                        activity.followRequestDelete(

                            activity.nextPosition(column),
                            access_info,
                            accountRef,
                            callback = activity.cancel_follow_request_complete_callback
                        )

                    relation.getFollowing(account) || relation.getRequested(account) -> {
                        // フォロー解除
                        activity.follow(

                            activity.nextPosition(column),
                            access_info,
                            accountRef,
                            bFollow = false,
                            callback = activity.unfollow_complete_callback
                        )
                    }

                    else -> {
                        // フォロー
                        activity.follow(

                            activity.nextPosition(column),
                            access_info,
                            accountRef,
                            bFollow = true,
                            callback = activity.follow_complete_callback
                        )
                    }
                }
            }

            btnTranslate -> CustomShare.invoke(
                activity,
                access_info,
                status,
                CustomShareTarget.Translate
            )

            btnCustomShare1 -> CustomShare.invoke(
                activity,
                access_info,
                status,
                CustomShareTarget.CustomShare1
            )

            btnCustomShare2 -> CustomShare.invoke(
                activity,
                access_info,
                status,
                CustomShareTarget.CustomShare2
            )

            btnCustomShare3 -> CustomShare.invoke(
                activity,
                access_info,
                status,
                CustomShareTarget.CustomShare3
            )

            btnMore -> DlgContextMenu(
                activity,
                column,
                status.accountRef,
                status,
                notification,
                itemViewHolder.tvContent
            ).show()
        }
    }

    override fun onLongClick(v: View): Boolean {

        close_window?.dismiss()
        close_window = null

        val status = this.status ?: return true

        when (v) {
            btnBoost -> activity.boostFromAnotherAccount(access_info, status)
            btnFavourite -> activity.favouriteFromAnotherAccount(access_info, status)
            btnBookmark -> activity.bookmarkFromAnotherAccount(access_info, status)

            btnReply -> activity.replyFromAnotherAccount(access_info, status)
            btnQuote -> activity.quoteFromAnotherAccount(access_info, status)

            btnReaction -> activity.reactionFromAnotherAccount(access_info, status)

            btnConversation -> activity.conversationOtherInstance(activity.nextPosition(column), status)

            btnFollow2 ->
                activity.followFromAnotherAccount(activity.nextPosition(column), access_info, status.account)

            btnTranslate -> shareUrl(status, CustomShareTarget.Translate)
            btnCustomShare1 -> shareUrl(status, CustomShareTarget.CustomShare1)
            btnCustomShare2 -> shareUrl(status, CustomShareTarget.CustomShare2)
            btnCustomShare3 -> shareUrl(status, CustomShareTarget.CustomShare3)
        }
        return true
    }

    private fun shareUrl(
        status: TootStatus,
        target: CustomShareTarget
    ) {
        val url = status.url ?: status.uri

        CustomShare.invoke(activity, url, target)
    }
}

open class _FlexboxLayout(ctx: Context) : FlexboxLayout(ctx) {

    inline fun <T : View> T.lparams(
        width: Int = android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        init: LayoutParams.() -> Unit = {}
    ): T {
        val layoutParams = LayoutParams(width, height)
        layoutParams.init()
        this@lparams.layoutParams = layoutParams
        return this
    }
}

class StatusButtonsViewHolder(
    activity: ActMain,
    lpWidth: Int,
    topMarginDp: Float,
    @JustifyContent justifyContent: Int = JustifyContent.CENTER
) {

    private val buttonHeight = ActMain.boostButtonSize
    internal val marginBetween = (ActMain.boostButtonSize.toFloat() * 0.05f + 0.5f).toInt()

    val paddingH = (buttonHeight.toFloat() * 0.1f + 0.5f).toInt()
    val paddingV = (buttonHeight.toFloat() * 0.1f + 0.5f).toInt()
    val compoundPaddingDp =
        0f //  ActMain.boostButtonSize.toFloat() * -0f / activity.resources.displayMetrics.density

    val viewRoot: FlexboxLayout

    lateinit var btnConversation: ImageButton
    lateinit var btnReply: CountImageButton
    lateinit var btnBoost: CountImageButton
    lateinit var btnFavourite: CountImageButton
    lateinit var btnBookmark: ImageButton
    lateinit var btnQuote: ImageButton
    lateinit var btnReaction: ImageButton
    lateinit var llFollow2: View
    lateinit var btnFollow2: ImageButton
    lateinit var ivFollowedBy2: ImageView
    lateinit var btnTranslate: ImageButton
    lateinit var btnCustomShare1: ImageButton
    lateinit var btnCustomShare2: ImageButton
    lateinit var btnCustomShare3: ImageButton
    lateinit var btnMore: ImageButton

    init {
        viewRoot = with(activity.UI {}) {

            customView<_FlexboxLayout> {
                // トップレベルのViewGroupのlparamsはイニシャライザ内部に置くしかないみたい
                layoutParams = LinearLayout.LayoutParams(lpWidth, wrapContent).apply {
                    topMargin = dip(topMarginDp)
                }
                flexWrap = FlexWrap.WRAP
                this.justifyContent = justifyContent

                fun normalButtons() {

                    btnConversation = imageButton {

                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        contentDescription = context.getString(R.string.conversation_view)

                        setPadding(paddingH, paddingV, paddingH, paddingV)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        imageResource = R.drawable.ic_forum
                    }.lparams(buttonHeight, buttonHeight)

                    btnReply = customView<CountImageButton> {

                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        setPadding(paddingH, paddingV, paddingH, paddingV)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        minimumWidth = buttonHeight
                    }.lparams(wrapContent, buttonHeight) {
                        startMargin = marginBetween
                    }

                    btnBoost = customView<CountImageButton> {

                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        setPadding(paddingH, paddingV, paddingH, paddingV)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        minimumWidth = buttonHeight
                    }.lparams(wrapContent, buttonHeight) {
                        startMargin = marginBetween
                    }

                    btnFavourite = customView<CountImageButton> {
                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        setPadding(paddingH, paddingV, paddingH, paddingV)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        minimumWidth = buttonHeight

                    }.lparams(wrapContent, buttonHeight) {
                        startMargin = marginBetween
                    }

                    btnBookmark = imageButton {
                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        setPadding(paddingH, paddingV, paddingH, paddingV)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        minimumWidth = buttonHeight

                    }.lparams(wrapContent, buttonHeight) {
                        startMargin = marginBetween
                    }

                    btnQuote = imageButton {
                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        setPadding(paddingH, paddingV, paddingH, paddingV)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        minimumWidth = buttonHeight

                    }.lparams(wrapContent, buttonHeight) {
                        startMargin = marginBetween
                    }

                    btnReaction = imageButton {
                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        setPadding(paddingH, paddingV, paddingH, paddingV)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        minimumWidth = buttonHeight
                    }.lparams(wrapContent, buttonHeight) {
                        startMargin = marginBetween
                    }

                    llFollow2 = frameLayout {
                        lparams(buttonHeight, buttonHeight) {
                            startMargin = marginBetween
                        }

                        btnFollow2 = imageButton {

                            background = ContextCompat.getDrawable(
                                context,
                                R.drawable.btn_bg_transparent_round6dp
                            )
                            setPadding(paddingH, paddingV, paddingH, paddingV)
                            scaleType = ImageView.ScaleType.FIT_CENTER

                            contentDescription = context.getString(R.string.follow)

                        }.lparams(matchParent, matchParent)

                        ivFollowedBy2 = imageView {

                            setPadding(paddingH, paddingV, paddingH, paddingV)
                            scaleType = ImageView.ScaleType.FIT_CENTER

                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        }.lparams(matchParent, matchParent)
                    }


                    btnMore = imageButton {
                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        setPadding(paddingH, paddingV, paddingH, paddingV)
                        scaleType = ImageView.ScaleType.FIT_CENTER

                        contentDescription = context.getString(R.string.more)
                        imageResource = R.drawable.ic_more
                    }.lparams(buttonHeight, buttonHeight) {
                        startMargin = marginBetween
                    }
                }

                fun additionalButtons() {
                    btnTranslate = imageButton {
                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        setPadding(paddingH, paddingV, paddingH, paddingV)
                        scaleType = ImageView.ScaleType.FIT_CENTER

                    }.lparams(buttonHeight, buttonHeight) {
                        startMargin = marginBetween
                    }

                    btnCustomShare1 = imageButton {
                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        setPadding(paddingH, paddingV, paddingH, paddingV)
                        scaleType = ImageView.ScaleType.FIT_CENTER

                    }.lparams(buttonHeight, buttonHeight) {
                        startMargin = marginBetween
                    }

                    btnCustomShare2 = imageButton {
                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        setPadding(paddingH, paddingV, paddingH, paddingV)
                        scaleType = ImageView.ScaleType.FIT_CENTER

                    }.lparams(buttonHeight, buttonHeight) {
                        startMargin = marginBetween
                    }

                    btnCustomShare3 = imageButton {
                        background = ContextCompat.getDrawable(
                            context,
                            R.drawable.btn_bg_transparent_round6dp
                        )
                        setPadding(paddingH, paddingV, paddingH, paddingV)
                        scaleType = ImageView.ScaleType.FIT_CENTER

                    }.lparams(buttonHeight, buttonHeight) {
                        startMargin = marginBetween
                    }
                }
                when (Pref.ipAdditionalButtonsPosition(activity.pref)) {
                    Pref.ABP_TOP, Pref.ABP_START -> {
                        additionalButtons()
                        normalButtons()
                    }

                    else -> {
                        normalButtons()
                        additionalButtons()
                    }
                }
            }
        }
    }
}

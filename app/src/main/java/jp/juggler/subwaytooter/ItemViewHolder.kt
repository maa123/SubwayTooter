package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.SystemClock
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.TextUtils
import android.text.style.BackgroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.JustifyContent
import jp.juggler.emoji.UnicodeEmoji
import jp.juggler.subwaytooter.Styler.defaultColorIcon
import jp.juggler.subwaytooter.action.*
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.dialog.DlgConfirm
import jp.juggler.subwaytooter.dialog.EmojiPicker
import jp.juggler.subwaytooter.drawable.PollPlotDrawable
import jp.juggler.subwaytooter.drawable.PreviewCardBorder
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.table.*
import jp.juggler.subwaytooter.util.*
import jp.juggler.subwaytooter.view.*
import jp.juggler.util.*
import org.jetbrains.anko.*
import kotlin.math.max

internal class ItemViewHolder(
	val activity: ActMain
) : View.OnClickListener, View.OnLongClickListener {

    companion object {

        private val log = LogCategory("ItemViewHolder")
        var toot_color_unlisted: Int = 0
        var toot_color_follower: Int = 0
        var toot_color_direct_user: Int = 0
        var toot_color_direct_me: Int = 0

    }

    val viewRoot: View

    private var bSimpleList: Boolean = false

    lateinit var column: Column

    internal lateinit var list_adapter: ItemListAdapter

    private lateinit var llBoosted: View
    private lateinit var ivBoosted: ImageView
    private lateinit var tvBoosted: TextView
    private lateinit var tvBoostedAcct: TextView
    private lateinit var tvBoostedTime: TextView

    private lateinit var llReply: View
    private lateinit var ivReply: ImageView
    private lateinit var tvReply: TextView

    private lateinit var llFollow: View
    private lateinit var ivFollow: MyNetworkImageView
    private lateinit var tvFollowerName: TextView
    private lateinit var tvFollowerAcct: TextView
    private lateinit var btnFollow: ImageButton
    private lateinit var ivFollowedBy: ImageView

    private lateinit var llStatus: View
    private lateinit var ivThumbnail: MyNetworkImageView
    private lateinit var tvName: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvAcct: TextView

    private lateinit var llContentWarning: View
    private lateinit var tvContentWarning: MyTextView
    private lateinit var btnContentWarning: Button

    private lateinit var llContents: View
    private lateinit var tvMentions: MyTextView
    internal lateinit var tvContent: MyTextView

    private lateinit var flMedia: View
    private lateinit var llMedia: View
    private lateinit var btnShowMedia: BlurhashView
    private lateinit var ivMedia1: MyNetworkImageView
    private lateinit var ivMedia2: MyNetworkImageView
    private lateinit var ivMedia3: MyNetworkImageView
    private lateinit var ivMedia4: MyNetworkImageView
    private lateinit var btnHideMedia: ImageButton

    private lateinit var statusButtonsViewHolder: StatusButtonsViewHolder
    private lateinit var llButtonBar: View

    private lateinit var llSearchTag: View
    private lateinit var btnSearchTag: Button
    private lateinit var btnGapHead: ImageButton
    private lateinit var btnGapTail: ImageButton
    private lateinit var llTrendTag: View
    private lateinit var tvTrendTagName: TextView
    private lateinit var tvTrendTagDesc: TextView
    private lateinit var tvTrendTagCount: TextView
    private lateinit var cvTagHistory: TagHistoryView

    private lateinit var llList: View
    private lateinit var btnListTL: Button
    private lateinit var btnListMore: ImageButton

    private lateinit var llFollowRequest: View
    private lateinit var btnFollowRequestAccept: ImageButton
    private lateinit var btnFollowRequestDeny: ImageButton

    private lateinit var llFilter: View
    private lateinit var tvFilterPhrase: TextView
    private lateinit var tvFilterDetail: TextView

    private lateinit var tvMediaDescription: TextView

    private lateinit var llCardOuter: View
    private lateinit var tvCardText: MyTextView
    private lateinit var flCardImage: View
    private lateinit var llCardImage: View
    private lateinit var ivCardImage: MyNetworkImageView
    private lateinit var btnCardImageHide: ImageButton
    private lateinit var btnCardImageShow: BlurhashView

    private lateinit var llExtra: LinearLayout

    private lateinit var llConversationIcons: View
    private lateinit var ivConversationIcon1: MyNetworkImageView
    private lateinit var ivConversationIcon2: MyNetworkImageView
    private lateinit var ivConversationIcon3: MyNetworkImageView
    private lateinit var ivConversationIcon4: MyNetworkImageView
    private lateinit var tvConversationIconsMore: TextView
    private lateinit var tvConversationParticipants: TextView

    private lateinit var tvApplication: TextView

    private lateinit var tvMessageHolder: TextView

    private lateinit var llOpenSticker: View
    private lateinit var ivOpenSticker: MyNetworkImageView
    private lateinit var tvOpenSticker: TextView

    private lateinit var tvLastStatusAt: TextView

    private lateinit var access_info: SavedAccount

    private var buttons_for_status: StatusButtons? = null

    private var item: TimelineItem? = null

    private var status_showing: TootStatus? = null
    private var status_reply: TootStatus? = null
    private var status_account: TootAccountRef? = null
    private var boost_account: TootAccountRef? = null
    private var follow_account: TootAccountRef? = null

    private var boost_time: Long = 0L

    private var content_color: Int = 0
    private var acct_color: Int = 0
    private var content_color_csl: ColorStateList = ColorStateList.valueOf(0)

    private val boost_invalidator: NetworkEmojiInvalidator
    private val reply_invalidator: NetworkEmojiInvalidator
    private val follow_invalidator: NetworkEmojiInvalidator
    private val name_invalidator: NetworkEmojiInvalidator
    private val content_invalidator: NetworkEmojiInvalidator
    private val spoiler_invalidator: NetworkEmojiInvalidator
    private val lastActive_invalidator: NetworkEmojiInvalidator
    private val extra_invalidator_list = ArrayList<NetworkEmojiInvalidator>()

    init {
        this.viewRoot = inflate(activity)

        for (v in arrayOf(
			btnListTL,
			btnListMore,
			btnSearchTag,
			btnGapHead,
			btnGapTail,
			btnContentWarning,
			btnShowMedia,
			ivMedia1,
			ivMedia2,
			ivMedia3,
			ivMedia4,
			btnFollow,
			ivCardImage,
			btnCardImageHide,
			btnCardImageShow,
			ivThumbnail,
			llBoosted,
			llReply,
			llFollow,
			btnFollow,
			btnFollowRequestAccept,
			btnFollowRequestDeny,
			btnHideMedia,
			llTrendTag,
			llFilter
		)) {
            v.setOnClickListener(this)
        }

        for (v in arrayOf(
			btnSearchTag,
			btnFollow,
			ivCardImage,
			llBoosted,
			llReply,
			llFollow,
			llConversationIcons,
			ivThumbnail,
			llTrendTag
		)) {
            v.setOnLongClickListener(this)
        }

        //
        tvContent.movementMethod = MyLinkMovementMethod
        tvMentions.movementMethod = MyLinkMovementMethod
        tvContentWarning.movementMethod = MyLinkMovementMethod
        tvMediaDescription.movementMethod = MyLinkMovementMethod
        tvCardText.movementMethod = MyLinkMovementMethod

        var f: Float

        f = activity.timeline_font_size_sp
        if (!f.isNaN()) {
            tvFollowerName.textSize = f
            tvName.textSize = f
            tvMentions.textSize = f
            tvContentWarning.textSize = f
            tvContent.textSize = f
            btnShowMedia.textSize = f
            btnCardImageShow.textSize = f
            tvApplication.textSize = f
            tvMessageHolder.textSize = f
            btnListTL.textSize = f
            tvTrendTagName.textSize = f
            tvTrendTagCount.textSize = f
            tvFilterPhrase.textSize = f
            tvMediaDescription.textSize = f
            tvCardText.textSize = f
            tvConversationIconsMore.textSize = f
            tvConversationParticipants.textSize = f
        }

        f = activity.notification_tl_font_size_sp
        if (!f.isNaN()) {
            tvBoosted.textSize = f
            tvReply.textSize = f
        }

        f = activity.acct_font_size_sp
        if (!f.isNaN()) {
            tvBoostedAcct.textSize = f
            tvBoostedTime.textSize = f
            tvFollowerAcct.textSize = f
            tvLastStatusAt.textSize = f
            tvAcct.textSize = f
            tvTime.textSize = f
            tvTrendTagDesc.textSize = f
            tvFilterDetail.textSize = f
        }

        val spacing = activity.timeline_spacing
        if (spacing != null) {
            tvFollowerName.setLineSpacing(0f, spacing)
            tvName.setLineSpacing(0f, spacing)
            tvMentions.setLineSpacing(0f, spacing)
            tvContentWarning.setLineSpacing(0f, spacing)
            tvContent.setLineSpacing(0f, spacing)
            btnShowMedia.setLineSpacing(0f, spacing)
            btnCardImageShow.setLineSpacing(0f, spacing)
            tvApplication.setLineSpacing(0f, spacing)
            tvMessageHolder.setLineSpacing(0f, spacing)
            btnListTL.setLineSpacing(0f, spacing)
            tvTrendTagName.setLineSpacing(0f, spacing)
            tvTrendTagCount.setLineSpacing(0f, spacing)
            tvFilterPhrase.setLineSpacing(0f, spacing)
            tvMediaDescription.setLineSpacing(0f, spacing)
            tvCardText.setLineSpacing(0f, spacing)
            tvConversationIconsMore.setLineSpacing(0f, spacing)
            tvConversationParticipants.setLineSpacing(0f, spacing)
            tvBoosted.setLineSpacing(0f, spacing)
            tvReply.setLineSpacing(0f, spacing)
            tvLastStatusAt.setLineSpacing(0f, spacing)
        }

        var s = activity.avatarIconSize
        ivThumbnail.layoutParams.height = s
        ivThumbnail.layoutParams.width = s
        ivFollow.layoutParams.width = s
        ivBoosted.layoutParams.width = s

        s = ActMain.replyIconSize + (activity.density * 8).toInt()
        ivReply.layoutParams.width = s
        ivReply.layoutParams.height = s

        s = activity.notificationTlIconSize
        ivBoosted.layoutParams.height = s

        this.content_invalidator = NetworkEmojiInvalidator(activity.handler, tvContent)
        this.spoiler_invalidator = NetworkEmojiInvalidator(activity.handler, tvContentWarning)
        this.boost_invalidator = NetworkEmojiInvalidator(activity.handler, tvBoosted)
        this.reply_invalidator = NetworkEmojiInvalidator(activity.handler, tvReply)
        this.follow_invalidator = NetworkEmojiInvalidator(activity.handler, tvFollowerName)
        this.name_invalidator = NetworkEmojiInvalidator(activity.handler, tvName)
        this.lastActive_invalidator = NetworkEmojiInvalidator(activity.handler, tvLastStatusAt)

        val cardBackground = llCardOuter.background
        if (cardBackground is PreviewCardBorder) {
            val density = activity.density
            cardBackground.round = (density * 8f)
            cardBackground.width = (density * 1f)
        }

        val textShowMedia = SpannableString(activity.getString(R.string.tap_to_show))
            .apply {
                val colorBg = activity.attrColor(R.attr.colorShowMediaBackground)
                    .applyAlphaMultiplier(0.5f)
                setSpan(
					BackgroundColorSpan(colorBg),
					0,
					this.length,
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
				)
            }

        btnShowMedia.text = textShowMedia
        btnCardImageShow.text = textShowMedia
    }

    fun onViewRecycled() {

    }

    @SuppressLint("ClickableViewAccessibility")
    fun bind(
		list_adapter: ItemListAdapter,
		column: Column,
		bSimpleList: Boolean,
		item: TimelineItem
	) {
        val b = Benchmark(log, "Item-bind", 40L)

        this.list_adapter = list_adapter
        this.column = column
        this.bSimpleList = bSimpleList

        this.access_info = column.access_info

        val font_bold = ActMain.timeline_font_bold
        val font_normal = ActMain.timeline_font
        viewRoot.scan { v ->
            try {
                when (v) {
                    // ボタンは太字なので触らない
					is CountImageButton -> {
					}
                    // ボタンは太字なので触らない
					is Button -> {
					}

					is TextView -> v.typeface = when {
						v === tvName ||
							v === tvFollowerName ||
							v === tvBoosted ||
							v === tvReply ||
							v === tvTrendTagCount ||
							v === tvTrendTagName ||
							v === tvConversationIconsMore ||
							v === tvConversationParticipants ||
							v === tvFilterPhrase -> font_bold
						else -> font_normal
					}
                }
            } catch (ex: Throwable) {
                log.trace(ex)
            }
        }

        if (bSimpleList) {

            viewRoot.setOnTouchListener { _, ev ->
                // ポップアップを閉じた時にクリックでリストを触ったことになってしまう不具合の回避
                val now = SystemClock.elapsedRealtime()
                // ポップアップを閉じた直後はタッチダウンを無視する
                if (now - StatusButtonsPopup.last_popup_close >= 30L) {
                    false
                } else {
                    val action = ev.action
                    log.d("onTouchEvent action=$action")
                    true
                }
            }

            viewRoot.setOnClickListener { viewClicked ->
                activity.closeListItemPopup()
                status_showing?.let { status ->
                    val popup =
                        StatusButtonsPopup(activity, column, bSimpleList, this@ItemViewHolder)
                    activity.listItemPopup = popup
                    popup.show(
						list_adapter.columnVh.listView,
						viewClicked,
						status,
						item as? TootNotification
					)
                }
            }
            llButtonBar.visibility = View.GONE
            this.buttons_for_status = null
        } else {
            viewRoot.isClickable = false
            llButtonBar.visibility = View.VISIBLE
            this.buttons_for_status = StatusButtons(
				activity,
				column,
				false,
				statusButtonsViewHolder,
				this
			)
        }

        this.status_showing = null
        this.status_reply = null
        this.status_account = null
        this.boost_account = null
        this.follow_account = null
        this.boost_time = 0L
        this.viewRoot.setBackgroundColor(0)
        this.boostedAction = defaultBoostedAction

        llOpenSticker.visibility = View.GONE
        llBoosted.visibility = View.GONE
        llReply.visibility = View.GONE
        llFollow.visibility = View.GONE
        llStatus.visibility = View.GONE
        llSearchTag.visibility = View.GONE
        btnGapHead.visibility = View.GONE
        btnGapTail.visibility = View.GONE
        llList.visibility = View.GONE
        llFollowRequest.visibility = View.GONE
        tvMessageHolder.visibility = View.GONE
        llTrendTag.visibility = View.GONE
        llFilter.visibility = View.GONE
        tvMediaDescription.visibility = View.GONE
        llCardOuter.visibility = View.GONE
        tvCardText.visibility = View.GONE
        flCardImage.visibility = View.GONE
        llConversationIcons.visibility = View.GONE

        removeExtraView()

        var c: Int
        c = column.getContentColor()
        this.content_color = c
        this.content_color_csl = ColorStateList.valueOf(c)

        tvBoosted.setTextColor(c)
        tvReply.setTextColor(c)
        tvFollowerName.setTextColor(c)
        tvName.setTextColor(c)
        tvMentions.setTextColor(c)
        tvContentWarning.setTextColor(c)
        tvContent.setTextColor(c)
        //NSFWは文字色固定 btnShowMedia.setTextColor( c );
        tvApplication.setTextColor(c)
        tvMessageHolder.setTextColor(c)
        tvTrendTagName.setTextColor(c)
        tvTrendTagCount.setTextColor(c)
        cvTagHistory.setColor(c)
        tvFilterPhrase.setTextColor(c)
        tvMediaDescription.setTextColor(c)
        tvCardText.setTextColor(c)
        tvConversationIconsMore.setTextColor(c)
        tvConversationParticipants.setTextColor(c)

        (llCardOuter.background as? PreviewCardBorder)?.let {
            val rgb = c and 0xffffff
            val alpha = max(1, c ushr (24 + 1)) // 本来の値の半分にする
            it.color = rgb or (alpha shl 24)
        }

        c = column.getAcctColor()
        this.acct_color = c
        tvBoostedTime.setTextColor(c)
        tvTime.setTextColor(c)
        tvTrendTagDesc.setTextColor(c)
        tvFilterDetail.setTextColor(c)
        tvFilterPhrase.setTextColor(c)

        // 以下のビューの文字色はsetAcct() で設定される
        //		tvBoostedAcct.setTextColor(c)
        //		tvFollowerAcct.setTextColor(c)
        //		tvAcct.setTextColor(c)

        this.item = item
        when (item) {
			is TootStatus -> {
				val reblog = item.reblog
				when {
					reblog == null -> showStatusOrReply(item)

					item.isQuoteToot -> {
						// 引用Renote
						val colorBg = Pref.ipEventBgColorBoost(activity.pref)
						showReply(reblog, R.drawable.ic_repeat, R.string.quote_to)
						showStatus(item, colorBg)
					}

					else -> {
						// 引用なしブースト
						val colorBg = Pref.ipEventBgColorBoost(activity.pref)
						showBoost(
							item.accountRef,
							item.time_created_at,
							R.drawable.ic_repeat,
							R.string.display_name_boosted_by,
							boost_status = item
						)
						showStatusOrReply(item.reblog, colorBg)
					}
				}
			}

			is TootAccountRef -> showAccount(item)

			is TootNotification -> showNotification(item)

			is TootGap -> showGap()
			is TootSearchGap -> showSearchGap(item)
			is TootDomainBlock -> showDomainBlock(item)
			is TootList -> showList(item)
			is MisskeyAntenna -> showAntenna(item)

			is TootMessageHolder -> showMessageHolder(item)

			is TootTag -> showSearchTag(item)

			is TootFilter -> showFilter(item)

			is TootConversationSummary -> {
				showStatusOrReply(item.last_status)
				showConversationIcons(item)
			}

			is TootScheduled -> {
				showScheduled(item)
			}

            else -> {
            }
        }
        b.report()
    }

    private fun showScheduled(item: TootScheduled) {
        try {

            llStatus.visibility = View.VISIBLE

            this.viewRoot.setBackgroundColor(0)

            showStatusTimeScheduled(activity, tvTime, item)

            val who = column.who_account!!.get()
            val whoRef = TootAccountRef(TootParser(activity, access_info), who)
            this.status_account = whoRef

            setAcct(tvAcct, access_info, who)

            tvName.text = whoRef.decoded_display_name
            name_invalidator.register(whoRef.decoded_display_name)
            ivThumbnail.setImageUrl(
				activity.pref,
				Styler.calcIconRound(ivThumbnail.layoutParams),
				access_info.supplyBaseUrl(who.avatar_static),
				access_info.supplyBaseUrl(who.avatar)
			)

            val content = SpannableString(item.text ?: "")

            tvMentions.visibility = View.GONE

            tvContent.text = content
            content_invalidator.register(content)

            tvContent.minLines = -1

            val decoded_spoiler_text = SpannableString(item.spoiler_text ?: "")
            when {
                decoded_spoiler_text.isNotEmpty() -> {
                    // 元データに含まれるContent Warning を使う
                    llContentWarning.visibility = View.VISIBLE
                    tvContentWarning.text = decoded_spoiler_text
                    spoiler_invalidator.register(decoded_spoiler_text)
                    val cw_shown = ContentWarning.isShown(item.uri, access_info.expand_cw)
                    showContent(cw_shown)
                }

                else -> {
                    // CWしない
                    llContentWarning.visibility = View.GONE
                    llContents.visibility = View.VISIBLE
                }
            }

            val media_attachments = item.media_attachments
            if (media_attachments?.isEmpty() != false) {
                flMedia.visibility = View.GONE
                llMedia.visibility = View.GONE
                btnShowMedia.visibility = View.GONE
            } else {
                flMedia.visibility = View.VISIBLE

                // hide sensitive media
                val default_shown = when {
                    column.hide_media_default -> false
                    access_info.dont_hide_nsfw -> true
                    else -> !item.sensitive
                }
                val is_shown = MediaShown.isShown(item.uri, default_shown)

                btnShowMedia.visibility = if (!is_shown) View.VISIBLE else View.GONE
                llMedia.visibility = if (!is_shown) View.GONE else View.VISIBLE
                val sb = StringBuilder()
                setMedia(media_attachments, sb, ivMedia1, 0)
                setMedia(media_attachments, sb, ivMedia2, 1)
                setMedia(media_attachments, sb, ivMedia3, 2)
                setMedia(media_attachments, sb, ivMedia4, 3)
                if (sb.isNotEmpty()) {
                    tvMediaDescription.visibility = View.VISIBLE
                    tvMediaDescription.text = sb
                }

                setIconDrawableId(
					activity,
					btnHideMedia,
					R.drawable.ic_close,
					color = content_color,
					alphaMultiplier = Styler.boost_alpha
				)
            }

            buttons_for_status?.hide()

            tvApplication.visibility = View.GONE

        } catch (ex: Throwable) {

        }
        llSearchTag.visibility = View.VISIBLE
        btnSearchTag.text = activity.getString(R.string.scheduled_status) + " " +
            TootStatus.formatTime(
				activity,
				item.timeScheduledAt,
				true
			)
    }

    private fun removeExtraView() {
        llExtra.scan { v ->
            if (v is MyNetworkImageView) {
                v.cancelLoading()
            }
        }
        llExtra.removeAllViews()

        for (invalidator in extra_invalidator_list) {
            invalidator.register(null)
        }
        extra_invalidator_list.clear()

    }

    private fun showConversationIcons(cs: TootConversationSummary) {

        val last_account_id = cs.last_status.account.id

        val accountsOther = cs.accounts.filter { it.get().id != last_account_id }
        if (accountsOther.isNotEmpty()) {
            llConversationIcons.visibility = View.VISIBLE

            val size = accountsOther.size

            tvConversationParticipants.text = if (size <= 1) {
                activity.getString(R.string.conversation_to)
            } else {
                activity.getString(R.string.participants)
            }

            fun showIcon(iv: MyNetworkImageView, idx: Int) {
                val bShown = idx < size
                iv.visibility = if (bShown) View.VISIBLE else View.GONE
                if (!bShown) return

                val who = accountsOther[idx].get()
                iv.setImageUrl(
					activity.pref,
					Styler.calcIconRound(iv.layoutParams),
					access_info.supplyBaseUrl(who.avatar_static),
					access_info.supplyBaseUrl(who.avatar)
				)
            }
            showIcon(ivConversationIcon1, 0)
            showIcon(ivConversationIcon2, 1)
            showIcon(ivConversationIcon3, 2)
            showIcon(ivConversationIcon4, 3)

            tvConversationIconsMore.text = when {
                size <= 4 -> ""
                else -> activity.getString(R.string.participants_and_more)
            }
        }

        if (cs.last_status.in_reply_to_id != null) {
            llSearchTag.visibility = View.VISIBLE
            btnSearchTag.text = activity.getString(R.string.show_conversation)
        }
    }

    private fun openConversationSummary() {
        val cs = item as? TootConversationSummary ?: return

        if (cs.unread) {
            cs.unread = false
            // 表示の更新
            list_adapter.notifyChange(
				reason = "ConversationSummary reset unread",
				reset = true
			)
            // 未読フラグのクリアをサーバに送る
            Action_Toot.clearConversationUnread(activity, access_info, cs)
        }

        Action_Toot.conversation(
			activity,
			activity.nextPosition(column),
			access_info,
			cs.last_status
		)
    }

    private fun showStatusOrReply(item: TootStatus, colorBgArg: Int = 0) {
        var colorBg = colorBgArg
        val reply = item.reply
        val in_reply_to_id = item.in_reply_to_id
        val in_reply_to_account_id = item.in_reply_to_account_id
        when {
            reply != null -> {
                showReply(reply, R.drawable.ic_reply, R.string.reply_to)
                if (colorBgArg == 0) colorBg = Pref.ipEventBgColorMention(activity.pref)
            }

            in_reply_to_id != null && in_reply_to_account_id != null -> {
                showReply(item, in_reply_to_account_id)
                if (colorBgArg == 0) colorBg = Pref.ipEventBgColorMention(activity.pref)
            }
        }
        showStatus(item, colorBg)
    }

    private fun showMessageHolder(item: TootMessageHolder) {
        tvMessageHolder.visibility = View.VISIBLE
        tvMessageHolder.text = item.text
        tvMessageHolder.gravity = item.gravity
    }

    private fun showNotification(n: TootNotification) {
        val n_status = n.status
        val n_accountRef = n.accountRef
        val n_account = n_accountRef?.get()

        fun showNotificationStatus(item: TootStatus, colorBgDefault: Int) {
            val reblog = item.reblog
            when {
                reblog == null -> showStatusOrReply(item, colorBgDefault)

                item.isQuoteToot -> {
                    // 引用Renote
                    showReply(reblog, R.drawable.ic_repeat, R.string.quote_to)
                    showStatus(item, Pref.ipEventBgColorQuote(activity.pref))
                }

                else -> {
                    // 通常のブースト。引用なしブースト。
                    // ブースト表示は通知イベントと被るのでしない
                    showStatusOrReply(reblog, Pref.ipEventBgColorBoost(activity.pref))
                }

            }
        }

        when (n.type) {

			TootNotification.TYPE_FAVOURITE -> {
				val colorBg = Pref.ipEventBgColorFavourite(activity.pref)
				if (n_account != null) showBoost(
					n_accountRef,
					n.time_created_at,
					if (access_info.isNicoru(n_account)) R.drawable.ic_nicoru else R.drawable.ic_star,
					R.string.display_name_favourited_by
				)
				if (n_status != null) {
					showNotificationStatus(n_status, colorBg)
				}
			}

			TootNotification.TYPE_REBLOG -> {
				val colorBg = Pref.ipEventBgColorBoost(activity.pref)
				if (n_account != null) showBoost(
					n_accountRef,
					n.time_created_at,
					R.drawable.ic_repeat,
					R.string.display_name_boosted_by,
					boost_status = n_status
				)
				if (n_status != null) {
					showNotificationStatus(n_status, colorBg)
				}

			}

			TootNotification.TYPE_RENOTE -> {
				// 引用のないreblog
				val colorBg = Pref.ipEventBgColorBoost(activity.pref)
				if (n_account != null) showBoost(
					n_accountRef,
					n.time_created_at,
					R.drawable.ic_repeat,
					R.string.display_name_boosted_by,
					boost_status = n_status
				)
				if (n_status != null) {
					showNotificationStatus(n_status, colorBg)
				}
			}

			TootNotification.TYPE_FOLLOW -> {
				val colorBg = Pref.ipEventBgColorFollow(activity.pref)
				if (n_account != null) {
					showBoost(
						n_accountRef,
						n.time_created_at,
						R.drawable.ic_follow_plus,
						R.string.display_name_followed_by
					)
					showAccount(n_accountRef)
					if (colorBg != 0) this.viewRoot.backgroundColor = colorBg
				}
			}

			TootNotification.TYPE_UNFOLLOW -> {
				val colorBg = Pref.ipEventBgColorUnfollow(activity.pref)
				if (n_account != null) {
					showBoost(
						n_accountRef,
						n.time_created_at,
						R.drawable.ic_follow_cross,
						R.string.display_name_unfollowed_by
					)
					showAccount(n_accountRef)
					if (colorBg != 0) this.viewRoot.backgroundColor = colorBg
				}
			}

			TootNotification.TYPE_MENTION,
			TootNotification.TYPE_REPLY -> {
				val colorBg = Pref.ipEventBgColorMention(activity.pref)
				if (!bSimpleList && !access_info.isMisskey) {
					when {
						n_account == null -> {

						}

						n_status?.in_reply_to_id != null || n_status?.reply != null -> {
							// トゥート内部に「～への返信」を表示するので、
							// 通知イベントの「～からの返信」は表示しない
						}

						else -> // 返信ではなくメンションの場合は「～からの返信」を表示する
							showBoost(
								n_accountRef,
								n.time_created_at,
								R.drawable.ic_reply,
								R.string.display_name_mentioned_by
							)
					}
				}
				if (n_status != null) {
					showNotificationStatus(n_status, colorBg)
				}
			}

			TootNotification.TYPE_REACTION -> {
				val colorBg = Pref.ipEventBgColorReaction(activity.pref)
				if (n_account != null) showBoost(
					n_accountRef,
					n.time_created_at,
					R.drawable.ic_face,
					R.string.display_name_reaction_by,
					misskeyReaction = n.reaction ?: "?",
                    boost_status = n_status
				)
				if (n_status != null) {
					showNotificationStatus(n_status, colorBg)
				}
			}

			TootNotification.TYPE_QUOTE -> {
				val colorBg = Pref.ipEventBgColorQuote(activity.pref)
				if (n_account != null) showBoost(
					n_accountRef,
					n.time_created_at,
					R.drawable.ic_repeat,
					R.string.display_name_quoted_by
				)
				if (n_status != null) {
					showNotificationStatus(n_status, colorBg)
				}
			}

			TootNotification.TYPE_STATUS -> {
				val colorBg = Pref.ipEventBgColorStatus(activity.pref)
				if (n_account != null) showBoost(
					n_accountRef,
					n.time_created_at,
					if (n_status == null) {
						R.drawable.ic_question
					} else {
						Styler.getVisibilityIconId(access_info.isMisskey, n_status.visibility)
					},
					R.string.display_name_posted_by
				)
				if (n_status != null) {
					showNotificationStatus(n_status, colorBg)
				}
			}

			TootNotification.TYPE_FOLLOW_REQUEST,
			TootNotification.TYPE_FOLLOW_REQUEST_MISSKEY -> {
				val colorBg = Pref.ipEventBgColorFollowRequest(activity.pref)
				if (n_account != null) {
					showBoost(
						n_accountRef,
						n.time_created_at,
						R.drawable.ic_follow_wait,
						R.string.display_name_follow_request_by
					)
					if (colorBg != 0) this.viewRoot.backgroundColor = colorBg
					boostedAction = {
						activity.addColumn(
							activity.nextPosition(column), access_info, ColumnType.FOLLOW_REQUESTS
						)
					}
				}
			}

			TootNotification.TYPE_FOLLOW_REQUEST_ACCEPTED_MISSKEY -> {
				val colorBg = Pref.ipEventBgColorFollow(activity.pref)
				if (n_account != null) {
					showBoost(
						n_accountRef,
						n.time_created_at,
						R.drawable.ic_follow_plus,
						R.string.display_name_follow_request_accepted_by
					)
					showAccount(n_accountRef)
					if (colorBg != 0) this.viewRoot.backgroundColor = colorBg
				}
			}

			TootNotification.TYPE_VOTE,
			TootNotification.TYPE_POLL_VOTE_MISSKEY -> {
				val colorBg = Pref.ipEventBgColorVote(activity.pref)
				if (n_account != null) showBoost(
					n_accountRef,
					n.time_created_at,
					R.drawable.ic_vote,
					R.string.display_name_voted_by
				)
				if (n_status != null) {
					showNotificationStatus(n_status, colorBg)
				}
			}

			TootNotification.TYPE_POLL -> {
				val colorBg = 0
				if (n_account != null) showBoost(
					n_accountRef,
					n.time_created_at,
					R.drawable.ic_vote,
					R.string.end_of_polling_from
				)
				if (n_status != null) {
					showNotificationStatus(n_status, colorBg)
				}
			}

            else -> {
                val colorBg = 0
                if (n_account != null) showBoost(
					n_accountRef,
					n.time_created_at,
					R.drawable.ic_question,
					R.string.unknown_notification_from
				)
                if (n_status != null) {
                    showNotificationStatus(n_status, colorBg)
                }
                tvMessageHolder.visibility = View.VISIBLE
                tvMessageHolder.text = "notification type is ${n.type}"
                tvMessageHolder.gravity = Gravity.CENTER
            }
        }
    }

    private fun showList(list: TootList) {
        llList.visibility = View.VISIBLE
        btnListTL.text = list.title
        btnListTL.textColor = content_color
        btnListMore.imageTintList = content_color_csl
    }

    private fun showAntenna(a: MisskeyAntenna) {
        llList.visibility = View.VISIBLE
        btnListTL.text = a.name
        btnListTL.textColor = content_color
        btnListMore.imageTintList = content_color_csl
    }

    private fun showDomainBlock(domain_block: TootDomainBlock) {
        llSearchTag.visibility = View.VISIBLE
        btnSearchTag.text = domain_block.domain.pretty
    }

    private fun showFilter(filter: TootFilter) {
        llFilter.visibility = View.VISIBLE
        tvFilterPhrase.text = filter.phrase

        val sb = StringBuffer()
        //
        sb.append(activity.getString(R.string.filter_context))
            .append(": ")
            .append(filter.getContextNames(activity).joinToString("/"))
        //
        val flags = ArrayList<String>()
        if (filter.irreversible) flags.add(activity.getString(R.string.filter_irreversible))
        if (filter.whole_word) flags.add(activity.getString(R.string.filter_word_match))
        if (flags.isNotEmpty()) {
            sb.append('\n')
                .append(flags.joinToString(", "))
        }
        //
        if (filter.time_expires_at != 0L) {
            sb.append('\n')
                .append(activity.getString(R.string.filter_expires_at))
                .append(": ")
                .append(TootStatus.formatTime(activity, filter.time_expires_at, false))
        }

        tvFilterDetail.text = sb.toString()
    }

    private fun showSearchTag(tag: TootTag) {
        if (tag.history?.isNotEmpty() == true) {
            llTrendTag.visibility = View.VISIBLE
            tvTrendTagName.text = "#${tag.name}"
            tvTrendTagDesc.text =
                activity.getString(R.string.people_talking, tag.accountDaily, tag.accountWeekly)
            tvTrendTagCount.text = "${tag.countDaily}(${tag.countWeekly})"
            cvTagHistory.setHistory(tag.history)
        } else {
            llSearchTag.visibility = View.VISIBLE
            btnSearchTag.text = "#" + tag.name

        }
    }

    private fun showGap() {
        llSearchTag.visibility = View.VISIBLE
        btnSearchTag.text = activity.getString(R.string.read_gap)

        btnGapHead.vg(column.type.gapDirection(column, true))
            ?.imageTintList = content_color_csl

        btnGapTail.vg(column.type.gapDirection(column, false))
            ?.imageTintList = content_color_csl

        val c = Pref.ipEventBgColorGap(App1.pref)
        if (c != 0) this.viewRoot.backgroundColor = c
    }

    private fun showSearchGap(item: TootSearchGap) {
        llSearchTag.visibility = View.VISIBLE
        btnSearchTag.text = activity.getString(
			when (item.type) {
				TootSearchGap.SearchType.Hashtag -> R.string.read_more_hashtag
				TootSearchGap.SearchType.Account -> R.string.read_more_account
				TootSearchGap.SearchType.Status -> R.string.read_more_status
			}
		)
    }

    private fun showReply(iconId: Int, text: Spannable) {

        llReply.visibility = View.VISIBLE


        setIconDrawableId(
			activity,
			ivReply,
			iconId,
			color = content_color,
			alphaMultiplier = Styler.boost_alpha
		)

        tvReply.text = text
        reply_invalidator.register(text)
    }

    private fun showReply(reply: TootStatus, iconId: Int, stringId: Int) {
        status_reply = reply
        showReply(
			iconId,
			reply.accountRef.decoded_display_name.intoStringResource(activity, stringId)
		)
    }

    private fun showReply(reply: TootStatus, accountId: EntityId) {
        val name = if (accountId == reply.account.id) {
            // 自己レスなら
            AcctColor.getNicknameWithColor(access_info, reply.account)
        } else {
            val m = reply.mentions?.find { it.id == accountId }
            if (m != null) {
                AcctColor.getNicknameWithColor(access_info.getFullAcct(m.acct))
            } else {
                SpannableString("ID(${accountId})")
            }
        }

        val text = name.intoStringResource(activity, R.string.reply_to)
        showReply(R.drawable.ic_reply, text)

        // tootsearchはreplyオブジェクトがなくin_reply_toだけが提供される場合があるが
        // tootsearchではどのタンスから読んだか分からないのでin_reply_toのIDも信用できない
    }

    private fun showBoost(
		whoRef: TootAccountRef,
		time: Long,
		iconId: Int,
		string_id: Int,
		misskeyReaction: String? = null,
		boost_status: TootStatus? = null
	) {
        boost_account = whoRef

        setIconDrawableId(
			activity,
			ivBoosted,
			iconId,
			color = content_color,
			alphaMultiplier = Styler.boost_alpha
		)

        val who = whoRef.get()

        // フォローの場合 decoded_display_name が2箇所で表示に使われるのを避ける必要がある
        val text: Spannable = if (misskeyReaction != null) {
            val options = DecodeOptions(
				activity,
				access_info,
				decodeEmoji = true,
				enlargeEmoji = 1.5f,
				enlargeCustomEmoji = 1.5f
			)
            val ssb = MisskeyReaction.toSpannableStringBuilder(misskeyReaction, options, boost_status)
            ssb.append(" ")
            ssb.append(who.decodeDisplayName(activity)
				.intoStringResource(activity, string_id))
        } else {
            who.decodeDisplayName(activity)
                .intoStringResource(activity, string_id)
        }

        boost_time = time
        llBoosted.visibility = View.VISIBLE
        showStatusTime(activity, tvBoostedTime, who, time = time, status = boost_status)
        tvBoosted.text = text
        boost_invalidator.register(text)
        setAcct(tvBoostedAcct, access_info, who)
    }

    private fun showAccount(whoRef: TootAccountRef) {

        follow_account = whoRef
        val who = whoRef.get()
        llFollow.visibility = View.VISIBLE
        ivFollow.setImageUrl(
			activity.pref,
			Styler.calcIconRound(ivFollow.layoutParams),
			access_info.supplyBaseUrl(who.avatar_static),
			access_info.supplyBaseUrl(who.avatar)
		)

        tvFollowerName.text = whoRef.decoded_display_name
        follow_invalidator.register(whoRef.decoded_display_name)

        setAcct(tvFollowerAcct, access_info, who)

        who.setAccountExtra(access_info, tvLastStatusAt, lastActive_invalidator)

        val relation = UserRelation.load(access_info.db_id, who.id)
        Styler.setFollowIcon(
			activity,
			btnFollow,
			ivFollowedBy,
			relation,
			who,
			content_color,
			alphaMultiplier = Styler.boost_alpha
		)

        if (column.type == ColumnType.FOLLOW_REQUESTS) {
            llFollowRequest.visibility = View.VISIBLE
            btnFollowRequestAccept.imageTintList = content_color_csl
            btnFollowRequestDeny.imageTintList = content_color_csl
        }
    }

    private fun showStatus(status: TootStatus, colorBg: Int = 0) {

        val filteredWord = status.filteredWord
        if (filteredWord != null) {
            showMessageHolder(
				TootMessageHolder(
					if (Pref.bpShowFilteredWord(activity.pref)) {
						"${activity.getString(R.string.filtered)} / $filteredWord"
					} else {
						activity.getString(R.string.filtered)
					}
				)
			)
            return
        }

        this.status_showing = status
        llStatus.visibility = View.VISIBLE

        if (status.conversation_main) {

            val conversationMainBgColor =
                Pref.ipConversationMainTootBgColor(activity.pref).notZero()
                    ?: (activity.attrColor(R.attr.colorImageButtonAccent) and 0xffffff) or 0x20000000

            this.viewRoot.setBackgroundColor(conversationMainBgColor)

        } else {
            val c = colorBg.notZero()

                ?: when (status.bookmarked) {
					true -> Pref.ipEventBgColorBookmark(App1.pref)
					false -> 0
                }.notZero()

                ?: when (status.getBackgroundColorType(access_info)) {
					TootVisibility.UnlistedHome -> toot_color_unlisted
					TootVisibility.PrivateFollowers -> toot_color_follower
					TootVisibility.DirectSpecified -> toot_color_direct_user
					TootVisibility.DirectPrivate -> toot_color_direct_me
                    // TODO add color setting for limited?
                    TootVisibility.Limited -> toot_color_follower
                    else -> 0
                }

            if (c != 0) {
                this.viewRoot.backgroundColor = c
            }
        }

        showStatusTime(activity, tvTime, who = status.account, status = status)

        val whoRef = status.accountRef
        val who = whoRef.get()
        this.status_account = whoRef

        setAcct(tvAcct, access_info, who)

        //		if(who == null) {
        //			tvName.text = "?"
        //			name_invalidator.register(null)
        //			ivThumbnail.setImageUrl(activity.pref, 16f, null, null)
        //		} else {
        tvName.text = whoRef.decoded_display_name
        name_invalidator.register(whoRef.decoded_display_name)
        ivThumbnail.setImageUrl(
			activity.pref,
			Styler.calcIconRound(ivThumbnail.layoutParams),
			access_info.supplyBaseUrl(who.avatar_static),
			access_info.supplyBaseUrl(who.avatar)
		)
        //		}

        showOpenSticker(who)

        var content = status.decoded_content

        // ニコフレのアンケートの表示
        val enquete = status.enquete
        when {
            enquete == null -> {
            }

            enquete.pollType == TootPollsType.FriendsNico && enquete.type != TootPolls.TYPE_ENQUETE -> {
                // フレニコの投票の結果表示は普通にテキストを表示するだけでよい
            }

            else -> {

                // アンケートの本文を上書きする
                val question = enquete.decoded_question
                if (question.isNotBlank()) content = question

                showEnqueteItems(status, enquete)

            }
        }

        showPreviewCard(status)

        //			if( status.decoded_tags == null ){
        //				tvTags.setVisibility( View.GONE );
        //			}else{
        //				tvTags.setVisibility( View.VISIBLE );
        //				tvTags.setText( status.decoded_tags );
        //			}

        if (status.decoded_mentions.isEmpty()) {
            tvMentions.visibility = View.GONE
        } else {
            tvMentions.visibility = View.VISIBLE
            tvMentions.text = status.decoded_mentions
        }

        if (status.time_deleted_at > 0L) {
            val s = SpannableStringBuilder()
                .append('(')
                .append(
					activity.getString(
						R.string.deleted_at,
						TootStatus.formatTime(activity, status.time_deleted_at, true)
					)
				)
                .append(')')
            content = s
        }

        tvContent.text = content
        content_invalidator.register(content)

        activity.checkAutoCW(status, content)
        val r = status.auto_cw

        tvContent.minLines = r?.originalLineCount ?: -1

        val decoded_spoiler_text = status.decoded_spoiler_text
        when {
            decoded_spoiler_text.isNotEmpty() -> {
                // 元データに含まれるContent Warning を使う
                llContentWarning.visibility = View.VISIBLE
                tvContentWarning.text = status.decoded_spoiler_text
                spoiler_invalidator.register(status.decoded_spoiler_text)
                val cw_shown = ContentWarning.isShown(status, access_info.expand_cw)
                showContent(cw_shown)
            }

            r?.decoded_spoiler_text != null -> {
                // 自動CW
                llContentWarning.visibility = View.VISIBLE
                tvContentWarning.text = r.decoded_spoiler_text
                spoiler_invalidator.register(r.decoded_spoiler_text)
                val cw_shown = ContentWarning.isShown(status, access_info.expand_cw)
                showContent(cw_shown)
            }

            else -> {
                // CWしない
                llContentWarning.visibility = View.GONE
                llContents.visibility = View.VISIBLE
            }
        }

        val media_attachments = status.media_attachments
        if (media_attachments == null || media_attachments.isEmpty()) {
            flMedia.visibility = View.GONE
            llMedia.visibility = View.GONE
            btnShowMedia.visibility = View.GONE
        } else {
            flMedia.visibility = View.VISIBLE

            // hide sensitive media
            val default_shown = when {
                column.hide_media_default -> false
                access_info.dont_hide_nsfw -> true
                else -> !status.sensitive
            }
            val is_shown = MediaShown.isShown(status, default_shown)

            btnShowMedia.visibility = if (!is_shown) View.VISIBLE else View.GONE
            llMedia.visibility = if (!is_shown) View.GONE else View.VISIBLE
            val sb = StringBuilder()
            setMedia(media_attachments, sb, ivMedia1, 0)
            setMedia(media_attachments, sb, ivMedia2, 1)
            setMedia(media_attachments, sb, ivMedia3, 2)
            setMedia(media_attachments, sb, ivMedia4, 3)

            val m0 =
                if (media_attachments.isEmpty()) null else media_attachments[0] as? TootAttachment
            btnShowMedia.blurhash = m0?.blurhash

            if (sb.isNotEmpty()) {
                tvMediaDescription.visibility = View.VISIBLE
                tvMediaDescription.text = sb
            }

            setIconDrawableId(
				activity,
				btnHideMedia,
				R.drawable.ic_close,
				color = content_color,
				alphaMultiplier = Styler.boost_alpha
			)
        }

        makeReactionsView(status)

        buttons_for_status?.bind(status, (item as? TootNotification))

        var sb: StringBuilder? = null

        fun prepareSb(): StringBuilder =
            sb?.append(", ") ?: StringBuilder().also { sb = it }

        val application = status.application
        if (application != null &&
            (column.type == ColumnType.CONVERSATION || Pref.bpShowAppName(activity.pref))
        ) {
            prepareSb().append(activity.getString(R.string.application_is, application.name ?: ""))
        }

        val language = status.language
        if (language != null &&
            (column.type == ColumnType.CONVERSATION || Pref.bpShowLanguage(activity.pref))
        ) {
            prepareSb().append(activity.getString(R.string.language_is, language))
        }

        tvApplication.vg(sb != null)?.text = sb
    }

    private fun showOpenSticker(who: TootAccount) {
        try {
            if (!Column.showOpenSticker) return

            val host = who.apDomain

            // LTLでホスト名が同じならTickerを表示しない
            when (column.type) {
				ColumnType.LOCAL, ColumnType.LOCAL_AROUND -> {
					if (host == access_info.apDomain) return
				}

                else -> {

                }
            }

            val item = OpenSticker.lastList[host.ascii] ?: return

            tvOpenSticker.text = item.name
            tvOpenSticker.textColor = item.fontColor

            val density = activity.density

            val lp = ivOpenSticker.layoutParams
            lp.height = (density * 16f + 0.5f).toInt()
            lp.width = (density * item.imageWidth + 0.5f).toInt()

            ivOpenSticker.layoutParams = lp
            ivOpenSticker.setImageUrl(activity.pref, 0f, item.favicon)
            val colorBg = item.bgColor
            when (colorBg.size) {
				1 -> {
					val c = colorBg.first()
					tvOpenSticker.setBackgroundColor(c)
					ivOpenSticker.setBackgroundColor(c)
				}

                else -> {
                    ivOpenSticker.setBackgroundColor(colorBg.last())
                    tvOpenSticker.background = colorBg.getGradation()
                }
            }
            llOpenSticker.visibility = View.VISIBLE
            llOpenSticker.requestLayout()

        } catch (ex: Throwable) {
            log.trace(ex)
        }
    }

    private fun showStatusTime(
		activity: ActMain,
		tv: TextView,
		@Suppress("UNUSED_PARAMETER") who: TootAccount,
		status: TootStatus? = null,
		time: Long? = null
	) {
        val sb = SpannableStringBuilder()

        if (status != null) {

            if (status.account.isAdmin) {
                if (sb.isNotEmpty()) sb.append('\u200B')
                sb.appendColorShadeIcon(activity, R.drawable.ic_shield, "admin")
            }

            if (status.account.isPro) {
                if (sb.isNotEmpty()) sb.append('\u200B')
                sb.appendColorShadeIcon(activity, R.drawable.ic_authorized, "pro")
            }

            if (status.account.isCat) {
                if (sb.isNotEmpty()) sb.append('\u200B')
                sb.appendColorShadeIcon(activity, R.drawable.ic_cat, "cat")
            }

            // botマーク
            if (status.account.bot) {
                if (sb.isNotEmpty()) sb.append('\u200B')
                sb.appendColorShadeIcon(activity, R.drawable.ic_bot, "bot")
            }

            if (status.account.suspended) {
                if (sb.isNotEmpty()) sb.append('\u200B')
                sb.appendColorShadeIcon(activity, R.drawable.ic_delete, "suspended")
            }

            // mobileマーク
            if (status.viaMobile) {
                if (sb.isNotEmpty()) sb.append('\u200B')
                sb.appendColorShadeIcon(activity, R.drawable.ic_mobile, "mobile")
            }

            // mobileマーク
            if (status.bookmarked) {
                if (sb.isNotEmpty()) sb.append('\u200B')
                sb.appendColorShadeIcon(activity, R.drawable.ic_bookmark, "bookmarked")
            }

            // NSFWマーク
            if (status.hasMedia() && status.sensitive) {
                if (sb.isNotEmpty()) sb.append('\u200B')
                sb.appendColorShadeIcon(activity, R.drawable.ic_eye_off, "NSFW")
            }

            // visibility
            val visIconId =
                Styler.getVisibilityIconId(access_info.isMisskey, status.visibility)
            if (R.drawable.ic_public != visIconId) {
                if (sb.isNotEmpty()) sb.append('\u200B')
                sb.appendColorShadeIcon(
					activity,
					visIconId,
					Styler.getVisibilityString(
						activity,
						access_info.isMisskey,
						status.visibility
					)
				)
            }

            // pinned
            if (status.pinned) {
                if (sb.isNotEmpty()) sb.append('\u200B')
                sb.appendColorShadeIcon(activity, R.drawable.ic_pin, "pinned")

                //				val start = sb.length
                //				sb.append("pinned")
                //				val end = sb.length
                //				val icon_id = Styler.getAttributeResourceId(activity, R.attr.ic_pin)
                //				sb.setSpan(
                //					EmojiImageSpan(activity, icon_id),
                //					start,
                //					end,
                //					Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                //				)
            }

            // unread
            if (status.conversationSummary?.unread == true) {
                if (sb.isNotEmpty()) sb.append('\u200B')

                sb.appendColorShadeIcon(
					activity,
					R.drawable.ic_unread,
					"unread",
					color = MyClickableSpan.defaultLinkColor
				)
            }

            if (status.isPromoted) {
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(activity.getString(R.string.promoted))
            }

            if (status.isFeatured) {
                if (sb.isNotEmpty()) sb.append(' ')
                sb.append(activity.getString(R.string.featured))
            }
        }

        if (sb.isNotEmpty()) sb.append(' ')
        sb.append(
			when {
				time != null -> TootStatus.formatTime(
					activity,
					time,
					column.type != ColumnType.CONVERSATION
				)
				status != null -> TootStatus.formatTime(
					activity,
					status.time_created_at,
					column.type != ColumnType.CONVERSATION
				)
				else -> "?"
			}
		)

        tv.text = sb
    }

    private fun showStatusTimeScheduled(
		activity: ActMain,
		tv: TextView,
		item: TootScheduled
	) {
        val sb = SpannableStringBuilder()

        // NSFWマーク
        if (item.hasMedia() && item.sensitive) {
            if (sb.isNotEmpty()) sb.append('\u200B')
            sb.appendColorShadeIcon(activity, R.drawable.ic_eye_off, "NSFW")
        }

        // visibility
        val visIconId =
            Styler.getVisibilityIconId(access_info.isMisskey, item.visibility)
        if (R.drawable.ic_public != visIconId) {
            if (sb.isNotEmpty()) sb.append('\u200B')
            sb.appendColorShadeIcon(
				activity,
				visIconId,
				Styler.getVisibilityString(
					activity,
					access_info.isMisskey,
					item.visibility
				)
			)
        }


        if (sb.isNotEmpty()) sb.append(' ')
        sb.append(
			TootStatus.formatTime(
				activity,
				item.timeScheduledAt,
				column.type != ColumnType.CONVERSATION
			)
		)

        tv.text = sb
    }
    //	fun updateRelativeTime() {
    //		val boost_time = this.boost_time
    //		if(boost_time != 0L) {
    //			tvBoostedTime.text = TootStatus.formatTime(tvBoostedTime.context, boost_time, true)
    //		}
    //		val status_showing = this.status_showing
    //		if(status_showing != null) {
    //			showStatusTime(activity, status_showing)
    //		}
    //	}

    private fun setAcct(tv: TextView, accessInfo: SavedAccount, who: TootAccount) {
        val ac = AcctColor.load(accessInfo, who)
        tv.text = when {
            AcctColor.hasNickname(ac) -> ac.nickname
            Pref.bpShortAcctLocalUser(App1.pref) -> "@${who.acct.pretty}"
            else -> "@${ac.nickname}"
        }
        tv.textColor = ac.color_fg.notZero() ?: this.acct_color

        tv.setBackgroundColor(ac.color_bg) // may 0
        tv.setPaddingRelative(activity.acct_pad_lr, 0, activity.acct_pad_lr, 0)

    }

    private fun showContent(shown: Boolean) {
        llContents.visibility = if (shown) View.VISIBLE else View.GONE
        btnContentWarning.setText(if (shown) R.string.hide else R.string.show)
        status_showing?.let { status ->
            val r = status.auto_cw
            tvContent.minLines = r?.originalLineCount ?: -1
            if (r?.decoded_spoiler_text != null) {
                // 自動CWの場合はContentWarningのテキストを切り替える
                tvContentWarning.text =
                    if (shown) activity.getString(R.string.auto_cw_prefix) else r.decoded_spoiler_text
            }
        }
    }

    private fun setMedia(
		media_attachments: ArrayList<TootAttachmentLike>,
		sbDesc: StringBuilder,
		iv: MyNetworkImageView,
		idx: Int
	) {
        val ta = if (idx < media_attachments.size) media_attachments[idx] else null
        if (ta == null) {
            iv.visibility = View.GONE
            return
        }

        iv.visibility = View.VISIBLE

        iv.setFocusPoint(ta.focusX, ta.focusY)

        if (Pref.bpDontCropMediaThumb(App1.pref)) {
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
        } else {
            iv.setScaleTypeForMedia()
        }

        val showUrl: Boolean

        when (ta.type) {
			TootAttachmentType.Audio -> {
				iv.setMediaType(0)
				iv.setDefaultImage(defaultColorIcon(activity, R.drawable.wide_music))
				iv.setImageUrl(activity.pref, 0f, ta.urlForThumbnail(activity.pref))
				showUrl = true
			}

			TootAttachmentType.Unknown -> {
				iv.setMediaType(0)
				iv.setDefaultImage(defaultColorIcon(activity, R.drawable.wide_question))
				iv.setImageUrl(activity.pref, 0f, null)
				showUrl = true
			}

            else -> when (val urlThumbnail = ta.urlForThumbnail(activity.pref)) {
				null, "" -> {
					iv.setMediaType(0)
					iv.setDefaultImage(defaultColorIcon(activity, R.drawable.wide_question))
					iv.setImageUrl(activity.pref, 0f, null)
					showUrl = true
				}

                else -> {
                    iv.setMediaType(
						when (ta.type) {
							TootAttachmentType.Video -> R.drawable.media_type_video
							TootAttachmentType.GIFV -> R.drawable.media_type_gifv
							else -> 0
						}
					)
                    iv.setDefaultImage(null)
                    iv.setImageUrl(
						activity.pref,
						0f,
						access_info.supplyBaseUrl(urlThumbnail),
						access_info.supplyBaseUrl(urlThumbnail)
					)
                    showUrl = false
                }
            }

        }

        fun appendDescription(s: String) {
            //			val lp = LinearLayout.LayoutParams(
            //				LinearLayout.LayoutParams.MATCH_PARENT,
            //				LinearLayout.LayoutParams.WRAP_CONTENT
            //			)
            //			lp.topMargin = (0.5f + activity.density * 3f).toInt()
            //
            //			val tv = MyTextView(activity)
            //			tv.layoutParams = lp
            //			//
            //			tv.movementMethod = MyLinkMovementMethod
            //			if(! activity.timeline_font_size_sp.isNaN()) {
            //				tv.textSize = activity.timeline_font_size_sp
            //			}
            //			tv.setTextColor(content_color)

            if (sbDesc.isNotEmpty()) sbDesc.append("\n")
            val desc = activity.getString(R.string.media_description, idx + 1, s)
            sbDesc.append(desc)
        }

        when (val description = ta.description.notEmpty()) {
			null -> if (showUrl) ta.urlForDescription.notEmpty()?.let { appendDescription(it) }
            else -> appendDescription(description)
        }
    }

    private val defaultBoostedAction: () -> Unit = {
        val pos = activity.nextPosition(column)
        val notification = (item as? TootNotification)
        boost_account?.let { whoRef ->
            if (access_info.isPseudo) {
                DlgContextMenu(activity, column, whoRef, null, notification, tvContent).show()
            } else {
                Action_User.profileLocal(activity, pos, access_info, whoRef.get())
            }
        }
    }
    private var boostedAction: () -> Unit = defaultBoostedAction

    override fun onClick(v: View) {

        val pos = activity.nextPosition(column)
        val item = this.item
        val notification = (item as? TootNotification)
        when (v) {

			btnHideMedia, btnCardImageHide -> {
				fun hideViews() {
					llMedia.visibility = View.GONE
					btnShowMedia.visibility = View.VISIBLE
					llCardImage.visibility = View.GONE
					btnCardImageShow.visibility = View.VISIBLE
				}
				status_showing?.let { status ->
					MediaShown.save(status, false)
					hideViews()
				}
				if (item is TootScheduled) {
					MediaShown.save(item.uri, false)
					hideViews()
				}
			}

			btnShowMedia, btnCardImageShow -> {
				fun showViews() {
					llMedia.visibility = View.VISIBLE
					btnShowMedia.visibility = View.GONE
					llCardImage.visibility = View.VISIBLE
					btnCardImageShow.visibility = View.GONE
				}
				status_showing?.let { status ->
					MediaShown.save(status, true)
					showViews()
				}
				if (item is TootScheduled) {
					MediaShown.save(item.uri, true)
					showViews()
				}
			}

			ivMedia1 -> clickMedia(0)
			ivMedia2 -> clickMedia(1)
			ivMedia3 -> clickMedia(2)
			ivMedia4 -> clickMedia(3)

			btnContentWarning -> {
				status_showing?.let { status ->
					val new_shown = llContents.visibility == View.GONE
					ContentWarning.save(status, new_shown)

					// 1個だけ開閉するのではなく、例えば通知TLにある複数の要素をまとめて開閉するなどある
					list_adapter.notifyChange(reason = "ContentWarning onClick", reset = true)

				}
				if (item is TootScheduled) {
					val new_shown = llContents.visibility == View.GONE
					ContentWarning.save(item.uri, new_shown)

					// 1個だけ開閉するのではなく、例えば通知TLにある複数の要素をまとめて開閉するなどある
					list_adapter.notifyChange(reason = "ContentWarning onClick", reset = true)
				}
			}

			ivThumbnail -> status_account?.let { whoRef ->
				when {
					access_info.isNA -> DlgContextMenu(
						activity,
						column,
						whoRef,
						null,
						notification,
						tvContent
					).show()

					// 2018/12/26 疑似アカウントでもプロフカラムを表示する https://github.com/tootsuite/mastodon/commit/108b2139cd87321f6c0aec63ef93db85ce30bfec

					else -> Action_User.profileLocal(
						activity,
						pos,
						access_info,
						whoRef.get()
					)
				}
			}

			llBoosted -> boostedAction()

			llReply -> {
				val s = status_reply

				when {
					s != null -> Action_Toot.conversation(activity, pos, access_info, s)

					// tootsearchは返信元のIDを取得するのにひと手間必要
					column.type == ColumnType.SEARCH_TS ||
						column.type == ColumnType.SEARCH_NOTESTOCK ->
						Action_Toot.showReplyTootsearch(activity, pos, status_showing)

					else -> {
						val id = status_showing?.in_reply_to_id
						if (id != null) {
							Action_Toot.conversationLocal(activity, pos, access_info, id)
						}
					}
				}
			}

			llFollow -> follow_account?.let { whoRef ->
				if (access_info.isPseudo) {
					DlgContextMenu(activity, column, whoRef, null, notification, tvContent).show()
				} else {
					Action_User.profileLocal(activity, pos, access_info, whoRef.get())
				}
			}

			btnFollow -> follow_account?.let { who ->
				DlgContextMenu(activity, column, who, null, notification, tvContent).show()
			}

			btnGapHead -> when (item) {
				is TootGap -> column.startGap(item, isHead = true)
			}

			btnGapTail -> when (item) {
				is TootGap -> column.startGap(item, isHead = false)
			}

			btnSearchTag, llTrendTag -> when (item) {

				is TootConversationSummary -> openConversationSummary()

				is TootGap -> when {
					column.type.gapDirection(column, true) ->
						column.startGap(item, isHead = true)

					column.type.gapDirection(column, false) ->
						column.startGap(item, isHead = false)

					else ->
						activity.showToast(true, "This column can't support gap reading.")
				}

				is TootSearchGap -> column.startGap(item, isHead = true)

				is TootDomainBlock -> {
					AlertDialog.Builder(activity)
						.setMessage(
							activity.getString(
								R.string.confirm_unblock_domain,
								item.domain.pretty
							)
						)
						.setNegativeButton(R.string.cancel, null)
						.setPositiveButton(R.string.ok) { _, _ ->
							Action_Instance.blockDomain(
								activity,
								access_info,
								item.domain,
								bBlock = false
							)
						}
						.show()
				}

				is TootTag -> {
					Action_HashTag.timeline(
						activity,
						activity.nextPosition(column),
						access_info,
						item.name // #を含まない
					)
				}

				is TootScheduled -> {
					ActionsDialog()
						.addAction(activity.getString(R.string.edit)) {
							Action_Toot.editScheduledPost(activity, access_info, item)
						}
						.addAction(activity.getString(R.string.delete)) {
							Action_Toot.deleteScheduledPost(activity, access_info, item) {
								column.onScheduleDeleted(item)
								activity.showToast(false, R.string.scheduled_post_deleted)
							}
						}
						.show(activity)
				}
			}

			btnListTL -> if (item is TootList) {
				activity.addColumn(pos, access_info, ColumnType.LIST_TL, item.id)
			} else if (item is MisskeyAntenna) {
				// TODO
				activity.addColumn(pos, access_info, ColumnType.MISSKEY_ANTENNA_TL, item.id)
			}

			btnListMore -> when (item) {
				is TootList -> {
					ActionsDialog()
						.addAction(activity.getString(R.string.list_timeline)) {
							activity.addColumn(pos, access_info, ColumnType.LIST_TL, item.id)
						}
						.addAction(activity.getString(R.string.list_member)) {
							activity.addColumn(
								false,
								pos,
								access_info,
								ColumnType.LIST_MEMBER,
								item.id
							)
						}
						.addAction(activity.getString(R.string.rename)) {
							Action_List.rename(activity, access_info, item)
						}
						.addAction(activity.getString(R.string.delete)) {
							Action_List.delete(activity, access_info, item)
						}
						.show(activity, item.title)
				}

				is MisskeyAntenna -> {
					// TODO
				}
			}

			btnFollowRequestAccept -> follow_account?.let { whoRef ->
				val who = whoRef.get()
				DlgConfirm.openSimple(
					activity,
					activity.getString(
						R.string.follow_accept_confirm,
						AcctColor.getNickname(access_info, who)
					)
				) {
					Action_Follow.authorizeFollowRequest(activity, access_info, whoRef, true)
				}
			}

			btnFollowRequestDeny -> follow_account?.let { whoRef ->
				val who = whoRef.get()
				DlgConfirm.openSimple(
					activity,
					activity.getString(
						R.string.follow_deny_confirm,
						AcctColor.getNickname(access_info, who)
					)
				) {
					Action_Follow.authorizeFollowRequest(activity, access_info, whoRef, false)
				}
			}

			llFilter -> if (item is TootFilter) {
				openFilterMenu(item)
			}

			ivCardImage -> status_showing?.card?.let { card ->
				val originalStatus = card.originalStatus
				if (originalStatus != null) {
					Action_Toot.conversation(
						activity,
						activity.nextPosition(column),
						access_info,
						originalStatus
					)
				} else {
					val url = card.url
					if (url?.isNotEmpty() == true) {
						openCustomTab(
							activity,
							pos,
							url,
							accessInfo = access_info
						)
					}
				}
			}

			llConversationIcons -> openConversationSummary()
        }
    }

    override fun onLongClick(v: View): Boolean {

        val notification = (item as? TootNotification)

        when (v) {

			ivThumbnail -> {
				status_account?.let { who ->
					DlgContextMenu(
						activity,
						column,
						who,
						null,
						notification,
						tvContent
					).show()
				}
				return true
			}

			llBoosted -> {
				boost_account?.let { who ->
					DlgContextMenu(
						activity,
						column,
						who,
						null,
						notification,
						tvContent
					).show()
				}
				return true
			}

			llReply -> {
				val s = status_reply
				when {

					// 返信元のstatusがあるならコンテキストメニュー
					s != null -> DlgContextMenu(
						activity,
						column,
						s.accountRef,
						s,
						notification,
						tvContent
					).show()

					// それ以外はコンテキストメニューではなく会話を開く

					// tootsearchは返信元のIDを取得するのにひと手間必要
					column.type == ColumnType.SEARCH_TS ||
						column.type == ColumnType.SEARCH_NOTESTOCK ->
						Action_Toot.showReplyTootsearch(
							activity,
							activity.nextPosition(column),
							status_showing
						)

					else -> {
						val id = status_showing?.in_reply_to_id
						if (id != null) {
							Action_Toot.conversationLocal(
								activity,
								activity.nextPosition(column),
								access_info,
								id
							)
						}
					}
				}
			}

			llFollow -> {
				follow_account?.let { whoRef ->
					DlgContextMenu(
						activity,
						column,
						whoRef,
						null,
						notification
					).show()
				}
				return true
			}

			btnFollow -> {
				follow_account?.let { whoRef ->
					Action_Follow.followFromAnotherAccount(
						activity,
						activity.nextPosition(column),
						access_info,
						whoRef.get()
					)
				}
				return true
			}

			ivCardImage -> Action_Toot.conversationOtherInstance(
				activity,
				activity.nextPosition(column),
				status_showing?.card?.originalStatus
			)

			btnSearchTag, llTrendTag -> {
				when (val item = this.item) {
					//					is TootGap -> column.startGap(item)
					//
					//					is TootDomainBlock -> {
					//						val domain = item.domain
					//						AlertDialog.Builder(activity)
					//							.setMessage(activity.getString(R.string.confirm_unblock_domain, domain))
					//							.setNegativeButton(R.string.cancel, null)
					//							.setPositiveButton(R.string.ok) { _, _ -> Action_Instance.blockDomain(activity, access_info, domain, false) }
					//							.show()
					//					}

					is TootTag -> {
						// search_tag は#を含まない
						val tagEncoded = item.name.encodePercent()
						val url = "https://${access_info.apiHost.ascii}/tags/$tagEncoded"
						Action_HashTag.timelineOtherInstance(
							activity = activity,
							pos = activity.nextPosition(column),
							url = url,
							host = access_info.apiHost,
							tag_without_sharp = item.name
						)
					}

				}
				return true
			}
        }

        return false
    }

    private fun clickMedia(i: Int) {
        try {
            val media_attachments =
                status_showing?.media_attachments ?: (item as? TootScheduled)?.media_attachments
                ?: return

            when (val item = if (i < media_attachments.size) media_attachments[i] else return) {
				is TootAttachmentMSP -> {
					// マストドン検索ポータルのデータではmedia_attachmentsが簡略化されている
					// 会話の流れを表示する
					Action_Toot.conversationOtherInstance(
						activity,
						activity.nextPosition(column),
						status_showing
					)
				}

				is TootAttachment -> when {

					// unknownが1枚だけなら内蔵ビューアを使わずにインテントを投げる
					item.type == TootAttachmentType.Unknown && media_attachments.size == 1 -> {
						// https://github.com/tateisu/SubwayTooter/pull/119
						// メディアタイプがunknownの場合、そのほとんどはリモートから来たURLである
						// Pref.bpPriorLocalURL の状態に関わらずリモートURLがあればそれをブラウザで開く
						when (val remoteUrl = item.remote_url.notEmpty()) {
							null -> activity.openCustomTab(item)
							else -> activity.openCustomTab(remoteUrl)
						}
					}

					// 内蔵メディアビューアを使う
					Pref.bpUseInternalMediaViewer(App1.pref) ->
						ActMediaViewer.open(
							activity,
							when (access_info.isMisskey) {
								true -> ServiceType.MISSKEY
								else -> ServiceType.MASTODON
							},
							media_attachments,
							i
						)

					// ブラウザで開く
					else -> activity.openCustomTab(item)
				}
            }
        } catch (ex: Throwable) {
            log.trace(ex)
        }
    }

    private fun showPreviewCard(status: TootStatus) {

        if (Pref.bpDontShowPreviewCard(activity.pref)) return

        val card = status.card ?: return

        // 会話カラムで返信ステータスなら捏造したカードを表示しない
        if (column.type == ColumnType.CONVERSATION
            && card.originalStatus != null
            && status.reply != null
        ) {
            return
        }

        var bShowOuter = false

        val sb = StringBuilder()
        fun showString() {
            if (sb.isNotEmpty()) {
                val text = DecodeOptions(
					activity, access_info,
					forceHtml = true,
					mentionDefaultHostDomain = status.account
				).decodeHTML(sb.toString())
                if (text.isNotEmpty()) {
                    tvCardText.visibility = View.VISIBLE
                    tvCardText.text = text
                    bShowOuter = true
                }
            }
        }

        if (status.reblog?.quote_muted == true) {
            addLinkAndCaption(
				sb,
				null,
				card.url,
				activity.getString(R.string.muted_quote)
			)
            showString()
        } else {
            addLinkAndCaption(
				sb,
				activity.getString(R.string.card_header_card),
				card.url,
				card.title
			)

            addLinkAndCaption(
				sb,
				activity.getString(R.string.card_header_author),
				card.author_url,
				card.author_name
			)

            addLinkAndCaption(
				sb,
				activity.getString(R.string.card_header_provider),
				card.provider_url,
				card.provider_name
			)

            val description = card.description
            if (description != null && description.isNotEmpty()) {
                if (sb.isNotEmpty()) sb.append("<br>")

                val limit = Pref.spCardDescriptionLength.toInt(activity.pref)

                sb.append(
					HTMLDecoder.encodeEntity(
						ellipsize(
							description,
							if (limit <= 0) 64 else limit
						)
					)
				)
            }

            showString()

            val image = card.image
            if (flCardImage.vg(image?.isNotEmpty() == true) != null) {

                flCardImage.layoutParams.height = if (card.originalStatus != null) {
                    activity.avatarIconSize
                } else {
                    activity.app_state.media_thumb_height
                }

                val imageUrl = access_info.supplyBaseUrl(image)
                ivCardImage.setImageUrl(activity.pref, 0f, imageUrl, imageUrl)

                btnCardImageShow.blurhash = card.blurhash

                // show about card outer
                bShowOuter = true

                // show about image content
                val default_shown = when {
                    column.hide_media_default -> false
                    access_info.dont_hide_nsfw -> true
                    else -> !status.sensitive
                }
                val is_shown = MediaShown.isShown(status, default_shown)
                llCardImage.vg(is_shown)
                btnCardImageShow.vg(!is_shown)
            }
        }

        if (bShowOuter) llCardOuter.visibility = View.VISIBLE
    }

    private fun addLinkAndCaption(
		sb: StringBuilder,
		header: String?,
		url: String?,
		caption: String?
	) {

        if (url.isNullOrEmpty() && caption.isNullOrEmpty()) return

        if (sb.isNotEmpty()) sb.append("<br>")

        if (header?.isNotEmpty() == true) {
            sb.append(HTMLDecoder.encodeEntity(header)).append(": ")
        }

        if (url != null && url.isNotEmpty()) {
            sb.append("<a href=\"").append(HTMLDecoder.encodeEntity(url)).append("\">")
        }
        sb.append(
			HTMLDecoder.encodeEntity(
				when {
					caption != null && caption.isNotEmpty() -> caption
					url != null && url.isNotEmpty() -> url
					else -> "???"
				}
			)
		)

        if (url != null && url.isNotEmpty()) {
            sb.append("</a>")
        }

    }

    private fun makeReactionsView(status: TootStatus) {
        if (!access_info.isMisskey) return


        val density = activity.density

        val buttonHeight = ActMain.boostButtonSize
        val marginBetween = (buttonHeight.toFloat() * 0.05f + 0.5f).toInt()

        val paddingH = (buttonHeight.toFloat() * 0.1f + 0.5f).toInt()
        val paddingV = (buttonHeight.toFloat() * 0.1f + 0.5f).toInt()

        val act = this@ItemViewHolder.activity // not Button(View).getActivity()

        val box = FlexboxLayout(activity).apply {
            flexWrap = FlexWrap.WRAP
            justifyContent = JustifyContent.FLEX_START
            layoutParams = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
                topMargin = (0.5f + density * 3f).toInt()
            }
        }

        // +/- ボタン
        box.addView(ImageButton(act).also{b->
            b.layoutParams = FlexboxLayout.LayoutParams(
                buttonHeight,
                buttonHeight
            ).apply{
                endMargin = marginBetween
            }

            b.background = ContextCompat.getDrawable(
                activity,
                R.drawable.btn_bg_transparent_round6dp
            )

            val hasMyReaction = status.myReaction?.isNotEmpty() == true
            b.contentDescription =
                activity.getString(if (hasMyReaction) R.string.reaction_remove else R.string.reaction_add)
            b.scaleType = ImageView.ScaleType.FIT_CENTER
            b.padding = paddingV
            b.setOnClickListener {
                if (hasMyReaction) {
                    removeReaction(status, false)
                } else {
                    addReaction(status, null)
                }
            }

            b.setOnLongClickListener {
                Action_Toot.reactionFromAnotherAccount(
                    activity,
                    access_info,
                    status_showing
                )
                true
            }

            setIconDrawableId(
                act,
                b,
                if (hasMyReaction) R.drawable.ic_remove else R.drawable.ic_add,
                color = content_color,
                alphaMultiplier = Styler.boost_alpha
            )
        })

        val reactionCounts = status.reactionCounts
        if (reactionCounts != null) {

            var lastButton: View? = null

            val options = DecodeOptions(
                act,
				access_info,
				decodeEmoji = true,
				enlargeEmoji = 1.5f,
				enlargeCustomEmoji = 1.5f
			)

            for (entry in reactionCounts.entries) {
                val key = entry.key
                val count = entry.value
                if (count <= 0) continue
                val ssb = MisskeyReaction.toSpannableStringBuilder(key, options, status)
                    .also { it.append(" $count") }

                val b = Button(act).apply {
                    layoutParams = FlexboxLayout.LayoutParams(
						FlexboxLayout.LayoutParams.WRAP_CONTENT,
						buttonHeight
					).apply {
                        endMargin = marginBetween
                    }
                    minWidthCompat = buttonHeight

                    background = if (MisskeyReaction.equals(status.myReaction, key)) {
                        // 自分がリアクションしたやつは背景を変える
                        getAdaptiveRippleDrawableRound(
							act,
                            Pref.ipButtonReactionedColor(act.pref).notZero() ?: act.attrColor(R.attr.colorImageButtonAccent),
							act.attrColor(R.attr.colorRippleEffect),
                            roundNormal = true
						)
                    } else {
                        ContextCompat.getDrawable(
							act,
							R.drawable.btn_bg_transparent_round6dp
						)
                    }

                    setTextColor(content_color)
                    setPadding(paddingH, paddingV, paddingH, paddingV)

                    text = ssb

                    allCaps = false
                    tag = key
                    setOnClickListener {
                        val code =  it.tag as? String
                        if( MisskeyReaction.equals(status.myReaction, code)){
                            removeReaction(status, false)
                        }else{
                            addReaction(status,code)
                        }
                    }

                    setOnLongClickListener {
                        Action_Toot.reactionFromAnotherAccount(
							this@ItemViewHolder.activity,
							access_info,
							status_showing,
							it.tag as? String
						)
                        true
                    }
                    // カスタム絵文字の場合、アニメーション等のコールバックを処理する必要がある
                    val invalidator = NetworkEmojiInvalidator(this@ItemViewHolder.activity.handler, this)
                    invalidator.register(ssb)
                    extra_invalidator_list.add(invalidator)
                }
                box.addView(b)
                lastButton = b
            }

            lastButton
                ?.layoutParams
                ?.cast<ViewGroup.MarginLayoutParams>()
                ?.endMargin = 0
        }

        llExtra.addView(box)
    }

    private fun addReaction(status: TootStatus, code: String?) {

        if (status.myReaction?.isNotEmpty() == true) {
            activity.showToast(false, R.string.already_reactioned)
            return
        }

        if (access_info.isPseudo || !access_info.isMisskey) return

        if (code == null) {
            EmojiPicker(activity, access_info, closeOnSelected = true) { result ->
                addReaction(status, when(val emoji = result.emoji){
                    is UnicodeEmoji -> emoji.unifiedCode
                    is CustomEmoji -> ":${emoji.shortcode}:"
                    else->error("unknown emoji type")
                })
            }.show()
            return
        }

        TootTaskRunner(activity, progress_style = TootTaskRunner.PROGRESS_NONE).run(access_info,
			object : TootTask {

				override suspend fun background(client: TootApiClient): TootApiResult? {

					val params = access_info.putMisskeyApiToken().apply {
						put("noteId", status.id.toString())
						put("reaction", code)
					}

					// 成功すると204 no content
					return client.request("/api/notes/reactions/create", params.toPostRequestBuilder())
				}

				override suspend fun handleResult(result: TootApiResult?) {
					result ?: return

					val error = result.error
					if (error != null) {
						activity.showToast(false, error)
						return
					}
					when (val resCode = result.response?.code) {
						in 200 until 300 -> {
							if (status.increaseReaction(code, true, caller="addReaction")) {
								// 1個だけ描画更新するのではなく、TLにある複数の要素をまとめて更新する
								list_adapter.notifyChange(reason = "addReaction complete", reset = true)
							}
						}
						else -> activity.showToast(false, "HTTP error $resCode")
					}
				}

			})
    }

    private fun removeReaction(status: TootStatus, confirmed: Boolean = false) {

        val reaction = status.myReaction

        if (reaction?.isNotEmpty() != true) {
            activity.showToast(false, R.string.not_reactioned)
            return
        }

        if (access_info.isPseudo || !access_info.isMisskey) return

        if (!confirmed) {
            AlertDialog.Builder(activity)
                .setMessage(activity.getString(R.string.reaction_remove_confirm, reaction))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ ->
                    removeReaction(status, confirmed = true)
                }
                .show()
            return
        }

        TootTaskRunner(activity, progress_style = TootTaskRunner.PROGRESS_NONE).run(access_info,
			object : TootTask {
				override suspend fun background(client: TootApiClient): TootApiResult? =
					// 成功すると204 no content
					client.request(
						"/api/notes/reactions/delete",
						access_info.putMisskeyApiToken().apply {
							put("noteId", status.id.toString())
						}
							.toPostRequestBuilder()
					)

				override suspend fun handleResult(result: TootApiResult?) {
					result ?: return

					val error = result.error
					if (error != null) {
						activity.showToast(false, error)
						return
					}

					if ((result.response?.code ?: -1) in 200 until 300) {
						if (status.decreaseReaction(reaction, true, "removeReaction")) {
							// 1個だけ描画更新するのではなく、TLにある複数の要素をまとめて更新する
							list_adapter.notifyChange(
								reason = "removeReaction complete",
								reset = true
							)
						}
					}
				}
			})
    }

    private fun showEnqueteItems(status: TootStatus, enquete: TootPolls) {
        val items = enquete.items ?: return

        val now = System.currentTimeMillis()

        val canVote = when (enquete.pollType) {
			TootPollsType.Mastodon -> when {
				enquete.expired -> false
				now >= enquete.expired_at -> false
				enquete.ownVoted -> false
				else -> true
			}

			TootPollsType.FriendsNico -> {
				val remain = enquete.time_start + TootPolls.ENQUETE_EXPIRE - now
				remain > 0L && !enquete.ownVoted
			}

			TootPollsType.Misskey -> !enquete.ownVoted

			TootPollsType.Notestock -> false
        }

        items.forEachIndexed { index, choice ->
            makeEnqueteChoiceView(status, enquete, canVote, index, choice)
        }

        when (enquete.pollType) {
			TootPollsType.Mastodon, TootPollsType.Notestock ->
				makeEnqueteFooterMastodon(status, enquete, canVote)

			TootPollsType.FriendsNico ->
				makeEnqueteFooterFriendsNico(enquete)

			TootPollsType.Misskey -> {
				// no footer?
			}
        }
    }

    private fun makeEnqueteChoiceView(
		status: TootStatus,
		enquete: TootPolls,
		canVote: Boolean,
		i: Int,
		item: TootPollsChoice
	) {

        val text = when (enquete.pollType) {
			TootPollsType.Misskey -> {
				val sb = SpannableStringBuilder()
					.append(item.decoded_text)

				if (enquete.ownVoted) {
					sb.append(" / ")
					sb.append(activity.getString(R.string.vote_count_text, item.votes))
					if (item.isVoted) sb.append(' ').append(0x2713.toChar())
				}
				sb
			}

			TootPollsType.FriendsNico -> {
				item.decoded_text
			}

			TootPollsType.Mastodon, TootPollsType.Notestock -> if (canVote) {
				item.decoded_text
			} else {
				val sb = SpannableStringBuilder()
					.append(item.decoded_text)
				if (!canVote) {
					val v = item.votes

					sb.append(" / ")
					sb.append(
						when {
							v == null ||
								(column.isSearchColumn && column.access_info.isNA) ->
								activity.getString(R.string.vote_count_unavailable)
							else ->
								activity.getString(R.string.vote_count_text, v)
						}
					)
					if (item.isVoted) sb.append(' ').append(0x2713.toChar())
				}
				sb
			}
        }

        // 投票ボタンの表示
        val lp = LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT
		).apply {
            if (i == 0) topMargin = (0.5f + activity.density * 3f).toInt()
        }

        if (!canVote) {

            val b = TextView(activity)
            b.layoutParams = lp

            b.text = text
            val invalidator = NetworkEmojiInvalidator(activity.handler, b)
            extra_invalidator_list.add(invalidator)
            invalidator.register(text)

            b.padding = (activity.density * 3f + 0.5f).toInt()

            val ratio = when (enquete.pollType) {
				TootPollsType.Mastodon -> {
					val votesCount = enquete.votes_count ?: 0
					val max = enquete.maxVotesCount ?: 0
					if (max > 0 && votesCount > 0) {
						(item.votes ?: 0).toFloat() / votesCount.toFloat()
					} else {
						null
					}
				}

                else -> {
                    val ratios = enquete.ratios
                    if (ratios != null && i <= ratios.size) {
                        ratios[i]
                    } else {
                        null
                    }
                }
            }

            if (ratio != null) {
                b.backgroundDrawable = PollPlotDrawable(
					color = (content_color and 0xFFFFFF) or 0x20000000,
					ratio = ratio,
					isRtl = b.layoutDirection == View.LAYOUT_DIRECTION_RTL,
					startWidth = (activity.density * 2f + 0.5f).toInt()
				)
            }

            llExtra.addView(b)

        } else if (enquete.multiple) {
            // 複数選択なのでチェックボックス
            val b = CheckBox(activity)
            b.layoutParams = lp
            b.isAllCaps = false
            b.text = text
            val invalidator = NetworkEmojiInvalidator(activity.handler, b)
            extra_invalidator_list.add(invalidator)
            invalidator.register(text)
            if (!canVote) {
                b.isEnabled = false
            } else {
                b.isChecked = item.checked
                b.setOnCheckedChangeListener { _, checked ->
                    item.checked = checked
                }
            }
            llExtra.addView(b)

        } else {
            val b = Button(activity)
            b.layoutParams = lp
            b.isAllCaps = false
            b.text = text
            val invalidator = NetworkEmojiInvalidator(activity.handler, b)
            extra_invalidator_list.add(invalidator)
            invalidator.register(text)
            if (!canVote) {
                b.isEnabled = false
            } else {
                val accessInfo = this@ItemViewHolder.access_info
                b.setOnClickListener { view ->
                    val context = view.context ?: return@setOnClickListener
                    onClickEnqueteChoice(status, enquete, context, accessInfo, i)
                }
            }
            llExtra.addView(b)
        }
    }

    private fun makeEnqueteFooterFriendsNico(enquete: TootPolls) {
        val density = activity.density
        val height = (0.5f + 6 * density).toInt()
        val view = EnqueteTimerView(activity)
        view.layoutParams =
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
        view.setParams(enquete.time_start, TootPolls.ENQUETE_EXPIRE)
        llExtra.addView(view)
    }

    private fun makeEnqueteFooterMastodon(
		status: TootStatus,
		enquete: TootPolls,
		canVote: Boolean
	) {

        val density = activity.density

        if (canVote && enquete.multiple) {
            // 複数選択の投票ボタン
            val lp = LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT,
				LinearLayout.LayoutParams.WRAP_CONTENT
			).apply {
                topMargin = (0.5f + density * 3f).toInt()
            }

            val b = Button(activity)
            b.layoutParams = lp
            b.isAllCaps = false
            b.text = activity.getString(R.string.vote_button)
            val accessInfo = this@ItemViewHolder.access_info
            b.setOnClickListener { view ->
                val context = view.context ?: return@setOnClickListener
                sendMultiple(status, enquete, context, accessInfo)
            }
            llExtra.addView(b)
        }

        val tv = TextView(activity)
        val lp = LinearLayout.LayoutParams(
			LinearLayout.LayoutParams.MATCH_PARENT,
			LinearLayout.LayoutParams.WRAP_CONTENT
		)
        lp.topMargin = (0.5f + 3 * density).toInt()
        tv.layoutParams = lp

        val sb = StringBuilder()

        val votes_count = enquete.votes_count ?: 0
        when {
            votes_count == 1 -> sb.append(activity.getString(R.string.vote_1))
            votes_count > 1 -> sb.append(activity.getString(R.string.vote_2, votes_count))
        }

        when (val t = enquete.expired_at) {

			Long.MAX_VALUE -> {
			}

            else -> {
                if (sb.isNotEmpty()) sb.append(" ")
                sb.append(
					activity.getString(
						R.string.vote_expire_at,
						TootStatus.formatTime(activity, t, false)
					)
				)
            }
        }

        tv.text = sb.toString()

        llExtra.addView(tv)
    }

    private fun onClickEnqueteChoice(
		status: TootStatus,
		enquete: TootPolls,
		context: Context,
		accessInfo: SavedAccount,
		idx: Int
	) {
        if (enquete.ownVoted) {
            context.showToast(false, R.string.already_voted)
            return
        }

        val now = System.currentTimeMillis()

        when (enquete.pollType) {
			TootPollsType.Misskey -> {
				// Misskeyのアンケートには期限がない？
			}

			TootPollsType.FriendsNico -> {
				val remain = enquete.time_start + TootPolls.ENQUETE_EXPIRE - now
				if (remain <= 0L) {
					context.showToast(false, R.string.enquete_was_end)
					return
				}
			}

			TootPollsType.Mastodon, TootPollsType.Notestock -> {
				if (enquete.expired || now >= enquete.expired_at) {
					context.showToast(false, R.string.enquete_was_end)
					return
				}
			}
        }

        TootTaskRunner(context).run(accessInfo, object : TootTask {
			override suspend fun background(client: TootApiClient) = when (enquete.pollType) {
				TootPollsType.Misskey -> client.request(
					"/api/notes/polls/vote",
					accessInfo.putMisskeyApiToken().apply {
						put("noteId", enquete.status_id.toString())
						put("choice", idx)

					}.toPostRequestBuilder()
				)
				TootPollsType.Mastodon -> client.request(
					"/api/v1/polls/${enquete.pollId}/votes",
					jsonObject {
						put("choices", jsonArray { add(idx) })
					}.toPostRequestBuilder()
				)
				TootPollsType.FriendsNico -> client.request(
					"/api/v1/votes/${enquete.status_id}",
					jsonObject {
						put("item_index", idx.toString())
					}.toPostRequestBuilder()
				)
				TootPollsType.Notestock -> TootApiResult("can't vote on pseudo account column.")
			}

			override suspend fun handleResult(result: TootApiResult?) {
				result ?: return  // cancelled.

				val data = result.jsonObject
				if (data != null) {
					when (enquete.pollType) {
						TootPollsType.Misskey -> if (enquete.increaseVote(activity, idx, true)) {
							context.showToast(false, R.string.enquete_voted)

							// 1個だけ開閉するのではなく、例えば通知TLにある複数の要素をまとめて開閉するなどある
							list_adapter.notifyChange(reason = "onClickEnqueteChoice", reset = true)
						}

						TootPollsType.Mastodon -> {
							val newPoll = TootPolls.parse(
								TootParser(activity, accessInfo),
								TootPollsType.Mastodon,
								status,
								status.media_attachments,
								data,
							)
							if (newPoll != null) {
								status.enquete = newPoll
								// 1個だけ開閉するのではなく、例えば通知TLにある複数の要素をまとめて開閉するなどある
								list_adapter.notifyChange(
									reason = "onClickEnqueteChoice",
									reset = true
								)
							} else if (result.error != null) {
								context.showToast(true, "response parse error")
							}
						}

						TootPollsType.FriendsNico -> {
							val message = data.string("message") ?: "?"
							val valid = data.optBoolean("valid")
							if (valid) {
								context.showToast(false, R.string.enquete_voted)
							} else {
								context.showToast(true, R.string.enquete_vote_failed, message)
							}
						}
						TootPollsType.Notestock -> error("will not happen")
					}
				} else {
					context.showToast(true, result.error)
				}

			}
		})
    }

    private fun sendMultiple(
		status: TootStatus,
		enquete: TootPolls,
		context: Context,
		accessInfo: SavedAccount
	) {
        val now = System.currentTimeMillis()
        if (now >= enquete.expired_at) {
            context.showToast(false, R.string.enquete_was_end)
            return
        }

        if (enquete.items?.find { it.checked } == null) {
            context.showToast(false, R.string.polls_choice_not_selected)
            return
        }

        TootTaskRunner(context).run(accessInfo, object : TootTask {

			var newPoll: TootPolls? = null

			override suspend fun background(client: TootApiClient): TootApiResult? {
				return client.request(
					"/api/v1/polls/${enquete.pollId}/votes",
					jsonObject {
						put("choices", jsonArray {
							enquete.items.forEachIndexed { index, choice ->
								if (choice.checked) add(index)
							}
						})
					}.toPostRequestBuilder()
				)?.also { result ->
					val data = result.jsonObject
					if (data != null) {
						newPoll = TootPolls.parse(
							TootParser(activity, accessInfo),
							TootPollsType.Mastodon,
							status,
							status.media_attachments,
							data,
						)
						if (newPoll == null) result.setError("response parse error")
					}
				}
			}

			override suspend fun handleResult(result: TootApiResult?) {
				result ?: return  // cancelled.

				val newPoll = this.newPoll
				if (newPoll != null) {
					status.enquete = newPoll
					// 1個だけ開閉するのではなく、例えば通知TLにある複数の要素をまとめて開閉するなどある
					list_adapter.notifyChange(reason = "onClickEnqueteChoice", reset = true)
				} else if (result.error != null) {
					context.showToast(true, result.error)
				}
			}
		})
    }

    private fun openFilterMenu(item: TootFilter) {
        val ad = ActionsDialog()
        ad.addAction(activity.getString(R.string.edit)) {
            ActKeywordFilter.open(activity, access_info, item.id)
        }
        ad.addAction(activity.getString(R.string.delete)) {
            Action_Filter.delete(activity, access_info, item)
        }
        ad.show(activity, activity.getString(R.string.filter_of, item.phrase))
    }

    internal fun getAccount() = status_account ?: boost_account ?: follow_account

    /////////////////////////////////////////////////////////////////////

    private fun inflate(activity: ActMain) = with(activity.UI {}) {
        val b = Benchmark(log, "Item-Inflate", 40L)
        val rv = verticalLayout {
            // トップレベルのViewGroupのlparamsはイニシャライザ内部に置くしかないみたい
            layoutParams =
                androidx.recyclerview.widget.RecyclerView.LayoutParams(matchParent, wrapContent)
                    .apply {
                        marginStart = dip(8)
                        marginEnd = dip(8)
                        topMargin = dip(2f)
                        bottomMargin = dip(1f)
                    }

            setPaddingRelative(dip(4), dip(1f), dip(4), dip(2f))

            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

            llBoosted = linearLayout {
                lparams(matchParent, wrapContent) {
                    bottomMargin = dip(6)
                }
                backgroundResource = R.drawable.btn_bg_transparent_round6dp
                gravity = Gravity.CENTER_VERTICAL

                ivBoosted = imageView {
                    scaleType = ImageView.ScaleType.FIT_END
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }.lparams(dip(48), dip(32)) {
                    endMargin = dip(4)
                }

                verticalLayout {
                    lparams(dip(0), wrapContent) {
                        weight = 1f
                    }

                    linearLayout {
                        lparams(matchParent, wrapContent)

                        tvBoostedAcct = textView {
                            ellipsize = TextUtils.TruncateAt.END
                            gravity = Gravity.END
                            maxLines = 1
                            textSize = 12f // textSize の単位はSP
                            // tools:text ="who@hoge"
                        }.lparams(dip(0), wrapContent) {
                            weight = 1f
                        }

                        tvBoostedTime = textView {

                            startPadding = dip(2)

                            gravity = Gravity.END
                            textSize = 12f // textSize の単位はSP
                            // tools:ignore="RtlSymmetry"
                            // tools:text="2017-04-16 09:37:14"
                        }.lparams(wrapContent, wrapContent)

                    }

                    tvBoosted = textView {
                        // tools:text = "～にブーストされました"
                    }.lparams(matchParent, wrapContent)
                }
            }

            llFollow = linearLayout {
                lparams(matchParent, wrapContent)

                background =
                    ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent_round6dp)
                gravity = Gravity.CENTER_VERTICAL

                ivFollow = myNetworkImageView {
                    contentDescription = context.getString(R.string.thumbnail)
                    scaleType = ImageView.ScaleType.FIT_END
                }.lparams(dip(48), dip(40)) {
                    endMargin = dip(4)
                }

                verticalLayout {

                    lparams(dip(0), wrapContent) {
                        weight = 1f
                    }

                    tvFollowerName = textView {
                        // tools:text="Follower Name"
                    }.lparams(matchParent, wrapContent)

                    tvFollowerAcct = textView {
                        setPaddingStartEnd(dip(4), dip(4))
                        textSize = 12f // SP
                    }.lparams(matchParent, wrapContent)

                    tvLastStatusAt = myTextView {
                        setPaddingStartEnd(dip(4), dip(4))
                        textSize = 12f // SP
                    }.lparams(matchParent, wrapContent)
                }

                frameLayout {
                    lparams(dip(40), dip(40)) {
                        startMargin = dip(4)
                    }

                    btnFollow = imageButton {
                        background =
                            ContextCompat.getDrawable(
								context,
								R.drawable.btn_bg_transparent_round6dp
							)
                        contentDescription = context.getString(R.string.follow)
                        scaleType = ImageView.ScaleType.CENTER
                        // tools:src="?attr/ic_follow_plus"
                    }.lparams(matchParent, matchParent)

                    ivFollowedBy = imageView {
                        scaleType = ImageView.ScaleType.CENTER
                        // tools:src="?attr/ic_followed_by"
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    }.lparams(matchParent, matchParent)

                }
            }


            llStatus = verticalLayout {
                lparams(matchParent, wrapContent)

                linearLayout {
                    lparams(matchParent, wrapContent)

                    tvAcct = textView {
                        ellipsize = TextUtils.TruncateAt.END
                        gravity = Gravity.END
                        maxLines = 1
                        textSize = 12f // SP
                        // tools:text="who@hoge"
                    }.lparams(dip(0), wrapContent) {
                        weight = 1f
                    }

                    tvTime = textView {
                        gravity = Gravity.END
                        startPadding = dip(2)
                        textSize = 12f // SP
                        // tools:ignore="RtlSymmetry"
                        // tools:text="2017-04-16 09:37:14"
                    }.lparams(wrapContent, wrapContent)

                }

                linearLayout {
                    lparams(matchParent, wrapContent)

                    ivThumbnail = myNetworkImageView {
                        background =
                            ContextCompat.getDrawable(
								context,
								R.drawable.btn_bg_transparent_round6dp
							)
                        contentDescription = context.getString(R.string.thumbnail)
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }.lparams(dip(48), dip(48)) {
                        topMargin = dip(4)
                        endMargin = dip(4)
                    }

                    verticalLayout {
                        lparams(dip(0), wrapContent) {
                            weight = 1f
                        }

                        tvName = textView {
                        }.lparams(matchParent, wrapContent)

                        llOpenSticker = linearLayout {
                            lparams(matchParent, wrapContent)

                            ivOpenSticker = myNetworkImageView {
                            }.lparams(dip(16), dip(16)) {
                                isBaselineAligned = false
                            }

                            tvOpenSticker = textView {
                                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10f)
                                gravity = Gravity.CENTER_VERTICAL
                                setPaddingStartEnd(dip(4f), dip(4f))
                            }.lparams(0, dip(16)) {
                                isBaselineAligned = false
                                weight = 1f
                            }
                        }

                        llReply = linearLayout {
                            lparams(matchParent, wrapContent) {
                                bottomMargin = dip(3)
                            }

                            background =
                                ContextCompat.getDrawable(
									context,
									R.drawable.btn_bg_transparent_round6dp
								)
                            gravity = Gravity.CENTER_VERTICAL

                            ivReply = imageView {
                                scaleType = ImageView.ScaleType.FIT_END
                                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                                padding = dip(4)
                            }.lparams(dip(32), dip(32)) {
                                endMargin = dip(4)
                            }

                            tvReply = textView {
                            }.lparams(dip(0), wrapContent) {
                                weight = 1f
                            }
                        }

                        llContentWarning = linearLayout {
                            lparams(matchParent, wrapContent) {
                                topMargin = dip(3)
                                isBaselineAligned = false
                            }
                            gravity = Gravity.CENTER_VERTICAL

                            btnContentWarning = button {

                                backgroundDrawable =
                                    ContextCompat.getDrawable(context, R.drawable.bg_button_cw)
                                minWidthCompat = dip(40)
                                padding = dip(4)
                                //tools:text="見る"
                            }.lparams(wrapContent, dip(40)) {
                                endMargin = dip(8)
                            }

                            verticalLayout {
                                lparams(dip(0), wrapContent) {
                                    weight = 1f
                                }

                                tvMentions = myTextView {
                                }.lparams(matchParent, wrapContent)

                                tvContentWarning = myTextView {
                                }.lparams(matchParent, wrapContent) {
                                    topMargin = dip(3)
                                }

                            }

                        }

                        llContents = verticalLayout {
                            lparams(matchParent, wrapContent)

                            tvContent = myTextView {
                                setLineSpacing(lineSpacingExtra, 1.1f)
                                // tools:text="Contents\nContents"
                            }.lparams(matchParent, wrapContent) {
                                topMargin = dip(3)
                            }

                            val thumbnailHeight = activity.app_state.media_thumb_height
                            val verticalArrangeThumbnails =
                                Pref.bpVerticalArrangeThumbnails(activity.pref)

                            flMedia = if (verticalArrangeThumbnails) {
                                frameLayout {
                                    lparams(matchParent, wrapContent) {
                                        topMargin = dip(3)
                                    }
                                    llMedia = verticalLayout {
                                        lparams(matchParent, matchParent)

                                        btnHideMedia = imageButton {

                                            background = ContextCompat.getDrawable(
												context,
												R.drawable.btn_bg_transparent_round6dp
											)
                                            contentDescription = context.getString(R.string.hide)
                                            imageResource = R.drawable.ic_close
                                        }.lparams(dip(32), dip(32)) {
                                            gravity = Gravity.END
                                        }

                                        ivMedia1 = myNetworkImageView {

                                            background = ContextCompat.getDrawable(
												context,
												R.drawable.bg_thumbnail
											)
                                            contentDescription =
                                                context.getString(R.string.thumbnail)
                                            scaleType = ImageView.ScaleType.CENTER_CROP

                                        }.lparams(matchParent, thumbnailHeight) {
                                            topMargin = dip(3)
                                        }

                                        ivMedia2 = myNetworkImageView {

                                            background = ContextCompat.getDrawable(
												context,
												R.drawable.bg_thumbnail
											)
                                            contentDescription =
                                                context.getString(R.string.thumbnail)
                                            scaleType = ImageView.ScaleType.CENTER_CROP

                                        }.lparams(matchParent, thumbnailHeight) {
                                            topMargin = dip(3)
                                        }

                                        ivMedia3 = myNetworkImageView {

                                            background = ContextCompat.getDrawable(
												context,
												R.drawable.bg_thumbnail
											)
                                            contentDescription =
                                                context.getString(R.string.thumbnail)
                                            scaleType = ImageView.ScaleType.CENTER_CROP

                                        }.lparams(matchParent, thumbnailHeight) {
                                            topMargin = dip(3)
                                        }

                                        ivMedia4 = myNetworkImageView {

                                            background = ContextCompat.getDrawable(
												context,
												R.drawable.bg_thumbnail
											)
                                            contentDescription =
                                                context.getString(R.string.thumbnail)
                                            scaleType = ImageView.ScaleType.CENTER_CROP

                                        }.lparams(matchParent, thumbnailHeight) {
                                            topMargin = dip(3)
                                        }
                                    }

                                    btnShowMedia = blurhashView {

                                        errorColor = context.attrColor(
											R.attr.colorShowMediaBackground
										)
                                        gravity = Gravity.CENTER

                                        textColor = context.attrColor(
											R.attr.colorShowMediaText
										)

                                        minHeightCompat = dip(48)

                                    }.lparams(matchParent, thumbnailHeight)

                                }
                            } else {
                                frameLayout {
                                    lparams(matchParent, thumbnailHeight) {
                                        topMargin = dip(3)
                                    }
                                    llMedia = linearLayout {
                                        lparams(matchParent, matchParent)

                                        ivMedia1 = myNetworkImageView {

                                            background = ContextCompat.getDrawable(
												context,
												R.drawable.bg_thumbnail
											)
                                            contentDescription =
                                                context.getString(R.string.thumbnail)
                                            scaleType = ImageView.ScaleType.CENTER_CROP

                                        }.lparams(0, matchParent) {
                                            weight = 1f
                                        }

                                        ivMedia2 = myNetworkImageView {

                                            background = ContextCompat.getDrawable(
												context,
												R.drawable.bg_thumbnail
											)
                                            contentDescription =
                                                context.getString(R.string.thumbnail)
                                            scaleType = ImageView.ScaleType.CENTER_CROP

                                        }.lparams(0, matchParent) {
                                            startMargin = dip(8)
                                            weight = 1f
                                        }

                                        ivMedia3 = myNetworkImageView {

                                            background = ContextCompat.getDrawable(
												context,
												R.drawable.bg_thumbnail
											)
                                            contentDescription =
                                                context.getString(R.string.thumbnail)
                                            scaleType = ImageView.ScaleType.CENTER_CROP

                                        }.lparams(0, matchParent) {
                                            startMargin = dip(8)
                                            weight = 1f
                                        }

                                        ivMedia4 = myNetworkImageView {

                                            background = ContextCompat.getDrawable(
												context,
												R.drawable.bg_thumbnail
											)
                                            contentDescription =
                                                context.getString(R.string.thumbnail)
                                            scaleType = ImageView.ScaleType.CENTER_CROP

                                        }.lparams(0, matchParent) {
                                            startMargin = dip(8)
                                            weight = 1f
                                        }

                                        btnHideMedia = imageButton {

                                            background = ContextCompat.getDrawable(
												context,
												R.drawable.btn_bg_transparent_round6dp
											)
                                            contentDescription = context.getString(R.string.hide)
                                            imageResource = R.drawable.ic_close
                                        }.lparams(dip(32), matchParent) {
                                            startMargin = dip(8)
                                        }
                                    }

                                    btnShowMedia = blurhashView {

                                        errorColor = context.attrColor(
											R.attr.colorShowMediaBackground
										)
                                        gravity = Gravity.CENTER

                                        textColor = context.attrColor(
											R.attr.colorShowMediaText
										)

                                    }.lparams(matchParent, matchParent)
                                }
                            }

                            tvMediaDescription = textView {}.lparams(matchParent, wrapContent)

                            llCardOuter = verticalLayout {
                                lparams(matchParent, wrapContent) {
                                    topMargin = dip(3)
                                    startMargin = dip(12)
                                    endMargin = dip(6)
                                }
                                padding = dip(3)
                                bottomPadding = dip(6)

                                background = PreviewCardBorder()

                                tvCardText = myTextView {
                                }.lparams(matchParent, wrapContent) {
                                }

                                flCardImage = frameLayout {
                                    lparams(matchParent, activity.app_state.media_thumb_height) {
                                        topMargin = dip(3)
                                    }

                                    llCardImage = linearLayout {
                                        lparams(matchParent, matchParent)

                                        ivCardImage = myNetworkImageView {

                                            contentDescription =
                                                context.getString(R.string.thumbnail)

                                            scaleType = if (Pref.bpDontCropMediaThumb(App1.pref))
                                                ImageView.ScaleType.FIT_CENTER
                                            else
                                                ImageView.ScaleType.CENTER_CROP

                                        }.lparams(0, matchParent) {
                                            weight = 1f
                                        }
                                        btnCardImageHide = imageButton {
                                            background = ContextCompat.getDrawable(
												context,
												R.drawable.btn_bg_transparent_round6dp
											)
                                            contentDescription = context.getString(R.string.hide)
                                            imageResource = R.drawable.ic_close
                                        }.lparams(dip(32), matchParent) {
                                            startMargin = dip(4)
                                        }
                                    }

                                    btnCardImageShow = blurhashView {

                                        errorColor = context.attrColor(
											R.attr.colorShowMediaBackground
										)
                                        gravity = Gravity.CENTER

                                        textColor = context.attrColor(
											R.attr.colorShowMediaText
										)

                                    }.lparams(matchParent, matchParent)
                                }

                            }


                            llExtra = verticalLayout {
                                lparams(matchParent, wrapContent) {
                                    topMargin = dip(0)
                                }
                            }
                        }

                        // button bar
                        statusButtonsViewHolder = StatusButtonsViewHolder(
							activity,
							matchParent,
							3f,
							justifyContent = when (Pref.ipBoostButtonJustify(App1.pref)) {
								0 -> JustifyContent.FLEX_START
								1 -> JustifyContent.CENTER
								else -> JustifyContent.FLEX_END
							}
						)
                        llButtonBar = statusButtonsViewHolder.viewRoot
                        addView(llButtonBar)

                        tvApplication = textView {
                            gravity = Gravity.END
                        }.lparams(matchParent, wrapContent)
                    }

                }

            }

            llConversationIcons = linearLayout {
                lparams(matchParent, dip(40))

                isBaselineAligned = false
                gravity = Gravity.START or Gravity.CENTER_VERTICAL

                tvConversationParticipants = textView {
                    text = context.getString(R.string.participants)
                }.lparams(wrapContent, wrapContent) {
                    endMargin = dip(3)
                }

                ivConversationIcon1 = myNetworkImageView {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }.lparams(dip(24), dip(24)) {
                    endMargin = dip(3)
                }
                ivConversationIcon2 = myNetworkImageView {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }.lparams(dip(24), dip(24)) {
                    endMargin = dip(3)
                }
                ivConversationIcon3 = myNetworkImageView {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }.lparams(dip(24), dip(24)) {
                    endMargin = dip(3)
                }
                ivConversationIcon4 = myNetworkImageView {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }.lparams(dip(24), dip(24)) {
                    endMargin = dip(3)
                }

                tvConversationIconsMore = textView {

                }.lparams(wrapContent, wrapContent)
            }

            llSearchTag = linearLayout {
                lparams(matchParent, wrapContent)

                btnSearchTag = button {
                    background =
                        ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent_round6dp)
                    allCaps = false
                }.lparams(0, wrapContent) {
                    weight = 1f
                }

                btnGapHead = imageButton {

                    background = ContextCompat.getDrawable(
						context,
						R.drawable.btn_bg_transparent_round6dp
					)
                    contentDescription = context.getString(R.string.read_gap_head)
                    imageResource = R.drawable.ic_arrow_drop_down
                }.lparams(dip(32), matchParent) {
                    startMargin = dip(8)
                }

                btnGapTail = imageButton {
                    background = ContextCompat.getDrawable(
						context,
						R.drawable.btn_bg_transparent_round6dp
					)
                    contentDescription = context.getString(R.string.read_gap_tail)
                    imageResource = R.drawable.ic_arrow_drop_up
                }.lparams(dip(32), matchParent) {
                    startMargin = dip(8)
                }
            }

            llTrendTag = linearLayout {
                lparams(matchParent, wrapContent)

                gravity = Gravity.CENTER_VERTICAL
                background =
                    ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent_round6dp)

                verticalLayout {
                    lparams(0, wrapContent) {
                        weight = 1f
                    }
                    tvTrendTagName = textView {
                    }.lparams(matchParent, wrapContent)

                    tvTrendTagDesc = textView {
                        textSize = 12f // SP
                    }.lparams(matchParent, wrapContent)
                }
                tvTrendTagCount = textView {

                }.lparams(wrapContent, wrapContent) {
                    startMargin = dip(6)
                    endMargin = dip(6)
                }

                cvTagHistory = trendTagHistoryView {

                }.lparams(dip(64), dip(32))

            }

            llList = linearLayout {
                lparams(matchParent, wrapContent)

                gravity = Gravity.CENTER_VERTICAL
                isBaselineAligned = false
                minimumHeight = dip(40)

                btnListTL = button {
                    background =
                        ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent_round6dp)
                    allCaps = false
                }.lparams(0, wrapContent) {
                    weight = 1f
                }

                btnListMore = imageButton {

                    background =
                        ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent_round6dp)
                    imageResource = R.drawable.ic_more
                    contentDescription = context.getString(R.string.more)
                }.lparams(dip(40), matchParent) {
                    startMargin = dip(4)
                }
            }

            tvMessageHolder = textView {
                padding = dip(4)
            }.lparams(matchParent, wrapContent)

            llFollowRequest = linearLayout {
                lparams(matchParent, wrapContent) {
                    topMargin = dip(6)
                }
                gravity = Gravity.END

                btnFollowRequestAccept = imageButton {
                    background =
                        ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent_round6dp)
                    contentDescription = context.getString(R.string.follow_accept)
                    imageResource = R.drawable.ic_check
                    setPadding(0, 0, 0, 0)
                }.lparams(dip(48f), dip(32f))

                btnFollowRequestDeny = imageButton {
                    background =
                        ContextCompat.getDrawable(context, R.drawable.btn_bg_transparent_round6dp)
                    contentDescription = context.getString(R.string.follow_deny)
                    imageResource = R.drawable.ic_close
                    setPadding(0, 0, 0, 0)
                }.lparams(dip(48f), dip(32f)) {
                    startMargin = dip(4)
                }
            }

            llFilter = verticalLayout {
                lparams(matchParent, wrapContent) {
                }
                minimumHeight = dip(40)

                tvFilterPhrase = textView {
                    typeface = Typeface.DEFAULT_BOLD
                }.lparams(matchParent, wrapContent)

                tvFilterDetail = textView {
                    textSize = 12f // SP
                }.lparams(matchParent, wrapContent)
            }
        }
        b.report()
        rv
    }
}



package jp.juggler.subwaytooter

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ShareCompat
import androidx.core.content.ContextCompat
import jp.juggler.subwaytooter.action.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.DlgListMember
import jp.juggler.subwaytooter.dialog.DlgQRCode
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.table.FavMute
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.util.*
import jp.juggler.util.*
import org.jetbrains.anko.allCaps
import org.jetbrains.anko.backgroundDrawable
import java.util.*

@SuppressLint("InflateParams")
internal class DlgContextMenu(
	val activity : ActMain,
	private val column : Column,
	private val whoRef : TootAccountRef?,
	private val status : TootStatus?,
	private val notification : TootNotification? = null,
	private val contentTextView : TextView? = null
) : View.OnClickListener, View.OnLongClickListener {
	
	companion object {
		
		private val log = LogCategory("DlgContextMenu")
	}
	
	private val access_info : SavedAccount
	private val relation : UserRelation
	
	private val dialog : Dialog
	
	private val viewRoot = activity.layoutInflater.inflate(R.layout.dlg_context_menu, null, false)
	
	private val btnCrossAccountActionsForStatus : Button =
		viewRoot.findViewById(R.id.btnCrossAccountActionsForStatus)
	private val llCrossAccountActionsForStatus : View =
		viewRoot.findViewById(R.id.llCrossAccountActionsForStatus)
	
	private val btnCrossAccountActionsForAccount : Button =
		viewRoot.findViewById(R.id.btnCrossAccountActionsForAccount)
	private val llCrossAccountActionsForAccount : View =
		viewRoot.findViewById(R.id.llCrossAccountActionsForAccount)
	
	private val btnAroundThisToot : Button =
		viewRoot.findViewById(R.id.btnAroundThisToot)
	private val llAroundThisToot : View =
		viewRoot.findViewById(R.id.llAroundThisToot)
	
	private val btnYourToot : Button =
		viewRoot.findViewById(R.id.btnYourToot)
	private val llYourToot : View =
		viewRoot.findViewById(R.id.llYourToot)
	
	private val btnStatusExtraAction : Button =
		viewRoot.findViewById(R.id.btnStatusExtraAction)
	private val llStatusExtraAction : View =
		viewRoot.findViewById(R.id.llStatusExtraAction)
	
	private val btnAccountExtraAction : Button =
		viewRoot.findViewById(R.id.btnAccountExtraAction)
	private val llAccountExtraAction : View =
		viewRoot.findViewById(R.id.llAccountExtraAction)
	
	private val btnPostNotification : Button = viewRoot.findViewById(R.id.btnPostNotification)
	
	init {
		this.access_info = column.access_info
		
		val columnType = column.type
		
		val who = whoRef?.get()
		val status = this.status
		
		this.relation = when {
			who == null -> UserRelation()
			access_info.isPseudo -> UserRelation.loadPseudo(access_info.getFullAcct(who))
			else -> UserRelation.load(access_info.db_id, who.id)
		}
		
		this.dialog = Dialog(activity)
		dialog.setContentView(viewRoot)
		dialog.setCancelable(true)
		dialog.setCanceledOnTouchOutside(true)
		
		val llStatus : View = viewRoot.findViewById(R.id.llStatus)
		val btnStatusWebPage : View = viewRoot.findViewById(R.id.btnStatusWebPage)
		val btnText : View = viewRoot.findViewById(R.id.btnText)
		val btnFavouriteAnotherAccount : View =
			viewRoot.findViewById(R.id.btnFavouriteAnotherAccount)
		val btnBookmarkAnotherAccount : View =
			viewRoot.findViewById(R.id.btnBookmarkAnotherAccount)
		val btnBoostAnotherAccount : View = viewRoot.findViewById(R.id.btnBoostAnotherAccount)
		val btnReactionAnotherAccount : View = viewRoot.findViewById(R.id.btnReactionAnotherAccount)
		val btnReplyAnotherAccount : View = viewRoot.findViewById(R.id.btnReplyAnotherAccount)
		val btnQuoteToot : View = viewRoot.findViewById(R.id.btnQuoteToot)
		val btnQuoteTootBT : View = viewRoot.findViewById(R.id.btnQuoteTootBT)
		val btnDelete : View = viewRoot.findViewById(R.id.btnDelete)
		val btnRedraft : View = viewRoot.findViewById(R.id.btnRedraft)
		
		val btnReportStatus : View = viewRoot.findViewById(R.id.btnReportStatus)
		val btnReportUser : View = viewRoot.findViewById(R.id.btnReportUser)
		val btnMuteApp : Button = viewRoot.findViewById(R.id.btnMuteApp)
		val llAccountActionBar : View = viewRoot.findViewById(R.id.llAccountActionBar)
		val btnFollow : ImageButton = viewRoot.findViewById(R.id.btnFollow)
		
		val btnMute : ImageView = viewRoot.findViewById(R.id.btnMute)
		val btnBlock : ImageView = viewRoot.findViewById(R.id.btnBlock)
		val btnProfile : View = viewRoot.findViewById(R.id.btnProfile)
		val btnSendMessage : View = viewRoot.findViewById(R.id.btnSendMessage)
		val btnAccountWebPage : View = viewRoot.findViewById(R.id.btnAccountWebPage)
		val btnFollowRequestOK : View = viewRoot.findViewById(R.id.btnFollowRequestOK)
		val btnFollowRequestNG : View = viewRoot.findViewById(R.id.btnFollowRequestNG)
		val btnDeleteSuggestion : View = viewRoot.findViewById(R.id.btnDeleteSuggestion)
		val btnFollowFromAnotherAccount : View =
			viewRoot.findViewById(R.id.btnFollowFromAnotherAccount)
		val btnSendMessageFromAnotherAccount : View =
			viewRoot.findViewById(R.id.btnSendMessageFromAnotherAccount)
		val btnOpenProfileFromAnotherAccount : View =
			viewRoot.findViewById(R.id.btnOpenProfileFromAnotherAccount)
		val btnDomainBlock : Button = viewRoot.findViewById(R.id.btnDomainBlock)
		val btnInstanceInformation : Button = viewRoot.findViewById(R.id.btnInstanceInformation)
		val btnProfileDirectory : Button = viewRoot.findViewById(R.id.btnProfileDirectory)
		val ivFollowedBy : ImageView = viewRoot.findViewById(R.id.ivFollowedBy)
		val btnOpenTimeline : Button = viewRoot.findViewById(R.id.btnOpenTimeline)
		val btnConversationAnotherAccount : View =
			viewRoot.findViewById(R.id.btnConversationAnotherAccount)
		val btnAvatarImage : View = viewRoot.findViewById(R.id.btnAvatarImage)
		
		val llNotification : View = viewRoot.findViewById(R.id.llNotification)
		val btnNotificationDelete : View = viewRoot.findViewById(R.id.btnNotificationDelete)
		val btnConversationMute : Button = viewRoot.findViewById(R.id.btnConversationMute)
		
		val btnHideBoost : View = viewRoot.findViewById(R.id.btnHideBoost)
		val btnShowBoost : View = viewRoot.findViewById(R.id.btnShowBoost)
		val btnHideFavourite : View = viewRoot.findViewById(R.id.btnHideFavourite)
		val btnShowFavourite : View = viewRoot.findViewById(R.id.btnShowFavourite)
		
		val btnListMemberAddRemove : View = viewRoot.findViewById(R.id.btnListMemberAddRemove)
		val btnEndorse : Button = viewRoot.findViewById(R.id.btnEndorse)
		
		val btnAroundAccountTL : View = viewRoot.findViewById(R.id.btnAroundAccountTL)
		val btnAroundLTL : View = viewRoot.findViewById(R.id.btnAroundLTL)
		val btnAroundFTL : View = viewRoot.findViewById(R.id.btnAroundFTL)
		val btnCopyAccountId : Button = viewRoot.findViewById(R.id.btnCopyAccountId)
		val btnOpenAccountInAdminWebUi : Button =
			viewRoot.findViewById(R.id.btnOpenAccountInAdminWebUi)
		val btnOpenInstanceInAdminWebUi : Button =
			viewRoot.findViewById(R.id.btnOpenInstanceInAdminWebUi)
		val btnBoostWithVisibility : Button = viewRoot.findViewById(R.id.btnBoostWithVisibility)
		val llLinks : LinearLayout = viewRoot.findViewById(R.id.llLinks)
		
		val btnNotificationFrom : Button = viewRoot.findViewById(R.id.btnNotificationFrom)
		val btnProfilePin = viewRoot.findViewById<View>(R.id.btnProfilePin)
		val btnProfileUnpin = viewRoot.findViewById<View>(R.id.btnProfileUnpin)
		val btnBoostedBy = viewRoot.findViewById<View>(R.id.btnBoostedBy)
		val btnFavouritedBy = viewRoot.findViewById<View>(R.id.btnFavouritedBy)
		
		val btnDomainTimeline = viewRoot.findViewById<View>(R.id.btnDomainTimeline)
		
		arrayOf(
			btnNotificationFrom,
			btnAroundAccountTL,
			btnAroundLTL,
			btnAroundFTL,
			btnStatusWebPage,
			btnText,
			btnFavouriteAnotherAccount,
			btnBookmarkAnotherAccount,
			btnBoostAnotherAccount,
			btnReactionAnotherAccount,
			btnReplyAnotherAccount,
			btnQuoteToot,
			btnQuoteTootBT,
			btnReportStatus,
			btnReportUser,
			btnMuteApp,
			btnDelete,
			btnRedraft,
			btnFollow,
			btnMute,
			btnBlock,
			btnProfile,
			btnSendMessage,
			btnAccountWebPage,
			btnFollowRequestOK,
			btnFollowRequestNG,
			btnDeleteSuggestion,
			btnFollowFromAnotherAccount,
			btnSendMessageFromAnotherAccount,
			btnOpenProfileFromAnotherAccount,
			btnOpenTimeline,
			btnConversationAnotherAccount,
			btnAvatarImage,
			btnNotificationDelete,
			btnConversationMute,
			btnHideBoost,
			btnShowBoost,
			btnHideFavourite,
			btnShowFavourite,
			btnListMemberAddRemove,
			btnInstanceInformation,
			btnProfileDirectory,
			btnDomainBlock,
			btnEndorse,
			btnCopyAccountId,
			btnOpenAccountInAdminWebUi,
			btnOpenInstanceInAdminWebUi,
			btnBoostWithVisibility,
			btnProfilePin,
			btnProfileUnpin,
			btnBoostedBy,
			btnFavouritedBy,
			btnDomainTimeline,
			btnPostNotification,
			
			viewRoot.findViewById(R.id.btnQuoteUrlStatus),
			viewRoot.findViewById(R.id.btnTranslate),
			viewRoot.findViewById(R.id.btnQuoteUrlAccount),
			viewRoot.findViewById(R.id.btnShareUrlStatus),
			viewRoot.findViewById(R.id.btnShareUrlAccount),
			viewRoot.findViewById(R.id.btnQuoteName)
		
		).forEach {
			it.setOnClickListener(this@DlgContextMenu)
		}
		
		arrayOf(
			btnFollow,
			btnProfile,
			btnMute,
			btnBlock,
			btnSendMessage
		).forEach {
			it.setOnLongClickListener(this)
		}
		
		val account_list = SavedAccount.loadAccountList(activity)
		//	final ArrayList< SavedAccount > account_list_non_pseudo_same_instance = new ArrayList<>();
		
		val account_list_non_pseudo = ArrayList<SavedAccount>()
		for(a in account_list) {
			if(! a.isPseudo) {
				account_list_non_pseudo.add(a)
				//				if( a.host.equalsIgnoreCase( access_info.host ) ){
				//					account_list_non_pseudo_same_instance.add( a );
				//				}
			}
		}
		
		if(status == null) {
			llStatus.visibility = View.GONE
			llLinks.visibility = View.GONE
		} else {
			val status_by_me = access_info.isMe(status.account)
			
			if(Pref.bpLinksInContextMenu(activity.pref) && contentTextView != null) {
				
				var insPos = 0
				
				fun addLinkButton(span : MyClickableSpan, caption : String) {
					val b = Button(activity)
					val lp = LinearLayout.LayoutParams(
						ViewGroup.LayoutParams.MATCH_PARENT,
						ViewGroup.LayoutParams.WRAP_CONTENT
					)
					b.layoutParams = lp
					b.backgroundDrawable =
						ContextCompat.getDrawable(activity, R.drawable.btn_bg_transparent_round6dp)
					b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
					b.minHeight = (activity.density * 32f + 0.5f).toInt()
					b.minimumHeight = (activity.density * 32f + 0.5f).toInt()
					val pad_lr = (activity.density * 8f + 0.5f).toInt()
					val pad_tb = (activity.density * 4f + 0.5f).toInt()
					b.setPaddingRelative(pad_lr, pad_tb, pad_lr, pad_tb)
					b.text = caption
					b.allCaps = false
					b.setOnClickListener {
						dialog.dismissSafe()
						span.onClick(contentTextView)
					}
					llLinks.addView(b, insPos ++)
				}
				
				val dc = status.decoded_content
				for(span in dc.getSpans(0, dc.length, MyClickableSpan::class.java)) {
					val caption = span.linkInfo.text
					when(caption.firstOrNull()) {
						'@', '#' -> addLinkButton(span, caption)
						else -> addLinkButton(span, span.linkInfo.url)
					}
				}
			}
			llLinks.vg(llLinks.childCount > 1)
			
			btnYourToot.vg(status_by_me)
			
			btnQuoteTootBT.vg(status.reblogParent != null)
			
			btnBoostWithVisibility.vg(! access_info.isPseudo && ! access_info.isMisskey)
			
			btnReportStatus.vg(! (status_by_me || access_info.isPseudo))
			
			val application_name = status.application?.name
			if(status_by_me || application_name == null || application_name.isEmpty()) {
				btnMuteApp.visibility = View.GONE
			} else {
				btnMuteApp.text = activity.getString(R.string.mute_app_of, application_name)
			}
			
			val canPin = status.canPin(access_info)
			btnProfileUnpin.vg(canPin && status.pinned)
			btnProfilePin.vg(canPin && ! status.pinned)
		}
		
		val bShowConversationMute = when {
			status == null -> false
			access_info.isMe(status.account) -> true
			notification != null && TootNotification.TYPE_MENTION == notification.type -> true
			else -> false
		}
		
		val muted = status?.muted ?: false
		btnConversationMute.vg(bShowConversationMute)
			?.setText(
				if(muted)
					R.string.unmute_this_conversation
				else R.string.mute_this_conversation
			)
		
		llNotification.vg(notification != null)
		
		val colorButtonAccent =
			Pref.ipButtonFollowingColor(activity.pref).notZero()
				?: activity.attrColor(R.attr.colorImageButtonAccent)
		
		val colorButtonError =
			Pref.ipButtonFollowRequestColor(activity.pref).notZero()
				?: activity.attrColor(R.attr.colorRegexFilterError)
		
		val colorButtonNormal =
			activity.attrColor(R.attr.colorImageButton)
		
		fun showRelation(relation : UserRelation) {
			
			// 被フォロー状態
			// Styler.setFollowIconとは異なり細かい状態を表示しない
			ivFollowedBy.vg(relation.followed_by)
			
			// フォロー状態
			// Styler.setFollowIconとは異なりミュートやブロックを表示しない
			btnFollow.setImageResource(
				when {
					relation.getRequested(who) -> R.drawable.ic_follow_wait
					relation.getFollowing(who) -> R.drawable.ic_follow_cross
					else -> R.drawable.ic_follow_plus
				}
			)
			
			
			btnFollow.imageTintList = ColorStateList.valueOf(
				when {
					relation.getRequested(who) -> colorButtonError
					relation.getFollowing(who) -> colorButtonAccent
					else -> colorButtonNormal
				}
			)
			
			// ミュート状態
			btnMute.imageTintList = ColorStateList.valueOf(
				when(relation.muting) {
					true -> colorButtonAccent
					else -> colorButtonNormal
				}
			)
			
			// ブロック状態
			btnBlock.imageTintList = ColorStateList.valueOf(
				when(relation.blocking) {
					true -> colorButtonAccent
					else -> colorButtonNormal
				}
			)
		}
		
		if(access_info.isPseudo) {
			// 疑似アカミュートができたのでアカウントアクションを表示する
			showRelation( relation)
			llAccountActionBar.visibility = View.VISIBLE
			ivFollowedBy.vg(false)
			btnFollow.setImageResource(R.drawable.ic_follow_plus)
			btnFollow.imageTintList =
				ColorStateList.valueOf(activity.attrColor(R.attr.colorImageButton))
			
			btnNotificationFrom.visibility = View.GONE
		} else {
			showRelation(relation)
		}
		
		val whoApiHost = getUserApiHost()
		val whoApDomain = getUserApDomain()
		
		viewRoot.findViewById<View>(R.id.llInstance)
			.vg(whoApiHost.isValid)
			?.let {
				val tvInstanceActions : TextView = viewRoot.findViewById(R.id.tvInstanceActions)
				tvInstanceActions.text =
					activity.getString(R.string.instance_actions_for, whoApDomain.pretty)
				
				// 疑似アカウントではドメインブロックできない
				// 自ドメインはブロックできない
				btnDomainBlock.vg(
					! (access_info.isPseudo || access_info.matchHost(whoApiHost))
				)
				
				btnDomainTimeline.vg(
					Pref.bpEnableDomainTimeline(activity.pref) &&
						! access_info.isPseudo &&
						! access_info.isMisskey
				)
			}
		
		if(who == null) {
			btnCopyAccountId.visibility = View.GONE
			btnOpenAccountInAdminWebUi.visibility = View.GONE
			btnOpenInstanceInAdminWebUi.visibility = View.GONE
			
			btnReportUser.visibility = View.GONE
			
		} else {
			
			btnCopyAccountId.visibility = View.VISIBLE
			btnCopyAccountId.text = activity.getString(R.string.copy_account_id, who.id.toString())
			
			btnOpenAccountInAdminWebUi.vg(! access_info.isPseudo)
			btnOpenInstanceInAdminWebUi.vg(! access_info.isPseudo)
			
			btnReportUser.vg(! (access_info.isPseudo || access_info.isMe(who)))
			
			btnPostNotification.vg(! access_info.isPseudo && access_info.isMastodon && relation.following)
				?.let {
					it.text = when(relation.notifying) {
						true -> activity.getString(R.string.stop_notify_posts_from_this_user)
						else -> activity.getString(R.string.notify_posts_from_this_user)
					}
				}
			
		}
		
		viewRoot.findViewById<View>(R.id.btnAccountText).setOnClickListener(this)
		
		if(access_info.isPseudo) {
			btnProfile.visibility = View.GONE
			btnSendMessage.visibility = View.GONE
			btnEndorse.visibility = View.GONE
		}
		
		btnEndorse.text = when(relation.endorsed) {
			false -> activity.getString(R.string.endorse_set)
			else -> activity.getString(R.string.endorse_unset)
		}
		
		if(columnType != ColumnType.FOLLOW_REQUESTS) {
			btnFollowRequestOK.visibility = View.GONE
			btnFollowRequestNG.visibility = View.GONE
		}
		
		if(columnType != ColumnType.FOLLOW_SUGGESTION) {
			btnDeleteSuggestion.visibility = View.GONE
		}
		
		if(account_list_non_pseudo.isEmpty()) {
			btnFollowFromAnotherAccount.visibility = View.GONE
			btnSendMessageFromAnotherAccount.visibility = View.GONE
		}
		
		viewRoot.findViewById<View>(R.id.btnNickname).setOnClickListener(this)
		viewRoot.findViewById<View>(R.id.btnCancel).setOnClickListener(this)
		viewRoot.findViewById<View>(R.id.btnAccountQrCode).setOnClickListener(this)
		
		if(access_info.isPseudo
			|| who == null
			|| ! relation.getFollowing(who)
			|| relation.following_reblogs == UserRelation.REBLOG_UNKNOWN) {
			btnHideBoost.visibility = View.GONE
			btnShowBoost.visibility = View.GONE
		} else if(relation.following_reblogs == UserRelation.REBLOG_SHOW) {
			btnHideBoost.visibility = View.VISIBLE
			btnShowBoost.visibility = View.GONE
		} else {
			btnHideBoost.visibility = View.GONE
			btnShowBoost.visibility = View.VISIBLE
		}
		
		when {
			who == null -> {
				btnHideFavourite.visibility = View.GONE
				btnShowFavourite.visibility = View.GONE
			}
			
			FavMute.contains(access_info.getFullAcct(who)) -> {
				btnHideFavourite.visibility = View.GONE
				btnShowFavourite.visibility = View.VISIBLE
			}
			
			else -> {
				btnHideFavourite.visibility = View.VISIBLE
				btnShowFavourite.visibility = View.GONE
			}
		}
		
		btnListMemberAddRemove.visibility = View.VISIBLE
		
		updateGroup(btnCrossAccountActionsForStatus, llCrossAccountActionsForStatus)
		updateGroup(btnCrossAccountActionsForAccount, llCrossAccountActionsForAccount)
		updateGroup(btnAroundThisToot, llAroundThisToot)
		updateGroup(btnYourToot, llYourToot)
		updateGroup(btnStatusExtraAction, llStatusExtraAction)
		updateGroup(btnAccountExtraAction, llAccountExtraAction)
		
	}
	
	fun show() {
		val window = dialog.window
		if(window != null) {
			val lp = window.attributes
			lp.width = (0.5f + 280f * activity.density).toInt()
			lp.height = WindowManager.LayoutParams.WRAP_CONTENT
			window.attributes = lp
		}
		dialog.show()
	}
	
	private fun getUserApiHost() : Host =
		when(val who_host = whoRef?.get()?.apiHost) {
			Host.UNKNOWN -> Host.parse(column.instance_uri)
			Host.EMPTY, null -> access_info.apiHost
			else -> who_host
		}
	
	private fun getUserApDomain() : Host =
		when(val who_host = whoRef?.get()?.apDomain) {
			Host.UNKNOWN -> Host.parse(column.instance_uri)
			Host.EMPTY, null -> access_info.apDomain
			else -> who_host
		}
	
	private fun updateGroup(btn : Button, group : View, toggle : Boolean = false) {
		
		if(btn.visibility != View.VISIBLE) {
			group.vg(false)
			return
		}
		
		when {
			Pref.bpAlwaysExpandContextMenuItems(activity.pref) -> {
				group.vg(true)
				btn.background = null
			}
			
			toggle -> group.vg(group.visibility != View.VISIBLE)
			else -> btn.setOnClickListener(this)
		}
		
		val iconId = if(group.visibility == View.VISIBLE) {
			R.drawable.ic_arrow_drop_up
		} else {
			R.drawable.ic_arrow_drop_down
		}
		
		val iconColor = activity.attrColor(R.attr.colorTimeSmall)
		val drawable = createColoredDrawable(activity, iconId, iconColor, 1f)
		btn.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable, null, null, null)
	}
	
	override fun onClick(v : View) {
		
		// ダイアログを閉じない操作
		when(v.id) {
			R.id.btnCrossAccountActionsForStatus ->
				return updateGroup(
					btnCrossAccountActionsForStatus,
					llCrossAccountActionsForStatus,
					toggle = true
				)
			
			R.id.btnCrossAccountActionsForAccount ->
				return updateGroup(
					btnCrossAccountActionsForAccount,
					llCrossAccountActionsForAccount,
					toggle = true
				)
			R.id.btnAroundThisToot ->
				return updateGroup(
					btnAroundThisToot,
					llAroundThisToot,
					toggle = true
				)
			R.id.btnYourToot ->
				return updateGroup(
					btnYourToot,
					llYourToot,
					toggle = true
				)
			R.id.btnStatusExtraAction ->
				return updateGroup(
					btnStatusExtraAction,
					llStatusExtraAction,
					toggle = true
				)
			R.id.btnAccountExtraAction ->
				return updateGroup(
					btnAccountExtraAction,
					llAccountExtraAction,
					toggle = true
				)
		}
		
		
		dialog.dismissSafe()
		
		val pos = activity.nextPosition(column)
		
		val whoRef = this.whoRef
		val who = whoRef?.get()
		
		if(whoRef != null && who != null) {
			when(v.id) {
				
				R.id.btnReportStatus -> if(status is TootStatus) {
					Action_User.reportForm(activity, access_info, who, status)
				}
				
				R.id.btnReportUser ->
					Action_User.reportForm(activity, access_info, who)
				
				R.id.btnFollow ->
					when {
						
						access_info.isPseudo -> Action_Follow.followFromAnotherAccount(
							activity,
							pos,
							access_info,
							who
						)
						
						access_info.isMisskey && relation.getRequested(who) && ! relation.getFollowing(
							who
						) -> Action_Follow.deleteFollowRequest(
							activity, pos, access_info, whoRef,
							callback = activity.cancel_follow_request_complete_callback
						)
						
						else -> {
							val bSet = ! (relation.getRequested(who) || relation.getFollowing(who))
							Action_Follow.follow(
								activity, pos, access_info, whoRef,
								bFollow = bSet,
								callback = when(bSet) {
									true -> activity.follow_complete_callback
									else -> activity.unfollow_complete_callback
								}
							)
						}
					}
				
				R.id.btnAccountText ->
					ActText.open(activity, ActMain.REQUEST_CODE_TEXT, access_info, who)
				
				R.id.btnMute -> when {
					relation.muting -> Action_User.unmute(
						activity,
						access_info,
						who,
						access_info
					)
					else -> Action_User.muteConfirm(
						activity,
						access_info,
						who,
						access_info
					)
				}
				
				R.id.btnBlock -> when {
					relation.blocking -> Action_User.block(
						activity,
						access_info,
						who,
						access_info,
						false
					)
					else -> Action_User.blockConfirm(activity, access_info, who, access_info)
				}
				
				R.id.btnProfile ->
					Action_User.profileLocal(activity, pos, access_info, who)
				
				R.id.btnSendMessage ->
					Action_User.mention(activity, access_info, who)
				
				R.id.btnAccountWebPage -> who.url?.let { url ->
					activity.openCustomTab(url)
				}
				
				R.id.btnFollowRequestOK ->
					Action_Follow.authorizeFollowRequest(activity, access_info, whoRef, true)
				
				R.id.btnDeleteSuggestion ->
					Action_User.deleteSuggestion(activity, access_info, who)
				
				R.id.btnFollowRequestNG ->
					Action_Follow.authorizeFollowRequest(activity, access_info, whoRef, false)
				
				R.id.btnFollowFromAnotherAccount ->
					Action_Follow.followFromAnotherAccount(activity, pos, access_info, who)
				
				R.id.btnSendMessageFromAnotherAccount ->
					Action_User.mentionFromAnotherAccount(activity, access_info, who)
				
				R.id.btnOpenProfileFromAnotherAccount ->
					Action_User.profileFromAnotherAccount(activity, pos, access_info, who)
				
				R.id.btnNickname ->
					ActNickname.open(
						activity,
						access_info.getFullAcct(who),
						true,
						ActMain.REQUEST_CODE_NICKNAME
					)
				
				R.id.btnAccountQrCode ->
					DlgQRCode.open(
						activity,
						whoRef.decoded_display_name,
						who.getUserUrl()
					)
				
				R.id.btnDomainBlock ->
					if(access_info.isPseudo) {
						// 疑似アカウントではドメインブロックできない
						activity.showToast(false, R.string.domain_block_from_pseudo)
						return
					} else {
						val whoApDomain = who.apDomain
						// 自分のドメインではブロックできない
						if(access_info.matchHost(whoApDomain)) {
							activity.showToast(false, R.string.domain_block_from_local)
							return
						}
						AlertDialog.Builder(activity)
							.setMessage(
								activity.getString(
									R.string.confirm_block_domain,
									whoApDomain
								)
							)
							.setNegativeButton(R.string.cancel, null)
							.setPositiveButton(R.string.ok) { _, _ ->
								Action_Instance.blockDomain(
									activity,
									access_info,
									whoApDomain,
									true
								)
							}
							.show()
					}
				
				R.id.btnOpenTimeline -> {
					who.apiHost.valid()?.let {
						Action_Instance.timelineLocal(activity, pos, it)
					}
				}
				
				R.id.btnDomainTimeline -> {
					who.apiHost.valid()?.let {
						Action_Instance.timelineDomain(activity, pos, access_info, it)
					}
				}
				
				R.id.btnAvatarImage -> {
					val url = if(! who.avatar.isNullOrEmpty()) who.avatar else who.avatar_static
					activity.openCustomTab(url)
					// XXX: 設定によっては内蔵メディアビューアで開けないか？
				}
				
				R.id.btnQuoteName -> {
					var sv = who.display_name
					try {
						val fmt = Pref.spQuoteNameFormat(activity.pref)
						if(fmt.contains("%1\$s")) {
							sv = String.format(fmt, sv)
						}
					} catch(ex : Throwable) {
						log.trace(ex)
					}
					
					Action_Account.openPost(activity, sv)
				}
				
				R.id.btnHideBoost ->
					Action_User.showBoosts(activity, access_info, who, false)
				
				R.id.btnShowBoost ->
					Action_User.showBoosts(activity, access_info, who, true)
				
				R.id.btnHideFavourite -> {
					val acct = access_info.getFullAcct(who)
					FavMute.save(acct)
					activity.showToast(false, R.string.changed)
					for(column in activity.app_state.columnList) {
						column.onHideFavouriteNotification(acct)
					}
				}
				
				R.id.btnShowFavourite -> {
					FavMute.delete(access_info.getFullAcct(who))
					activity.showToast(false, R.string.changed)
				}
				
				R.id.btnListMemberAddRemove ->
					DlgListMember(activity, who, access_info).show()
				
				R.id.btnInstanceInformation -> {
					Action_Instance.information(activity, pos, getUserApiHost())
				}
				
				R.id.btnProfileDirectory -> {
					Action_Instance.profileDirectoryFromInstanceInformation(
						activity,
						column,
						getUserApiHost()
					)
				}
				
				R.id.btnEndorse -> Action_Account.endorse(
					activity,
					access_info,
					who,
					! relation.endorsed
				)
				
				R.id.btnAroundAccountTL -> Action_Instance.timelinePublicAround(
					activity,
					access_info,
					pos,
					who.apiHost,
					status,
					ColumnType.ACCOUNT_AROUND, allowPseudo = false
				)
				
				R.id.btnAroundLTL -> Action_Instance.timelinePublicAround(
					activity,
					access_info,
					pos,
					who.apiHost,
					status,
					ColumnType.LOCAL_AROUND
				)
				
				R.id.btnAroundFTL -> Action_Instance.timelinePublicAround(
					activity,
					access_info,
					pos,
					who.apiHost,
					status,
					ColumnType.FEDERATED_AROUND
				)
				
				R.id.btnCopyAccountId -> who.id.toString().copyToClipboard(activity)
				
				R.id.btnOpenAccountInAdminWebUi ->
					activity.openBrowser(
						"https://${access_info.apiHost.ascii}/admin/accounts/${who.id}"
					)
				
				R.id.btnOpenInstanceInAdminWebUi ->
					activity.openBrowser(
						"https://${access_info.apiHost.ascii}/admin/instances/${who.apDomain.ascii}"
					)
				
				R.id.btnBoostWithVisibility -> {
					val status = this.status ?: return
					val list = if(access_info.isMisskey) {
						arrayOf(
							TootVisibility.Public,
							TootVisibility.UnlistedHome,
							TootVisibility.PrivateFollowers,
							TootVisibility.LocalPublic,
							TootVisibility.LocalHome,
							TootVisibility.LocalFollowers,
							TootVisibility.DirectSpecified,
							TootVisibility.DirectPrivate
						)
					} else {
						arrayOf(
							TootVisibility.Public,
							TootVisibility.UnlistedHome,
							TootVisibility.PrivateFollowers
						)
					}
					val caption_list = list
						.map { Styler.getVisibilityCaption(activity, access_info.isMisskey, it) }
						.toTypedArray()
					
					AlertDialog.Builder(activity)
						.setTitle(R.string.choose_visibility)
						.setItems(caption_list) { _, which ->
							if(which in list.indices) {
								Action_Toot.boost(
									activity,
									access_info,
									status,
									access_info.getFullAcct(status.account),
									NOT_CROSS_ACCOUNT,
									visibility = list[which],
									callback =activity.boost_complete_callback,
								)
							}
						}
						.setNegativeButton(R.string.cancel, null)
						.show()
				}
				
				R.id.btnNotificationFrom -> {
					if(access_info.isMisskey) {
						activity.showToast(false, R.string.misskey_account_not_supported)
					} else {
						access_info.getFullAcct(who).validFull()?.let {
							activity.addColumn(
								pos,
								access_info,
								ColumnType.NOTIFICATION_FROM_ACCT,
								it
							)
						}
					}
				}
				
				R.id.btnPostNotification ->
					if(! access_info.isPseudo && access_info.isMastodon && relation.following) {
						val toggle = ! relation.notifying
						Action_User.statusNotification(activity, access_info, who.id, toggle)
					}
			}
		}
		
		when(v.id) {
			
			R.id.btnStatusWebPage ->
				activity.openCustomTab(status?.url)
			
			R.id.btnText -> if(status != null) {
				ActText.open(activity, ActMain.REQUEST_CODE_TEXT, access_info, status)
			}
			
			R.id.btnFavouriteAnotherAccount -> Action_Toot.favouriteFromAnotherAccount(
				activity,
				access_info,
				status
			)
			
			R.id.btnBookmarkAnotherAccount -> Action_Toot.bookmarkFromAnotherAccount(
				activity,
				access_info,
				status
			)
			
			R.id.btnBoostAnotherAccount -> Action_Toot.boostFromAnotherAccount(
				activity,
				access_info,
				status
			)
			R.id.btnReactionAnotherAccount -> Action_Toot.reactionFromAnotherAccount(
				activity,
				access_info,
				status
			)
			
			R.id.btnReplyAnotherAccount -> Action_Toot.replyFromAnotherAccount(
				activity,
				access_info,
				status
			)
			R.id.btnQuoteToot -> Action_Toot.replyFromAnotherAccount(
				activity,
				access_info,
				status,
				quote = true
			)
			R.id.btnQuoteTootBT -> Action_Toot.replyFromAnotherAccount(
				activity,
				access_info,
				status?.reblogParent,
				quote = true
			)
			
			R.id.btnConversationAnotherAccount -> status?.let { status ->
				Action_Toot.conversationOtherInstance(activity, pos, status)
			}
			
			R.id.btnDelete -> status?.let { status ->
				AlertDialog.Builder(activity)
					.setMessage(activity.getString(R.string.confirm_delete_status))
					.setNegativeButton(R.string.cancel, null)
					.setPositiveButton(R.string.ok) { _, _ ->
						Action_Toot.delete(
							activity,
							access_info,
							status.id
						)
					}
					.show()
			}
			R.id.btnRedraft -> status?.let { status ->
				Action_Toot.redraft(activity, access_info, status)
			}
			
			R.id.btnMuteApp -> status?.application?.let {
				Action_App.muteApp(activity, it)
			}
			
			R.id.btnBoostedBy -> status?.let {
				activity.addColumn(false, pos, access_info, ColumnType.BOOSTED_BY, it.id)
			}
			
			R.id.btnFavouritedBy -> status?.let {
				activity.addColumn(false, pos, access_info, ColumnType.FAVOURITED_BY, it.id)
			}
			
			R.id.btnCancel -> dialog.cancel()
			
			R.id.btnTranslate -> CustomShare.invoke(
				activity,
				access_info,
				status,
				CustomShareTarget.Translate
			)
			
			R.id.btnQuoteUrlStatus -> status?.url?.let { url ->
				if(url.isNotEmpty()) Action_Account.openPost(activity, url)
			}
			
			R.id.btnQuoteUrlAccount -> who?.url?.let { url ->
				if(url.isNotEmpty()) Action_Account.openPost(activity, url)
			}
			R.id.btnShareUrlStatus -> status?.url?.let { url ->
				if(url.isNotEmpty()) shareText(activity, url)
			}
			
			R.id.btnShareUrlAccount -> who?.url?.let { url ->
				if(url.isNotEmpty()) shareText(activity, url)
			}
			R.id.btnNotificationDelete -> notification?.let { notification ->
				Action_Notification.deleteOne(activity, access_info, notification)
			}
			
			R.id.btnConversationMute -> status?.let { status ->
				Action_Toot.muteConversation(activity, access_info, status)
			}
			
			R.id.btnProfilePin -> status?.let { status ->
				Action_Toot.pin(activity, access_info, status, true)
			}
			
			R.id.btnProfileUnpin -> status?.let { status ->
				Action_Toot.pin(activity, access_info, status, false)
			}
			
		}
	}
	
	private fun shareText(activity : ActMain, text : String) {
		ShareCompat.IntentBuilder.from(activity)
			.setText(text)
			.setType("text/plain")
			.startChooser()
	}
	
	override fun onLongClick(v : View) : Boolean {
		
		val whoRef = this.whoRef
		val who = whoRef?.get()
		
		
		when(v.id) {
			R.id.btnFollow -> {
				dialog.dismissSafe()
				Action_Follow.followFromAnotherAccount(
					activity,
					activity.nextPosition(column),
					access_info,
					who
				)
				return true
			}
			
			R.id.btnProfile -> {
				dialog.dismissSafe()
				Action_User.profileFromAnotherAccount(
					activity,
					activity.nextPosition(column),
					access_info,
					who
				)
				return true
			}
			
			R.id.btnSendMessage -> {
				dialog.dismissSafe()
				Action_User.mentionFromAnotherAccount(activity, access_info, who)
				return true
			}
			
			R.id.btnMute -> if(who != null) {
				Action_User.muteFromAnotherAccount(activity, who, access_info)
				return true
			}
			R.id.btnBlock -> if(who != null) {
				Action_User.blockFromAnotherAccount(activity, who, access_info)
				return true
			}
		}
		
		return false
	}
	
}

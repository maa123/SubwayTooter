package jp.juggler.subwaytooter

import android.content.Context
import android.content.res.Configuration
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.content.ContextCompat
import jp.juggler.subwaytooter.emoji.EmojiMap
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootVisibility
import jp.juggler.subwaytooter.span.EmojiImageSpan
import jp.juggler.subwaytooter.span.createSpan
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.util.LogCategory
import jp.juggler.util.attrColor
import jp.juggler.util.notZero
import jp.juggler.util.setIconDrawableId
import kotlin.math.max
import kotlin.math.min

private val log = LogCategory("Styler")

object Styler {
	
	fun defaultColorIcon(context : Context, iconId : Int) : Drawable? =
		ContextCompat.getDrawable(context, iconId)?.also {
			it.setTint(context.attrColor(R.attr.colorVectorDrawable))
			it.setTintMode(PorterDuff.Mode.SRC_IN)
		}
	
	fun getVisibilityIconId(isMisskeyData : Boolean, visibility : TootVisibility) : Int {
		val isMisskey = when(Pref.ipVisibilityStyle(App1.pref)) {
			Pref.VS_MASTODON -> false
			Pref.VS_MISSKEY -> true
			else -> isMisskeyData
		}
		return when {
			isMisskey -> when(visibility) {
				TootVisibility.Public -> R.drawable.ic_public
				TootVisibility.UnlistedHome -> R.drawable.ic_home
				TootVisibility.PrivateFollowers -> R.drawable.ic_lock_open
				TootVisibility.DirectSpecified -> R.drawable.ic_mail
				TootVisibility.DirectPrivate -> R.drawable.ic_lock
				TootVisibility.WebSetting -> R.drawable.ic_question
				TootVisibility.AccountSetting -> R.drawable.ic_question
				
				TootVisibility.LocalPublic -> R.drawable.ic_local_ltl
				TootVisibility.LocalHome -> R.drawable.ic_local_home
				TootVisibility.LocalFollowers -> R.drawable.ic_local_lock_open

				TootVisibility.Unknown-> R.drawable.ic_question
				TootVisibility.Limited ->R.drawable.ic_account_circle
				TootVisibility.Mutual -> R.drawable.ic_bidirectional
			}
			else -> when(visibility) {
				TootVisibility.Public -> R.drawable.ic_public
				TootVisibility.UnlistedHome -> R.drawable.ic_lock_open
				TootVisibility.PrivateFollowers -> R.drawable.ic_lock
				TootVisibility.DirectSpecified -> R.drawable.ic_mail
				TootVisibility.DirectPrivate -> R.drawable.ic_mail
				TootVisibility.WebSetting -> R.drawable.ic_question
				TootVisibility.AccountSetting -> R.drawable.ic_question
				
				TootVisibility.LocalPublic -> R.drawable.ic_local_ltl
				TootVisibility.LocalHome -> R.drawable.ic_local_lock_open
				TootVisibility.LocalFollowers -> R.drawable.ic_local_lock

				TootVisibility.Unknown-> R.drawable.ic_question
				TootVisibility.Limited ->R.drawable.ic_account_circle
				TootVisibility.Mutual -> R.drawable.ic_bidirectional
			}
		}
	}
	
	fun getVisibilityString(
		context : Context,
		isMisskeyData : Boolean,
		visibility : TootVisibility
	) : String {
		val isMisskey = when(Pref.ipVisibilityStyle(App1.pref)) {
			Pref.VS_MASTODON -> false
			Pref.VS_MISSKEY -> true
			else -> isMisskeyData
		}
		return context.getString(
			when {
				isMisskey -> when(visibility) {
					TootVisibility.Public -> R.string.visibility_public
					TootVisibility.UnlistedHome -> R.string.visibility_home
					TootVisibility.PrivateFollowers -> R.string.visibility_followers
					TootVisibility.DirectSpecified -> R.string.visibility_direct
					TootVisibility.DirectPrivate -> R.string.visibility_private
					TootVisibility.WebSetting -> R.string.visibility_web_setting
					TootVisibility.AccountSetting -> R.string.visibility_account_setting
					
					TootVisibility.LocalPublic -> R.string.visibility_local_public
					TootVisibility.LocalHome -> R.string.visibility_local_home
					TootVisibility.LocalFollowers -> R.string.visibility_local_followers

					TootVisibility.Unknown-> R.string.visibility_unknown
					TootVisibility.Limited ->R.string.visibility_limited
					TootVisibility.Mutual -> R.string.visibility_mutual
				}
				else -> when(visibility) {
					TootVisibility.Public -> R.string.visibility_public
					TootVisibility.UnlistedHome -> R.string.visibility_unlisted
					TootVisibility.PrivateFollowers -> R.string.visibility_followers
					TootVisibility.DirectSpecified -> R.string.visibility_direct
					TootVisibility.DirectPrivate -> R.string.visibility_direct
					TootVisibility.WebSetting -> R.string.visibility_web_setting
					TootVisibility.AccountSetting -> R.string.visibility_account_setting
					
					TootVisibility.LocalPublic -> R.string.visibility_local_public
					TootVisibility.LocalHome -> R.string.visibility_local_unlisted
					TootVisibility.LocalFollowers -> R.string.visibility_local_followers

					TootVisibility.Unknown-> R.string.visibility_unknown
					TootVisibility.Limited ->R.string.visibility_limited
					TootVisibility.Mutual -> R.string.visibility_mutual
				}
			}
		)
	}
	
	// アイコン付きの装飾テキストを返す
	fun getVisibilityCaption(
		context : Context,
		isMisskeyData : Boolean,
		visibility : TootVisibility
	) : CharSequence {
		
		val icon_id = getVisibilityIconId(isMisskeyData, visibility)
		val sv = getVisibilityString(context, isMisskeyData, visibility)
		val color = context.attrColor(R.attr.colorVectorDrawable)
		val sb = SpannableStringBuilder()
		
		// アイコン部分
		val start = sb.length
		sb.append(" ")
		val end = sb.length
		sb.setSpan(
			EmojiImageSpan(
				context,
				icon_id,
				useColorShader = true,
				color = color
			),
			start,
			end,
			Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
		)
		
		// 文字列部分
		sb.append(' ')
		sb.append(sv)
		
		return sb
	}
	
	fun setFollowIcon(
		context : Context,
		ibFollow : ImageButton,
		ivDot : ImageView,
		relation : UserRelation,
		who : TootAccount,
		defaultColor : Int,
		alphaMultiplier : Float
	) {
		fun colorAccent() =
			Pref.ipButtonFollowingColor(context.pref()).notZero()
				?: context.attrColor(R.attr.colorImageButtonAccent)
		
		fun colorError() =
			Pref.ipButtonFollowRequestColor(context.pref()).notZero()
				?: context.attrColor(R.attr.colorRegexFilterError)
		
		// 被フォロー状態
		when {
			
			relation.blocked_by -> {
				ivDot.visibility = View.VISIBLE
				setIconDrawableId(
					context,
					ivDot,
					R.drawable.ic_blocked_by,
					color = colorError(),
					alphaMultiplier = alphaMultiplier
				)
			}
			
			relation.requested_by -> {
				ivDot.visibility = View.VISIBLE
				setIconDrawableId(
					context,
					ivDot,
					R.drawable.ic_requested_by,
					color = colorError(),
					alphaMultiplier = alphaMultiplier
				)
			}
			
			relation.followed_by -> {
				ivDot.visibility = View.VISIBLE
				setIconDrawableId(
					context,
					ivDot,
					R.drawable.ic_followed_by,
					color = colorAccent(),
					alphaMultiplier = alphaMultiplier
				)
				// 被フォローリクエスト状態の時に followed_by が 真と偽の両方がありえるようなので
				// Relationshipだけを見ても被フォローリクエスト状態は分からないっぽい
				// 仕方ないので馬鹿正直に「 followed_byが真ならバッジをつける」しかできない
			}
			
			else -> {
				ivDot.visibility = View.GONE
			}
		}
		
		// フォローボタン
		// follow button
		val color : Int
		val iconId : Int
		val contentDescription : String
		
		when {
			relation.blocking -> {
				iconId = R.drawable.ic_block
				color = defaultColor
				contentDescription = context.getString(R.string.follow)
			}
			
			relation.muting -> {
				iconId = R.drawable.ic_volume_off
				color = defaultColor
				contentDescription = context.getString(R.string.follow)
			}
			
			relation.getFollowing(who) -> {
				iconId = R.drawable.ic_follow_cross
				color = colorAccent()
				contentDescription = context.getString(R.string.unfollow)
			}
			
			relation.getRequested(who) -> {
				iconId = R.drawable.ic_follow_wait
				color = colorError()
				contentDescription = context.getString(R.string.unfollow)
			}
			
			else -> {
				iconId = R.drawable.ic_follow_plus
				color = defaultColor
				contentDescription = context.getString(R.string.follow)
			}
		}
		
		setIconDrawableId(
			context,
			ibFollow,
			iconId,
			color = color,
			alphaMultiplier = alphaMultiplier
		)
		ibFollow.contentDescription = contentDescription
	}
	
	private fun getHorizontalPadding(v : View, delta_dp : Float) : Int {
		// Essential Phone PH-1は 短辺439dp
		val form_width_max = 460f
		val dm = v.resources.displayMetrics
		val screen_w = dm.widthPixels
		val content_w = (0.5f + form_width_max * dm.density).toInt()
		val pad_lr = max(0, (screen_w - content_w) / 2)
		return pad_lr + (0.5f + delta_dp * dm.density).toInt()
	}
	
	private fun getOrientationString(orientation : Int?) = when(orientation) {
		null -> "null"
		Configuration.ORIENTATION_LANDSCAPE -> "landscape"
		Configuration.ORIENTATION_PORTRAIT -> "portrait"
		Configuration.ORIENTATION_UNDEFINED -> "undefined"
		else -> orientation.toString()
	}
	
	fun fixHorizontalPadding(v : View, delta_dp : Float = 12f) {
		val pad_t = v.paddingTop
		val pad_b = v.paddingBottom
		
		val dm = v.resources.displayMetrics
		val widthDp = dm.widthPixels / dm.density
		if(widthDp >= 640f && v.resources?.configuration?.orientation == Configuration.ORIENTATION_PORTRAIT) {
			val pad_lr = (0.5f + delta_dp * dm.density).toInt()
			when(Pref.ipJustifyWindowContentPortrait(App1.pref)) {
				Pref.JWCP_START -> {
					v.setPaddingRelative(pad_lr, pad_t, pad_lr + dm.widthPixels / 2, pad_b)
					return
				}
				
				Pref.JWCP_END -> {
					v.setPaddingRelative(pad_lr + dm.widthPixels / 2, pad_t, pad_lr, pad_b)
					return
				}
			}
		}
		
		val pad_lr = getHorizontalPadding(v, delta_dp)
		v.setPaddingRelative(pad_lr, pad_t, pad_lr, pad_b)
	}
	
	fun fixHorizontalPadding0(v : View) = fixHorizontalPadding(v, 0f)
	
	fun fixHorizontalMargin(v : View) {
		val lp = v.layoutParams
		if(lp is ViewGroup.MarginLayoutParams) {
			
			val dm = v.resources.displayMetrics
			val orientationString = getOrientationString(v.resources?.configuration?.orientation)
			val widthDp = dm.widthPixels / dm.density
			log.d("fixHorizontalMargin: orientation=$orientationString, w=${widthDp}dp, h=${dm.heightPixels / dm.density}")
			
			if(widthDp >= 640f && v.resources?.configuration?.orientation == Configuration.ORIENTATION_PORTRAIT) {
				when(Pref.ipJustifyWindowContentPortrait(App1.pref)) {
					Pref.JWCP_START -> {
						lp.marginStart = 0
						lp.marginEnd = dm.widthPixels / 2
						return
					}
					
					Pref.JWCP_END -> {
						lp.marginStart = dm.widthPixels / 2
						lp.marginEnd = 0
						return
					}
				}
			}
			
			val pad_lr = getHorizontalPadding(v, 0f)
			lp.leftMargin = pad_lr
			lp.rightMargin = pad_lr
		}
	}
	
	// ActMainの初期化時に更新される
	var round_ratio : Float = 0.33f * 0.5f
	var boost_alpha : Float = 1f
	
	fun calcIconRound(wh : Int) = wh.toFloat() * round_ratio
	
	fun calcIconRound(lp : ViewGroup.LayoutParams) =
		min(lp.width, lp.height).toFloat() * round_ratio
	
}

fun SpannableStringBuilder.appendColorShadeIcon(
	context : Context,
	drawable_id : Int,
	text : String,
	color : Int? = null
) : SpannableStringBuilder {
	val start = this.length
	this.append(text)
	val end = this.length
	this.setSpan(
		EmojiImageSpan(context, drawable_id, useColorShader = true, color = color),
		start,
		end,
		Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
	)
	return this
}

fun SpannableStringBuilder.appendMisskeyReaction(
	context : Context,
	emojiUtf16 : String,
	text : String
) : SpannableStringBuilder {
	val start = this.length
	this.append(text)
	val end = this.length
	
	this.setSpan(
		EmojiMap.unicodeMap[emojiUtf16] !!.createSpan(context),
		start, end,
		Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
	)
	return this
}

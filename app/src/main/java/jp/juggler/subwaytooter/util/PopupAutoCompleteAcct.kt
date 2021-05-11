package jp.juggler.subwaytooter.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.SharedPreferences
import android.os.Handler
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.view.Gravity
import android.view.View
import android.widget.CheckedTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.view.MyEditText
import jp.juggler.util.LogCategory
import jp.juggler.util.asciiPattern
import jp.juggler.util.attrColor
import jp.juggler.util.groupEx
import java.util.*
import kotlin.math.min

@SuppressLint("InflateParams")
internal class PopupAutoCompleteAcct(
	val activity : Activity,
	private val etContent : EditText,
	private val formRoot : View,
	private val bMainScreen : Boolean
) {
	
	companion object {
		
		internal val log = LogCategory("PopupAutoCompleteAcct")
		
		// 絵文字ショートコードにマッチするとても雑な正規表現
		private val reLastShortCode = """:([^\s:]+):\z""".asciiPattern()
	}
	
	private val acct_popup : PopupWindow
	private val llItems : LinearLayout
	val density : Float
	private val popup_width : Int
	val handler : Handler
	
	private val pref : SharedPreferences = App1.pref
	
	private var popup_rows : Int = 0
	
	val isShowing : Boolean
		get() = acct_popup.isShowing
	
	fun dismiss() {
		try {
			acct_popup.dismiss()
		} catch(ex : Throwable) {
			log.trace(ex)
		}
	}
	
	init {
		this.density = activity.resources.displayMetrics.density
		this.handler = App1.getAppState(activity, "PopupAutoCompleteAcct.ctor").handler
		
		popup_width = (0.5f + 240f * density).toInt()
		
		val viewRoot = activity.layoutInflater.inflate(R.layout.acct_complete_popup, null, false)
		llItems = viewRoot.findViewById(R.id.llItems)
		//
		acct_popup = PopupWindow(activity)
		acct_popup.setBackgroundDrawable(
			ContextCompat.getDrawable(
				activity,
				R.drawable.acct_popup_bg
			)
		)
		acct_popup.contentView = viewRoot
		acct_popup.isTouchable = true
	}
	
	fun setList(
		et : MyEditText,
		sel_start : Int,
		sel_end : Int,
		acct_list : ArrayList<CharSequence>?,
		picker_caption : String?,
		picker_callback : Runnable?
	) {
		
		llItems.removeAllViews()
		
		popup_rows = 0
		
		run {
			val v = activity.layoutInflater
				.inflate(R.layout.lv_spinner_dropdown, llItems, false) as CheckedTextView
			v.setTextColor(activity.attrColor(android.R.attr.textColorPrimary))
			v.setText(R.string.close)
			v.setOnClickListener { acct_popup.dismiss() }
			llItems.addView(v)
			++ popup_rows
		}
		
		if(picker_caption != null && picker_callback != null) {
			val v = activity.layoutInflater
				.inflate(R.layout.lv_spinner_dropdown, llItems, false) as CheckedTextView
			v.setTextColor(activity.attrColor(android.R.attr.textColorPrimary))
			v.text = picker_caption
			v.setOnClickListener {
				acct_popup.dismiss()
				picker_callback.run()
			}
			llItems.addView(v)
			++ popup_rows
		}
		
		
		if(acct_list != null) {
			var i = 0
			while(true) {
				if(i >= acct_list.size) break
				val acct = acct_list[i]
				val v = activity.layoutInflater
					.inflate(R.layout.lv_spinner_dropdown, llItems, false) as CheckedTextView
				v.setTextColor(activity.attrColor(android.R.attr.textColorPrimary))
				v.text = acct
				if(acct is Spannable) {
					NetworkEmojiInvalidator(handler, v).register(acct)
				}
				v.setOnClickListener {
					
					val start : Int
					val editable = et.text ?: ""
					val sb = SpannableStringBuilder()
					
					val src_length = editable.length
					start = min(src_length, sel_start)
					val end = min(src_length, sel_end)
					sb.append(editable.subSequence(0, start))
					val remain = editable.subSequence(end, src_length)
					
					if(acct[0] == ' ') {
						// 絵文字ショートコード
						val separator = EmojiDecoder.customEmojiSeparator(pref)
						if(! EmojiDecoder.canStartShortCode(sb, start)) sb.append(separator)
						sb.append(findShortCode(acct.toString()))
						// セパレータにZWSPを使う設定なら、補完した次の位置にもZWSPを追加する。連続して入力補完できるようになる。
						if(separator != ' ') sb.append(separator)
					} else if(acct[0] == '@' && null != acct.find { it >= 0x80.toChar() }) {
						// @user@host IDNドメインを含む
						// 直後に空白を付与する
						sb.append("@" + Acct.parse(acct.toString().substring(1)).ascii).append(" ")
					} else {
						// @user@host
						// #hashtag
						// 直後に空白を付与する
						sb.append(acct).append(" ")
					}
					
					val newSelection = sb.length
					sb.append(remain)
					
					et.text = sb
					et.setSelection(newSelection)
					acct_popup.dismiss()
				}
				
				llItems.addView(v)
				++ popup_rows
				++ i
			}
		}
		
		updatePosition()
	}
	
	private fun findShortCode(acct : String) : String {
		val m = reLastShortCode.matcher(acct)
		if(m.find()) return m.groupEx(0) !!
		return acct
	}
	
	fun updatePosition() {
		
		val location = IntArray(2)
		etContent.getLocationOnScreen(location)
		val text_top = location[1]
		
		var popup_top : Int
		var popup_height : Int
		
		if(bMainScreen) {
			val popup_bottom = text_top + etContent.totalPaddingTop - etContent.scrollY
			val max = popup_bottom - (0.5f + 48f * 1f * density).toInt()
			val min = (0.5f + 48f * 2f * density).toInt()
			popup_height = (0.5f + 48f * popup_rows.toFloat() * density).toInt()
			if(popup_height < min) popup_height = min
			if(popup_height > max) popup_height = max
			popup_top = popup_bottom - popup_height
			
		} else {
			formRoot.getLocationOnScreen(location)
			val form_top = location[1]
			val form_bottom = location[1] + formRoot.height
			
			val layout = etContent.layout
			
			popup_top = try {
				(text_top
					+ etContent.totalPaddingTop
					+ layout.getLineBottom(layout.lineCount - 1)) - etContent.scrollY
			} catch(ex : Throwable) {
				// java.lang.IllegalStateException
				0
			}
			
			if(popup_top < form_top) popup_top = form_top
			
			popup_height = form_bottom - popup_top
			
			val min = (0.5f + 48f * 2f * density).toInt()
			val max = (0.5f + 48f * popup_rows.toFloat() * density).toInt()
			
			if(popup_height < min) popup_height = min
			if(popup_height > max) popup_height = max
		}
		
		if(acct_popup.isShowing) {
			acct_popup.update(0, popup_top, popup_width, popup_height)
		} else {
			acct_popup.width = popup_width
			acct_popup.height = popup_height
			acct_popup.showAtLocation(
				etContent, Gravity.CENTER_HORIZONTAL or Gravity.TOP, 0, popup_top
			)
		}
	}
}

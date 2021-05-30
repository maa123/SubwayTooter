package jp.juggler.subwaytooter

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import jp.juggler.subwaytooter.action.*
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.dialog.pickAccount
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.VersionString
import jp.juggler.subwaytooter.util.openBrowser
import jp.juggler.util.*
import kotlinx.coroutines.*
import org.jetbrains.anko.backgroundColor
import java.lang.ref.WeakReference

class SideMenuAdapter(
    private val actMain: ActMain,
    val handler: Handler,
    navigationView: ViewGroup,
    private val drawer: DrawerLayout
) : BaseAdapter() {

    companion object {

        private val itemTypeCount = ItemType.values().size

        private var lastVersionView: WeakReference<TextView>? = null

        private var versionRow = SpannableStringBuilder("")

        private var releaseInfo: JsonObject? = null

        private fun clickableSpan(url: String) =
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    widget.activity?.openBrowser(url)
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = false
                }
            }

        // 文字列を組み立ててhandler経由でViewに設定する
        // メインスレッドでもそれ以外でも動作する
        fun afterGet(appContext: Context, handler: Handler, currentVersion: String) {

            versionRow = SpannableStringBuilder().apply {
                append(
                    appContext.getString(
                        R.string.app_name_with_version,
                        appContext.getString(R.string.app_name),
                        currentVersion
                    )
                )
                val newRelease = releaseInfo?.jsonObject(
                    if (Pref.bpCheckBetaVersion(App1.pref)) "beta" else "stable"
                )

                val newVersion =
                    (newRelease?.string("name")?.notEmpty() ?: newRelease?.string("tag_name"))
                        ?.replace("""(v|version)\s*""".toRegex(RegexOption.IGNORE_CASE), "")
                        ?.trim()

                if (newVersion == null || newVersion.isEmpty() || VersionString(currentVersion) >= VersionString(
                        newVersion
                    )
                ) {
                    val url = "https://github.com/tateisu/SubwayTooter/releases"
                    append("\n")
                    val start = length
                    append(appContext.getString(R.string.release_note))
                    setSpan(
                        clickableSpan(url),
                        start, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                } else {
                    append("\n")
                    var start = length
                    append(
                        appContext.getString(
                            R.string.new_version_available,
                            newVersion
                        )
                    )
                    setSpan(
                        ForegroundColorSpan(
                            appContext.attrColor(R.attr.colorRegexFilterError)
                        ),
                        start, length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )

                    newRelease?.string("html_url")?.let { url ->
                        append("\n")
                        start = length
                        append(appContext.getString(R.string.release_note_with_assets))
                        setSpan(
                            clickableSpan(url),
                            start, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                }
            }
            handler.post { lastVersionView?.get()?.text = versionRow }
        }

        // メインスレッドから呼ばれる
        private fun checkVersion(appContext: Context, handler: Handler) {
            val currentVersion = try {
                appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
            } catch (ex: PackageManager.NameNotFoundException) {
                "??"
            }

            versionRow = SpannableStringBuilder().apply {
                append(
                    appContext.getString(
                        R.string.app_name_with_version,
                        appContext.getString(R.string.app_name),
                        currentVersion
                    )
                )
            }

            val lastUpdated = releaseInfo?.string("updated_at")?.let { TootStatus.parseTime(it) }
            if (lastUpdated != null && System.currentTimeMillis() - lastUpdated < 86400000L) {
                afterGet(appContext, handler, currentVersion)
            } else {
                launchIO {
                    val json =
                        App1.getHttpCached("https://mastodon-msg.juggler.jp/appVersion/appVersion.json")
                            ?.decodeUTF8()?.decodeJsonObject()
                    if (json != null) {
                        releaseInfo = json
                        afterGet(appContext, handler, currentVersion)
                    }
                }
            }
        }
    }

    private enum class ItemType(val id: Int) {
        IT_NORMAL(0),
        IT_GROUP_HEADER(1),
        IT_DIVIDER(2),
        IT_VERSION(3)
    }

    private class Item(
        val title: Int = 0,
        val icon: Int = 0,
        val action: ActMain.() -> Unit = {}
    ) {

        val itemType: ItemType
            get() = when {
                title == 0 -> ItemType.IT_DIVIDER
                title == 1 -> ItemType.IT_VERSION
                icon == 0 -> ItemType.IT_GROUP_HEADER
                else -> ItemType.IT_NORMAL
            }
    }

    /*
        no title => section divider
        else no icon => section header with title
        else => menu item with icon and title
    */

    private val list = arrayOf(

        Item(icon = R.drawable.ic_info, title = 1),

        Item(),
        Item(title = R.string.account),

        Item(title = R.string.account_add, icon = R.drawable.ic_account_add) {
            accountAdd()
        },

        Item(icon = R.drawable.ic_settings, title = R.string.account_setting) {
            accountOpenSetting()
        },

        Item(),
        Item(title = R.string.column),

        Item(icon = R.drawable.ic_list_numbered, title = R.string.column_list) {
            openColumnList()
        },

        Item(icon = R.drawable.ic_close, title = R.string.close_all_columns) {
            closeColumnAll()
        },

        Item(icon = R.drawable.ic_home, title = R.string.home) {
            timeline(defaultInsertPosition, ColumnType.HOME)
        },

        Item(icon = R.drawable.ic_announcement, title = R.string.notifications) {
            timeline(defaultInsertPosition, ColumnType.NOTIFICATIONS)
        },

        Item(icon = R.drawable.ic_mail, title = R.string.direct_messages) {
            timeline(defaultInsertPosition, ColumnType.DIRECT_MESSAGES)
        },

        Item(icon = R.drawable.ic_share, title = R.string.misskey_hybrid_timeline_long) {
            timeline(defaultInsertPosition, ColumnType.MISSKEY_HYBRID)
        },

        Item(icon = R.drawable.ic_run, title = R.string.local_timeline) {
            timeline(defaultInsertPosition, ColumnType.LOCAL)
        },

        Item(icon = R.drawable.ic_bike, title = R.string.federate_timeline) {
            timeline(defaultInsertPosition, ColumnType.FEDERATE)
        },

        Item(icon = R.drawable.ic_list_list, title = R.string.lists) {
            timeline(defaultInsertPosition, ColumnType.LIST_LIST)
        },

        Item(icon = R.drawable.ic_satellite, title = R.string.antenna_list_misskey) {
            timeline(defaultInsertPosition, ColumnType.MISSKEY_ANTENNA_LIST)
        },

        Item(icon = R.drawable.ic_search, title = R.string.search) {
            timeline(defaultInsertPosition, ColumnType.SEARCH, args = arrayOf("", false))
        },

        Item(icon = R.drawable.ic_hashtag, title = R.string.trend_tag) {
            timeline(defaultInsertPosition, ColumnType.TREND_TAG)
        },

        Item(icon = R.drawable.ic_star, title = R.string.favourites) {
            timeline(defaultInsertPosition, ColumnType.FAVOURITES)
        },

        Item(icon = R.drawable.ic_bookmark, title = R.string.bookmarks) {
            timeline(defaultInsertPosition, ColumnType.BOOKMARKS)
        },
        Item(icon = R.drawable.ic_face, title = R.string.reactioned_posts) {
            launchMain {
                accountListCanSeeMyReactions()?.let { list ->
                    if (list.isEmpty()) {
                        showToast(false, R.string.not_available_for_current_accounts)
                    } else {
                        val columnType = ColumnType.REACTIONS
                        pickAccount(
                            accountListArg = list.toMutableList(),
                            bAuto = true,
                            message = getString(
                                R.string.account_picker_add_timeline_of,
                                columnType.name1(applicationContext)
                            )
                        )?.let { addColumn(defaultInsertPosition, it, columnType) }
                    }
                }
            }
        },

        Item(icon = R.drawable.ic_account_box, title = R.string.profile) {
            timeline(defaultInsertPosition, ColumnType.PROFILE)
        },

        Item(icon = R.drawable.ic_follow_wait, title = R.string.follow_requests) {
            timeline(defaultInsertPosition, ColumnType.FOLLOW_REQUESTS)
        },

        Item(icon = R.drawable.ic_follow_plus, title = R.string.follow_suggestion) {
            timeline(defaultInsertPosition, ColumnType.FOLLOW_SUGGESTION)
        },

        Item(icon = R.drawable.ic_follow_plus, title = R.string.endorse_set) {
            timeline(defaultInsertPosition, ColumnType.ENDORSEMENT)
        },

        Item(icon = R.drawable.ic_follow_plus, title = R.string.profile_directory) {
            serverProfileDirectoryFromSideMenu()
        },

        Item(icon = R.drawable.ic_volume_off, title = R.string.muted_users) {
            timeline(defaultInsertPosition, ColumnType.MUTES)
        },

        Item(icon = R.drawable.ic_block, title = R.string.blocked_users) {
            timeline(defaultInsertPosition, ColumnType.BLOCKS)
        },

        Item(icon = R.drawable.ic_volume_off, title = R.string.keyword_filters) {
            timeline(defaultInsertPosition, ColumnType.KEYWORD_FILTER)
        },

        Item(icon = R.drawable.ic_cloud_off, title = R.string.blocked_domains) {
            timeline(defaultInsertPosition, ColumnType.DOMAIN_BLOCKS)
        },

        Item(icon = R.drawable.ic_timer, title = R.string.scheduled_status_list) {
            timeline(defaultInsertPosition, ColumnType.SCHEDULED_STATUS)
        },

        Item(),
        Item(title = R.string.toot_search),

        Item(icon = R.drawable.ic_search, title = R.string.mastodon_search_portal) {
            addColumn(defaultInsertPosition, SavedAccount.na, ColumnType.SEARCH_MSP, "")
        },
        Item(icon = R.drawable.ic_search, title = R.string.tootsearch) {
            addColumn(defaultInsertPosition, SavedAccount.na, ColumnType.SEARCH_TS, "")
        },
        Item(icon = R.drawable.ic_search, title = R.string.notestock) {
            addColumn(defaultInsertPosition, SavedAccount.na, ColumnType.SEARCH_NOTESTOCK, "")
        },

        Item(),
        Item(title = R.string.setting),

        Item(icon = R.drawable.ic_settings, title = R.string.app_setting) {

            arAppSetting.launch(
                ActAppSetting.createIntent(this)
            )

        },

        Item(icon = R.drawable.ic_settings, title = R.string.highlight_word) {
            startActivity(Intent(this, ActHighlightWordList::class.java))
        },

        Item(icon = R.drawable.ic_volume_off, title = R.string.muted_app) {
            startActivity(Intent(this, ActMutedApp::class.java))
        },

        Item(icon = R.drawable.ic_volume_off, title = R.string.muted_word) {
            startActivity(Intent(this, ActMutedWord::class.java))
        },

        Item(icon = R.drawable.ic_volume_off, title = R.string.fav_muted_user) {
            startActivity(Intent(this, ActFavMute::class.java))
        },

        Item(
            icon = R.drawable.ic_volume_off,
            title = R.string.muted_users_from_pseudo_account
        ) {
            startActivity(Intent(this, ActMutedPseudoAccount::class.java))
        },

        Item(icon = R.drawable.ic_info, title = R.string.app_about) {

            arAbout.launch(
                Intent(this, ActAbout::class.java)
            )
        },

        Item(icon = R.drawable.ic_info, title = R.string.oss_license) {
            startActivity(Intent(this, ActOSSLicense::class.java))
        },

        Item(icon = R.drawable.ic_hot_tub, title = R.string.app_exit) {
            finish()
        }
    )

    private val iconColor = actMain.attrColor(R.attr.colorTimeSmall)

    override fun getCount(): Int = list.size
    override fun getItem(position: Int): Any = list[position]
    override fun getItemId(position: Int): Long = 0L

    override fun getViewTypeCount(): Int = itemTypeCount
    override fun getItemViewType(position: Int): Int = list[position].itemType.id

    private inline fun <reified T : View> viewOrInflate(
        view: View?,
        parent: ViewGroup?,
        resId: Int
    ): T =
        (view ?: actMain.layoutInflater.inflate(resId, parent, false))
            as? T ?: error("invalid view type! ${T::class.java.simpleName}")

    override fun getView(position: Int, view: View?, parent: ViewGroup?): View =
        list[position].run {
            when (itemType) {
                ItemType.IT_DIVIDER ->
                    viewOrInflate(view, parent, R.layout.lv_sidemenu_separator)
                ItemType.IT_GROUP_HEADER ->
                    viewOrInflate<TextView>(view, parent, R.layout.lv_sidemenu_group).apply {
                        text = actMain.getString(title)
                    }
                ItemType.IT_NORMAL ->
                    viewOrInflate<TextView>(view, parent, R.layout.lv_sidemenu_item).apply {
                        isAllCaps = false
                        text = actMain.getString(title)
                        val drawable = createColoredDrawable(actMain, icon, iconColor, 1f)
                        setCompoundDrawablesRelativeWithIntrinsicBounds(
                            drawable,
                            null,
                            null,
                            null
                        )

                        setOnClickListener {
                            action(actMain)
                            drawer.closeDrawer(GravityCompat.START)
                        }
                    }

                ItemType.IT_VERSION ->
                    viewOrInflate<TextView>(view, parent, R.layout.lv_sidemenu_item).apply {
                        lastVersionView = WeakReference(this)
                        movementMethod = LinkMovementMethod.getInstance()
                        textSize = 18f
                        isAllCaps = false
                        background = null
                        text = versionRow
                    }
            }
        }

    init {
        checkVersion(actMain.applicationContext, handler)

        ListView(actMain).apply {
            adapter = this@SideMenuAdapter
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            backgroundColor = actMain.attrColor(R.attr.colorWindowBackground)
            selector = StateListDrawable()
            divider = null
            dividerHeight = 0
            isScrollbarFadingEnabled = false

            val pad_tb = (actMain.density * 12f + 0.5f).toInt()
            setPadding(0, pad_tb, 0, pad_tb)
            clipToPadding = false
            scrollBarStyle = ListView.SCROLLBARS_OUTSIDE_OVERLAY

            navigationView.addView(this)
        }
    }
}

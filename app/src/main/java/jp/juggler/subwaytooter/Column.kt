package jp.juggler.subwaytooter

import android.content.Context
import android.content.SharedPreferences
import android.util.SparseArray
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.streaming.*
import jp.juggler.subwaytooter.table.*
import jp.juggler.subwaytooter.util.BucketList
import jp.juggler.subwaytooter.util.ScrollPosition
import jp.juggler.util.*
import okhttp3.Handshake
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

enum class ColumnPagingType { Default, Cursor, Offset, None, }

enum class ProfileTab(val id: Int, val ct: ColumnType) {
    Status(0, ColumnType.TabStatus),
    Following(1, ColumnType.TabFollowing),
    Followers(2, ColumnType.TabFollowers)
}

enum class HeaderType(val viewType: Int) {
    Profile(1), Search(2), Instance(3), Filter(4), ProfileDirectory(5),
}

class Column(
    val app_state: AppState,
    val context: Context,
    val access_info: SavedAccount,
    typeId: Int,
    val column_id: String
) {
    companion object {

        internal val log = LogCategory("Column")

        internal const val LOOP_TIMEOUT = 10000L
        internal const val LOOP_READ_ENOUGH = 30 // フィルタ後のデータ数がコレ以上ならループを諦めます
        internal const val RELATIONSHIP_LOAD_STEP = 40
        internal const val ACCT_DB_STEP = 100
        internal const val MISSKEY_HASHTAG_LIMIT = 30
        internal const val HASHTAG_ELLIPSIZE = 26

        val typeMap: SparseArray<ColumnType> = SparseArray()

        internal var showOpenSticker = false

        internal const val QUICK_FILTER_ALL = 0
        internal const val QUICK_FILTER_MENTION = 1
        internal const val QUICK_FILTER_FAVOURITE = 2
        internal const val QUICK_FILTER_BOOST = 3
        internal const val QUICK_FILTER_FOLLOW = 4
        internal const val QUICK_FILTER_REACTION = 5
        internal const val QUICK_FILTER_VOTE = 6
        internal const val QUICK_FILTER_POST = 7

        fun loadAccount(context: Context, src: JsonObject): SavedAccount {
            val account_db_id = src.long(ColumnEncoder.KEY_ACCOUNT_ROW_ID) ?: -1L
            return if (account_db_id >= 0) {
                SavedAccount.loadAccount(context, account_db_id)
                    ?: throw RuntimeException("missing account")
            } else {
                SavedAccount.na
            }

        }

        // private val channelIdSeed = AtomicInteger(0)

        // より古いデータの取得に使う
        internal val reMaxId = """[&?]max_id=([^&?;\s]+)""".asciiPattern()

        // より新しいデータの取得に使う
        val reMinId = """[&?](min_id|since_id)=([^&?;\s]+)""".asciiPattern()

        val COLUMN_REGEX_FILTER_DEFAULT: (CharSequence?) -> Boolean = { false }


        var defaultColorHeaderBg = 0
        var defaultColorHeaderName = 0
        var defaultColorHeaderPageNumber = 0
        var defaultColorContentBg = 0
        var defaultColorContentAcct = 0
        var defaultColorContentText = 0

        fun reloadDefaultColor(activity: AppCompatActivity, pref: SharedPreferences) {

            defaultColorHeaderBg = Pref.ipCcdHeaderBg(pref).notZero()
                ?: activity.attrColor(R.attr.color_column_header)

            defaultColorHeaderName = Pref.ipCcdHeaderFg(pref).notZero()
                ?: activity.attrColor(R.attr.colorColumnHeaderName)

            defaultColorHeaderPageNumber = Pref.ipCcdHeaderFg(pref).notZero()
                ?: activity.attrColor(R.attr.colorColumnHeaderPageNumber)

            defaultColorContentBg = Pref.ipCcdContentBg(pref)
            // may zero

            defaultColorContentAcct = Pref.ipCcdContentAcct(pref).notZero()
                ?: activity.attrColor(R.attr.colorTimeSmall)

            defaultColorContentText = Pref.ipCcdContentText(pref).notZero()
                ?: activity.attrColor(R.attr.colorContentText)

        }

        private val internalIdSeed = AtomicInteger(0)
    }

    // カラムオブジェクトの識別に使うID。
    val internalId = internalIdSeed.incrementAndGet()

    val type = ColumnType.parse(typeId)

    internal var dont_close: Boolean = false

    internal var with_attachment: Boolean = false
    internal var with_highlight: Boolean = false
    internal var dont_show_boost: Boolean = false
    internal var dont_show_reply: Boolean = false

    internal var dont_show_normal_toot: Boolean = false
    internal var dont_show_non_public_toot: Boolean = false

    internal var dont_show_favourite: Boolean = false // 通知カラムのみ
    internal var dont_show_follow: Boolean = false // 通知カラムのみ
    internal var dont_show_reaction: Boolean = false // 通知カラムのみ
    internal var dont_show_vote: Boolean = false // 通知カラムのみ

    internal var quick_filter = QUICK_FILTER_ALL

    @Volatile
    internal var dont_streaming: Boolean = false

    internal var dont_auto_refresh: Boolean = false
    internal var hide_media_default: Boolean = false
    internal var system_notification_not_related: Boolean = false
    internal var instance_local: Boolean = false

    internal var enable_speech: Boolean = false
    internal var use_old_api = false

    internal var regex_text: String = ""

    internal var header_bg_color: Int = 0
    internal var header_fg_color: Int = 0
    internal var column_bg_color: Int = 0
    internal var acct_color: Int = 0
    internal var content_color: Int = 0
    internal var column_bg_image: String = ""
    internal var column_bg_image_alpha = 1f

    internal var profile_tab = ProfileTab.Status

    internal var status_id: EntityId? = null

    // プロフカラムではアカウントのID。リストカラムではリストのID
    internal var profile_id: EntityId? = null

    internal var search_query: String = ""
    internal var search_resolve: Boolean = false
    internal var remote_only: Boolean = false
    internal var instance_uri: String = ""
    internal var hashtag: String = ""
    internal var hashtag_any: String = ""
    internal var hashtag_all: String = ""
    internal var hashtag_none: String = ""
    internal var hashtag_acct: String = ""

    internal var language_filter: JsonObject? = null

    // 告知のリスト
    internal var announcements: MutableList<TootAnnouncement>? = null

    // 表示中の告知
    internal var announcementId: EntityId? = null

    // 告知を閉じた時刻, 0なら閉じていない
    internal var announcementHideTime = 0L

    // 告知データを更新したタイミング
    internal var announcementUpdated = 0L

    // プロフカラムでのアカウント情報
    @Volatile
    internal var who_account: TootAccountRef? = null

    // プロフカラムでのfeatured tag 情報(Mastodon3.3.0)
    @Volatile
    internal var who_featured_tags: List<TootTag>? = null

    // リストカラムでのリスト情報
    @Volatile
    internal var list_info: TootList? = null

    // アンテナカラムでのリスト情報
    @Volatile
    internal var antenna_info: MisskeyAntenna? = null

    // 「インスタンス情報」カラムに表示するインスタンス情報
    // (SavedAccount中のインスタンス情報とは異なるので注意)
    internal var instance_information: TootInstance? = null
    internal var handshake: Handshake? = null

    internal var scroll_save: ScrollPosition? = null
    var last_viewing_item_id: EntityId? = null

    internal val is_dispose = AtomicBoolean()

    @Volatile
    internal var bFirstInitialized = false

    var filter_reload_required: Boolean = false

    //////////////////////////////////////////////////////////////////////////////////////

    // カラムを閉じた後のnotifyDataSetChangedのタイミングで、add/removeされる順序が期待通りにならないので
    // 参照を１つだけ持つのではなく、リストを保持して先頭の要素を使うことにする

    val _holder_list = LinkedList<ColumnViewHolder>()

    internal // 複数のリスナがある場合、最も新しいものを返す
    val viewHolder: ColumnViewHolder?
        get() {
            if (is_dispose.get()) return null
            return if (_holder_list.isEmpty()) null else _holder_list.first
        }

    //////////////////////////////////////////////////////////////////////////////////////

    internal var lastTask: ColumnTask? = null

    @Volatile
    internal var bInitialLoading: Boolean = false

    @Volatile
    internal var bRefreshLoading: Boolean = false

    internal var mInitialLoadingError: String = ""
    internal var mRefreshLoadingError: String = ""
    internal var mRefreshLoadingErrorTime: Long = 0L
    internal var mRefreshLoadingErrorPopupState: Int = 0

    internal var task_progress: String? = null

    internal val list_data = BucketList<TimelineItem>()
    internal val duplicate_map = DuplicateMap()


    @Volatile
    var column_regex_filter = COLUMN_REGEX_FILTER_DEFAULT

    @Volatile
    var keywordFilterTrees: FilterTrees? = null

    @Volatile
    var favMuteSet: HashSet<Acct>? = null

    @Volatile
    var highlight_trie: WordTrieTree? = null

    // タイムライン中のデータの始端と終端
    // misskeyは
    internal var idRecent: EntityId? = null
    internal var idOld: EntityId? = null
    internal var offsetNext: Int = 0
    internal var pagingType: ColumnPagingType = ColumnPagingType.Default

    var bRefreshingTop: Boolean = false

    // ListViewの表示更新が追いつかないとスクロール位置が崩れるので
    // 一定時間より短期間にはデータ更新しないようにする
    val last_show_stream_data = AtomicLong(0L)
    val stream_data_queue = ConcurrentLinkedQueue<TimelineItem>()

    @Volatile
    var bPutGap: Boolean = false

    var cacheHeaderDesc: String? = null

    // DMカラム更新時に新APIの利用に成功したなら真
    internal var useConversationSummaries = false

    // DMカラムのストリーミングイベントで新形式のイベントを利用できたなら真
    internal var useConversationSummaryStreaming = false

    ////////////////////////////////////////////////////////////////

    val procMergeStreamingMessage =
        Runnable { this@Column.mergeStreamingMessage() }

    val streamCallback = object : StreamCallback {
        override fun onStreamStatusChanged(status: StreamStatus) =
            this@Column.onStreamStatusChanged(status)

        override fun onTimelineItem(item: TimelineItem, channelId: String?, stream: JsonArray?) =
            this@Column.onStreamingTimelineItem(item)

        override fun onEmojiReactionNotification(notification: TootNotification) =
            runOnMainLooperForStreamingEvent { this@Column.updateEmojiReactionByApiResponse(notification.status) }

        override fun onEmojiReactionEvent(reaction: TootReaction) =
            runOnMainLooperForStreamingEvent { this@Column.updateEmojiReactionByEvent(reaction) }

        override fun onNoteUpdated(ev: MisskeyNoteUpdate, channelId: String?) =
            runOnMainLooperForStreamingEvent { this@Column.onMisskeyNoteUpdated(ev) }

        override fun onAnnouncementUpdate(item: TootAnnouncement) =
            runOnMainLooperForStreamingEvent { this@Column.onAnnouncementUpdate(item) }

        override fun onAnnouncementDelete(id: EntityId) =
            runOnMainLooperForStreamingEvent { this@Column.onAnnouncementDelete(id) }

        override fun onAnnouncementReaction(reaction: TootReaction) =
            runOnMainLooperForStreamingEvent { this@Column.onAnnouncementReaction(reaction) }
    }

    // create from column spec
    internal constructor(
        app_state: AppState,
        access_info: SavedAccount,
        type: Int,
        vararg params: Any
    ) : this(
        app_state = app_state,
        context = app_state.context,
        access_info = access_info,
        typeId = type,
        column_id = ColumnEncoder.generateColumnId()
    ) {
        ColumnSpec.decode(this, params)
    }

    internal constructor(app_state: AppState, src: JsonObject)
        : this(
        app_state,
        app_state.context,
        loadAccount(app_state.context, src),
        src.optInt(ColumnEncoder.KEY_TYPE),
        ColumnEncoder.decodeColumnId(src)
    ) {
        ColumnEncoder.decode(this, src)
    }

    override fun hashCode(): Int = internalId

    override fun equals(other: Any?): Boolean = this === other

    internal fun dispose() {
        is_dispose.set(true)
        app_state.streamManager.updateStreamingColumns()

        for (vh in _holder_list) {
            try {
                vh.listView.adapter = null
            } catch (ignored: Throwable) {
            }
        }
    }

    init {
        ColumnEncoder.registerColumnId(column_id, this)
    }
}

package jp.juggler.subwaytooter

import android.content.Context
import android.text.SpannableStringBuilder
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.TootPolls
import jp.juggler.subwaytooter.api.entity.TootPollsChoice
import jp.juggler.subwaytooter.api.entity.TootPollsType
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.drawable.PollPlotDrawable
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.NetworkEmojiInvalidator
import jp.juggler.subwaytooter.view.EnqueteTimerView
import jp.juggler.util.*
import org.jetbrains.anko.backgroundDrawable
import org.jetbrains.anko.padding

fun ItemViewHolder.showEnqueteItems(status: TootStatus, enquete: TootPolls) {
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

fun ItemViewHolder.makeEnqueteChoiceView(
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
            b.isEnabledAlpha = false
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
            val accessInfo = this@makeEnqueteChoiceView.access_info
            b.setOnClickListener { view ->
                val context = view.context ?: return@setOnClickListener
                onClickEnqueteChoice(status, enquete, context, accessInfo, i)
            }
        }
        llExtra.addView(b)
    }
}

fun ItemViewHolder.makeEnqueteFooterFriendsNico(enquete: TootPolls) {
    val density = activity.density
    val height = (0.5f + 6 * density).toInt()
    val view = EnqueteTimerView(activity)
    view.layoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height)
    view.setParams(enquete.time_start, TootPolls.ENQUETE_EXPIRE)
    llExtra.addView(view)
}

fun ItemViewHolder.makeEnqueteFooterMastodon(
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
        val accessInfo = this@makeEnqueteFooterMastodon.access_info
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

fun ItemViewHolder.onClickEnqueteChoice(
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

    launchMain {
        activity.runApiTask(accessInfo) { client ->
            when (enquete.pollType) {
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
                        put("choices", jp.juggler.util.jsonArray { add(idx) })
                    }.toPostRequestBuilder()
                )
                TootPollsType.FriendsNico -> client.request(
                    "/api/v1/votes/${enquete.status_id}",
                    jsonObject {
                        put("item_index", idx.toString())
                    }.toPostRequestBuilder()
                )
                TootPollsType.Notestock ->
                    TootApiResult("can't vote on pseudo account column.")
            }
        }?.let { result ->
            when (val data = result.jsonObject) {
                null -> activity.showToast(true, result.error)
                else -> when (enquete.pollType) {
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
            }
        }
    }
}

fun ItemViewHolder.sendMultiple(
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

    launchMain {
        var newPoll: TootPolls? = null
        activity.runApiTask (accessInfo){ client->
            client.request(
                "/api/v1/polls/${enquete.pollId}/votes",
                jsonObject {
                    put("choices", jp.juggler.util.jsonArray {
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
        }?.let{ result->


            when ( val data = newPoll) {
                null -> result.error?.let{ context.showToast(true, it)}
                else -> {
                    status.enquete = data
                    // 1個だけ開閉するのではなく、例えば通知TLにある複数の要素をまとめて開閉するなどある
                    list_adapter.notifyChange(reason = "onClickEnqueteChoice", reset = true)
                }
            }
        }
    }
}

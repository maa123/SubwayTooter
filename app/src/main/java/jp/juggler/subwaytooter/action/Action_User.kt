package jp.juggler.subwaytooter.action

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.widget.*
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.AccountPicker
import jp.juggler.subwaytooter.dialog.ReportForm
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.subwaytooter.util.TootApiResultCallback
import jp.juggler.subwaytooter.util.matchHost
import jp.juggler.subwaytooter.util.openCustomTab
import jp.juggler.util.*
import kotlinx.coroutines.*
import okhttp3.Request

object Action_User {

    // ユーザをミュート/ミュート解除する
    private fun mute(
		activity: ActMain,
		access_info: SavedAccount,
		whoArg: TootAccount,
		whoAccessInfo: SavedAccount,
		bMute: Boolean,
		bMuteNotification: Boolean,
		duration: Int?,
	) {
        val whoAcct = whoAccessInfo.getFullAcct(whoArg)
        if (access_info.isMe(whoAcct)) {
            activity.showToast(false, R.string.it_is_you)
            return
        }

        TootTaskRunner(activity).run(access_info, object : TootTask {

			var relationResult: UserRelation? = null
			var whoIdResult: EntityId? = null

			override suspend fun background(client: TootApiClient): TootApiResult? {
				return if (access_info.isPseudo) {
					if (!whoAcct.isValidFull) {
						TootApiResult("can't mute pseudo acct ${whoAcct.pretty}")
					} else {
						val relation = UserRelation.loadPseudo(whoAcct)
						relation.muting = bMute
						relation.savePseudo(whoAcct.ascii)
						relationResult = relation
						whoIdResult = whoArg.id
						TootApiResult()
					}
				} else {
					val whoId = if (access_info.matchHost(whoAccessInfo)) {
						whoArg.id
					} else {
						val (result, accountRef) = client.syncAccountByAcct(access_info, whoAcct)
						accountRef?.get()?.id ?: return result
					}
					whoIdResult = whoId

					if (access_info.isMisskey) {
						client.request(
							when (bMute) {
								true -> "/api/mute/create"
								else -> "/api/mute/delete"
							},
							access_info.putMisskeyApiToken().apply {
								put("userId", whoId.toString())
							}.toPostRequestBuilder()
						)?.apply {
							if (jsonObject != null) {
								// 204 no content

								// update user relation
								val ur = UserRelation.load(access_info.db_id, whoId)
								ur.muting = bMute
								saveUserRelationMisskey(
									access_info,
									whoId,
									TootParser(activity, access_info)
								)
								relationResult = ur
							}
						}
					} else {
						client.request(
							"/api/v1/accounts/${whoId}/${if (bMute) "mute" else "unmute"}",
							when {
								!bMute -> "".toFormRequestBody()
								else ->
									jsonObject {
										put("notifications", bMuteNotification)
										if (duration != null) put("duration", duration)
									}
										.toRequestBody()
							}.toPost()
						)?.apply {
							val jsonObject = jsonObject
							if (jsonObject != null) {
								relationResult = saveUserRelation(
									access_info,
									parseItem(
										::TootRelationShip,
										TootParser(activity, access_info),
										jsonObject
									)
								)
							}
						}
					}
				}
			}

			override suspend fun handleResult(result: TootApiResult?) {
				if (result == null) return  // cancelled.

				val relation = relationResult
				val whoId = whoIdResult

				if (relation != null && whoId != null) {
					// 未確認だが、自分をミュートしようとするとリクエストは成功するがレスポンス中のmutingはfalseになるはず
					if (bMute && !relation.muting) {
						activity.showToast(false, R.string.not_muted)
						return
					}

					for (column in activity.app_state.columnList) {
						if (column.access_info.isPseudo) {
							if (relation.muting && column.type != ColumnType.PROFILE) {
								// ミュートしたユーザの情報はTLから消える
								column.removeAccountInTimelinePseudo(whoAcct)
							}
							// フォローアイコンの表示更新が走る
							column.updateFollowIcons(access_info)
						} else if (column.access_info == access_info) {
							when {
								!relation.muting -> {
									if (column.type == ColumnType.MUTES) {
										// ミュート解除したら「ミュートしたユーザ」カラムから消える
										column.removeUser(access_info, ColumnType.MUTES, whoId)
									} else {
										// 他のカラムではフォローアイコンの表示更新が走る
										column.updateFollowIcons(access_info)
									}

								}

								column.type == ColumnType.PROFILE && column.profile_id == whoId -> {
									// 該当ユーザのプロフページのトゥートはミュートしてても見れる
									// しかしフォローアイコンの表示更新は必要
									column.updateFollowIcons(access_info)
								}

								else -> {
									// ミュートしたユーザの情報はTLから消える
									column.removeAccountInTimeline(access_info, whoId)
								}
							}
						}
					}

					activity.showToast(
						false,
						if (relation.muting) R.string.mute_succeeded else R.string.unmute_succeeded
					)

				} else {
					activity.showToast(false, result.error)
				}
			}
		})
    }

    fun unmute(
		activity: ActMain,
		access_info: SavedAccount,
		whoArg: TootAccount,
		whoAccessInfo: SavedAccount,
	) = mute(
		activity,
		access_info,
		whoArg,
		whoAccessInfo,
		bMute = false,
		bMuteNotification = false,
		duration = null,
	)

    fun muteConfirm(
		activity: ActMain,
		access_info: SavedAccount,
		who: TootAccount,
		whoAccessInfo: SavedAccount,
	) = activity.launch {
        try {
            @SuppressLint("InflateParams")
            val view = activity.layoutInflater.inflate(R.layout.dlg_confirm, null, false)

            val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
            tvMessage.text = activity.getString(R.string.confirm_mute_user, who.username)
            tvMessage.text = activity.getString(R.string.confirm_mute_user, who.username)

            // 「次回以降スキップ」のチェックボックスは「このユーザからの通知もミュート」に再利用する
            // このオプションはMisskeyや疑似アカウントにはない
            val cbMuteNotification = view.findViewById<CheckBox>(R.id.cbSkipNext)
            val hasMuteNotification = !access_info.isMisskey && !access_info.isPseudo
            cbMuteNotification.isChecked = hasMuteNotification
            cbMuteNotification.vg(hasMuteNotification)?.apply {
                setText(R.string.confirm_mute_notification_for_user)
            }

            // Mastodon 3.3から時限ミュート設定ができる
            val choiceList = arrayOf(
				Pair(0, activity.getString(R.string.duration_indefinite)),
				Pair(300, activity.getString(R.string.duration_minutes_5)),
				Pair(1800, activity.getString(R.string.duration_minutes_30)),
				Pair(3600, activity.getString(R.string.duration_hours_1)),
				Pair(21600, activity.getString(R.string.duration_hours_6)),
				Pair(86400, activity.getString(R.string.duration_days_1)),
				Pair(259200, activity.getString(R.string.duration_days_3)),
				Pair(604800, activity.getString(R.string.duration_days_7)),
			)

            val hasMuteDuration = when {
                access_info.isMisskey || access_info.isPseudo -> false
                else -> withContext(Dispatchers.IO) {
                    val client = TootApiClient(activity, callback = object : TootApiCallback {
						override val isApiCancelled: Boolean
							get() = true != coroutineContext[Job]?.isActive
					})
                        .apply { account = access_info }
					val (ti, ri) = TootInstance.get(client)
                    when {
                        ti != null -> ti.versionGE(TootInstance.VERSION_3_3_0_rc1)
                        ri == null -> throw CancellationException()
                        else -> throw RuntimeException(ri.error)
                    }
                }
            }

            val spMuteDuration: Spinner = view.findViewById(R.id.spMuteDuration)
            if (hasMuteDuration) {
				view.findViewById<View>(R.id.llMuteDuration).vg(true)
                spMuteDuration.apply {
                    adapter = ArrayAdapter(
						activity,
						android.R.layout.simple_spinner_item,
						choiceList.map { it.second }.toTypedArray(),
					).apply {
                        setDropDownViewResource(R.layout.lv_spinner_dropdown)
                    }
                }
            }

            AlertDialog.Builder(activity)
                .setView(view)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok)
                { _, _ ->
                    mute(
						activity,
						access_info,
						who,
						whoAccessInfo,
						bMute = true,
						bMuteNotification = cbMuteNotification.isChecked,
						duration = spMuteDuration.selectedItemPosition
							.takeIf { hasMuteDuration && it in choiceList.indices }
							?.let { choiceList[it].first }
					)
                }
                .show()

        } catch (ex: CancellationException) {
            // not show error
        } catch (ex: RuntimeException) {
            activity.showToast(true, ex.message)
        }
    }

    fun muteFromAnotherAccount(
		activity: ActMain,
		who: TootAccount,
		whoAccessInfo: SavedAccount
	) {
        AccountPicker.pick(
			activity,
			bAllowPseudo = false,
			bAuto = false,
			message = activity.getString(R.string.account_picker_mute, who.acct.pretty),
			accountListArg = makeAccountListNonPseudo(activity, who.apDomain)
		) { muteConfirm(activity, it, who, whoAccessInfo) }
    }

    // ユーザをブロック/ブロック解除する
    fun block(
		activity: ActMain,
		access_info: SavedAccount,
		whoArg: TootAccount,
		whoAccessInfo: SavedAccount,
		bBlock: Boolean
	) {
        val whoAcct = whoArg.acct

        if (access_info.isMe(whoAcct)) {
            activity.showToast(false, R.string.it_is_you)
            return
        }

        TootTaskRunner(activity).run(access_info, object : TootTask {

			var relationResult: UserRelation? = null
			var whoIdResult: EntityId? = null

			override suspend fun background(client: TootApiClient): TootApiResult? {
				if (access_info.isPseudo)
					return if (whoAcct.ascii.contains('?')) {
						TootApiResult("can't block pseudo account ${whoAcct.pretty}")
					} else {
						val relation = UserRelation.loadPseudo(whoAcct)
						relation.blocking = bBlock
						relation.savePseudo(whoAcct.ascii)
						relationResult = relation
						TootApiResult()
					}

				val whoId = if (access_info.matchHost(whoAccessInfo)) {
					whoArg.id
				} else {
					val (result, accountRef) = client.syncAccountByAcct(access_info, whoAcct)
					accountRef?.get()?.id ?: return result
				}
				whoIdResult = whoId

				return if (access_info.isMisskey) {

					fun saveBlock(v: Boolean) {
						val ur = UserRelation.load(access_info.db_id, whoId)
						ur.blocking = v
						UserRelation.save1Misskey(
							System.currentTimeMillis(),
							access_info.db_id,
							whoId.toString(),
							ur
						)
						relationResult = ur
					}

					client.request(
						"/api/blocking/${if (bBlock) "create" else "delete"}",
						access_info.putMisskeyApiToken().apply {
							put("userId", whoId.toString())
						}.toPostRequestBuilder()
					)?.apply {
						val error = this.error
						when {
							// success
							error == null -> saveBlock(bBlock)

							// already
							error.contains("already blocking") -> saveBlock(bBlock)
							error.contains("already not blocking") -> saveBlock(bBlock)

							// else something error
						}
					}
				} else {
					client.request(
						"/api/v1/accounts/${whoId}/${if (bBlock) "block" else "unblock"}",
						"".toFormRequestBody().toPost()
					)?.apply {
						val jsonObject = this.jsonObject
						if (jsonObject != null) {
							relationResult = saveUserRelation(
								access_info,
								parseItem(
									::TootRelationShip,
									TootParser(activity, access_info),
									jsonObject
								)
							)
						}
					}
				}
			}

			override suspend fun handleResult(result: TootApiResult?) {

				if (result == null) return  // cancelled.

				val relation = relationResult
				val whoId = whoIdResult
				if (relation != null && whoId != null) {

					// 自分をブロックしようとすると、blocking==falseで帰ってくる
					if (bBlock && !relation.blocking) {
						activity.showToast(false, R.string.not_blocked)
						return
					}

					for (column in activity.app_state.columnList) {
						if (column.access_info.isPseudo) {
							if (relation.blocking) {
								// ミュートしたユーザの情報はTLから消える
								column.removeAccountInTimelinePseudo(whoAcct)
							}
							// フォローアイコンの表示更新が走る
							column.updateFollowIcons(access_info)
						} else if (column.access_info == access_info) {
							when {
								!relation.blocking -> {
									if (column.type == ColumnType.BLOCKS) {
										// ブロック解除したら「ブロックしたユーザ」カラムのリストから消える
										column.removeUser(access_info, ColumnType.BLOCKS, whoId)
									} else {
										// 他のカラムではフォローアイコンの更新を行う
										column.updateFollowIcons(access_info)
									}
								}

								access_info.isMisskey -> {
									// Misskeyのブロックはフォロー解除とフォロー拒否だけなので
									// カラム中の投稿を消すなどの効果はない
									// しかしカラム中のフォローアイコン表示の更新は必要
									column.updateFollowIcons(access_info)
								}

								// 該当ユーザのプロフカラムではブロックしててもトゥートを見れる
								// しかしカラム中のフォローアイコン表示の更新は必要
								column.type == ColumnType.PROFILE && whoId == column.profile_id -> {
									column.updateFollowIcons(access_info)
								}

								// MastodonではブロックしたらTLからそのアカウントの投稿が消える
								else -> column.removeAccountInTimeline(access_info, whoId)
							}
						}
					}

					activity.showToast(
						false,
						if (relation.blocking)
							R.string.block_succeeded
						else
							R.string.unblock_succeeded
					)
				} else {
					activity.showToast(false, result.error)
				}
			}
		})
    }

    fun blockConfirm(
		activity: ActMain,
		access_info: SavedAccount,
		who: TootAccount,
		whoAccessInfo: SavedAccount
	) {
        AlertDialog.Builder(activity)
            .setMessage(
				activity.getString(
					R.string.confirm_block_user,
					who.username
				)
			)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok) { _, _ ->
                block(
					activity,
					access_info,
					who,
					whoAccessInfo,
					true
				)
            }
            .show()
    }

    fun blockFromAnotherAccount(
		activity: ActMain,
		who: TootAccount,
		whoAccessInfo: SavedAccount
	) {
        AccountPicker.pick(
			activity,
			bAllowPseudo = false,
			bAuto = false,
			message = activity.getString(R.string.account_picker_block, who.acct.pretty),
			accountListArg = makeAccountListNonPseudo(activity, who.apDomain)
		) { ai ->
            blockConfirm(activity, ai, who, whoAccessInfo)
        }
    }

//////////////////////////////////////////////////////////////////////////////////////

    // URLからユーザを検索してプロフを開く
    private fun profileFromUrlOrAcct(
		activity: ActMain,
		pos: Int,
		access_info: SavedAccount,
		who_url: String,
		acct: Acct
	) {
        TootTaskRunner(activity).run(access_info, object : TootTask {

			var who: TootAccount? = null

			override suspend fun background(client: TootApiClient): TootApiResult? {
				val (result, ar) = client.syncAccountByUrl(access_info, who_url)
				if (result == null) return null
				who = ar?.get()
				if (who != null) return result

				val (r2, ar2) = client.syncAccountByAcct(access_info, acct)
				who = ar2?.get()
				return r2
			}

			override suspend fun handleResult(result: TootApiResult?) {
				result ?: return // cancelled.

				when (val who = this.who) {
					null -> {
						activity.showToast(true, result.error)
						// 仕方ないのでchrome tab で開く
						activity.openCustomTab(who_url)
					}

					else -> activity.addColumn(pos, access_info, ColumnType.PROFILE, who.id)
				}
			}
		})
    }

    // アカウントを選んでユーザプロフを開く
    fun profileFromAnotherAccount(
		activity: ActMain,
		pos: Int,
		access_info: SavedAccount,
		who: TootAccount?
	) {
        if (who?.url == null) return


        AccountPicker.pick(
			activity,
			bAllowPseudo = false,
			bAuto = false,
			message = activity.getString(
				R.string.account_picker_open_user_who,
				AcctColor.getNickname(access_info, who)
			),
			accountListArg = makeAccountListNonPseudo(activity, who.apDomain)
		) { ai ->
            if (ai.matchHost(access_info)) {
                activity.addColumn(pos, ai, ColumnType.PROFILE, who.id)
            } else {
                profileFromUrlOrAcct(activity, pos, ai, who.url, access_info.getFullAcct(who))
            }
        }
    }

    // 今のアカウントでユーザプロフを開く
    fun profileLocal(
		activity: ActMain,
		pos: Int,
		access_info: SavedAccount,
		who: TootAccount
	) {
        when {
            access_info.isNA -> profileFromAnotherAccount(activity, pos, access_info, who)
            else -> activity.addColumn(pos, access_info, ColumnType.PROFILE, who.id)
        }
    }

    // User URL で指定されたユーザのプロフを開く
// Intent-Filter や openChromeTabから 呼ばれる
    fun profile(
		activity: ActMain,
		pos: Int,
		access_info: SavedAccount?,
		url: String,
		host: Host,
		user: String,
		original_url: String = url
	) {
        val acct = Acct.parse(user, host)

        if (access_info?.isPseudo == false) {
            // 文脈のアカウントがあり、疑似アカウントではない

            if (access_info.matchHost(host)) {

                // 文脈のアカウントと同じインスタンスなら、アカウントIDを探して開いてしまう
                TootTaskRunner(activity).run(access_info, object : TootTask {

					var who: TootAccount? = null

					override suspend fun background(client: TootApiClient): TootApiResult? {
						val (result, ar) = client.syncAccountByAcct(access_info, acct)
						who = ar?.get()
						return result
					}

					override suspend fun handleResult(result: TootApiResult?) {
						result ?: return // cancelled
						when (val who = this.who) {
							null -> {
								// ダメならchromeで開く
								activity.openCustomTab(url)
							}

							else -> profileLocal(activity, pos, access_info, who)
						}
					}
				})
            } else {
                // 文脈のアカウントと異なるインスタンスなら、別アカウントで開く
                profileFromUrlOrAcct(activity, pos, access_info, url, acct)
            }
            return
        }

        // 文脈がない、もしくは疑似アカウントだった
        // 疑似アカウントでは検索APIを使えないため、IDが分からない

        if (!SavedAccount.hasRealAccount()) {
            // 疑似アカウントしか登録されていない
            // chrome tab で開く
            activity.openCustomTab(original_url)
        } else {
            AccountPicker.pick(
				activity,
				bAllowPseudo = false,
				bAuto = false,
				message = activity.getString(
					R.string.account_picker_open_user_who,
					AcctColor.getNickname(acct)
				),
				accountListArg = makeAccountListNonPseudo(activity, host),
				extra_callback = { ll, pad_se, pad_tb ->

					// chrome tab で開くアクションを追加

					val lp = LinearLayout.LayoutParams(
						LinearLayout.LayoutParams.MATCH_PARENT,
						LinearLayout.LayoutParams.WRAP_CONTENT
					)
					val b = Button(activity)
					b.setPaddingRelative(pad_se, pad_tb, pad_se, pad_tb)
					b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
					b.isAllCaps = false
					b.layoutParams = lp
					b.minHeight = (0.5f + 32f * activity.density).toInt()
					b.text = activity.getString(R.string.open_in_browser)
					b.setBackgroundResource(R.drawable.btn_bg_transparent_round6dp)

					b.setOnClickListener {
						activity.openCustomTab(original_url)
					}
					ll.addView(b, 0)
				}
			) {
                profileFromUrlOrAcct(activity, pos, it, url, acct)
            }
        }
    }

//////////////////////////////////////////////////////////////////////////////////////

    // 通報フォームを開く
    fun reportForm(
		activity: ActMain,
		access_info: SavedAccount,
		who: TootAccount,
		status: TootStatus? = null
	) {
        ReportForm.showReportForm(activity, access_info, who, status) { dialog, comment, forward ->
            report(activity, access_info, who, status, comment, forward) {
                dialog.dismissSafe()
            }
        }
    }

    // 通報する
    private fun report(
		activity: ActMain,
		access_info: SavedAccount,
		who: TootAccount,
		status: TootStatus?,
		comment: String,
		forward: Boolean,
		onReportComplete: TootApiResultCallback
	) {
        if (access_info.isMe(who)) {
            activity.showToast(false, R.string.it_is_you)
            return
        }

        TootTaskRunner(activity).run(access_info, object : TootTask {
			override suspend fun background(client: TootApiClient): TootApiResult? {
				return client.request(
					"/api/v1/reports",
					JsonObject().apply {
						put("account_id", who.id.toString())
						put("comment", comment)
						put("forward", forward)
						if (status != null) {
							put("status_ids", jsonArray {
								add(status.id.toString())
							})
						}
					}.toPostRequestBuilder()
				)
			}

			override suspend fun handleResult(result: TootApiResult?) {
				result ?: return // cancelled.

				if (result.jsonObject != null) {
					onReportComplete(result)

					activity.showToast(false, R.string.report_completed)
				} else {
					activity.showToast(true, result.error)
				}
			}
		})
    }

    // show/hide boosts from (following) user
    fun showBoosts(
		activity: ActMain, access_info: SavedAccount, who: TootAccount, bShow: Boolean
	) {
        if (access_info.isMe(who)) {
            activity.showToast(false, R.string.it_is_you)
            return
        }

        TootTaskRunner(activity).run(access_info, object : TootTask {

			var relation: UserRelation? = null
			override suspend fun background(client: TootApiClient): TootApiResult? {

				val result = client.request(
					"/api/v1/accounts/${who.id}/follow",
					jsonObject {
						try {
							put("reblogs", bShow)
						} catch (ex: Throwable) {
							return TootApiResult(ex.withCaption("json encoding error"))
						}
					}.toPostRequestBuilder()
				)

				val jsonObject = result?.jsonObject
				if (jsonObject != null) {
					relation =
						saveUserRelation(
							access_info,
							parseItem(
								::TootRelationShip,
								TootParser(activity, access_info),
								jsonObject
							)
						)
				}
				return result
			}

			override suspend fun handleResult(result: TootApiResult?) {

				if (result == null) return  // cancelled.

				if (relation != null) {
					activity.showToast(true, R.string.operation_succeeded)
				} else {
					activity.showToast(true, result.error)
				}
			}
		})
    }

    // メンションを含むトゥートを作る
    private fun mention(
		activity: ActMain,
		account: SavedAccount,
		initial_text: String
	) {
        ActPost.open(
			activity,
			ActMain.REQUEST_CODE_POST,
			account.db_id,
			initial_text = initial_text
		)
    }

    // メンションを含むトゥートを作る
    fun mention(
		activity: ActMain, account: SavedAccount, who: TootAccount
	) {
        mention(activity, account, "@${account.getFullAcct(who).ascii} ")
    }

    // メンションを含むトゥートを作る
    fun mentionFromAnotherAccount(
		activity: ActMain, access_info: SavedAccount, who: TootAccount?
	) {
        if (who == null) return

        val initial_text = "@${access_info.getFullAcct(who).ascii} "
        AccountPicker.pick(
			activity,
			bAllowPseudo = false,
			bAuto = false,
			message = activity.getString(R.string.account_picker_toot),
			accountListArg = makeAccountListNonPseudo(activity, who.apDomain)
		) { ai ->
            mention(activity, ai, initial_text)
        }
    }

    fun deleteSuggestion(
		activity: ActMain,
		access_info: SavedAccount,
		who: TootAccount,
		bConfirmed: Boolean = false
	) {
        if (!bConfirmed) {

            val name = who.decodeDisplayName(activity)
            AlertDialog.Builder(activity)
                .setMessage(name.intoStringResource(activity, R.string.delete_succeeded_confirm))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok) { _, _ ->
                    deleteSuggestion(activity, access_info, who, bConfirmed = true)
                }
                .show()
            return
        }
        TootTaskRunner(activity).run(access_info, object : TootTask {
			override suspend fun background(client: TootApiClient): TootApiResult? {
				return client.request("/api/v1/suggestions/${who.id}", Request.Builder().delete())
			}

			override suspend fun handleResult(result: TootApiResult?) {
				// cancelled
				result ?: return

				// error
				val error = result.error
				if (error != null) {
					activity.showToast(true, result.error)
					return
				}

				activity.showToast(false, R.string.delete_succeeded)

				// update suggestion column
				for (column in activity.app_state.columnList) {
					column.removeUser(access_info, ColumnType.FOLLOW_SUGGESTION, who.id)
				}
			}
		})
    }

    fun statusNotification(
		activity: ActMain,
		accessInfo: SavedAccount,
		whoId: EntityId,
		enabled: Boolean
	) {
        TootTaskRunner(activity).run(accessInfo, object : TootTask {
			override suspend fun background(client: TootApiClient): TootApiResult? {
				return client.request(
					"/api/v1/accounts/$whoId/follow",
					jsonObject {
						put("notify", enabled)
					}.toPostRequestBuilder()
				)?.also { result ->
					val relation = parseItem(
						::TootRelationShip,
						TootParser(activity, accessInfo),
						result.jsonObject
					)
					if (relation != null) {
						UserRelation.save1Mastodon(
							System.currentTimeMillis(),
							accessInfo.db_id,
							relation
						)
					}
				}
			}

			override suspend fun handleResult(result: TootApiResult?) {
				// cancelled
				result ?: return

				// error
				val error = result.error
				if (error != null) {
					activity.showToast(true, result.error)
					return
				}

				activity.showToast(false, R.string.operation_succeeded)
			}
		})
    }
}

package jp.juggler.subwaytooter.action

import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.ColumnType
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.AccountPicker
import jp.juggler.subwaytooter.dialog.DlgConfirm
import jp.juggler.subwaytooter.table.AcctColor
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.UserRelation
import jp.juggler.util.showToast
import jp.juggler.util.toFormRequestBody
import jp.juggler.util.toPost
import jp.juggler.util.toPostRequestBuilder

object Action_Follow {
	
	fun follow(
		activity : ActMain,
		pos : Int,
		access_info : SavedAccount,
		whoRef : TootAccountRef,
		bFollow : Boolean = true,
		bConfirmMoved : Boolean = false,
		bConfirmed : Boolean = false,
		callback : () -> Unit = {}
	) {
		val who = whoRef.get()
		
		if(access_info.isMe(who)) {
			activity.showToast(false, R.string.it_is_you)
			return
		}
		
		if(! bConfirmMoved && bFollow && who.moved != null) {
			AlertDialog.Builder(activity)
				.setMessage(
					activity.getString(
						R.string.jump_moved_user,
						access_info.getFullAcct(who),
						access_info.getFullAcct(who.moved)
					)
				)
				.setPositiveButton(R.string.ok) { _, _ ->
					Action_User.profileFromAnotherAccount(
						activity,
						pos,
						access_info,
						who.moved
					)
				}
				.setNeutralButton(R.string.ignore_suggestion) { _, _ ->
					follow(
						activity,
						pos,
						access_info,
						whoRef,
						bFollow = bFollow,
						bConfirmMoved = true, // CHANGED
						bConfirmed = bConfirmed,
						callback = callback
					)
				}
				.setNegativeButton(android.R.string.cancel, null)
				.show()
			return
		}
		
		if(! bConfirmed) {
			if(bFollow && who.locked) {
				DlgConfirm.open(
					activity,
					activity.getString(
						R.string.confirm_follow_request_who_from,
						whoRef.decoded_display_name,
						AcctColor.getNickname(access_info)
					), object : DlgConfirm.Callback {
						
						override fun onOK() {
							follow(
								activity,
								pos,
								access_info,
								whoRef,
								bFollow = bFollow,
								bConfirmMoved = bConfirmMoved,
								bConfirmed = true, // CHANGED
								callback = callback
							)
						}
						
						override var isConfirmEnabled : Boolean
							get() = access_info.confirm_follow_locked
							set(value) {
								access_info.confirm_follow_locked = value
								access_info.saveSetting()
								activity.reloadAccountSetting(access_info)
							}
					})
				return
			} else if(bFollow) {
				val msg = activity.getString(
					R.string.confirm_follow_who_from,
					whoRef.decoded_display_name,
					AcctColor.getNickname(access_info)
				)
				
				DlgConfirm.open(
					activity,
					msg,
					object : DlgConfirm.Callback {
						
						override fun onOK() {
							follow(
								activity,
								pos,
								access_info,
								whoRef,
								bFollow = bFollow,
								bConfirmMoved = bConfirmMoved,
								bConfirmed = true, //CHANGED
								callback = callback
							)
						}
						
						override var isConfirmEnabled : Boolean
							get() = access_info.confirm_follow
							set(value) {
								access_info.confirm_follow = value
								access_info.saveSetting()
								activity.reloadAccountSetting(access_info)
							}
					})
				return
			} else {
				DlgConfirm.open(
					activity,
					activity.getString(
						R.string.confirm_unfollow_who_from,
						whoRef.decoded_display_name,
						AcctColor.getNickname(access_info)
					),
					object : DlgConfirm.Callback {
						
						override fun onOK() {
							follow(
								activity,
								pos,
								access_info,
								whoRef,
								bFollow = bFollow,
								bConfirmMoved = bConfirmMoved,
								bConfirmed = true, // CHANGED
								callback = callback
							)
						}
						
						override var isConfirmEnabled : Boolean
							get() = access_info.confirm_unfollow
							set(value) {
								access_info.confirm_unfollow = value
								access_info.saveSetting()
								activity.reloadAccountSetting(access_info)
							}
					})
				return
			}
		}
		
		TootTaskRunner(activity, TootTaskRunner.PROGRESS_NONE).run(access_info, object : TootTask {
			
			var relation : UserRelation? = null
			
			override suspend fun background(client : TootApiClient) : TootApiResult? {
				val parser = TootParser(activity, access_info)
				
				var userId = who.id
				if(who.isRemote) {
					
					// リモートユーザの確認
					
					val skipAccountSync = if(access_info.isMisskey) {
						// Misskey の /users/show はリモートユーザに関して404を返すので
						// userIdからリモートﾕｰｻﾞを照合することはできない。
						// ただし検索APIがエラーになるかどうかは未確認
						false
					} else {
						// https://github.com/tateisu/SubwayTooter/issues/124
						// によると、閉じたタンスのユーザを同期しようとすると検索APIがエラーを返す
						// この問題を回避するため、手持ちのuserIdで照合したユーザのacctが目的のユーザと同じなら
						// 検索APIを呼び出さないようにする
						val result = client.request("/api/v1/accounts/${userId}")
							?: return null
						who.acct == parser.account(result.jsonObject)?.acct
					}
					
					if(! skipAccountSync) {
						// 同タンスのIDではなかった場合、検索APIを使う
						val (result, ar) = client.syncAccountByAcct(access_info, who.acct)
						val user = ar?.get() ?: return result
						userId = user.id
					}
				}
				
				return if(access_info.isMisskey) {
					
					client.request(
						when {
							bFollow -> "/api/following/create"
							else -> "/api/following/delete"
						},
						access_info.putMisskeyApiToken().apply {
							put("userId", userId)
						}
							.toPostRequestBuilder()
					)?.also { result ->
						
						fun saveFollow(f : Boolean) {
							val ur = UserRelation.load(access_info.db_id, userId)
							ur.following = f
							UserRelation.save1Misskey(
								System.currentTimeMillis(),
								access_info.db_id,
								userId.toString(),
								ur
							)
							relation = ur
						}
						
						val error = result.error
						when {
							// success
							error == null -> saveFollow(bFollow)
							
							// already followed/unfollowed
							error.contains("already following") -> saveFollow(bFollow)
							error.contains("already not following") -> saveFollow(bFollow)
							
							// else something error
						}
					}
					
				} else {
					client.request(
						"/api/v1/accounts/${userId}/${if(bFollow) "follow" else "unfollow"}",
						"".toFormRequestBody().toPost()
					)?.also { result ->
						val newRelation = parseItem(::TootRelationShip, parser, result.jsonObject)
						if(newRelation != null) {
							relation = saveUserRelation(access_info, newRelation)
						}
					}
				}
			}
			
			override suspend fun handleResult(result : TootApiResult?) {
				
				if(result == null) return  // cancelled.
				
				val relation = this.relation
				if(relation != null) {
					
					if(bFollow && relation.getRequested(who)) {
						// 鍵付きアカウントにフォローリクエストを申請した状態
						activity.showToast(false, R.string.follow_requested)
					} else if(! bFollow && relation.getRequested(who)) {
						activity.showToast(false, R.string.follow_request_cant_remove_by_sender)
					} else {
						// ローカル操作成功、もしくはリモートフォロー成功
						
						callback()
					}
					
					activity.showColumnMatchAccount(access_info)
					
				} else if(bFollow && who.locked && (result.response?.code ?: - 1) == 422) {
					activity.showToast(false, R.string.cant_follow_locked_user)
				} else {
					activity.showToast(false, result.error)
				}
				
			}
		})
	}
	
	fun deleteFollowRequest(
		activity : ActMain,
		pos : Int,
		access_info : SavedAccount,
		whoRef : TootAccountRef,
		bConfirmed : Boolean = false,
		callback : () -> Unit = {}
	) {
		if(! access_info.isMisskey) {
			follow(
				activity,
				pos,
				access_info,
				whoRef,
				bFollow = false,
				bConfirmed = bConfirmed,
				callback = callback
			)
			return
		}
		
		val who = whoRef.get()
		
		if(access_info.isMe(who)) {
			activity.showToast(false, R.string.it_is_you)
			return
		}
		
		if(! bConfirmed) {
			DlgConfirm.openSimple(
				activity,
				activity.getString(
					R.string.confirm_cancel_follow_request_who_from,
					whoRef.decoded_display_name,
					AcctColor.getNickname(access_info)
				)
			) {
				deleteFollowRequest(
					activity,
					pos,
					access_info,
					whoRef,
					bConfirmed = true, // CHANGED
					callback = callback
				)
			}
			return
		}
		
		TootTaskRunner(activity, TootTaskRunner.PROGRESS_NONE).run(access_info, object : TootTask {
			
			var relation : UserRelation? = null
			
			override suspend fun background(client : TootApiClient) : TootApiResult? {
				
				return if(access_info.isMisskey) {
					
					var userId : EntityId = who.id
					
					// リモートユーザの同期
					if(who.isRemote) {
						val (result, ar) = client.syncAccountByAcct(access_info, who.acct)
						val user = ar?.get() ?: return result
						userId = user.id
					}
					
					client.request(
						"/api/following/requests/cancel", access_info.putMisskeyApiToken().apply {
							put("userId", userId)
						}
							.toPostRequestBuilder()
					)?.also { result ->
						// parserに残ってるRelationをDBに保存する
						val parser = TootParser(activity, access_info)
						val user = parser.account(result.jsonObject)
						if(user != null) {
							relation = saveUserRelationMisskey(access_info, user.id, parser)
						}
					}
				} else {
					TootApiResult("Mastodon has no API to cancel follow request")
				}
			}
			
			override suspend fun handleResult(result : TootApiResult?) {
				
				if(result == null) return  // cancelled.
				
				val relation = this.relation
				if(relation != null) {
					// ローカル操作成功、もしくはリモートフォロー成功
					callback()
					activity.showColumnMatchAccount(access_info)
				} else {
					activity.showToast(false, result.error)
				}
				
			}
		})
	}
	
	// acct で指定したユーザをリモートフォローする
	fun followRemote(
		activity : ActMain,
		access_info : SavedAccount,
		acct : Acct,
		locked : Boolean,
		bConfirmed : Boolean = false,
		callback : () -> Unit = {}
	) {
		
		if(access_info.isMe(acct)) {
			activity.showToast(false, R.string.it_is_you)
			return
		}
		
		if(! bConfirmed) {
			if(locked) {
				DlgConfirm.open(
					activity,
					activity.getString(
						R.string.confirm_follow_request_who_from,
						AcctColor.getNickname(acct),
						AcctColor.getNickname(access_info)
					),
					object : DlgConfirm.Callback {
						override fun onOK() {
							followRemote(
								activity,
								access_info,
								acct,
								locked,
								bConfirmed = true, //CHANGE
								callback = callback
							)
						}
						
						override var isConfirmEnabled : Boolean
							get() = access_info.confirm_follow_locked
							set(value) {
								access_info.confirm_follow_locked = value
								access_info.saveSetting()
								activity.reloadAccountSetting(access_info)
							}
					})
				return
			} else {
				DlgConfirm.open(
					activity,
					activity.getString(
						R.string.confirm_follow_who_from,
						AcctColor.getNickname(acct),
						AcctColor.getNickname(access_info)
					),
					object : DlgConfirm.Callback {
						
						override fun onOK() {
							followRemote(
								activity,
								access_info,
								acct,
								locked,
								bConfirmed = true, //CHANGE
								callback = callback
							)
						}
						
						override var isConfirmEnabled : Boolean
							get() = access_info.confirm_follow
							set(value) {
								access_info.confirm_follow = value
								access_info.saveSetting()
								activity.reloadAccountSetting(access_info)
							}
					})
				return
			}
		}
		
		TootTaskRunner(activity, TootTaskRunner.PROGRESS_NONE).run(access_info, object : TootTask {
			
			var relation : UserRelation? = null
			
			override suspend fun background(client : TootApiClient) : TootApiResult? {
				
				val parser = TootParser(activity, access_info)
				
				val (r2, ar) = client.syncAccountByAcct(access_info, acct)
				var user = ar?.get() ?: return r2
				val userId = user.id
				
				return if(access_info.isMisskey) {
					client.request(
						"/api/following/create",
						access_info.putMisskeyApiToken().apply {
							put("userId", userId)
						}
							.toPostRequestBuilder()
					).also { result ->
						if(result?.error?.contains("already following") == true
							|| result?.error?.contains("already not following") == true
						) {
							// DBから読み直して値を変更する
							this.relation = UserRelation.load(access_info.db_id, userId).apply {
								following = true
							}
						} else {
							// parserに残ってるRelationをDBに保存する
							user = parser.account(result?.jsonObject) ?: return result
							this.relation = saveUserRelationMisskey(access_info, user.id, parser)
						}
					}
				} else {
					client.request(
						"/api/v1/accounts/${userId}/follow", "".toFormRequestBody().toPost()
					)?.also { result ->
						val newRelation = parseItem(::TootRelationShip, parser, result.jsonObject)
						if(newRelation != null) {
							relation = saveUserRelation(access_info, newRelation)
						}
					}
				}
			}
			
			override suspend fun handleResult(result : TootApiResult?) {
				
				if(result == null) return  // cancelled.
				
				if(relation != null) {
					callback()
					activity.showColumnMatchAccount(access_info)
				} else if(locked && (result.response?.code ?: - 1) == 422) {
					activity.showToast(false, R.string.cant_follow_locked_user)
				} else {
					activity.showToast(false, result.error)
				}
				
			}
		})
	}
	
	fun followFromAnotherAccount(
		activity : ActMain,
		pos : Int,
		access_info : SavedAccount,
		account : TootAccount?,
		bConfirmMoved : Boolean = false
	) {
		if(account == null) return
		
		if(! bConfirmMoved && account.moved != null) {
			AlertDialog.Builder(activity)
				.setMessage(
					activity.getString(
						R.string.jump_moved_user,
						access_info.getFullAcct(account),
						access_info.getFullAcct(account.moved)
					)
				)
				.setPositiveButton(R.string.ok) { _, _ ->
					Action_User.profileFromAnotherAccount(activity, pos, access_info, account.moved)
				}
				.setNeutralButton(R.string.ignore_suggestion) { _, _ ->
					followFromAnotherAccount(
						activity,
						pos,
						access_info,
						account,
						bConfirmMoved = true //CHANGED
					)
				}
				.setNegativeButton(android.R.string.cancel, null)
				.show()
			return
		}
		
		val who_acct = access_info.getFullAcct(account)
		AccountPicker.pick(
			activity,
			bAuto = false,
			message = activity.getString(R.string.account_picker_follow),
			accountListArg = makeAccountListNonPseudo(activity, account.apiHost)
		) { ai ->
			followRemote(
				activity,
				ai,
				who_acct,
				account.locked,
				callback = activity.follow_complete_callback
			)
		}
	}
	
	fun authorizeFollowRequest(
		activity : ActMain,
		access_info : SavedAccount,
		whoRef : TootAccountRef,
		bAllow : Boolean
	) {
		val who = whoRef.get()
		if(access_info.isMe(who)) {
			activity.showToast(false, R.string.it_is_you)
			return
		}
		
		TootTaskRunner(activity).run(access_info, object : TootTask {
			
			override suspend fun background(client : TootApiClient) : TootApiResult? {
				
				val parser = TootParser(activity, access_info)
				
				return if(access_info.isMisskey) {
					client.request(
						"/api/following/requests/${if(bAllow) "accept" else "reject"}",
						access_info.putMisskeyApiToken().apply {
							put("userId", who.id)
						}
							.toPostRequestBuilder()
					).also { result ->
						val user = parser.account(result?.jsonObject)
						if(user != null) {
							// parserに残ってるRelationをDBに保存する
							saveUserRelationMisskey(access_info, user.id, parser)
						}
						// 読めなくてもエラー処理は行わない
					}
				} else {
					client.request(
						"/api/v1/follow_requests/${who.id}/${if(bAllow) "authorize" else "reject"}",
						"".toFormRequestBody().toPost()
					)?.also { result ->
						// Mastodon 3.0.0 から更新されたリレーションを返す
						// https//github.com/tootsuite/mastodon/pull/11800
						val newRelation = parseItem(::TootRelationShip, parser, result.jsonObject)
						if(newRelation != null) {
							saveUserRelation(access_info, newRelation)
						}
						// 読めなくてもエラー処理は行わない
					}
				}
			}
			
			override suspend fun handleResult(result : TootApiResult?) {
				if(result == null) return  // cancelled.
				
				val jsonObject = result.jsonObject
				if(jsonObject != null) {
					for(column in activity.app_state.columnList) {
						column.removeUser(access_info, ColumnType.FOLLOW_REQUESTS, who.id)
						
						// 他のカラムでもフォロー状態の表示更新が必要
						if(column.access_info == access_info
							&& column.type != ColumnType.FOLLOW_REQUESTS
						) {
							column.fireRebindAdapterItems()
						}
					}
					
					activity.showToast(
						false,
						if(bAllow) R.string.follow_request_authorized else R.string.follow_request_rejected,
						whoRef.decoded_display_name
					)
				} else {
					activity.showToast(false, result.error)
				}
			}
		})
	}
}

package jp.juggler.subwaytooter.action

import android.app.AlertDialog
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.DlgConfirm
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.*
import okhttp3.Request
import java.util.regex.Pattern

object Action_ListMember {
	
	private val reFollowError = "follow".asciiPattern(Pattern.CASE_INSENSITIVE)
	
	fun interface Callback {
		
		fun onListMemberUpdated(willRegistered : Boolean, bSuccess : Boolean)
	}
	
	fun add(
		activity : ActMain,
		access_info : SavedAccount,
		list_id : EntityId,
		local_who : TootAccount,
		bFollow : Boolean = false,
		callback : Callback?
	) {
		
		TootTaskRunner(activity).run(access_info, object : TootTask {
			override suspend fun background(client : TootApiClient) : TootApiResult? {
				
				val parser = TootParser(activity, access_info)
				
				var userId = local_who.id
				
				return if(access_info.isMisskey) {
					// misskeyのリストはフォロー無関係
					
					client.request(
						"/api/users/lists/push",
						access_info.putMisskeyApiToken().apply {
							put("listId", list_id)
							put("userId", local_who.id)
							
						}.toPostRequestBuilder()
					)
					// 204 no content
				} else {
					
					val isMe = access_info.isMe(local_who)
					if(isMe) {
						val (ti, ri) = TootInstance.get(client)
						if(ti == null) return ri
						if(! ti.versionGE(TootInstance.VERSION_3_1_0_rc1)) {
							return TootApiResult(activity.getString(R.string.it_is_you))
						}
					} else if(bFollow) {
						// リモートユーザの解決
						if(! access_info.isLocalUser(local_who)) {
							val (r2, ar) = client.syncAccountByAcct(access_info, local_who.acct)
							val user = ar?.get() ?: return r2
							userId = user.id
						}
						
						val result = client.request(
							"/api/v1/accounts/$userId/follow",
							"".toFormRequestBody().toPost()
						) ?: return null
						
						val relation = saveUserRelation(
							access_info,
							parseItem(::TootRelationShip, parser, result.jsonObject)
						)
							?: return TootApiResult("parse error.")
						
						if(! relation.following) {
							@Suppress("ControlFlowWithEmptyBody")
							if(relation.requested) {
								return TootApiResult(activity.getString(R.string.cant_add_list_follow_requesting))
							} else {
								// リモートフォローの場合、正常ケースでもここを通る場合がある
								// 何もしてはいけない…
							}
						}
					}
					
					// リストメンバー追加
					
					client.request(
						"/api/v1/lists/$list_id/accounts",
						jsonObject {
							put(
								"account_ids",
								jsonArray {
									add(userId.toString())
								}
							)
						}
							.toPostRequestBuilder()
					)
				}
			}
			
			override suspend fun handleResult(result : TootApiResult?) {
				var bSuccess = false
				
				try {
					
					if(result == null) return  // cancelled.
					
					if(result.jsonObject != null) {
						for(column in activity.app_state.columnList) {
							// リストメンバー追加イベントをカラムに伝達
							column.onListMemberUpdated(access_info, list_id, local_who, true)
						}
						// フォロー状態の更新を表示に反映させる
						if(bFollow) activity.showColumnMatchAccount(access_info)
						
						activity.showToast(false, R.string.list_member_added)
						
						bSuccess = true
						
					} else {
						val response = result.response
						val error = result.error
						if(response != null
							&& response.code == 422
							&& error != null
							&& reFollowError.matcher(error).find()
						) {
							
							if(! bFollow) {
								DlgConfirm.openSimple(
									activity,
									activity.getString(
										R.string.list_retry_with_follow,
										access_info.getFullAcct(local_who)
									)
								) {
									add(
										activity,
										access_info,
										list_id,
										local_who,
										bFollow = true,
										callback = callback
									)
								}
							} else {
								AlertDialog.Builder(activity)
									.setCancelable(true)
									.setMessage(R.string.cant_add_list_follow_requesting)
									.setNeutralButton(R.string.close, null)
									.show()
							}
							return
						}
						
						activity.showToast(true, error)
						
					}
				} finally {
					callback?.onListMemberUpdated(true, bSuccess)
				}
				
			}
		})
	}
	
	fun delete(
		activity : ActMain,
		access_info : SavedAccount,
		list_id : EntityId,
		local_who : TootAccount,
		callback : Callback?
	) {
		TootTaskRunner(activity).run(access_info, object : TootTask {
			override suspend fun background(client : TootApiClient) : TootApiResult? {
				return if(access_info.isMisskey) {
					client.request(
						"/api/users/lists/pull",
						access_info.putMisskeyApiToken().apply {
							put("listId", list_id.toString())
							put("userId", local_who.id.toString())
						}
							.toPostRequestBuilder()
					)
				} else {
					client.request(
						"/api/v1/lists/${list_id}/accounts?account_ids[]=${local_who.id}",
						Request.Builder().delete()
					)
				}
			}
			
			override suspend fun handleResult(result : TootApiResult?) {
				var bSuccess = false
				
				try {
					
					if(result == null) return  // cancelled.
					
					if(result.jsonObject != null) {
						
						for(column in activity.app_state.columnList) {
							column.onListMemberUpdated(access_info, list_id, local_who, false)
						}
						
						activity.showToast(false, R.string.delete_succeeded)
						
						bSuccess = true
						
					} else {
						activity.showToast(false, result.error)
					}
				} finally {
					callback?.onListMemberUpdated(false, bSuccess)
				}
				
			}
		})
	}
}

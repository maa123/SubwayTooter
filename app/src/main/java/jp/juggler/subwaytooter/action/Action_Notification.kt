package jp.juggler.subwaytooter.action

import androidx.appcompat.app.AlertDialog
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootTask
import jp.juggler.subwaytooter.api.TootTaskRunner
import jp.juggler.subwaytooter.api.entity.TootNotification
import jp.juggler.subwaytooter.isNotificationColumn
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.showToast
import jp.juggler.util.toFormRequestBody
import jp.juggler.util.toPost

object Action_Notification {
	
	fun deleteAll(
		activity : ActMain, target_account : SavedAccount, bConfirmed : Boolean
	) {
		if(! bConfirmed) {
			AlertDialog.Builder(activity)
				.setMessage(R.string.confirm_delete_notification)
				.setNegativeButton(R.string.cancel, null)
				.setPositiveButton(R.string.ok) { _, _ ->
					deleteAll(activity, target_account, true)
				}
				.show()
			return
		}
		TootTaskRunner(activity).run(target_account, object : TootTask {
			override suspend fun background(client : TootApiClient) : TootApiResult? {
				// 空データを送る
				return client.request(
					"/api/v1/notifications/clear",
					"".toFormRequestBody().toPost()
				)
			}
			
			override suspend fun handleResult(result : TootApiResult?) {
				if(result == null) return  // cancelled.
				
				if(result.jsonObject != null) {
					// ok. api have return empty object.
					for(column in activity.app_state.columnList) {
						if(column.isNotificationColumn && column.access_info == target_account) {
							column.removeNotifications()
						}
					}
					activity.showToast(false, R.string.delete_succeeded)
				} else {
					activity.showToast(false, result.error)
				}
				
			}
		})
	}
	
	fun deleteOne(
		activity : ActMain, access_info : SavedAccount, notification : TootNotification
	) {
		TootTaskRunner(activity).run(access_info, object : TootTask {
			override suspend fun background(client : TootApiClient) : TootApiResult? {
				
				// https://github.com/tootsuite/mastodon/commit/30f5bcf3e749be9651ed39a07b893f70605f8a39
				// 2種類のAPIがあり、片方は除去された
				
				// まず新しいAPIを試す
				val result = client.request(
					"/api/v1/notifications/${notification.id}/dismiss",
					"".toFormRequestBody().toPost()
				)
				
				return when(result?.response?.code) {
					
					// 新しいAPIがない場合、古いAPIを試す
					422 -> client.request(
						"/api/v1/notifications/dismiss",
						"id=${notification.id}".toFormRequestBody().toPost()
					)
					
					else -> result
				}
			}
			
			override suspend fun handleResult(result : TootApiResult?) {
				if(result == null) return // cancelled.
				
				if(result.jsonObject != null) {
					// 成功したら空オブジェクトが返される
					for(column in activity.app_state.columnList) {
						column.removeNotificationOne(access_info, notification)
					}
					activity.showToast(true, R.string.delete_succeeded)
				} else {
					activity.showToast(true, result.error)
				}
				
			}
		})
	}
	
}

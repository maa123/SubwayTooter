package jp.juggler.subwaytooter.util

import android.content.Context
import android.os.Handler
import android.os.SystemClock
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.api.entity.CustomEmoji
import jp.juggler.subwaytooter.api.entity.parseList
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class CustomEmojiLister(
	val context : Context,
	private val handler : Handler
) {
	
	companion object {
		
		private val log = LogCategory("CustomEmojiLister")
		
		internal const val CACHE_MAX = 50
		
		internal const val ERROR_EXPIRE = 60000L * 5
		
		private val elapsedTime : Long
			get() = SystemClock.elapsedRealtime()
	}
	
	internal class CacheItem(
		val key : String,
		var list : ArrayList<CustomEmoji>? = null,
		var listWithAliases : ArrayList<CustomEmoji>? = null,
		// ロードした時刻
		var time_update : Long = elapsedTime,
		// 参照された時刻
		var time_used : Long = time_update
	)
	
	internal class Request(
		val accessInfo : SavedAccount,
		val reportWithAliases : Boolean = false,
		val onListLoaded : (list : ArrayList<CustomEmoji>) -> Unit?
	)
	
	// 成功キャッシュ
	internal val cache = ConcurrentHashMap<String, CacheItem>()
	
	// エラーキャッシュ
	internal val cache_error = ConcurrentHashMap<String, Long>()
	
	private val cache_error_item = CacheItem("error")
	
	// ロード要求
	internal val queue = ConcurrentLinkedQueue<Request>()
	
	private val worker : Worker
	
	init {
		this.worker = Worker()
	}
	
	// ネットワーク接続が変化したらエラーキャッシュをクリア
	fun onNetworkChanged() {
		cache_error.clear()
	}
	
	private fun getCached(now : Long, accessInfo : SavedAccount) : CacheItem? {
		val host = accessInfo.apiHost.ascii
		
		// 成功キャッシュ
		val item = cache[host]
		if(item != null && now - item.time_update <= ERROR_EXPIRE) {
			item.time_used = now
			return item
		}
		
		// エラーキャッシュ
		val time_error = cache_error[host]
		if(time_error != null && now < time_error + ERROR_EXPIRE) {
			return cache_error_item
		}
		
		return null
	}
	
	fun getList(
		accessInfo : SavedAccount,
		onListLoaded : (list : ArrayList<CustomEmoji>) -> Unit
	) : ArrayList<CustomEmoji>? {
		try {
			synchronized(cache) {
				val item = getCached(elapsedTime, accessInfo)
				if(item != null) return item.list
			}
			
			queue.add(Request(accessInfo, onListLoaded = onListLoaded))
			worker.notifyEx()
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		return null
	}
	
	fun getListWithAliases(
		accessInfo : SavedAccount,
		onListLoaded : (list : ArrayList<CustomEmoji>) -> Unit
	) : ArrayList<CustomEmoji>? {
		try {
			synchronized(cache) {
				val item = getCached(elapsedTime, accessInfo)
				if(item != null) return item.listWithAliases
			}
			
			queue.add(
				Request(
					accessInfo,
					reportWithAliases = true,
					onListLoaded = onListLoaded
				)
			)
			worker.notifyEx()
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		return null
	}
	
	fun getMap(accessInfo : SavedAccount) : HashMap<String, CustomEmoji>? {
		val list = getList(accessInfo) {
			// 遅延ロード非対応
		} ?: return null
		//
		val dst = HashMap<String, CustomEmoji>()
		for(e in list) {
			dst[e.shortcode] = e
		}
		return dst
	}
	
	private inner class Worker : WorkerBase() {
		
		override fun cancel() {
			// このスレッドはキャンセルされない。プロセスが生きている限り動き続ける。
		}
		
		override suspend fun run() {
			while(true) {
				try {
					// リクエストを取得する
					val request = queue.poll()
					if(request == null) {
						// なければ待機
						waitEx(86400000L)
						continue
					}
					
					val cached = synchronized(cache) {
						
						val item = getCached(elapsedTime, request.accessInfo)
						return@synchronized if(item != null) {
							val list = item.list
							val listWithAliases = item.listWithAliases
							if(list != null && listWithAliases != null) {
								fireCallback(request, list, listWithAliases)
							}
							true
						} else {
							// キャッシュにはなかった
							sweep_cache()
							false
						}
					}
					if(cached) continue
					
					val accessInfo = request.accessInfo
					val cacheKey = accessInfo.apiHost.ascii
					var list : ArrayList<CustomEmoji>? = null
					var listWithAlias : ArrayList<CustomEmoji>? = null
					try {
						val data = if(accessInfo.isMisskey) {
							App1.getHttpCachedString(
								"https://${cacheKey}/api/meta",
								accessInfo = accessInfo
							) { builder ->
								builder.post(JsonObject().toRequestBody())
							}
						} else {
							App1.getHttpCachedString(
								"https://${cacheKey}/api/v1/custom_emojis",
								accessInfo = accessInfo
							)
						}
						
						if(data != null) {
							val a = decodeEmojiList(data, accessInfo)
							list = a
							listWithAlias = makeListWithAlias(a)
						}
						
					} catch(ex : Throwable) {
						log.trace(ex)
					}
					
					synchronized(cache) {
						val now = elapsedTime
						if(list == null || listWithAlias == null) {
							cache_error.put(cacheKey, now)
						} else {
							var item : CacheItem? = cache[cacheKey]
							if(item == null) {
								item = CacheItem(cacheKey, list, listWithAlias)
								cache[cacheKey] = item
							} else {
								item.list = list
								item.listWithAliases = listWithAlias
								item.time_update = now
							}
							fireCallback(request, list, listWithAlias)
						}
					}
				} catch(ex : Throwable) {
					log.trace(ex)
					waitEx(3000L)
				}
			}
		}
		
		private fun fireCallback(
			request : Request,
			list : ArrayList<CustomEmoji>,
			listWithAliases : ArrayList<CustomEmoji>
		) {
			handler.post {
				request.onListLoaded(
					if(request.reportWithAliases) {
						listWithAliases
					} else {
						list
					}
				)
			}
		}
		
		// キャッシュの掃除
		private fun sweep_cache() {
			// 超過してる数
			val over = cache.size - CACHE_MAX
			if(over <= 0) return
			
			// 古い要素を一時リストに集める
			val now = elapsedTime
			val list = ArrayList<CacheItem>(over)
			for(item in cache.values) {
				if(now - item.time_used > 1000L) list.add(item)
			}
			
			// 昇順ソート
			list.sortBy { it.time_used }
			
			// 古い物から順に捨てる
			var removed = 0
			for(item in list) {
				cache.remove(item.key)
				if(++ removed >= over) break
			}
		}
		
		private fun decodeEmojiList(
			data : String,
			accessInfo : SavedAccount
		) : ArrayList<CustomEmoji>? {
			return try {
				val list = if(accessInfo.isMisskey) {
					parseList(
						CustomEmoji.decodeMisskey,
						accessInfo.apDomain,
						data.decodeJsonObject().jsonArray("emojis")
					)
				} else {
					parseList(
						CustomEmoji.decode,
						accessInfo.apDomain,
						data.decodeJsonArray()
					)
				}
				list.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.shortcode })
				list
			} catch(ex : Throwable) {
				log.e(ex, "decodeEmojiList failed. instance=%s", accessInfo.apiHost.ascii)
				null
			}
		}
		
		private fun makeListWithAlias(list : ArrayList<CustomEmoji>?) : ArrayList<CustomEmoji> {
			val dst = ArrayList<CustomEmoji>()
			if(list != null) {
				dst.addAll(list)
				for(item in list) {
					val aliases = item.aliases ?: continue
					for(alias in aliases) {
						if(alias.equals(item.shortcode, ignoreCase = true)) continue
						dst.add(item.makeAlias(alias))
					}
				}
				dst.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.alias ?: it.shortcode })
			}
			return dst
		}
	}
	
}

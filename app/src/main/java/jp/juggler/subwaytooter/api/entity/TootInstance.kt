package jp.juggler.subwaytooter.api.entity

import android.os.SystemClock
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.Pref
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.subwaytooter.util.VersionString
import jp.juggler.util.*
import okhttp3.Request
import org.json.JSONObject
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.ArrayList

class TootInstance(parser : TootParser, src : JSONObject) {
	
	// いつ取得したか(内部利用)
	private var time_parse : Long = SystemClock.elapsedRealtime()
	
	val isExpire : Boolean
		get() = SystemClock.elapsedRealtime() - time_parse >= EXPIRE
	
	//	URI of the current instance
	val uri : String?
	
	//	The instance's title
	val title : String?
	
	//	A description for the instance
	val description : String?
	
	// An email address which can be used to contact the instance administrator
	// misskeyの場合はURLらしい
	val email : String?
	
	val version : String?
	
	// バージョンの内部表現
	private val decoded_version : VersionString
	
	// インスタンスのサムネイル。推奨サイズ1200x630px。マストドン1.6.1以降。
	val thumbnail : String?
	
	// ユーザ数等の数字。マストドン1.6以降。
	val stats : Stats?
	
	// 言語のリスト。マストドン2.3.0以降
	val languages : ArrayList<String>?
	
	val contact_account : TootAccount?
	
	// (Pleroma only) トゥートの最大文字数
	val max_toot_chars : Int?
	
	// インスタンスの種別
	enum class InstanceType {
		
		Mastodon,
		Misskey,
		Pixelfed,
		Pleroma
	}
	
	val instanceType : InstanceType
	
	// XXX: urls をパースしてない。使ってないから…
	
	init {
		if(parser.serviceType == ServiceType.MISSKEY) {
			
			this.uri = parser.accessHost
			this.title = parser.accessHost
			this.description = "(Misskey instance)"
			val sv = src.optJSONObject("maintainer")?.parseString("url")
			this.email = when {
				sv?.startsWith("mailto:") == true -> sv.substring(7)
				else -> sv
			}
			
			this.version = src.parseString("version")
			this.decoded_version = VersionString(version)
			this.stats = null
			this.thumbnail = null
			this.max_toot_chars = src.parseInt("maxNoteTextLength")
			this.instanceType = InstanceType.Misskey
			this.languages = src.optJSONArray("langs")?.toStringArrayList() ?: ArrayList()
			this.contact_account = null
			
		} else {
			this.uri = src.parseString("uri")
			this.title = src.parseString("title")
			this.description = src.parseString("description")
			
			val sv = src.parseString("email")
			this.email = when {
				sv?.startsWith("mailto:") == true -> sv.substring(7)
				else -> sv
			}
			
			this.version = src.parseString("version")
			this.decoded_version = VersionString(version)
			this.stats = parseItem(::Stats, src.optJSONObject("stats"))
			this.thumbnail = src.parseString("thumbnail")
			
			this.max_toot_chars = src.parseInt("max_toot_chars")
			
			this.instanceType = when {
				rePleroma.matcher(version ?: "").find() -> InstanceType.Pleroma
				rePixelfed.matcher(version ?: "").find() -> InstanceType.Pixelfed
				else -> InstanceType.Mastodon
			}
			
			languages = src.optJSONArray("languages")?.toStringArrayList()
			
			val parser2 = TootParser(
				parser.context,
				LinkHelper.newLinkHelper(uri ?: "?")
			)
			contact_account =
				parseItem(::TootAccount, parser2, src.optJSONObject("contact_account"))
		}
	}
	
	class Stats(src : JSONObject) {
		val user_count : Long
		val status_count : Long
		val domain_count : Long
		
		init {
			this.user_count = src.parseLong("user_count") ?: - 1L
			this.status_count = src.parseLong("status_count") ?: - 1L
			this.domain_count = src.parseLong("domain_count") ?: - 1L
		}
	}
	
	fun versionGE(check : VersionString) : Boolean {
		if(decoded_version.isEmpty || check.isEmpty) return false
		val i = VersionString.compare(decoded_version, check)
		return i >= 0
	}
	
	val misskeyVersion : Int
		get() = when {
			instanceType != InstanceType.Misskey -> 0
			versionGE(MISSKEY_VERSION_11) -> 11
			else -> 10
		}
	

	companion object {
		private val rePleroma = Pattern.compile("""\bpleroma\b""", Pattern.CASE_INSENSITIVE)
		private val rePixelfed = Pattern.compile("""\bpixelfed\b""", Pattern.CASE_INSENSITIVE)
		
		val VERSION_1_6 = VersionString("1.6")
		val VERSION_2_4_0_rc1 = VersionString("2.4.0rc1")
		val VERSION_2_4_0_rc2 = VersionString("2.4.0rc2")
		//		val VERSION_2_4_0 = VersionString("2.4.0")
		//		val VERSION_2_4_1_rc1 = VersionString("2.4.1rc1")
		val VERSION_2_4_1 = VersionString("2.4.1")
		val VERSION_2_6_0 = VersionString("2.6.0")
		val VERSION_2_7_0_rc1 = VersionString("2.7.0rc1")
		val VERSION_3_0_0_rc1 = VersionString("3.0.0rc1")
		
		val MISSKEY_VERSION_11 = VersionString("11.0")
		
		private val reDigits = Pattern.compile("(\\d+)")
		
		private const val EXPIRE = (1000 * 3600).toLong()
		
		
		// 引数はtoken_infoかTootInstanceのパース前のいずれか
		fun parseMisskeyVersion(token_info : JSONObject) : Int {
			return when(val o = token_info.opt(TootApiClient.KEY_MISSKEY_VERSION)) {
				is Int -> o
				is Boolean -> if(o) 10 else 0
				else -> 0
			}
		}
		
		// 疑似アカウントの追加時に、インスタンスの検証を行う
		private fun TootApiClient.getInstanceInformationMastodon() : TootApiResult? {
			val result = TootApiResult.makeWithCaption(instance)
			if(result.error != null) return result
			
			if(sendRequest(result) {
					Request.Builder().url("https://$instance/api/v1/instance").build()
				}
			) {
				parseJson(result) ?: return null
			}
			
			// misskeyの事は忘れて本来のエラー情報を返す
			return result
		}
		
		// 疑似アカウントの追加時に、インスタンスの検証を行う
		private fun TootApiClient.getInstanceInformationMisskey() : TootApiResult? {
			val result = TootApiResult.makeWithCaption(instance)
			if(result.error != null) return result
			if(sendRequest(result) {
					JSONObject().apply {
						put("dummy", 1)
					}
						.toPostRequestBuilder()
						.url("https://$instance/api/meta")
						.build()
				}) {
				parseJson(result) ?: return null
				
				result.jsonObject?.apply {
					val m = reDigits.matcher(parseString("version") ?: "")
					if(m.find()) {
						put(TootApiClient.KEY_MISSKEY_VERSION, m.groupEx(1) !!.toInt())
					}
				}
			}
			return result
		}
		
		// 疑似アカウントの追加時に、インスタンスの検証を行う
		private fun TootApiClient.getInstanceInformation() : TootApiResult? {
			// misskeyのインスタンス情報を読めたら、それはmisskeyのインスタンス
			val r2 = getInstanceInformationMisskey() ?: return null
			if(r2.jsonObject != null) return r2
			
			// マストドンのインスタンス情報を読めたら、それはマストドンのインスタンス
			val r1 = getInstanceInformationMastodon() ?: return null
			if(r1.jsonObject != null) return r1
			
			return r1 // 通信エラーの表示ならr1でもr2でも構わないはず
		}
		
		//		// インスタンス情報を取得する
		//		private fun parseInstanceInformation(
		//			client : TootApiClient,
		//			result : TootApiResult?,
		//			allowPixelfed : Boolean = false
		//		)
		//			: Pair<TootApiResult?, TootInstance?> {
		//			var ti : TootInstance? = null
		//			val json = result?.jsonObject
		//			if(json != null) {
		//				val parser = TootParser(
		//					client.context,
		//					LinkHelper.newLinkHelper(
		//						client.instance,
		//						misskeyVersion = TootApiClient.parseMisskeyVersion(json)
		//					)
		//				)
		//				ti = parser.instance(json)
		//				when {
		//
		//					ti == null ->
		//						result.setError()
		
		//				}
		//			}
		//			return Pair(result, ti)
		//		}
		
		
		// インスタンス情報のキャッシュ

		class CacheEntry( var data : TootInstance? = null )
		
		private val cache = HashMap<String, CacheEntry>()
		
		private fun getCacheEntry(host : String) : CacheEntry =
			synchronized(cache) {
				val hostLower = host.toLowerCase(Locale.JAPAN)
				var item = cache[hostLower]
				if(item == null) {
					item = CacheEntry()
					cache[hostLower] = item
				}
				item
			}
		
		// get from cache
		// no request, no expiration check
		fun getCached(host : String) = getCacheEntry(host).data
		
		fun get(
			client : TootApiClient,
			host : String? = client.instance,
			account : SavedAccount? = if(host == client.instance) client.account else null,
			allowPixelfed : Boolean = false
		) : Pair<TootApiResult?, TootInstance?> {
			
			val tmpInstance = client.instance
			val tmpAccount = client.account
			try {
				client.account = account
				if(host != null) client.instance = host
				val instanceName = client.instance !!.toLowerCase(Locale.JAPAN)
				
				// ホスト名ごとに用意したオブジェクトで同期する
				val cacheEntry = getCacheEntry(instanceName)
				synchronized(cacheEntry) {
					// re-use cached item.
					val now = SystemClock.elapsedRealtime()
					var item = cacheEntry.data
					if(item != null && now - item.time_parse <= EXPIRE) {
						
						if(item.instanceType == InstanceType.Pixelfed &&
							! Pref.bpEnablePixelfed(App1.pref) &&
							! allowPixelfed
						) {
							return Pair(
								TootApiResult("currently Pixelfed instance is not supported."),
								null
							)
						}
						
						return Pair(TootApiResult(), item)
						
					}
					
					// get new information
					val result = if(account != null) {
						if(account.isMisskey) {
							val params = JSONObject().apply {
								put("dummy", 1)
							}
							client.request("/api/meta", params.toPostRequestBuilder())
						} else {
							client.request("/api/v1/instance")
						}
					} else {
						client.getInstanceInformation()
					}
					
					val json = result?.jsonObject ?: return Pair(result, null)
					
					item = parseItem(
						::TootInstance,
						if(account != null) {
							TootParser(client.context, account)
						} else {
							TootParser(
								client.context,
								LinkHelper.newLinkHelper(
									instanceName,
									misskeyVersion = parseMisskeyVersion(json)
								)
							)
						},
						json
					)
					
					return when {
						item == null ->
							Pair(result.setError("can't parse data in instance information."), null)
						
						item.instanceType == InstanceType.Pixelfed &&
							! Pref.bpEnablePixelfed(App1.pref) &&
							! allowPixelfed ->
							Pair(
								result.setError("currently Pixelfed instance is not supported."),
								null
							)
						
						else -> {
							cacheEntry.data = item
							Pair(result, item)
						}
					}
				}
			} finally {
				client.account = tmpAccount
				client.instance = tmpInstance // must be last.
			}
		}
	}
}

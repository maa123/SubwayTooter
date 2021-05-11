package jp.juggler.subwaytooter.api.entity

import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.api.entity.TootAnnouncement.Reaction
import jp.juggler.util.*

object TootPayload {
	
	val log = LogCategory("TootPayload")
	
	private const val PAYLOAD = "payload"
	
	private val reNumber = "([-]?\\d+)".asciiPattern()
	
	// ストリーミングAPIのペイロード部分をTootStatus,TootNotification,整数IDのどれかに解釈する
	fun parsePayload(
		parser : TootParser,
		event : String,
		parent : JsonObject,
		parent_text : String
	) : Any? {
		try {
			val payload = parent[PAYLOAD] ?: return null
			
			if(payload is JsonObject) {
				return when(event) {
					
					// ここを通るケースはまだ確認できていない
					"update" -> parser.status(payload)
					
					// ここを通るケースはまだ確認できていない
					"notification" -> parser.notification(payload)
					
					// ここを通るケースはまだ確認できていない
					else -> {
						log.e("unknown payload(1). message=%s", parent_text)
						null
					}
				}
			} else if(payload is JsonArray) {
				log.e("unknown payload(1b). message=%s", parent_text)
				return null
			}
			
			if(payload is Number) {
				// 2017/8/24 18:37 mastodon.juggler.jpでここを通った
				return payload.toLong()
			}
			
			if(payload is String) {
				
				if(payload[0] == '{') {
					val src = payload.decodeJsonObject()
					return when(event) {
						// 2017/8/24 18:37 mastodon.juggler.jpでここを通った
						"update" -> parser.status(src)
						
						// 2017/8/24 18:37 mastodon.juggler.jpでここを通った
						"notification" -> parser.notification(src)
						
						"conversation" -> parseItem(::TootConversationSummary, parser, src)
						
						"announcement" -> parseItem(::TootAnnouncement, parser, src)
						
						"announcement.reaction" -> parseItem(::Reaction, src)
						
						else -> {
							log.e("unknown payload(2). message=%s", parent_text)
							// ここを通るケースはまだ確認できていない
						}
					}
				} else if(payload[0] == '[') {
					log.e("unknown payload(2b). message=%s", parent_text)
					return null
				}
				
				// 2017/8/24 18:37 mdx.ggtea.org でここを通った
				val m = reNumber.matcher(payload)
				if(m.find()) {
					return m.groupEx(1) !!.toLong(10)
				}
			}
			
			// ここを通るケースはまだ確認できていない
			log.e("unknown payload(3). message=%s", parent_text)
			
		} catch(ex : Throwable) {
			log.trace(ex)
		}
		
		return null
	}
}

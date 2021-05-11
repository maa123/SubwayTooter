package jp.juggler.subwaytooter.api.entity

import androidx.test.runner.AndroidJUnit4
import android.test.mock.MockContext
import jp.juggler.subwaytooter.api.TootParser
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.util.JsonArray
import jp.juggler.util.JsonObject
import jp.juggler.util.notEmptyOrThrow
import jp.juggler.util.decodeJsonObject
import org.junit.Assert.*
import org.junit.runner.RunWith

import org.junit.Test

@RunWith(AndroidJUnit4::class)
class TestEntityUtils {
	
	class TestEntity(val s : String, val l : Long) : Mappable<String> {
		constructor(src : JsonObject) : this(
			s = src.stringOrThrow("s"),
			l = src.long("l") ?: 0L
		)
		
		@Suppress("UNUSED_PARAMETER")
		constructor(parser : TootParser, src : JsonObject) : this(
			s = src.stringOrThrow("s"),
			l = src.long("l") ?: 0L
		)
		
		override val mapKey : String
			get() = s
	}
	
	@Test
	fun testParseItem() {
		assertEquals(null, parseItem(::TestEntity, null))
		
		run {
			val src = """{"s":null,"l":"100"}""".decodeJsonObject()
			val item = parseItem(::TestEntity, src)
			assertNull(item)
		}
		run {
			val src = """{"s":"","l":"100"}""".decodeJsonObject()
			val item = parseItem(::TestEntity, src)
			assertNull(item)
		}
		run {
			val src = """{"s":"A","l":null}""".decodeJsonObject()
			val item = parseItem(::TestEntity, src)
			assertNotNull(item)
			assertEquals(src.optString("s"), item?.s)
			assertEquals(src.optLong("l"), item?.l)
		}
		run {
			val src = """{"s":"A","l":""}""".decodeJsonObject()
			val item = parseItem(::TestEntity, src)
			assertNotNull(item)
			assertEquals(src.optString("s"), item?.s)
			assertEquals(src.optLong("l"), item?.l)
		}
		run {
			val src = """{"s":"A","l":100}""".decodeJsonObject()
			val item = parseItem(::TestEntity, src)
			assertNotNull(item)
			assertEquals(src.optString("s"), item?.s)
			assertEquals(src.optLong("l"), item?.l)
		}
		run {
			val src ="""{"s":"A","l":"100"}""".decodeJsonObject()
			val item = parseItem(::TestEntity, src)
			assertNotNull(item)
			assertEquals(src.optString("s"), item?.s)
			assertEquals(src.optLong("l"), item?.l)
		}
	}
	
	@Test
	fun testParseList() {
		assertEquals(0, parseList(::TestEntity, null).size)
		
		val src = JsonArray()
		assertEquals(0, parseList(::TestEntity, src).size)
		
		src.add("""{"s":"A","l":"100"}""".decodeJsonObject())
		assertEquals(1, parseList(::TestEntity, src).size)
		
		src.add("""{"s":"A","l":"100"}""".decodeJsonObject())
		assertEquals(2, parseList(::TestEntity, src).size)
		
		// error
		src.add("""{"s":"","l":"100"}""".decodeJsonObject())
		assertEquals(2, parseList(::TestEntity, src).size)
		
	}
	
	@Test
	fun testParseListOrNull() {
		assertEquals(null, parseListOrNull(::TestEntity, null))
		
		val src = JsonArray()
		assertEquals(null, parseListOrNull(::TestEntity, src))
		
		src.add("""{"s":"A","l":"100"}""".decodeJsonObject())
		assertEquals(1, parseListOrNull(::TestEntity, src)?.size)
		
		src.add("""{"s":"A","l":"100"}""".decodeJsonObject())
		assertEquals(2, parseListOrNull(::TestEntity, src)?.size)
		
		// error
		src.add("""{"s":"","l":"100"}""".decodeJsonObject())
		assertEquals(2, parseListOrNull(::TestEntity, src)?.size)
		
	}
	
	@Test
	fun testParseMap() {
		assertEquals(0, parseMap(::TestEntity, null).size)
		
		val src = JsonArray()
		assertEquals(0, parseMap(::TestEntity, src).size)
		
		src.add("""{"s":"A","l":"100"}""".decodeJsonObject())
		assertEquals(1, parseMap(::TestEntity, src).size)
		
		src.add("""{"s":"B","l":"100"}""".decodeJsonObject())
		assertEquals(2, parseMap(::TestEntity, src).size)
		
		// error
		src.add("""{"s":"","l":"100"}""".decodeJsonObject())
		assertEquals(2, parseMap(::TestEntity, src).size)
		
	}
	
	@Test
	fun testParseMapOrNull() {
		assertEquals(null, parseMapOrNull(::TestEntity, null))
		
		val src = JsonArray()
		assertEquals(null, parseMapOrNull(::TestEntity, src))
		
		src.add("""{"s":"A","l":"100"}""".decodeJsonObject())
		assertEquals(1, parseMapOrNull(::TestEntity, src)?.size)
		
		src.add("""{"s":"B","l":"100"}""".decodeJsonObject())
		assertEquals(2, parseMapOrNull(::TestEntity, src)?.size)
		
		// error
		src.add("""{"s":"","l":"100"}""".decodeJsonObject())
		assertEquals(2, parseMapOrNull(::TestEntity, src)?.size)
		
	}
	
	private val parser = TootParser(MockContext(), SavedAccount.na)
	
	@Test
	fun testParseItemWithParser() {
		
		assertEquals(null, parseItem(::TestEntity, parser, null))
		
		run {
			val src ="""{"s":null,"l":"100"}""".decodeJsonObject()
			val item = parseItem(::TestEntity, parser, src)
			assertNull(item)
		}
		run {
			val src = """{"s":"","l":"100"}""".decodeJsonObject()
			val item = parseItem(::TestEntity, parser, src)
			assertNull(item)
		}
		run {
			val src = """{"s":"A","l":null}""".decodeJsonObject()
			val item = parseItem(::TestEntity, parser, src)
			assertNotNull(item)
			assertEquals(src.optString("s"), item?.s)
			assertEquals(src.optLong("l"), item?.l)
		}
		run {
			val src = """{"s":"A","l":""}""".decodeJsonObject()
			val item = parseItem(::TestEntity, parser, src)
			assertNotNull(item)
			assertEquals(src.optString("s"), item?.s)
			assertEquals(src.optLong("l"), item?.l)
		}
		run {
			val src = """{"s":"A","l":100}""".decodeJsonObject()
			val item = parseItem(::TestEntity, parser, src)
			assertNotNull(item)
			assertEquals(src.optString("s"), item?.s)
			assertEquals(src.optLong("l"), item?.l)
		}
		run {
			val src = """{"s":"A","l":"100"}""".decodeJsonObject()
			val item = parseItem(::TestEntity, parser, src)
			assertNotNull(item)
			assertEquals(src.optString("s"), item?.s)
			assertEquals(src.optLong("l"), item?.l)
		}
	}
	
	@Test
	fun testParseListWithParser() {
		assertEquals(0, parseList(::TestEntity, parser, null).size)
		
		val src = JsonArray()
		assertEquals(0, parseList(::TestEntity, parser, src).size)
		
		src.add("""{"s":"A","l":"100"}""".decodeJsonObject())
		assertEquals(1, parseList(::TestEntity, parser, src).size)
		
		src.add("""{"s":"A","l":"100"}""".decodeJsonObject())
		assertEquals(2, parseList(::TestEntity, parser, src).size)
		
		// error
		src.add("""{"s":"","l":"100"}""".decodeJsonObject())
		assertEquals(2, parseList(::TestEntity, parser, src).size)
		
	}
	
	@Test
	fun testParseListOrNullWithParser() {
		assertEquals(null, parseListOrNull(::TestEntity, parser, null))
		
		val src = JsonArray()
		assertEquals(null, parseListOrNull(::TestEntity, parser, src))
		
		src.add("""{"s":"A","l":"100"}""".decodeJsonObject())
		assertEquals(1, parseListOrNull(::TestEntity, parser, src)?.size)
		
		src.add("""{"s":"A","l":"100"}""".decodeJsonObject())
		assertEquals(2, parseListOrNull(::TestEntity, parser, src)?.size)
		
		// error
		src.add("""{"s":"","l":"100"}""".decodeJsonObject())
		assertEquals(2, parseListOrNull(::TestEntity, parser, src)?.size)
		
	}
	
	@Test(expected = RuntimeException::class)
	fun testNotEmptyOrThrow1() {
		println(notEmptyOrThrow("param1", null))
	}
	
	@Test(expected = RuntimeException::class)
	fun testNotEmptyOrThrow2() {
		println(notEmptyOrThrow("param1", ""))
	}
	
	@Test
	fun testNotEmptyOrThrow3() {
		assertEquals("A", notEmptyOrThrow("param1", "A"))
	}
	
	@Test(expected = RuntimeException::class)
	fun testNotEmptyOrThrow4() {
		println("""{"param1":null}""".decodeJsonObject().stringOrThrow("param1"))
	}
	
	@Test(expected = RuntimeException::class)
	fun testNotEmptyOrThrow5() {
		println("""{"param1":""}""".decodeJsonObject().stringOrThrow("param1"))
	}
	
	@Test
	fun testNotEmptyOrThrow6() {
		assertEquals("A", """{"param1":"A"}""".decodeJsonObject().stringOrThrow("param1"))
	}
}
package jp.juggler.subwaytooter.api

import android.content.Context
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.table.ClientInfo
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.*
import jp.juggler.util.*
import okhttp3.*
import java.util.*
import java.util.concurrent.atomic.AtomicReference

class TootApiClient(
    val context: Context,
    val httpClient: SimpleHttpClient =
        SimpleHttpClientImpl(context, App1.ok_http_client),
    val callback: TootApiCallback
) {
    companion object {

        private val log = LogCategory("TootApiClient")

        private const val DEFAULT_CLIENT_NAME = "SubwayTooter"
        private const val REDIRECT_URL = "subwaytooter://oauth/"

        // 20181225 3=>4 client credentialの取得時にもscopeの取得が必要になった
        // 20190147 4=>5 client id とユーザIDが同じだと同じアクセストークンが返ってくるので複数端末の利用で困る。
        // AUTH_VERSIONが古いclient情報は使わない。また、インポートの対象にしない。
        private const val AUTH_VERSION = 5

        internal const val KEY_CLIENT_CREDENTIAL = "SubwayTooterClientCredential"
        internal const val KEY_CLIENT_SCOPE = "SubwayTooterClientScope"
        private const val KEY_AUTH_VERSION = "SubwayTooterAuthVersion"
        const val KEY_IS_MISSKEY = "isMisskey" // for ClientInfo
        const val KEY_MISSKEY_VERSION = "isMisskey" // for tokenInfo,TootInstance
        const val KEY_MISSKEY_APP_SECRET = "secret"
        const val KEY_API_KEY_MISSKEY = "apiKeyMisskey"
        const val KEY_USER_ID = "userId"

        private const val NO_INFORMATION = "(no information)"

        private val reStartJsonArray = """\A\s*\[""".asciiPattern()
        private val reStartJsonObject = """\A\s*\{""".asciiPattern()

        val DEFAULT_JSON_ERROR_PARSER =
            { json: JsonObject -> json["error"]?.toString() }

        fun getScopeString(ti: TootInstance?) = when {
            // 古いサーバ
            ti?.versionGE(TootInstance.VERSION_2_4_0_rc1) == false -> "read+write+follow"
            // 新しいサーバか、AUTHORIZED_FETCH(3.0.0以降)によりサーバ情報を取得できなかった
            else -> "read+write+follow+push"

            // 過去の試行錯誤かな
            // ti.versionGE(TootInstance.VERSION_2_7_0_rc1) -> "read+write+follow+push+create"
        }

        fun getScopeArrayMisskey(@Suppress("UNUSED_PARAMETER") ti: TootInstance) =
            JsonArray().apply {
                if (ti.versionGE(TootInstance.MISSKEY_VERSION_11)) {
                    // https://github.com/syuilo/misskey/blob/master/src/server/api/kinds.ts
                    arrayOf(
                        "read:account",
                        "write:account",
                        "read:blocks",
                        "write:blocks",
                        "read:drive",
                        "write:drive",
                        "read:favorites",
                        "write:favorites",
                        "read:following",
                        "write:following",
                        "read:messaging",
                        "write:messaging",
                        "read:mutes",
                        "write:mutes",
                        "write:notes",
                        "read:notifications",
                        "write:notifications",
                        "read:reactions",
                        "write:reactions",
                        "write:votes"
                    )
                } else {
                    // https://github.com/syuilo/misskey/issues/2341
                    arrayOf(
                        "account-read",
                        "account-write",
                        "account/read",
                        "account/write",
                        "drive-read",
                        "drive-write",
                        "favorite-read",
                        "favorite-write",
                        "favorites-read",
                        "following-read",
                        "following-write",
                        "messaging-read",
                        "messaging-write",
                        "note-read",
                        "note-write",
                        "notification-read",
                        "notification-write",
                        "reaction-read",
                        "reaction-write",
                        "vote-read",
                        "vote-write"

                    )

                }
                    // APIのエラーを回避するため、重複を排除する
                    .toMutableSet()
                    .forEach { add(it) }
            }

        private fun encodeScopeArray(scope_array: JsonArray?): String? {
            scope_array ?: return null
            val list = scope_array.stringArrayList()
            list.sort()
            return list.joinToString(",")
        }

        private fun compareScopeArray(a: JsonArray, b: JsonArray?): Boolean {
            return encodeScopeArray(a) == encodeScopeArray(b)
        }

        fun formatResponse(
            response: Response,
            caption: String = "?",
            bodyString: String? = null
        ) = TootApiResult(
            response = response,
            caption = caption,
            bodyString = bodyString
        ).apply { parseErrorResponse() }.error ?: "(null)"

        fun simplifyErrorHtml(
            response: Response,
            caption:String = "?",
            bodyString:String =response.body?.string() ?: "",
            jsonErrorParser: (json: JsonObject) -> String? = DEFAULT_JSON_ERROR_PARSER
        ) = TootApiResult(
            response = response,
            caption = caption,
        ).simplifyErrorHtml( bodyString,jsonErrorParser)
    }

    // 認証に関する設定を保存する
    internal val pref = context.pref()

    // インスタンスのホスト名
    var apiHost: Host? = null

    // アカウントがある場合に使用する
    var account: SavedAccount? = null
        set(value) {
            apiHost = value?.apiHost
            field = value
        }

    var currentCallCallback: (Call) -> Unit
        get() = httpClient.onCallCreated
        set(value) {
            httpClient.onCallCreated = value
        }

    @Suppress("unused")
    internal val isApiCancelled: Boolean
        get() = callback.isApiCancelled

    suspend fun publishApiProgress(s: String) {
        callback.publishApiProgress(s)
    }

    suspend fun publishApiProgressRatio(value: Int, max: Int) {
        callback.publishApiProgressRatio(value, max)
    }

    //////////////////////////////////////////////////////////////////////
    // ユーティリティ

    // リクエストをokHttpに渡してレスポンスを取得する
//    internal inline fun sendRequest(
//        result: TootApiResult,
//        progressPath: String? = null,
//        tmpOkhttpClient: OkHttpClient? = null,
//        block: () -> Request
//    ): Boolean {
//        return try {
//            result.response = null
//            result.bodyString = null
//            result.data = null
//
//            val request = block()
//
//            result.requestInfo = "${request.method} ${progressPath ?: request.url.encodedPath}"
//
//            callback.publishApiProgress(
//                context.getString(
//                    R.string.request_api, request.method, progressPath ?: request.url.encodedPath
//                )
//            )
//
//            val response = httpClient.getResponse(request, tmpOkhttpClient = tmpOkhttpClient)
//            result.response = response
//
//            null == result.error
//
//        } catch (ex: Throwable) {
//            result.setError(
//                "${result.caption}: ${
//                    ex.withCaption(
//                        context.resources,
//                        R.string.network_error
//                    )
//                }"
//            )
//            false
//        }
//    }

    // リクエストをokHttpに渡してレスポンスを取得する
    suspend inline fun sendRequest(
        result: TootApiResult,
        progressPath: String? = null,
        tmpOkhttpClient: OkHttpClient? = null,
        block: () -> Request
    ): Boolean {

        result.response = null
        result.bodyString = null
        result.data = null
        val request = block()

        return try {
            result.requestInfo = "${request.method} ${progressPath ?: request.url.encodedPath}"

            callback.publishApiProgress(
                context.getString(
                    R.string.request_api, request.method, progressPath ?: request.url.encodedPath
                )
            )

            val response = httpClient.getResponse(request, tmpOkhttpClient = tmpOkhttpClient)
            result.response = response

            null == result.error

        } catch (ex: Throwable) {
            result.setError(
                "${result.caption}: ${
                    ex.withCaption(
                        context.resources,
                        R.string.network_error
                    )
                }"
            )
            false
        }
    }

    // レスポンスがエラーかボディがカラならエラー状態を設定する
    // 例外を出すかも
    internal suspend fun readBodyString(
        result: TootApiResult,
        progressPath: String? = null,
        jsonErrorParser: (json: JsonObject) -> String? = DEFAULT_JSON_ERROR_PARSER
    ): String? {

        if (isApiCancelled) return null

        val response = result.response!!

        val request = response.request
        publishApiProgress(
            context.getString(
                R.string.reading_api,
                request.method,
                progressPath ?: result.caption
            )
        )

        val bodyString = response.body?.string()
        if (isApiCancelled) return null

        // Misskey の /api/notes/favorites/create は 204(no content)を返す。ボディはカラになる。
        if (bodyString?.isEmpty() != false && response.code in 200 until 300) {
            result.bodyString = ""
            return ""
        }

        if (!response.isSuccessful || bodyString?.isEmpty() != false) {
            result.parseErrorResponse(
                bodyString?.notEmpty() ?: NO_INFORMATION,
                jsonErrorParser
            )
        }

        return if (result.error != null) {
            null
        } else {
            publishApiProgress(context.getString(R.string.parsing_response))
            result.bodyString = bodyString
            bodyString
        }
    }

    // レスポンスがエラーかボディがカラならエラー状態を設定する
    // 例外を出すかも
    private suspend fun readBodyBytes(
        result: TootApiResult,
        progressPath: String? = null,
        jsonErrorParser: (json: JsonObject) -> String? = DEFAULT_JSON_ERROR_PARSER
    ): ByteArray? {

        if (isApiCancelled) return null

        val response = result.response!!

        val request = response.request
        publishApiProgress(
            context.getString(
                R.string.reading_api,
                request.method,
                progressPath ?: result.caption
            )
        )

        val bodyBytes = response.body?.bytes()
        if (isApiCancelled) return null

        if (!response.isSuccessful || bodyBytes?.isEmpty() != false) {
            result.parseErrorResponse(
                bodyBytes?.notEmpty()?.decodeUTF8() ?: NO_INFORMATION,
                jsonErrorParser
            )
        }

        return if (result.error != null) {
            null
        } else {
            result.bodyString = "(binary data)"
            result.data = bodyBytes
            bodyBytes
        }
    }

    private suspend fun parseBytes(
        result: TootApiResult,
        progressPath: String? = null,
        jsonErrorParser: (json: JsonObject) -> String? = DEFAULT_JSON_ERROR_PARSER
    ): TootApiResult? {
        try {
            readBodyBytes(result, progressPath, jsonErrorParser)
                ?: return if (isApiCancelled) null else result
        } catch (ex: Throwable) {
            log.trace(ex)
            result.parseErrorResponse(result.bodyString ?: NO_INFORMATION)
        }
        return result
    }

    internal suspend fun parseString(
        result: TootApiResult,
        progressPath: String? = null,
        jsonErrorParser: (json: JsonObject) -> String? = DEFAULT_JSON_ERROR_PARSER
    ): TootApiResult? {
        try {
            val bodyString = readBodyString(result, progressPath, jsonErrorParser)
                ?: return if (isApiCancelled) null else result

            result.data = bodyString
        } catch (ex: Throwable) {
            log.trace(ex)
            result.parseErrorResponse(result.bodyString ?: NO_INFORMATION)
        }
        return result
    }

    // レスポンスからJSONデータを読む
    internal suspend fun parseJson(
        result: TootApiResult,
        progressPath: String? = null,
        jsonErrorParser: (json: JsonObject) -> String? = DEFAULT_JSON_ERROR_PARSER
    ): TootApiResult? // 引数に指定したresultそのものか、キャンセルされたらnull
    {
        val response = result.response!! // nullにならないはず

        try {
            var bodyString = readBodyString(result, progressPath, jsonErrorParser)
                ?: return if (isApiCancelled) null else result

            if (bodyString.isEmpty()) {

                // 204 no content は 空オブジェクトと解釈する
                result.data = JsonObject()

            } else if (reStartJsonArray.matcher(bodyString).find()) {
                result.data = bodyString.decodeJsonArray()

            } else if (reStartJsonObject.matcher(bodyString).find()) {
                val json = bodyString.decodeJsonObject()
                val error_message = jsonErrorParser(json)
                if (error_message != null) {
                    result.error = error_message
                } else {
                    result.data = json
                }
            } else {
                // HTMLならタグを除去する
                val ct = response.body?.contentType()
                if (ct?.subtype == "html") {
                    val decoded = DecodeOptions().decodeHTML(bodyString).toString()
                        .replace("""[\s　]+""".toRegex(), " ")
                    bodyString = decoded
                }

                val sb = StringBuilder()
                    .append(context.getString(R.string.response_not_json))
                    .append(' ')
                    .append(bodyString)

                if (sb.isNotEmpty()) sb.append(' ')
                sb.append("(HTTP ").append(response.code.toString())

                val message = response.message
                if (message.isNotEmpty()) sb.append(' ').append(message)

                sb.append(")")

                val url = response.request.url.toString()
                if (url.isNotEmpty()) sb.append(' ').append(url)

                result.error = sb.toString()
            }

        } catch (ex: Throwable) {
            log.trace(ex)
            result.parseErrorResponse(result.bodyString ?: NO_INFORMATION)
        }
        return result

    }

    //////////////////////////////////////////////////////////////////////

//    fun request(
//        path: String,
//        request_builder: Request.Builder = Request.Builder()
//    ): TootApiResult? {
//        val result = TootApiResult.makeWithCaption(apiHost?.pretty)
//        if (result.error != null) return result
//
//        val account = this.account // may null
//
//        try {
//            if (!sendRequest(result) {
//
//                    log.d("request: $path")
//
//                    request_builder.url("https://${apiHost?.ascii}$path")
//
//                    val access_token = account?.getAccessToken()
//                    if (access_token?.isNotEmpty() == true) {
//                        request_builder.header("Authorization", "Bearer $access_token")
//                    }
//
//                    request_builder.build()
//
//                }) return result
//
//            return parseJson(result)
//        } finally {
//            val error = result.error
//            if (error != null) log.d("error: $error")
//        }
//    }
//

    suspend fun request(
        path: String,
        request_builder: Request.Builder = Request.Builder(),
        forceAccessToken: String? = null,
    ): TootApiResult? {
        val result = TootApiResult.makeWithCaption(apiHost?.pretty)
        if (result.error != null) return result

        val account = this.account // may null

        try {
            if (!sendRequest(result) {

                    val url = "https://${apiHost?.ascii}$path"

                    request_builder.url(url)

                    (forceAccessToken ?: account?.getAccessToken())
                        ?.notEmpty()?.let { request_builder.header("Authorization", "Bearer $it") }

                    request_builder.build()
                        .also { log.d("request: ${it.method} $url") }

                }) return result

            return parseJson(result)
        } finally {
            val error = result.error
            if (error != null) log.d("error: $error")
        }
    }

    //////////////////////////////////////////////////////////////////////
    // misskey authentication

    private suspend fun getAppInfoMisskey(appId: String?): TootApiResult? {
        appId ?: return TootApiResult("missing app id")
        val result = TootApiResult.makeWithCaption(apiHost?.pretty)
        if (result.error != null) return result
        if (sendRequest(result) {
                JsonObject().apply {
                    put("appId", appId)
                }
                    .toPostRequestBuilder()
                    .url("https://${apiHost?.ascii}/api/app/show")
                    .build()
            }) {
            parseJson(result) ?: return null
            result.jsonObject?.put(KEY_IS_MISSKEY, true)
        }
        return result
    }

    private suspend fun prepareBrowserUrlMisskey(appSecret: String): String? {

        val result = TootApiResult.makeWithCaption(apiHost?.pretty)

        if (result.error != null) {
            context.showToast(false, result.error)
            return null
        }



        if (!sendRequest(result) {
                JsonObject().apply {
                    put("appSecret", appSecret)
                }
                    .toPostRequestBuilder()
                    .url("https://${apiHost?.ascii}/api/auth/session/generate")
                    .build()
            }
        ) {
            val error = result.error
            if (error != null) {
                context.showToast(false, error)
                return null
            }
            return null
        }

        parseJson(result) ?: return null

        val jsonObject = result.jsonObject
        if (jsonObject == null) {
            context.showToast(false, result.error)
            return null
        }
        // {"token":"0ba88e2d-4b7d-4599-8d90-dc341a005637","url":"https://misskey.xyz/auth/0ba88e2d-4b7d-4599-8d90-dc341a005637"}

        // ブラウザで開くURL
        val url = jsonObject.string("url")
        if (url?.isEmpty() != false) {
            context.showToast(false, "missing 'url' in auth session response.")
            return null
        }

        val e = PrefDevice.prefDevice(context)
            .edit()
            .putString(PrefDevice.LAST_AUTH_INSTANCE, apiHost?.ascii)
            .putString(PrefDevice.LAST_AUTH_SECRET, appSecret)

        val account = this.account
        if (account != null) {
            e.putLong(PrefDevice.LAST_AUTH_DB_ID, account.db_id)
        } else {
            e.remove(PrefDevice.LAST_AUTH_DB_ID)
        }

        e.apply()

        return url
    }

    private suspend fun registerClientMisskey(
        scope_array: JsonArray,
        client_name: String
    ): TootApiResult? {
        val result = TootApiResult.makeWithCaption(apiHost?.pretty)
        if (result.error != null) return result
        if (sendRequest(result) {
                JsonObject().apply {
                    put("nameId", "SubwayTooter")
                    put("name", client_name)
                    put("description", "Android app for federated SNS")
                    put("callbackUrl", "subwaytooter://misskey/auth_callback")
                    put("permission", scope_array)
                }
                    .toPostRequestBuilder()
                    .url("https://${apiHost?.ascii}/api/app/create")
                    .build()
            }) {
            parseJson(result) ?: return null
        }
        return result
    }

    private suspend fun authentication1Misskey(
        clientNameArg: String,
        ti: TootInstance
    ): TootApiResult? {
        val result = TootApiResult.makeWithCaption(this.apiHost?.pretty)
        if (result.error != null) return result
        val instance = result.caption // same to instance

        // クライアントIDがアプリ上に保存されているか？
        val client_name = clientNameArg.notEmpty() ?: DEFAULT_CLIENT_NAME
        val client_info = ClientInfo.load(instance, client_name)

        // スコープ一覧を取得する
        val scope_array = getScopeArrayMisskey(ti)

        if (client_info != null
            && AUTH_VERSION == client_info.int(KEY_AUTH_VERSION)
            && client_info.boolean(KEY_IS_MISSKEY) == true
        ) {
            val appSecret = client_info.string(KEY_MISSKEY_APP_SECRET)

            val r2 = getAppInfoMisskey(client_info.string("id"))
            val tmpClientInfo = r2?.jsonObject
            // tmpClientInfo はsecretを含まないので保存してはいけない
            when {
                // アプリが登録済みで
                // クライアント名が一致してて
                // パーミッションが同じ
                tmpClientInfo != null
                    && client_name == tmpClientInfo.string("name")
                    && compareScopeArray(scope_array, tmpClientInfo["permission"].cast())
                    && appSecret?.isNotEmpty() == true -> {
                    // クライアント情報を再利用する
                    result.data = prepareBrowserUrlMisskey(appSecret)
                    return result
                }

                else -> {
                    // XXX appSecretを使ってクライアント情報を削除できるようにするべきだが、該当するAPIが存在しない
                }
            }
        }

        val r2 = registerClientMisskey(scope_array, client_name)
        val jsonObject = r2?.jsonObject ?: return r2

        val appSecret = jsonObject.string(KEY_MISSKEY_APP_SECRET)
        if (appSecret?.isEmpty() != false) {
            context.showToast(true, context.getString(R.string.cant_get_misskey_app_secret))
            return null
        }
        //		{
        //			"createdAt": "2018-08-19T00:43:10.105Z",
        //			"userId": null,
        //			"name": "Via芸",
        //			"nameId": "test1",
        //			"description": "test1",
        //			"permission": [
        //			"account-read",
        //			"account-write",
        //			"note-write",
        //			"reaction-write",
        //			"following-write",
        //			"drive-read",
        //			"drive-write",
        //			"notification-read",
        //			"notification-write"
        //			],
        //			"callbackUrl": "test1://test1/auth_callback",
        //			"id": "5b78bd1ea0db0527f25815c3",
        //			"iconUrl": "https://misskey.xyz/files/app-default.jpg"
        //		}

        // 2018/8/19現在、/api/app/create のレスポンスにsecretが含まれないので認証に使えない
        // https://github.com/syuilo/misskey/issues/2343

        jsonObject[KEY_IS_MISSKEY] = true
        jsonObject[KEY_AUTH_VERSION] = AUTH_VERSION
        ClientInfo.save(instance, client_name, jsonObject.toString())
        result.data = prepareBrowserUrlMisskey(appSecret)

        return result
    }

    // oAuth2認証の続きを行う
    suspend fun authentication2Misskey(
        clientNameArg: String,
        token: String,
        misskeyVersion: Int
    ): TootApiResult? {
        val result = TootApiResult.makeWithCaption(apiHost?.pretty)
        if (result.error != null) return result
        val instance = result.caption // same to instance
        val client_name = clientNameArg.notEmpty() ?: DEFAULT_CLIENT_NAME

        @Suppress("UNUSED_VARIABLE")
        val client_info = ClientInfo.load(instance, client_name)
            ?: return result.setError("missing client id")

        val appSecret = client_info.string(KEY_MISSKEY_APP_SECRET)
        if (appSecret?.isEmpty() != false) {
            return result.setError(context.getString(R.string.cant_get_misskey_app_secret))
        }

        if (!sendRequest(result) {
                JsonObject().apply {
                    put("appSecret", appSecret)
                    put("token", token)
                }
                    .toPostRequestBuilder()
                    .url("https://$instance/api/auth/session/userkey")
                    .build()
            }
        ) {
            return result
        }

        parseJson(result) ?: return null

        val token_info = result.jsonObject ?: return result

        // {"accessToken":"...","user":{…}}

        val access_token = token_info.string("accessToken")
        if (access_token?.isEmpty() != false) {
            return result.setError("missing accessToken in the response.")
        }

        val user = token_info["user"].cast<JsonObject>()
            ?: return result.setError("missing user in the response.")

        token_info.remove("user")

        val apiKey = "$access_token$appSecret".encodeUTF8().digestSHA256().encodeHexLower()

        // ユーザ情報を読めたならtokenInfoを保存する
        EntityId.mayNull(user.string("id"))?.putTo(token_info, KEY_USER_ID)
        token_info[KEY_MISSKEY_VERSION] = misskeyVersion
        token_info[KEY_AUTH_VERSION] = AUTH_VERSION
        token_info[KEY_API_KEY_MISSKEY] = apiKey

        // tokenInfoとユーザ情報の入ったresultを返す
        result.tokenInfo = token_info
        result.data = user
        return result
    }

    //////////////////////////////////////////////////////////////////////

    // クライアントをタンスに登録
    suspend fun registerClient(scope_string: String, clientName: String): TootApiResult? {
        val result = TootApiResult.makeWithCaption(apiHost?.pretty)
        if (result.error != null) return result
        val instance = result.caption // same to instance
        // OAuth2 クライアント登録
        if (!sendRequest(result) {
                ("client_name=" + clientName.encodePercent()
                    + "&redirect_uris=" + REDIRECT_URL.encodePercent()
                    + "&scopes=$scope_string"
                    ).toFormRequestBody().toPost()
                    .url("https://$instance/api/v1/apps")
                    .build()
            }) return result

        return parseJson(result)
    }

    // クライアントアプリの登録を確認するためのトークンを生成する
    // oAuth2 Client Credentials の取得
    // https://github.com/doorkeeper-gem/doorkeeper/wiki/Client-Credentials-flow
    // このトークンはAPIを呼び出すたびに新しく生成される…
    internal suspend fun getClientCredential(client_info: JsonObject): TootApiResult? {
        val result = TootApiResult.makeWithCaption(apiHost?.pretty)
        if (result.error != null) return result

        if (!sendRequest(result) {

                val client_id = client_info.string("client_id")
                    ?: return result.setError("missing client_id")

                val client_secret = client_info.string("client_secret")
                    ?: return result.setError("missing client_secret")

                "grant_type=client_credentials&scope=read+write&client_id=${client_id.encodePercent()}&client_secret=${client_secret.encodePercent()}"
                    .toFormRequestBody().toPost()
                    .url("https://${apiHost?.ascii}/oauth/token")
                    .build()
            }) return result

        val r2 = parseJson(result)
        val jsonObject = r2?.jsonObject ?: return r2

        log.d("getClientCredential: ${jsonObject}")

        val sv = jsonObject.string("access_token")?.notEmpty()
        if (sv != null) {
            result.data = sv
        } else {
            result.data = null
            result.error = "missing client credential."
        }
        return result
    }

    // client_credentialがまだ有効か調べる
    internal suspend fun verifyClientCredential(client_credential: String): TootApiResult? {
        val result = TootApiResult.makeWithCaption(apiHost?.pretty)
        if (result.error != null) return result

        if (!sendRequest(result) {
                Request.Builder()
                    .url("https://${apiHost?.ascii}/api/v1/apps/verify_credentials")
                    .header("Authorization", "Bearer $client_credential")
                    .build()
            }) return result

        return parseJson(result)
    }

    // client_credentialを無効にする
    private suspend fun revokeClientCredential(
        client_info: JsonObject,
        client_credential: String
    ): TootApiResult? {
        val result = TootApiResult.makeWithCaption(apiHost?.pretty)
        if (result.error != null) return result

        val client_id = client_info.string("client_id")
            ?: return result.setError("missing client_id")

        val client_secret = client_info.string("client_secret")
            ?: return result.setError("missing client_secret")

        if (!sendRequest(result) {
                ("token=" + client_credential.encodePercent()
                    + "&client_id=" + client_id.encodePercent()
                    + "&client_secret=" + client_secret.encodePercent()
                    ).toFormRequestBody().toPost()
                    .url("https://${apiHost?.ascii}/oauth/revoke")
                    .build()

            }) return result

        return parseJson(result)
    }

    // 認証ページURLを作る
    internal fun prepareBrowserUrl(scope_string: String, client_info: JsonObject): String? {
        val account = this.account
        val client_id = client_info.string("client_id") ?: return null

        val state = StringBuilder()
            .append((if (account != null) "db:${account.db_id}" else "host:${apiHost?.ascii}"))
            .append(',')
            .append("random:${System.currentTimeMillis()}")
            .toString()

        return ("https://${apiHost?.ascii}/oauth/authorize"
            + "?client_id=" + client_id.encodePercent()
            + "&response_type=code"
            + "&redirect_uri=" + REDIRECT_URL.encodePercent()
            + "&scope=$scope_string"
            + "&scopes=$scope_string"
            + "&state=" + state.encodePercent()
            + "&grant_type=authorization_code"
            + "&approval_prompt=force"
            + "&force_login=true"
            //		+"&access_type=offline"
            )
    }

    private suspend fun prepareClientMastodon(
        clientNameArg: String,
        ti: TootInstance?,
        forceUpdateClient: Boolean = false
    ): TootApiResult? {
        // 前準備
        val result = TootApiResult.makeWithCaption(apiHost?.pretty)
        if (result.error != null) return result
        val instance = result.caption // same to instance

        // クライアントIDがアプリ上に保存されているか？
        val client_name = clientNameArg.notEmpty() ?: DEFAULT_CLIENT_NAME
        var client_info = ClientInfo.load(instance, client_name)

        // スコープ一覧を取得する
        val scope_string = getScopeString(ti)

        when {
            AUTH_VERSION != client_info?.int(KEY_AUTH_VERSION) -> {
                // 古いクライアント情報は使わない。削除もしない。
            }

            client_info.boolean(KEY_IS_MISSKEY) == true -> {
                // Misskeyにはclient情報をまだ利用できるかどうか調べる手段がないので、再利用しない
            }

            else -> {
                val old_scope = client_info.string(KEY_CLIENT_SCOPE)

                // client_credential をまだ取得していないなら取得する
                var client_credential = client_info.string(KEY_CLIENT_CREDENTIAL)
                if (client_credential?.isEmpty() != false) {
                    val resultSub = getClientCredential(client_info)
                    client_credential = resultSub?.string
                    if (client_credential?.isNotEmpty() == true) {
                        try {
                            client_info[KEY_CLIENT_CREDENTIAL] = client_credential
                            ClientInfo.save(instance, client_name, client_info.toString())
                        } catch (ignored: JsonException) {
                        }
                    }
                }

                // client_credential があるならcredentialがまだ使えるか確認する
                if (client_credential?.isNotEmpty() == true) {
                    val resultSub = verifyClientCredential(client_credential)
                    val currentCC = resultSub?.jsonObject
                    if (currentCC != null) {
                        if (old_scope != scope_string || forceUpdateClient) {
                            // マストドン2.4でスコープが追加された
                            // 取得時のスコープ指定がマッチしない(もしくは記録されていない)ならクライアント情報を再利用してはいけない
                            ClientInfo.delete(instance, client_name)

                            // client credential をタンスから消去する
                            revokeClientCredential(client_info, client_credential)

                            // XXX クライアントアプリ情報そのものはまだサーバに残っているが、明示的に消す方法は現状存在しない
                        } else {
                            // クライアント情報を再利用する
                            result.data = client_info
                            return result
                        }
                    }
                }
            }
        }

        val r2 = registerClient(scope_string, client_name)
        client_info = r2?.jsonObject ?: return r2

        // {"id":999,"redirect_uri":"urn:ietf:wg:oauth:2.0:oob","client_id":"******","client_secret":"******"}
        client_info[KEY_AUTH_VERSION] = AUTH_VERSION
        client_info[KEY_CLIENT_SCOPE] = scope_string

        // client_credential をまだ取得していないなら取得する
        var client_credential = client_info.string(KEY_CLIENT_CREDENTIAL)
        if (client_credential?.isEmpty() != false) {
            val resultSub = getClientCredential(client_info)
            client_credential = resultSub?.string
            if (client_credential?.isEmpty() != false) return resultSub

            client_info[KEY_CLIENT_CREDENTIAL] = client_credential
        }

        try {
            ClientInfo.save(instance, client_name, client_info.toString())
        } catch (ignored: JsonException) {
        }
        result.data = client_info
        return result
    }

    private suspend fun authentication1Mastodon(
        clientNameArg: String,
        ti: TootInstance?,
        forceUpdateClient: Boolean = false
    ): TootApiResult? {

        if (ti?.instanceType == InstanceType.Pixelfed) {
            return TootApiResult("currently Pixelfed instance is not supported.")
        }

        return prepareClientMastodon(clientNameArg, ti, forceUpdateClient)?.also { result ->
            val client_info = result.jsonObject
            if (client_info != null) {
                result.data = prepareBrowserUrl(getScopeString(ti), client_info)
            }
        }
    }

    // クライアントを登録してブラウザで開くURLを生成する
    suspend fun authentication1(
        clientNameArg: String,
        forceUpdateClient: Boolean = false
    ): TootApiResult? {

        val (ti, ri) = TootInstance.get(this)
        log.i("authentication1: instance info version=${ti?.version} misskeyVersion=${ti?.misskeyVersion} responseCode=${ri?.response?.code}")
        return if (ti == null) when (ri?.response?.code) {
            // https://github.com/tateisu/SubwayTooter/issues/155
            // Mastodon's WHITELIST_MODE
            401 -> authentication1Mastodon(clientNameArg, null, forceUpdateClient)
            else -> ri
        } else when {
            ti.misskeyVersion > 0 -> authentication1Misskey(clientNameArg, ti)
            else -> authentication1Mastodon(clientNameArg, ti, forceUpdateClient)
        }
    }

    // oAuth2認証の続きを行う
    suspend fun authentication2Mastodon(
        clientNameArg: String,
        code: String,
        outAccessToken: AtomicReference<String>
    ): TootApiResult? {
        val result = TootApiResult.makeWithCaption(apiHost?.pretty)
        if (result.error != null) return result

        val instance = result.caption // same to instance
        val client_name = if (clientNameArg.isNotEmpty()) clientNameArg else DEFAULT_CLIENT_NAME
        val client_info =
            ClientInfo.load(instance, client_name) ?: return result.setError("missing client id")

        if (!sendRequest(result) {

                val scope_string = client_info.string(KEY_CLIENT_SCOPE)
                val client_id = client_info.string("client_id")
                val client_secret = client_info.string("client_secret")
                if (client_id == null) return result.setError("missing client_id ")
                if (client_secret == null) return result.setError("missing client_secret")

                val post_content = ("grant_type=authorization_code"
                    + "&code=" + code.encodePercent()
                    + "&client_id=" + client_id.encodePercent()
                    + "&redirect_uri=" + REDIRECT_URL.encodePercent()
                    + "&client_secret=" + client_secret.encodePercent()
                    + "&scope=$scope_string"
                    + "&scopes=$scope_string")

                post_content.toFormRequestBody().toPost()
                    .url("https://$instance/oauth/token")
                    .build()

            }) return result

        val r2 = parseJson(result)
        val token_info = r2?.jsonObject ?: return r2

        // {"access_token":"******","token_type":"bearer","scope":"read","created_at":1492334641}
        val access_token = token_info.string("access_token")
        if (access_token?.isEmpty() != false) {
            return result.setError("missing access_token in the response.")
        }
        outAccessToken.set(access_token)
        return getUserCredential(access_token, token_info)
    }

    // アクセストークン手動入力でアカウントを更新する場合、アカウントの情報を取得する
    suspend fun getUserCredential(
        access_token: String, tokenInfo: JsonObject = JsonObject(), misskeyVersion: Int = 0
    ): TootApiResult? {
        if (misskeyVersion > 0) {
            val result = TootApiResult.makeWithCaption(apiHost?.pretty)
            if (result.error != null) return result

            // 認証されたアカウントのユーザ情報を取得する
            if (!sendRequest(result) {
                    JsonObject().apply {
                        put("i", access_token)
                    }
                        .toPostRequestBuilder()
                        .url("https://${apiHost?.ascii}/api/i")
                        .build()
                }) return result

            val r2 = parseJson(result)
            if (r2?.jsonObject != null) {
                // ユーザ情報を読めたならtokenInfoを保存する
                tokenInfo[KEY_AUTH_VERSION] = AUTH_VERSION
                tokenInfo[KEY_API_KEY_MISSKEY] = access_token
                tokenInfo[KEY_MISSKEY_VERSION] = misskeyVersion
                result.tokenInfo = tokenInfo
            }
            return r2

        } else {
            val result = TootApiResult.makeWithCaption(apiHost?.pretty)
            if (result.error != null) return result

            // 認証されたアカウントのユーザ情報を取得する
            if (!sendRequest(result) {
                    Request.Builder()
                        .url("https://${apiHost?.ascii}/api/v1/accounts/verify_credentials")
                        .header("Authorization", "Bearer $access_token")
                        .build()
                }) return result

            val r2 = parseJson(result)
            if (r2?.jsonObject != null) {
                // ユーザ情報を読めたならtokenInfoを保存する
                tokenInfo[KEY_AUTH_VERSION] = AUTH_VERSION
                tokenInfo["access_token"] = access_token
                result.tokenInfo = tokenInfo
            }
            return r2

        }

    }

    suspend fun createUser1(clientNameArg: String): TootApiResult? {

        val (ti, ri) = TootInstance.get(this)
        ti ?: return ri

        return when (ti.instanceType) {
            InstanceType.Misskey ->
                TootApiResult("Misskey has no API to create new account")
            InstanceType.Pleroma ->
                TootApiResult("Pleroma has no API to create new account")
            InstanceType.Pixelfed ->
                TootApiResult("Pixelfed has no API to create new account")
            else ->
                prepareClientMastodon(clientNameArg, ti)
            // result.JsonObject に credentialつきのclient_info を格納して返す
        }
    }

    // ユーザ名入力の後に呼ばれる
    suspend fun createUser2Mastodon(
        client_info: JsonObject,
        username: String,
        email: String,
        password: String,
        agreement: Boolean,
        reason: String?
    ): TootApiResult? {

        val result = TootApiResult.makeWithCaption(apiHost?.pretty)
        if (result.error != null) return result

        log.d("createUser2Mastodon: client is : ${client_info}")

        val client_credential = client_info.string(KEY_CLIENT_CREDENTIAL)
            ?: return result.setError("createUser2Mastodon(): missing client credential")

        if (!sendRequest(result) {

                val params = ArrayList<String>().apply {
                    add("username=${username.encodePercent()}")
                    add("email=${email.encodePercent()}")
                    add("password=${password.encodePercent()}")
                    add("agreement=${agreement}")
                    if (reason?.isNotEmpty() == true) add("reason=${reason.encodePercent()}")
                }

                params
                    .joinToString("&").toFormRequestBody().toPost()
                    .url("https://${apiHost?.ascii}/api/v1/accounts")
                    .header("Authorization", "Bearer ${client_credential}")
                    .build()

            }) return result

        return parseJson(result)
    }


    ////////////////////////////////////////////////////////////////////////
    // JSONデータ以外を扱うリクエスト

    suspend fun http(req: Request): TootApiResult {
        val result = TootApiResult.makeWithCaption(req.url.host)
        if (result.error != null) return result
        sendRequest(result, progressPath = null) { req }
        return result
    }

    //	fun requestJson(req : Request) : TootApiResult? {
    //		val result = TootApiResult.makeWithCaption(req.url().host())
    //		if(result.error != null) return result
    //		if(sendRequest(result, progressPath = null) { req }) {
    //			decodeJsonValue(result)
    //		}
    //		return result
    //	}

    // 疑似アカウントでステータスURLからステータスIDを取得するためにHTMLを取得する
    suspend fun getHttp(url: String): TootApiResult? {
        val result = http(Request.Builder().url(url).build())
        return if (result.error != null) result else parseString(result)
    }

    suspend fun getHttpBytes(url: String): Pair<TootApiResult?, ByteArray?> {
        val result = TootApiResult.makeWithCaption(url)
        if (result.error != null) return Pair(result, null)

        if (!sendRequest(result, progressPath = url) {
                Request.Builder().url(url).build()
            }) {
            return Pair(result, null)
        }
        val r2 = parseBytes(result)
        return Pair(r2, r2?.data as? ByteArray)
    }

    suspend fun webSocket(
        path: String,
        ws_listener: WebSocketListener
    ): Pair<TootApiResult?, WebSocket?> {
        var ws: WebSocket? = null
        val result = TootApiResult.makeWithCaption(apiHost?.pretty)
        if (result.error != null) return Pair(result, null)
        val account = this.account ?: return Pair(TootApiResult("account is null"), null)
        try {
            var url = "wss://${apiHost?.ascii}$path"

            val request_builder = Request.Builder()

            if (account.isMisskey) {
                val accessToken = account.misskeyApiToken
                if (accessToken?.isNotEmpty() == true) {
                    val delm = if (-1 != url.indexOf('?')) '&' else '?'
                    url = "$url${delm}i=${accessToken.encodePercent()}"
                }
            } else {
                val access_token = account.getAccessToken()
                if (access_token?.isNotEmpty() == true) {
                    val delm = if (-1 != url.indexOf('?')) '&' else '?'
                    url = "$url${delm}access_token=${access_token.encodePercent()}"
                }
            }

            val request = request_builder.url(url).build()
            publishApiProgress(context.getString(R.string.request_api, request.method, path))
            ws = httpClient.getWebSocket(request, ws_listener)
            if (isApiCancelled) {
                ws.cancel()
                return Pair(null, null)
            }
        } catch (ex: Throwable) {
            log.trace(ex)
            result.error =
                "${result.caption}: ${ex.withCaption(context.resources, R.string.network_error)}"
        }
        return Pair(result, ws)

    }

}

// query: query_string after ? ( ? itself is excluded )
suspend fun TootApiClient.requestMastodonSearch(
    parser: TootParser,
    query: String
): Pair<TootApiResult?, TootResults?> {

    var searchApiVersion = 2
    var apiResult = request("/api/v2/search?$query")
        ?: return Pair(null, null)

    if ((apiResult.response?.code ?: 0) in 400 until 500) {
        searchApiVersion = 1
        apiResult = request("/api/v1/search?$query")
            ?: return Pair(null, null)

    }

    val searchResult = parser.results(apiResult.jsonObject)
    searchResult?.searchApiVersion = searchApiVersion

    return Pair(apiResult, searchResult)
}

// result.data に TootAccountRefを格納して返す。もしくはエラーかキャンセル
suspend fun TootApiClient.syncAccountByUrl(
    accessInfo: SavedAccount,
    who_url: String
): Pair<TootApiResult?, TootAccountRef?> {

    // misskey由来のアカウントURLは https://host/@user@instance などがある
    val m = TootAccount.reAccountUrl.matcher(who_url)
    if (m.find()) {
        // val host = m.group(1)
        val user = m.groupEx(2)!!.decodePercent()
        val instance = m.groupEx(3)?.decodePercent()
        if (instance?.isNotEmpty() == true) {
            return this.syncAccountByUrl(accessInfo, "https://$instance/@$user")
        }
    }

    val parser = TootParser(context, accessInfo)

    return if (accessInfo.isMisskey) {

        val acct = TootAccount.getAcctFromUrl(who_url)
            ?: return Pair(
                TootApiResult(context.getString(R.string.user_id_conversion_failed)),
                null
            )

        var ar: TootAccountRef? = null
        val result = request(
            "/api/users/show",
            accessInfo.putMisskeyApiToken().apply {
                put("username", acct.username)
                acct.host?.let { put("host", it.ascii) }
            }.toPostRequestBuilder()
        )
            ?.apply {
                ar = TootAccountRef.mayNull(parser, parser.account(jsonObject))
                if (ar == null && error == null) {
                    setError(context.getString(R.string.user_id_conversion_failed))
                }
            }
        Pair(result, ar)
    } else {
        val (apiResult, searchResult) = requestMastodonSearch(
            parser,
            "q=${who_url.encodePercent()}&resolve=true"
        )
        val ar = searchResult?.accounts?.firstOrNull()
        if (apiResult != null && apiResult.error == null && ar == null) {
            apiResult.setError(context.getString(R.string.user_id_conversion_failed))
        }
        Pair(apiResult, ar)
    }
}

suspend fun TootApiClient.syncAccountByAcct(
    accessInfo: SavedAccount,
    acctArg: String
): Pair<TootApiResult?, TootAccountRef?> = syncAccountByAcct(accessInfo, Acct.parse(acctArg))

suspend fun TootApiClient.syncAccountByAcct(
    accessInfo: SavedAccount,
    acct: Acct
): Pair<TootApiResult?, TootAccountRef?> {

    val parser = TootParser(context, accessInfo)
    return if (accessInfo.isMisskey) {
        var ar: TootAccountRef? = null
        val result = request(
            "/api/users/show",
            accessInfo.putMisskeyApiToken()
                .apply {
                    if (acct.isValid) put("username", acct.username)
                    if (acct.host != null) put("host", acct.host.ascii)
                }
                .toPostRequestBuilder()
        )
            ?.apply {
                ar = TootAccountRef.mayNull(parser, parser.account(jsonObject))
                if (ar == null && error == null) {
                    setError(context.getString(R.string.user_id_conversion_failed))
                }
            }
        Pair(result, ar)
    } else {
        val (apiResult, searchResult) = requestMastodonSearch(
            parser,
            "q=${acct.ascii.encodePercent()}&resolve=true"
        )
        val ar = searchResult?.accounts?.firstOrNull()
        if (apiResult != null && apiResult.error == null && ar == null) {
            apiResult.setError(context.getString(R.string.user_id_conversion_failed))
        }
        Pair(apiResult, ar)
    }

}

suspend fun TootApiClient.syncStatus(
    accessInfo: SavedAccount,
    urlArg: String
): Pair<TootApiResult?, TootStatus?> {

    var url = urlArg

    // misskey の投稿URLは外部タンスの投稿を複製したものの可能性がある
    // これを投稿元タンスのURLに変換しないと、投稿の同期には使えない
    val m = TootStatus.reStatusPageMisskey.matcher(urlArg)
    if (m.find()) {
        val host = Host.parse(m.groupEx(1)!!)
        val noteId = m.groupEx(2)

        TootApiClient(context, callback = callback)
            .apply { apiHost = host }
            .request(
                "/api/notes/show",
                JsonObject().apply {
                    put("noteId", noteId)
                }
                    .toPostRequestBuilder()
            )
            ?.also { result ->
                TootParser(
                    context,
                    linkHelper = LinkHelper.create(host, misskeyVersion = 10),
                    serviceType = ServiceType.MISSKEY
                )
                    .status(result.jsonObject)
                    ?.apply {
                        if (accessInfo.matchHost(host)) {
                            return Pair(result, this)
                        }
                        uri.letNotEmpty { url = it }
                    }

            }
            ?: return Pair(null, null) // cancelled.
    }

    // 使いたいタンス上の投稿IDを取得する
    val parser = TootParser(context, accessInfo)
    return if (accessInfo.isMisskey) {
        var targetStatus: TootStatus? = null
        val result = request(
            "/api/ap/show",
            accessInfo.putMisskeyApiToken().apply {
                put("uri", url)
            }
                .toPostRequestBuilder()
        )
            ?.apply {
                targetStatus = parser.parseMisskeyApShow(jsonObject) as? TootStatus
                if (targetStatus == null && error == null) {
                    setError(context.getString(R.string.cant_sync_toot))
                }
            }
        Pair(result, targetStatus)
    } else {
        val (apiResult, searchResult) = requestMastodonSearch(
            parser,
            "q=${url.encodePercent()}&resolve=true"
        )
        val targetStatus = searchResult?.statuses?.firstOrNull()
        if (apiResult != null && apiResult.error == null && targetStatus == null) {
            apiResult.setError(context.getString(R.string.cant_sync_toot))
        }
        Pair(apiResult, targetStatus)
    }

}

suspend fun TootApiClient.syncStatus(
    accessInfo: SavedAccount,
    statusRemote: TootStatus
): Pair<TootApiResult?, TootStatus?> {

    // URL->URIの順に試す

    val uriList = ArrayList<String>(2)

    statusRemote.url.letNotEmpty {
        when {
            it.contains("/notes/") -> {
                // Misskeyタンスから読んだマストドンの投稿はurlがmisskeyタンス上のものになる
                // ActivityPub object id としては不適切なので使わない
            }

            else -> uriList.add(it)
        }
    }

    statusRemote.uri.letNotEmpty {
        // uri の方は↑の問題はない
        uriList.add(it)
    }

    if (accessInfo.isMisskey && uriList.firstOrNull()?.contains("@") == true) {
        // https://github.com/syuilo/misskey/pull/2832
        // @user を含むuri はMisskeyだと少し効率が悪いそうなので順序を入れ替える
        uriList.reverse()
    }

    for (uri in uriList) {
        val pair = syncStatus(accessInfo, uri)
        if (pair.second != null || pair.first == null) {
            return pair
        }
    }

    return Pair(TootApiResult("can't resolve status URL/URI."), null)
}

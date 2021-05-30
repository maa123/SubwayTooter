package jp.juggler.subwaytooter.action

import android.app.Dialog
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import jp.juggler.subwaytooter.*
import jp.juggler.subwaytooter.api.*
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.dialog.*
import jp.juggler.subwaytooter.notification.PollingWorker
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.util.LinkHelper
import jp.juggler.subwaytooter.util.openBrowser
import jp.juggler.util.*
import kotlinx.coroutines.*
import ru.gildor.coroutines.okhttp.await

private val log = LogCategory("Action_Account")

private val mailRegex =
    """\A[a-z0-9_+&*-]+(?:\.[a-z0-9_+&*-]+)*@(?:[a-z0-9-]+\.)+[a-z]{2,12}\z""".toRegex(
        RegexOption.IGNORE_CASE
    )


private fun ActMain.accountCreate(
    apiHost: Host,
    client_info: JsonObject,
    dialog_host: Dialog
) {
    val activity = this
    DlgCreateAccount(
        activity,
        apiHost
    ) { dialog_create, username, email, password, agreement, reason ->
        // dialog引数が二つあるのに注意
        launchMain {
            var resultTootAccount: TootAccount? = null
            var resultApDomain: Host? = null
            runApiTask(apiHost) { client ->
                val r1 = client.createUser2Mastodon(
                    client_info,
                    username,
                    email,
                    password,
                    agreement,
                    reason
                )
                val tokenJson = r1?.jsonObject ?: return@runApiTask r1

                val misskeyVersion = TootInstance.parseMisskeyVersion(tokenJson)
                val parser = TootParser(
                    activity,
                    linkHelper = LinkHelper.create(apiHost, misskeyVersion = misskeyVersion)
                )

                // ここだけMastodon専用
                val access_token = tokenJson.string("access_token")
                    ?: return@runApiTask TootApiResult("can't get user access token")

                client.apiHost = apiHost
                val (ti, ri) = TootInstance.getEx(client, forceAccessToken = access_token)
                ti ?: return@runApiTask ri

                resultApDomain = ti.uri?.let { Host.parse(it) }

                client.getUserCredential(access_token, misskeyVersion = misskeyVersion)?.let { r2 ->
                    parser.account(r2.jsonObject)?.let {
                        resultTootAccount = it
                        return@runApiTask r2
                    }
                }

                val jsonObject = jsonObject {
                    put("id", EntityId.CONFIRMING.toString())
                    put("username", username)
                    put("acct", username)
                    put("url", "https://$apiHost/@$username")
                }

                resultTootAccount = parser.account(jsonObject)
                r1.data = jsonObject
                r1.tokenInfo = tokenJson
                r1
            }?.let { result ->
                val sa: SavedAccount? = null
                if (activity.afterAccountVerify(result, resultTootAccount, sa, apiHost, resultApDomain)) {
                    dialog_host.dismissSafe()
                    dialog_create.dismissSafe()
                }
            }
        }
    }.show()
}

// アカウントの追加
fun ActMain.accountAdd() {
    val activity = this
    LoginForm.showLoginForm(this, null) { dialog, instance, action ->
        launchMain {
            val result = runApiTask(instance) { client ->
                when (action) {

                    LoginForm.Action.Existing ->
                        client.authentication1(Pref.spClientName(pref))

                    LoginForm.Action.Create ->
                        client.createUser1(Pref.spClientName(pref))

                    LoginForm.Action.Pseudo, LoginForm.Action.Token -> {
                        val (ti, ri) = TootInstance.get(client)
                        if (ti != null) ri?.data = ti
                        ri
                    }
                }
            } ?: return@launchMain // cancelled.

            val data = result.data
            if (result.error == null && data != null) {
                when (action) {
                    LoginForm.Action.Existing -> if (data is String) {
                        // ブラウザ用URLが生成された
                        openBrowser(data.toUri())
                        dialog.dismissSafe()
                        return@launchMain
                    }

                    LoginForm.Action.Create -> if (data is JsonObject) {
                        // インスタンスを確認できた
                        accountCreate(instance, data, dialog)
                        return@launchMain
                    }

                    LoginForm.Action.Pseudo -> if (data is TootInstance) {
                        addPseudoAccount(instance, data)?.let { a ->
                            showToast(false, R.string.server_confirmed)
                            val pos = activity.app_state.columnCount
                            addColumn(pos, a, ColumnType.LOCAL)
                            dialog.dismissSafe()
                        }
                    }

                    LoginForm.Action.Token -> if (data is TootInstance) {
                        DlgTextInput.show(
                            activity,
                            getString(R.string.access_token_or_api_token),
                            null,
                            callback = object : DlgTextInput.Callback {

                                @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
                                override fun onOK(
                                    dialog_token: Dialog,
                                    text: String
                                ) {

                                    // dialog引数が二つあるのに注意
                                    activity.checkAccessToken(
                                        dialog,
                                        dialog_token,
                                        instance,
                                        text,
                                        null
                                    )

                                }

                                override fun onEmptyError() {
                                    activity.showToast(true, R.string.token_not_specified)
                                }
                            }
                        )
                        return@launchMain
                    }
                }
            }

            val errorText = result.error ?: "(no error information)"
            if (errorText.contains("SSLHandshakeException")
                && (Build.VERSION.RELEASE.startsWith("7.0")
                    || Build.VERSION.RELEASE.startsWith("7.1")
                    && !Build.VERSION.RELEASE.startsWith("7.1.")
                    )
            ) {
                AlertDialog.Builder(activity)
                    .setMessage(errorText + "\n\n" + activity.getString(R.string.ssl_bug_7_0))
                    .setNeutralButton(R.string.close, null)
                    .show()
            } else {
                activity.showToast(true, "$errorText ${result.requestInfo}".trim())
            }
        }
    }
}

fun AppCompatActivity.accountRemove(account: SavedAccount) {
    // if account is default account of tablet mode,
    // reset default.
    val pref = pref()
    if (account.db_id == Pref.lpTabletTootDefaultAccount(pref)) {
        pref.edit().put(Pref.lpTabletTootDefaultAccount, -1L).apply()
    }

    account.delete()
    appServerUnregister(applicationContext, account)
}

private fun appServerUnregister(context: Context, account: SavedAccount) {
    launchIO {
        try {
            val install_id = PrefDevice.prefDevice(context)
                .getString(PrefDevice.KEY_INSTALL_ID, null)
            if (install_id?.isEmpty() != false)
                error("missing install_id")

            val tag = account.notification_tag
            if (tag?.isEmpty() != false)
                error("missing notification_tag")

            val call = App1.ok_http_client.newCall(
                ("instance_url=" + "https://${account.apiHost.ascii}".encodePercent()
                    + "&app_id=" + context.packageName.encodePercent()
                    + "&tag=" + tag
                    )
                    .toFormRequestBody()
                    .toPost()
                    .url(PollingWorker.APP_SERVER + "/unregister")
                    .build()
            )

            val response = call.await()

            log.e("appServerUnregister: %s", response)
        } catch (ex: Throwable) {
            log.trace(ex, "appServerUnregister failed.")
        }
    }
}

// アカウント設定
fun ActMain.accountOpenSetting() {
    launchMain {
        pickAccount(
            bAllowPseudo = true,
            bAuto = true,
            message = getString(R.string.account_picker_open_setting)
        )?.let {
            arAccountSetting.launch(ActAccountSetting.createIntent(this@accountOpenSetting, it))
        }
    }
}


fun ActMain.accountResendConfirmMail(accessInfo: SavedAccount) {
    DlgConfirmMail(
        this,
        accessInfo
    ) { email ->
        launchMain {
            runApiTask(accessInfo) { client ->
                email?.let {
                    if (!mailRegex.matches(it))
                        return@runApiTask TootApiResult("email address is not valid.")
                }

                client.request(
                    "/api/v1/emails/confirmations",
                    ArrayList<String>().apply {
                        if (email != null) add("email=${email.encodePercent()}")
                    }.joinToString("&").toFormRequestBody().toPost()
                )
            }?.let { result ->
                when (val error = result.error) {
                    null -> showToast(true, R.string.resend_confirm_mail_requested)
                    else -> showToast(true, error)
                }
            }
        }
    }.show()
}

//
fun accountListReorder(
    src: List<SavedAccount>,
    pickupHost: Host?,
    filter: (SavedAccount) -> Boolean = { true }
): MutableList<SavedAccount> {
    val list_same_host = java.util.ArrayList<SavedAccount>()
    val list_other_host = java.util.ArrayList<SavedAccount>()
    for (a in src) {
        if (!filter(a)) continue
        when (pickupHost) {
            null, a.apDomain, a.apiHost -> list_same_host
            else -> list_other_host
        }.add(a)
    }
    SavedAccount.sort(list_same_host)
    SavedAccount.sort(list_other_host)
    list_same_host.addAll(list_other_host)
    return list_same_host
}

// 疑似アカ以外のアカウントのリスト
fun Context.accountListNonPseudo(
    pickupHost: Host?
) = accountListReorder(
    SavedAccount.loadAccountList(this),
    pickupHost
) { !it.isPseudo }

// 条件でフィルタする。サーバ情報を読む場合がある。
suspend fun Context.accountListWithFilter(
    pickupHost: Host?,
    check: suspend (TootApiClient, SavedAccount) -> Boolean
): MutableList<SavedAccount>? {
    var resultList: MutableList<SavedAccount>? = null
    runApiTask { client ->
        coroutineScope {
            resultList = SavedAccount.loadAccountList(this@accountListWithFilter)
                .map {
                    async {
                        try {
                            if (check(client, it)) it else null
                        } catch (ex: Throwable) {
                            log.trace(ex, "accountListWithFilter failed.")
                            null
                        }
                    }
                }
                .mapNotNull { it.await() }
                .let { accountListReorder(it, pickupHost) }
        }
        if (client.isApiCancelled) null else TootApiResult()
    }
    return resultList
}

suspend fun ActMain.accountListCanQuote(pickupHost: Host? = null) =
    accountListWithFilter(pickupHost) { client, a ->
        when {
            client.isApiCancelled -> false
            a.isPseudo -> false
            a.isMisskey -> true
            else -> {
                val (ti, ri) = TootInstance.getEx(client.copy(), account = a)
                if (ti == null) {
                    ri?.error?.let { log.w(it) }
                    false
                } else InstanceCapability.quote(ti)
            }
        }
    }

suspend fun ActMain.accountListCanReaction(pickupHost: Host? = null) =
    accountListWithFilter(pickupHost) { client, a ->
        when {
            client.isApiCancelled -> false
            a.isPseudo -> false
            a.isMisskey -> true
            else -> {
                val (ti, ri) = TootInstance.getEx(client.copy(), account = a)
                if (ti == null) {
                    ri?.error?.let { log.w(it) }
                    false
                } else InstanceCapability.emojiReaction(a, ti)
            }
        }
    }

suspend fun ActMain.accountListCanSeeMyReactions(pickupHost: Host? = null) =
    accountListWithFilter(pickupHost) { client, a ->
        when {
            client.isApiCancelled -> false
            a.isPseudo -> false
            else -> {
                val (ti, ri) = TootInstance.getEx(client.copy(), account = a)
                if (ti == null) {
                    ri?.error?.let { log.w(it) }
                    false
                } else InstanceCapability.listMyReactions(a, ti)
            }
        }
    }

// アクセストークンを手動で入力した場合
fun ActMain.checkAccessToken(
    dialog_host: Dialog?,
    dialog_token: Dialog?,
    apiHost: Host,
    access_token: String,
    sa: SavedAccount?
) {
    launchMain {
        var resultAccount: TootAccount? = null
        var resultApDomain: Host? = null

        runApiTask(apiHost) { client ->
            val (ti, ri) = TootInstance.getEx(client, forceAccessToken = access_token)
            ti ?: return@runApiTask ri

            val apDomain = ti.uri?.let { Host.parse(it) }
                ?: return@runApiTask TootApiResult("missing uri in Instance Information")

            val misskeyVersion = ti.misskeyVersion

            client.getUserCredential(access_token, misskeyVersion = misskeyVersion)
                ?.also { result ->
                    resultApDomain = apDomain
                    resultAccount = TootParser(
                        this,
                        LinkHelper.create(
                            apiHostArg = apiHost,
                            apDomainArg = apDomain,
                            misskeyVersion = misskeyVersion
                        )
                    ).account(result.jsonObject)
                }
        }?.let { result ->
            if (afterAccountVerify(result, resultAccount, sa, apiHost, resultApDomain)) {
                dialog_host?.dismissSafe()
                dialog_token?.dismissSafe()
            }
        }
    }
}

// アクセストークンの手動入力(更新)
fun ActMain.checkAccessToken2(db_id: Long) {

    val sa = SavedAccount.loadAccount(this, db_id) ?: return

    DlgTextInput.show(
        this,
        getString(R.string.access_token_or_api_token),
        null,
        callback = object : DlgTextInput.Callback {
            override fun onOK(dialog: Dialog, text: String) {
                checkAccessToken(null, dialog, sa.apiHost, text, sa)
            }

            override fun onEmptyError() {
                showToast(true, R.string.token_not_specified)
            }
        })
}

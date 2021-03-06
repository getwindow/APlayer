package remix.myplayer.misc.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.text.TextUtils
import remix.myplayer.App
import remix.myplayer.R
import remix.myplayer.bean.github.Release
import remix.myplayer.request.network.HttpClient
import remix.myplayer.request.network.RxUtil
import remix.myplayer.util.LogUtil
import remix.myplayer.util.SPUtil
import remix.myplayer.util.Util

object UpdateAgent {
    private const val TAG = "UpdateAgent"

    @JvmStatic
    var listener: Listener? = null

    @JvmStatic
    var forceCheck = false

    @JvmStatic
    fun check(context: Context) {
        if (listener == null)
            return
        HttpClient.getGithubApiservice().getLatestRelease("rRemix", "APlayer")
                .compose(RxUtil.applyScheduler())
                .doFinally {
                    listener = null
                }
                .subscribe({
                    val release = it
                    if (release == null || release.assets == null || release.assets.size == 0) {
                        listener?.onUpdateReturned(UpdateStatus.No, context.getString(R.string.no_update), null)
                        return@subscribe
                    }
                    //比较版本号
                    val versionCode = getOnlineVersionCode(release)
                    if (versionCode <= getLocalVersionCode()) {
                        listener?.onUpdateReturned(UpdateStatus.No, context.getString(R.string.no_update), null)
                        //删除以前的安装包
                        val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                        if (downloadDir.exists() && downloadDir.listFiles() != null && downloadDir.listFiles().isNotEmpty()) {
                            Util.deleteFilesByDirectory(downloadDir)
                        }
                        return@subscribe
                    }
                    //是否忽略了该版本的更新
                    if (!forceCheck && SPUtil.getValue(context, SPUtil.UPDATE_KEY.NAME, versionCode.toString(), false)) {
                        listener?.onUpdateReturned(UpdateStatus.IGNORED, context.getString(R.string.update_ignore), release)
                        return@subscribe
                    }
                    //路径不合法
                    if (release.assets[0].size < 0) {
                        listener?.onUpdateReturned(UpdateStatus.ErrorSizeFormat, "Size为空", release)
                        return@subscribe
                    }
                    //文件大小不合法
                    if (TextUtils.isEmpty(release.assets[0].browser_download_url)) {
                        listener?.onUpdateReturned(UpdateStatus.ErrorSizeFormat, "下载地址为空", release)
                        return@subscribe
                    }
                    //更新
                    listener?.onUpdateReturned(UpdateStatus.Yes, "Start Update", release)

                }, {
                    listener?.onUpdateError(it)
                })

    }

    private fun getLocalVersionCode(): Int {
        var versionCode = 0
        try {
            versionCode = App.getContext().packageManager.getPackageInfo(App.getContext().packageName, 0).versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            LogUtil.e(TAG, e)
        }
        return versionCode
    }

    fun getOnlineVersionCode(release: Release): Int {
        //Release-v1.3.5.2-80
        val numberAndCode = release.name.split("-")
        if (numberAndCode.size < 2)
            return 0
        return numberAndCode[2].toInt()
    }
}

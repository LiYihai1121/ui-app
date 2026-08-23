package com.ldp.adskip.sync

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import com.ldp.adskip.AdskipApp
import com.ldp.adskip.core.LogRing
import com.ldp.adskip.data.Prefs

/**
 * 同步调度三合一：用 JobScheduler 取代 AlarmManager 套件。
 *
 * 取代：SyncScheduler + SyncAlarmReceiver + BootReceiver
 *
 * 收益：
 * - [JobInfo.Builder.setPersisted](true) 由系统跨重启恢复（删除 BootReceiver）
 * - [JobInfo.Builder.setRequiredNetworkType] 约束网络（删除自判连通性代码）
 * - Doze 下自动推迟到维护窗口
 */
class SyncJobService : JobService() {

    companion object {
        private const val JOB_ID = 2101
        private const val INTERVAL_MS = 12 * 60 * 60 * 1000L // 12 小时

        /** 开启/关闭自动同步 */
        fun setEnabled(context: Context, enabled: Boolean) {
            Prefs.setAutoSyncEnabled(context, enabled)
            if (enabled) schedule(context) else cancel(context)
        }

        /** 注册周期任务 */
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, SyncJobService::class.java))
                .setPeriodic(INTERVAL_MS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build()
            scheduler.schedule(job)
            LogRing.d("Sync", "JobScheduler scheduled, interval=${INTERVAL_MS}ms")
        }

        /** 取消周期任务 */
        fun cancel(context: Context) {
            context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
            LogRing.d("Sync", "JobScheduler cancelled")
        }

        /** 检查任务是否已注册 */
        fun isScheduled(context: Context): Boolean {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            return scheduler.allPendingJobs.any { it.id == JOB_ID }
        }
    }

    override fun onStartJob(params: JobParameters): Boolean {
        LogRing.d("Sync", "onStartJob")
        val container = AdskipApp.get(this)
        val serverUrl = Prefs.getServerUrl(this)

        // 在 IO 线程执行同步
        container.executors.io.execute {
            try {
                container.syncClient.syncRulesSilently(this, serverUrl, container.rulesRepo)
            } catch (e: Exception) {
                LogRing.e("Sync", "job failed: ${e.message}")
            } finally {
                jobFinished(params, false)
            }
        }
        return true // 表示异步处理中
    }

    override fun onStopJob(params: JobParameters): Boolean {
        LogRing.w("Sync", "onStopJob")
        return false // 不重试
    }
}

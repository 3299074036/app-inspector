package dev.appinspector.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 安装快照：每次刷新列表时记录当前已安装包。
 * 「已删除」筛选 = 快照中有、但当前已安装集合里没有的包。
 * （手工构建：用 SQLiteOpenHelper 直写，避免 Room 注解处理）
 */
data class AppSnapshot(
    val packageName: String,
    val appName: String,
    val versionName: String?,
    val versionCode: Long,
    /** 最近一次见到该包的时间戳 */
    val lastSeen: Long,
)

class SnapshotDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "app_inspector.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE snapshots(" +
                "packageName TEXT PRIMARY KEY," +
                "appName TEXT NOT NULL," +
                "versionName TEXT," +
                "versionCode INTEGER NOT NULL," +
                "lastSeen INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    suspend fun upsertAll(items: List<AppSnapshot>) = withContext(Dispatchers.IO) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (s in items) {
                val cv = ContentValues().apply {
                    put("packageName", s.packageName)
                    put("appName", s.appName)
                    put("versionName", s.versionName)
                    put("versionCode", s.versionCode)
                    put("lastSeen", s.lastSeen)
                }
                db.insertWithOnConflict("snapshots", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun deleted(installed: Collection<String>): List<AppSnapshot> =
        withContext(Dispatchers.IO) {
            val installedSet = installed.toHashSet()
            queryAll("SELECT * FROM snapshots ORDER BY lastSeen DESC")
                .filter { it.packageName !in installedSet }
        }

    suspend fun clear() = withContext(Dispatchers.IO) {
        writableDatabase.delete("snapshots", null, null)
    }

    suspend fun count(): Long = withContext(Dispatchers.IO) {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM snapshots", null).use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
    }

    private fun queryAll(sql: String): List<AppSnapshot> {
        readableDatabase.rawQuery(sql, null).use { c ->
            val iPkg = c.getColumnIndexOrThrow("packageName")
            val iName = c.getColumnIndexOrThrow("appName")
            val iVer = c.getColumnIndexOrThrow("versionName")
            val iVc = c.getColumnIndexOrThrow("versionCode")
            val iSeen = c.getColumnIndexOrThrow("lastSeen")
            val list = ArrayList<AppSnapshot>()
            while (c.moveToNext()) {
                list.add(
                    AppSnapshot(
                        packageName = c.getString(iPkg),
                        appName = c.getString(iName),
                        versionName = if (c.isNull(iVer)) null else c.getString(iVer),
                        versionCode = c.getLong(iVc),
                        lastSeen = c.getLong(iSeen),
                    )
                )
            }
            return list
        }
    }

    companion object {
        @Volatile
        private var instance: SnapshotDb? = null

        fun get(context: Context): SnapshotDb =
            instance ?: synchronized(this) {
                instance ?: SnapshotDb(context).also { instance = it }
            }
    }
}

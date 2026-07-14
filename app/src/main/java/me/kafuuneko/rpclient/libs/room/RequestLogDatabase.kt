package me.kafuuneko.rpclient.libs.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import me.kafuuneko.rpclient.libs.room.dao.LLMRequestLogDao
import me.kafuuneko.rpclient.libs.room.entity.LLMRequestLog

/**
 * 独立的本地请求日志数据库。
 *
 * 该数据库保存仅在调试模式下产生的原始请求和响应，并由备份规则明确排除；
 * 可丢弃的调试数据不会与业务数据共库。
 */
@Database(
    entities = [LLMRequestLog::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class RequestLogDatabase : RoomDatabase() {
    abstract fun getLLMRequestLogDao(): LLMRequestLogDao
}

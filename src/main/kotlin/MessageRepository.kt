package com.mmedojevic

import akka.NotUsed
import akka.stream.javadsl.Source
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.querybuilder.QueryBuilder.*
import java.net.InetSocketAddress
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

class MessageRepository {
    private val executor = Executors.newFixedThreadPool(10)
    private val session: CqlSession = CqlSession.builder()
        .addContactPoint(InetSocketAddress("127.0.0.1", 9042))
        .withLocalDatacenter("datacenter1")
        .withKeyspace("speak2")
        .withAuthCredentials("cassandra", "cassandra")
        .build()
    
    private val insertStatement: PreparedStatement = session.prepare(
        "INSERT INTO chat_messages (id, chat_id, sender_id, message, sent_at) VALUES (?, ?, ?, ?, ?)"
    )
    
    private val selectStatement: PreparedStatement = session.prepare(
        "SELECT id, chat_id, sender_id, message, sent_at FROM chat_messages WHERE chat_id = ? ORDER BY sent_at"
    )
    
    fun saveMessage(chatId: UUID, senderId: UUID, messageText: String): CompletableFuture<ChatMessage> {
        return CompletableFuture.supplyAsync({
            val message = ChatMessage(chatId = chatId, senderId = senderId, message = messageText)
            
            session.execute(insertStatement.bind(
                message.id,
                message.chatId,
                message.senderId,
                message.message,
                message.sentAt.toInstant()
            ))
            
            message
        }, executor)
    }
    
    fun getMessages(chatId: UUID): CompletableFuture<List<ChatMessage>> {
        return CompletableFuture.supplyAsync({
            val resultSet = session.execute(selectStatement.bind(chatId))
            
            val messages = mutableListOf<ChatMessage>()
            for (row in resultSet) {
                val message = ChatMessage(
                    id = row.getUuid("id")!!,
                    chatId = row.getUuid("chat_id")!!,
                    senderId = row.getUuid("sender_id")!!,
                    message = row.getString("message")!!,
                    sentAt = java.sql.Timestamp.from(row.getInstant("sent_at"))
                )
                messages.add(message)
            }
            
            messages.toList()
        }, executor)
    }
    
    fun createMessageSource(chatId: UUID, senderId: UUID, messageText: String): Source<ChatMessage, NotUsed> {
        return Source.completionStage(saveMessage(chatId, senderId, messageText))
    }
    
    fun close() {
        session.close()
        executor.shutdown()
    }
}
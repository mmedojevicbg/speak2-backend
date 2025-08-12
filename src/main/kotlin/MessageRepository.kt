package com.mmedojevic

import akka.NotUsed
import akka.stream.javadsl.Source
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors

class MessageRepository {
    private val executor = Executors.newFixedThreadPool(10)
    private val dbUrl = "jdbc:postgresql://localhost:5432/speak2"
    private val dbUser = "admin"
    private val dbPassword = "secret"
    
    fun saveMessage(chatId: UUID, senderId: UUID, messageText: String): CompletableFuture<ChatMessage> {
        return CompletableFuture.supplyAsync({
            val message = ChatMessage(chatId = chatId, senderId = senderId, message = messageText)
            
            val connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword)
            val sql = "INSERT INTO chat_messages (id, chat_id, sender_id, message, sent_at) VALUES (?, ?, ?, ?, ?)"
            val statement = connection.prepareStatement(sql)
            
            statement.setObject(1, message.id)
            statement.setObject(2, message.chatId)
            statement.setObject(3, message.senderId)
            statement.setString(4, message.message)
            statement.setTimestamp(5, message.sentAt)
            
            statement.executeUpdate()
            
            statement.close()
            connection.close()
            
            message
        }, executor)
    }
    
    fun getMessages(chatId: UUID): CompletableFuture<List<ChatMessage>> {
        return CompletableFuture.supplyAsync({
            val connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword)
            val sql = "SELECT id, chat_id, sender_id, message, sent_at FROM chat_messages WHERE chat_id = ? ORDER BY sent_at"
            val statement = connection.prepareStatement(sql)
            
            statement.setObject(1, chatId)
            val resultSet = statement.executeQuery()
            
            val messages = mutableListOf<ChatMessage>()
            while (resultSet.next()) {
                val message = ChatMessage(
                    id = resultSet.getObject("id") as UUID,
                    chatId = resultSet.getObject("chat_id") as UUID,
                    senderId = resultSet.getObject("sender_id") as UUID,
                    message = resultSet.getString("message"),
                    sentAt = resultSet.getTimestamp("sent_at")
                )
                messages.add(message)
            }
            
            resultSet.close()
            statement.close()
            connection.close()
            
            messages.toList()
        }, executor)
    }
    
    fun createMessageSource(chatId: UUID, senderId: UUID, messageText: String): Source<ChatMessage, NotUsed> {
        return Source.completionStage(saveMessage(chatId, senderId, messageText))
    }
    
    fun close() {
        executor.shutdown()
    }
}
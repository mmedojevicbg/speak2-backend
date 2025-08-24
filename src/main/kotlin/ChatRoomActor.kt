package com.mmedojevic

import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonSubTypes
import java.util.UUID

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = Initialize::class, name = "initialize"),
    JsonSubTypes.Type(value = Send::class, name = "send"),
    JsonSubTypes.Type(value = Terminate::class, name = "terminate"),
    JsonSubTypes.Type(value = Get::class, name = "get")
)
interface ChatRoomCommand
data class Initialize(val id: String, val webSocket: WebSocket) : ChatRoomCommand
data class Send(val id: String, val msg: String, val userInfo: UserInfo?) : ChatRoomCommand
object Terminate : ChatRoomCommand
data class Get(val userInfo: UserInfo?, val sessionId: String) : ChatRoomCommand
data class GrammarCheckResponse(val correctedText: String) : ChatRoomCommand

class ChatRoomActor(context: ActorContext<ChatRoomCommand>) : AbstractBehavior<ChatRoomCommand>(context) {
    private lateinit var id: String
    private lateinit var webSocket: WebSocket
    private val messageRepository = MessageRepository()
    private val grammarCheckActor = context.spawn(GrammarCheckActor.create(), "grammarCheck")

    override fun createReceive(): Receive<ChatRoomCommand> = newReceiveBuilder()

        .onMessage(Initialize::class.java) { msg ->
            this.id = msg.id
            this.webSocket = msg.webSocket
            System.out.println("ChatRoom ${context.self.path()} initialized (id: ${this.id})")
            webSocket.sendMessageToWebSocket(this.id, "Chat room ${this.id} initialized")
            this
        }
        .onMessage(Send::class.java) { msg ->
            System.out.println("ChatRoom ${msg.id} received message (${msg.msg})")
            val senderInfo = msg.userInfo
            val senderDisplay = if (senderInfo != null) "${senderInfo.name} (${senderInfo.sub})" else "Anonymous"
            println("Message from user: $senderDisplay")

            val displayMessage = if (senderInfo != null) "${senderInfo.name}: ${msg.msg}" else "Anonymous: ${msg.msg}"
            webSocket.sendMessageToWebSocket(msg.id, "Message: $displayMessage")

            // Save corrected message to database using Akka Streams
            val chatIdUuid = UUID.fromString(msg.id)
            val senderId = if (senderInfo != null) UUID.fromString(senderInfo.sub) else UUID.randomUUID()

            messageRepository.createMessageSource(chatIdUuid, senderId, msg.msg)
                .runWith(akka.stream.javadsl.Sink.ignore(), context.system)
                .whenComplete { _, ex ->
                    if (ex != null) {
                        System.err.println("Failed to save message to database: ${ex.message}")
                    } else {
                        System.out.println("Message saved to database successfully for user: $senderDisplay")
                    }
                }

            // Send message to grammar check first
            grammarCheckActor.tell(GrammarCheckRequest(msg.msg, context.self))
            this
        }
        .onMessage(Terminate::class.java) { msg ->
            System.out.println("ChatRoom ${context.self.path()} terminating")
            webSocket.sendMessageToWebSocket(this.id, "Chat room ${this.id} terminated")
            messageRepository.close()
            Behaviors.stopped()
        }
        .onMessage(Get::class.java) { msg ->
            System.out.println("ChatRoom ${context.self.path()} getting messages")
            
            // Get messages from database
            val chatIdUuid = UUID.fromString(this.id)
            messageRepository.getMessages(chatIdUuid)
                .whenComplete { dbMessages, ex ->
                    if (ex == null) {
                        dbMessages.forEach {
                            webSocket.sendMessageToSession(this.id, msg.sessionId, "${it.senderId}: ${it.message}")
                        }
                    } else {
                        System.err.println("Failed to get messages from database: ${ex.message}")

                    }
                }
            this
        }
        .onMessage(GrammarCheckResponse::class.java) {
            val correctedMessage = it.correctedText
            val displayMessage = "Grammar corrections: $correctedMessage"
            webSocket.sendMessageToWebSocket(id, displayMessage)
            this
        }
        .build()

    companion object {
        fun create(): Behavior<ChatRoomCommand> = Behaviors.setup { context ->
            ChatRoomActor(context)
        }
    }
}

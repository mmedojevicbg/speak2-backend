package com.mmedojevic

import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.cluster.sharding.typed.javadsl.Entity
import akka.cluster.sharding.typed.javadsl.EntityTypeKey
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonSubTypes

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
    JsonSubTypes.Type(value = CreateChatRoomCommand::class, name = "create"),
    JsonSubTypes.Type(value = SendMessageCommand::class, name = "send"),
    JsonSubTypes.Type(value = DeleteChatRoomCommand::class, name = "delete"),
    JsonSubTypes.Type(value = GetMessagesCommand::class, name = "get"),
    JsonSubTypes.Type(value = Start::class, name = "start")
)
interface SupervisorCommand
data class CreateChatRoomCommand(val id: String, val webSocket: WebSocket, val userInfo: UserInfo?) : SupervisorCommand
data class SendMessageCommand(val id: String, val msg: String, val userInfo: UserInfo?) : SupervisorCommand
data class DeleteChatRoomCommand(val id: String) : SupervisorCommand
data class GetMessagesCommand(val id: String, val userInfo: UserInfo?, val sessionId: String) : SupervisorCommand
object Start : SupervisorCommand

class ChatSupervisorActor(context: ActorContext<SupervisorCommand>) : AbstractBehavior<SupervisorCommand>(context) {
    private val webSocketInstances = mutableMapOf<String, WebSocket>()
    private val sharding = ClusterSharding.get(context.system)

    companion object {
        val CHAT_ROOM_TYPE_KEY: EntityTypeKey<ChatRoomCommand> = EntityTypeKey.create(ChatRoomCommand::class.java, "ChatRoom")
        
        fun create(): Behavior<SupervisorCommand> = Behaviors.setup { context ->
            ChatSupervisorActor(context)
        }
    }

    init {
        sharding.init(
            Entity.of(CHAT_ROOM_TYPE_KEY) { entityContext ->
                ChatRoomActor.create()
            }
        )
    }

    override fun createReceive(): Receive<SupervisorCommand> = newReceiveBuilder()
        .onMessage(CreateChatRoomCommand::class.java) { msg ->
            println("Creating new chat room entity for ${msg.id}")
            val entityRef = sharding.entityRefFor(CHAT_ROOM_TYPE_KEY, msg.id)
            entityRef.tell(Initialize(msg.id, msg.webSocket))
            webSocketInstances[msg.id] = msg.webSocket
            this
        }
        .onMessage(SendMessageCommand::class.java) { msg ->
            val entityRef = sharding.entityRefFor(CHAT_ROOM_TYPE_KEY, msg.id)
            entityRef.tell(Send(msg.id, msg.msg, msg.userInfo))
            this
        }
        .onMessage(DeleteChatRoomCommand::class.java) { msg ->
            val entityRef = sharding.entityRefFor(CHAT_ROOM_TYPE_KEY, msg.id)
            entityRef.tell(Terminate)
            webSocketInstances.remove(msg.id)
            this
        }
        .onMessage(GetMessagesCommand::class.java) { msg ->
            val entityRef = sharding.entityRefFor(CHAT_ROOM_TYPE_KEY, msg.id)
            entityRef.tell(Get(msg.userInfo, msg.sessionId))
            this
        }
        .build()
}
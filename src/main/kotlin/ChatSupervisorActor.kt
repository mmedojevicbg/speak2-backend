package com.mmedojevic

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive

interface SupervisorCommand
data class CreateChatRoomCommand(val id: String, val webSocket: WebSocket, val userInfo: UserInfo?) : SupervisorCommand
data class SendMessageCommand(val id: String, val msg: String, val userInfo: UserInfo?) : SupervisorCommand
data class DeleteChatRoomCommand(val id: String) : SupervisorCommand
data class GetMessagesCommand(val id: String, val userInfo: UserInfo?, val sessionId: String) : SupervisorCommand
object Start : SupervisorCommand

class ChatSupervisorActor(context: ActorContext<SupervisorCommand>) : AbstractBehavior<SupervisorCommand>(context) {
    private val webSocketInstances = mutableMapOf<String, WebSocket>()

    override fun createReceive(): Receive<SupervisorCommand> = newReceiveBuilder()
        .onMessage(CreateChatRoomCommand::class.java) { msg ->
            val actorName = "chat-room-${msg.id}"
            val existingChild = context.getChild(actorName)
            
            val child = if (existingChild.isPresent) {
                println("Chat room ${msg.id} already exists, using existing actor")
                existingChild.get() as ActorRef<ChatRoomCommand>
            } else {
                println("Creating new chat room actor for ${msg.id}")
                val newChild = context.spawn(ChatRoomActor.create(msg.webSocket), actorName)
                newChild.tell(Initialize(msg.id))
                webSocketInstances[msg.id] = msg.webSocket
                newChild
            }
            
            this
        }
        .onMessage(SendMessageCommand::class.java) { msg ->
            val child = context.getChild("chat-room-${msg.id}").get() as ActorRef<ChatRoomCommand>
            child.tell(Send(msg.id, msg.msg, msg.userInfo))
            this
        }
        .onMessage(DeleteChatRoomCommand::class.java) { msg ->
            val actorName = "chat-room-${msg.id}"
            context.getChild(actorName).ifPresent { actor ->
                (actor as ActorRef<ChatRoomCommand>).tell(Terminate)
            }
            webSocketInstances.remove(msg.id)
            this
        }
        .onMessage(GetMessagesCommand::class.java) { msg ->
            val child = context.getChild("chat-room-${msg.id}").get() as ActorRef<ChatRoomCommand>
            child.tell(Get(msg.userInfo, msg.sessionId))
            this
        }
        .build()

    companion object {
        fun create(): Behavior<SupervisorCommand> = Behaviors.setup { context ->
            ChatSupervisorActor(context)
        }
    }
}
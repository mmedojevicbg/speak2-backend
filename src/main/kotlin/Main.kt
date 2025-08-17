package com.mmedojevic

import akka.actor.typed.ActorSystem
import akka.actor.typed.javadsl.Behaviors
import akka.cluster.sharding.typed.javadsl.ClusterSharding
import akka.cluster.sharding.typed.javadsl.Entity
import akka.cluster.sharding.typed.javadsl.EntityTypeKey
import akka.http.javadsl.Http

fun main() {
    val system = ActorSystem.create(Behaviors.empty<Void>(), "chat-system")
    
    // Initialize cluster sharding
    val sharding = ClusterSharding.get(system)
    val chatRoomTypeKey: EntityTypeKey<ChatRoomCommand> = EntityTypeKey.create(ChatRoomCommand::class.java, "ChatRoom")
    
    sharding.init(
        Entity.of(chatRoomTypeKey) { entityContext ->
            ChatRoomActor.create()
        }
    )

    val webSocket = WebSocket()
    val route = webSocket.websocketRoute(system, sharding, chatRoomTypeKey)

    Http.get(system).newServerAt("localhost", 18000).bind(route)

    // Add shutdown hook
    Runtime.getRuntime().addShutdownHook(Thread {
        system.terminate()
    })
}
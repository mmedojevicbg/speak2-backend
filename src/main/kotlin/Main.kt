package com.mmedojevic

import akka.actor.typed.ActorSystem
import akka.http.javadsl.Http
//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main() {
    val system = ActorSystem.create(ChatSupervisorActor.create(), "chat-system")

    // Start the supervisor
    system.tell(Start)

    val webSocket = WebSocket()
    val route = webSocket.websocketRoute(system)

    Http.get(system).newServerAt("localhost", 18000).bind(route)

    // Add shutdown hook
    Runtime.getRuntime().addShutdownHook(Thread {
        system.terminate()
    })
}
package com.mmedojevic

import akka.NotUsed
import akka.actor.typed.ActorSystem
import akka.http.javadsl.model.ws.Message
import akka.http.javadsl.model.ws.TextMessage
import akka.http.javadsl.server.AllDirectives
import akka.http.javadsl.server.Route
import akka.http.javadsl.server.PathMatchers.segment
import akka.stream.OverflowStrategy
import akka.stream.javadsl.Flow
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import akka.stream.javadsl.SourceQueueWithComplete
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class WebSocket : AllDirectives() {
    private val logger = LoggerFactory.getLogger(WebSocket::class.java)
    private val jwtDecoder = JWTDecoder()
    // Map of chat room ID to list of WebSocket sessions
    private val webSocketQueues = ConcurrentHashMap<String, MutableList<Pair<String, SourceQueueWithComplete<Message>>>>()
    
    private fun createWebSocketFlow(actorRef: ActorSystem<SupervisorCommand>, id: String, userInfo: UserInfo?): Flow<Message, Message, NotUsed>? {
        val sourceQueue = Source.queue<Message>(100, OverflowStrategy.dropHead())
        val materialized = sourceQueue.preMaterialize(actorRef)
        val queue = materialized.first()
        val source = materialized.second()
        
        // Generate unique session ID for this WebSocket connection
        val sessionId = UUID.randomUUID().toString()
        
        // Store the queue so actors can send messages back to all sessions in this room
        webSocketQueues.computeIfAbsent(id) { mutableListOf() }.add(Pair(sessionId, queue))
        logger.info("Added WebSocket session $sessionId to room $id. Total sessions: ${webSocketQueues[id]?.size}")
        
        // Handle connection cleanup when WebSocket closes
        queue.watchCompletion().whenComplete { _, _ ->
            removeSession(id, sessionId)
        }

        val sink: Sink<Message, NotUsed> = Flow.create<Message>()
            .to(Sink.foreach { text ->
                val commandString = text.asTextMessage().strictText
                val (command, payload) = commandString.split("|", limit = 2)
                when (command) {
                    "init" -> {
                        actorRef.tell(CreateChatRoomCommand(id, this, userInfo))
                    }
                    "send" -> {
                        actorRef.tell(SendMessageCommand(id, payload, userInfo))
                    }
                    "get" -> {
                        actorRef.tell(GetMessagesCommand(id, userInfo, sessionId))
                    }
                    "delete" -> {
                        actorRef.tell(DeleteChatRoomCommand(id))
                        // Close all sessions for this room
                        webSocketQueues[id]?.forEach { (_, queue) -> queue.complete() }
                        webSocketQueues.remove(id)
                    }
                    else -> {
                        logger.warn("Unknown command: $command")
                    }
                }
            })

        return Flow.fromSinkAndSourceCoupled(sink, source)
    }
    
    fun sendMessageToWebSocket(id: String, message: String) {
        val sessions = webSocketQueues[id]
        if (sessions != null) {
            logger.info("Broadcasting message to ${sessions.size} sessions in room $id")
            sessions.forEach { (sessionId, queue) ->
                try {
                    queue.offer(TextMessage.create(message))
                } catch (e: Exception) {
                    logger.warn("Failed to send message to session $sessionId in room $id: ${e.message}")
                }
            }
        } else {
            logger.warn("No active sessions found for room $id")
        }
    }


    fun sendMessageToSession(id: String, sessionId: String, message: String) {
        val sessions = webSocketQueues[id]
        sessions?.find { it.first == sessionId }?.second?.offer(TextMessage.create(message))
    }
    
    private fun removeSession(roomId: String, sessionId: String) {
        webSocketQueues[roomId]?.removeIf { it.first == sessionId }
        val remainingSessions = webSocketQueues[roomId]?.size ?: 0
        logger.info("Removed session $sessionId from room $roomId. Remaining sessions: $remainingSessions")
        
        // Clean up empty room entries
        if (remainingSessions == 0) {
            webSocketQueues.remove(roomId)
            logger.info("Removed empty room $roomId")
        }
    }

    fun websocketRoute(system: ActorSystem<SupervisorCommand>): Route = pathPrefix("chat-room") {
        path(segment()) { dynamicSegment ->
            extractRequest { request ->
                extractUpgradeToWebSocket { upgrade ->
                    // Extract JWT from Sec-WebSocket-Protocol header
                    val protocolHeader = request.getHeader("Sec-WebSocket-Protocol")
                    val (jwt, userInfo) = if (protocolHeader.isPresent) {
                        val jwtToken = protocolHeader.get().value()
                        logger.info("Extracted JWT from Sec-WebSocket-Protocol: $jwtToken")
                        val user = jwtDecoder.extractUserInfo(jwtToken)
                        Pair(jwtToken, user)
                    } else {
                        logger.warn("No Sec-WebSocket-Protocol header found")
                        Pair(null, null)
                    }
                    
                    if (userInfo != null) {
                        logger.info("Authenticated user: ${userInfo.name} (${userInfo.sub})")
                    } else {
                        logger.warn("Failed to authenticate user from JWT")
                    }
                    
                    // Create WebSocket response with subprotocol echoed back
                    val flow = createWebSocketFlow(system, dynamicSegment, userInfo)
                    val response = if (jwt != null) {
                        upgrade.handleMessagesWith(flow, jwt)  // Echo back the JWT as subprotocol
                    } else {
                        upgrade.handleMessagesWith(flow)
                    }
                    
                    complete(response)
                }
            }
        }
    }
}
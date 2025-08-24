package com.mmedojevic

import akka.actor.typed.Behavior
import akka.actor.typed.javadsl.AbstractBehavior
import akka.actor.typed.javadsl.ActorContext
import akka.actor.typed.javadsl.Behaviors
import akka.actor.typed.javadsl.Receive
import akka.http.javadsl.Http
import akka.http.javadsl.model.*
import akka.http.javadsl.unmarshalling.Unmarshaller
import akka.stream.javadsl.Source
import akka.util.ByteString
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import java.time.Duration
import java.util.concurrent.CompletableFuture

data class GrammarCheckRequest(
    val text: String,
    val replyTo: akka.actor.typed.ActorRef<ChatRoomCommand>
)

data class GrammarApiRequest(@JsonProperty("text") val text: String)
data class GrammarApiResponse(@JsonProperty("corrected") val corrected: String)

class GrammarCheckActor(context: ActorContext<GrammarCheckRequest>) : AbstractBehavior<GrammarCheckRequest>(context) {
    
    private val http = Http.get(context.system)
    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())
    private val grammarServiceUrl = "http://localhost:8000/correct"
    
    override fun createReceive(): Receive<GrammarCheckRequest> = newReceiveBuilder()
        .onMessage(GrammarCheckRequest::class.java) { request ->
            processGrammarCheck(request)
            this
        }
        .build()

    private fun processGrammarCheck(request: GrammarCheckRequest) {
        try {
            val requestBody = GrammarApiRequest(request.text)
            val jsonBody = objectMapper.writeValueAsString(requestBody)
            
            Source.single(request)
                .throttle(1, Duration.ofSeconds(1)) // Rate limiting: 1 request per second
                .mapAsync(1) { req ->
                    val httpRequest = HttpRequest.create()
                        .withMethod(HttpMethods.POST)
                        .withUri(grammarServiceUrl)
                        .withEntity(ContentTypes.APPLICATION_JSON, jsonBody)
                    
                    http.singleRequest(httpRequest)
                        .thenCompose { response ->
                            if (response.status().isSuccess()) {
                                Unmarshaller.entityToString()
                                    .unmarshal(response.entity(), context.system)
                                    .thenApply { responseBody ->
                                        try {
                                            val apiResponse = objectMapper.readValue<GrammarApiResponse>(responseBody)
                                            GrammarCheckResponse(apiResponse.corrected)
                                        } catch (e: Exception) {
                                            context.log.error("Failed to parse grammar service response", e)
                                            GrammarCheckResponse(req.text) // Return original text on parse error
                                        }
                                    }
                            } else {
                                context.log.error("Grammar service returned error: ${response.status()}")
                                CompletableFuture.completedFuture(GrammarCheckResponse(req.text))
                            }
                        }
                        .exceptionally { throwable ->
                            context.log.error("Grammar service request failed", throwable)
                            GrammarCheckResponse(req.text) // Return original text on error
                        }
                }
                .runWith(akka.stream.javadsl.Sink.foreach { response ->
                    request.replyTo.tell(response)
                }, context.system)
                
        } catch (e: Exception) {
            context.log.error("Failed to process grammar check request", e)
            request.replyTo.tell(GrammarCheckResponse(request.text))
        }
    }

    companion object {
        fun create(): Behavior<GrammarCheckRequest> = Behaviors.setup { context ->
            GrammarCheckActor(context)
        }
    }
}
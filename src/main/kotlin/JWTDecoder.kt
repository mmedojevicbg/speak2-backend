package com.mmedojevic

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.util.*

data class UserInfo(
    val sub: String,
    val name: String
)

class JWTDecoder {
    private val objectMapper = ObjectMapper().registerKotlinModule()
    
    fun extractUserInfo(jwtToken: String): UserInfo? {
        return try {
            // JWT format: header.payload.signature
            val parts = jwtToken.split(".")
            if (parts.size != 3) {
                println("Invalid JWT format: expected 3 parts, got ${parts.size}")
                return null
            }
            
            // Decode the payload (second part)
            val payloadEncoded = parts[1]
            val payloadBytes = Base64.getUrlDecoder().decode(payloadEncoded)
            val payloadJson = String(payloadBytes)
            
            println("Decoded JWT payload: $payloadJson")
            
            // Parse JSON to extract sub and name
            val payloadMap = objectMapper.readValue(payloadJson, Map::class.java) as Map<String, Any>
            
            val sub = payloadMap["sub"] as? String
            val name = payloadMap["name"] as? String
            
            if (sub != null && name != null) {
                UserInfo(sub = sub, name = name)
            } else {
                println("Missing sub or name in JWT payload. sub: $sub, name: $name")
                null
            }
        } catch (e: Exception) {
            println("Failed to decode JWT: ${e.message}")
            null
        }
    }
}
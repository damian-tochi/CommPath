package com.damian.commpath.sample

import com.damian.commpath.CommPath
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Example module demonstrating how to implement CommPath.Module.
 * This module handles user-related operations.
 */
class ExampleModule : CommPath.Module {
    
    override val id: String = "example_module"
    
    private val _userCount = MutableStateFlow(0)
    val userCount: StateFlow<Int> = _userCount
    
    override fun onRegister() {
        println("ExampleModule registered with CommPath")
        _userCount.value = 10  // Initial value
    }
    
    override fun onUnregister() {
        println("ExampleModule unregistered from CommPath")
    }
    
    override suspend fun onReceive(message: CommPath.Message) {
        when (message.action) {
            "increment_user" -> {
                _userCount.value++
                println("User count incremented to ${_userCount.value}")
            }
            "decrement_user" -> {
                _userCount.value = (_userCount.value - 1).coerceAtLeast(0)
                println("User count decremented to ${_userCount.value}")
            }
            "get_user_count" -> {
                // Send response via broadcast
                CommPath.send(
                    CommPath.Message(
                        targetModuleId = message.targetModuleId,
                        action = "user_count_response",
                        data = mapOf("count" to _userCount.value)
                    )
                )
            }
        }
    }
    
    override suspend fun onRequest(request: CommPath.Request, timeoutMs: Long): CommPath.Response {
        return when (request.action) {
            "get_user_count" -> {
                CommPath.Response.success(mapOf("count" to _userCount.value))
            }
            "add_users" -> {
                val amount = request.data["amount"] as? Int ?: 0
                _userCount.value += amount
                CommPath.Response.success(mapOf("newCount" to _userCount.value))
            }
            "process_transaction" -> {
                // Simulate some work
                delay(100)
                val transactionId = request.data["transactionId"] as? String ?: "unknown"
                CommPath.Response.success(mapOf("status" to "completed", "transactionId" to transactionId))
            }
            else -> {
                CommPath.Response.error("Unknown action: ${request.action}")
            }
        }
    }
}

package com.damian.commpath

import android.content.Context
import androidx.startup.Initializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * CommPath - Single entry-point class for inter-module communication.
 * 
 * This class provides a simplified API for modules to register, communicate,
 * and perform transactions in a multi-module Android application.
 * 
 * Usage:
 * ```
 * // Initialize
 * CommPath.init(context)
 * 
 * // Register a module
 * CommPath.registerModule(MyModule())
 * 
 * // Send a message
 * CommPath.send(Message("moduleId", "action", data))
 * 
 * // Request-Response pattern
 * val result = CommPath.request(Request("moduleId", "action", data))
 * ```
 */
object CommPath {
    
    private const val TAG = "CommPath"
    
    // Internal coroutine scope for async operations
    private val commPathScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    
    // Module registry
    private val modules = ConcurrentHashMap<String, Module>()
    
    // Message bus for event-driven communication
    private val _messageBus = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    val messageBus = _messageBus.asSharedFlow()
    
    // State tracking
    private val _isInitialized = MutableStateFlow(false)
    val isInitialized = _isInitialized.asStateFlow()
    
    /**
     * Initialize CommPath.
     * Should be called once at application startup.
     */
    fun init(context: Context) {
        if (_isInitialized.value) {
            return
        }
        _isInitialized.value = true
        
        // Start message dispatcher
        commPathScope.launch {
            _messageBus.collect { message ->
                dispatchMessage(message)
            }
        }
    }
    
    /**
     * Register a module with CommPath.
     * Modules must have a unique ID.
     */
    fun registerModule(module: Module) {
        require(module.id.isNotBlank()) { "Module ID cannot be blank" }
        modules[module.id] = module
        module.onRegister()
    }
    
    /**
     * Unregister a module.
     */
    fun unregisterModule(moduleId: String) {
        modules[moduleId]?.onUnregister()
        modules.remove(moduleId)
    }
    
    /**
     * Send a message to a specific module or broadcast to all.
     * Fire-and-forget pattern.
     */
    fun send(message: Message) {
        if (!_isInitialized.value) {
            throw IllegalStateException("CommPath not initialized. Call CommPath.init() first.")
        }
        
        commPathScope.launch {
            if (message.targetModuleId != null) {
                // Direct message to specific module
                modules[message.targetModuleId]?.onReceive(message)
            } else {
                // Broadcast to all modules
                modules.values.forEach { it.onReceive(message) }
            }
        }
    }
    
    /**
     * Send a message and await a response.
     * Request-Response pattern with timeout.
     */
    suspend fun request(request: Request, timeoutMs: Long = 5000): Response {
        if (!_isInitialized.value) {
            throw IllegalStateException("CommPath not initialized. Call CommPath.init() first.")
        }
        
        val targetModule = modules[request.targetModuleId]
            ?: throw IllegalArgumentException("Module not found: ${request.targetModuleId}")
        
        return targetModule.onRequest(request, timeoutMs)
    }
    
    /**
     * Send a request with callback (non-suspending).
     */
    fun request(request: Request, callback: (Response) -> Unit, timeoutMs: Long = 5000) {
        commPathScope.launch {
            try {
                val response = this@CommPath.request(request, timeoutMs)
                callback(response)
            } catch (e: Exception) {
                callback(Response.error(e.message ?: "Unknown error"))
            }
        }
    }
    
    /**
     * Broadcast an event to all registered modules.
     */
    fun broadcast(event: Event) {
        send(Message(targetModuleId = null, action = event.type, data = event.data))
    }
    
    /**
     * Get a registered module by ID.
     */
    fun <T : Module> getModule(moduleId: String): T? {
        @Suppress("UNCHECKED_CAST")
        return modules[moduleId] as? T
    }
    
    /**
     * Check if a module is registered.
     */
    fun hasModule(moduleId: String): Boolean = modules.containsKey(moduleId)
    
    /**
     * Get all registered module IDs.
     */
    fun getRegisteredModules(): List<String> = modules.keys.toList()
    
    /**
     * Clear all modules and reset state.
     * Primarily for testing purposes.
     */
    internal fun clear() {
        modules.values.forEach { it.onUnregister() }
        modules.clear()
        _isInitialized.value = false
    }
    
    /**
     * Internal message dispatcher.
     */
    private suspend fun dispatchMessage(message: Message) {
        // Message is already delivered to target module in send()
        // This method can be extended for additional routing logic
    }
    
    /**
     * Module interface that all communication modules must implement.
     */
    interface Module {
        val id: String
        
        /** Called when module is registered */
        fun onRegister()
        
        /** Called when module is unregistered */
        fun onUnregister()
        
        /** Receive messages sent to this module */
        suspend fun onReceive(message: Message)
        
        /** Handle request-response pattern */
        suspend fun onRequest(request: Request, timeoutMs: Long): Response
    }
    
    /**
     * Data class representing a message.
     */
    data class Message(
        val targetModuleId: String?,  // null for broadcast
        val action: String,
        val data: Map<String, Any> = emptyMap(),
        val timestamp: Long = System.currentTimeMillis(),
        val messageId: String = generateId()
    )
    
    /**
     * Data class representing a request.
     */
    data class Request(
        val targetModuleId: String,
        val action: String,
        val data: Map<String, Any> = emptyMap(),
        val requestId: String = generateId(),
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Data class representing a response.
     */
    data class Response(
        val requestId: String,
        val data: Map<String, Any> = emptyMap(),
        val error: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        val isSuccess: Boolean get() = error == null
        
        companion object {
            fun success(data: Map<String, Any> = emptyMap()): Response {
                return Response(requestId = generateId(), data = data)
            }
            
            fun error(message: String): Response {
                return Response(requestId = generateId(), error = message)
            }
        }
    }
    
    /**
     * Data class representing an event.
     */
    data class Event(
        val type: String,
        val data: Map<String, Any> = emptyMap(),
        val timestamp: Long = System.currentTimeMillis()
    )
    
    private fun generateId(): String = "msg_${System.currentTimeMillis()}_${(0..9999).random()}"
}

/**
 * AndroidX Startup Initializer for automatic CommPath initialization.
 * Add to AndroidManifest.xml if you want auto-initialization:
 * 
 * <provider
 *     android:name="androidx.startup.InitializationProvider"
 *     android:authorities="${applicationId}.androidx-startup"
 *     android:exported="false"
 *     tools:node="merge">
 *     <meta-data
 *         android:name="com.damian.commpath.CommPathInitializer"
 *         android:value="androidx.startup" />
 * </provider>
 */
class CommPathInitializer : Initializer<Unit> {
    override fun create(context: Context) {
        CommPath.init(context)
    }
    
    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
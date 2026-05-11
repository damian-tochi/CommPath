# CommPath

A lightweight Android Kotlin library that simplifies communication and transactions between modules in multi-module applications through a single entry-point class.

## Features

- **Single Entry-Point**: All inter-module communication goes through the `CommPath` object
- **Module Registration**: Easy registration/unregistration of modules
- **Message Passing**: Fire-and-forget message broadcasting
- **Request-Response**: Synchronous request-response pattern with timeout support
- **Event-Driven**: SharedFlow-based event bus for reactive programming
- **Coroutine Support**: Built on Kotlin Coroutines for async operations
- **AndroidX Startup**: Optional automatic initialization

## Setup

### Gradle Configuration

Add the library module to your project:

```kotlin
// settings.gradle.kts
include(":app")  // your library module
include(":demoapp")  // optional demo app
```

In your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":app"))
}
```

### Dependencies

The library requires:
- Kotlin Coroutines Core
- AndroidX Startup Runtime (optional, for auto-initialization)

## Quick Start

### 1. Initialize CommPath

```kotlin
// In your Application class or MainActivity
CommPath.init(applicationContext)
```

Or use AndroidX Startup for automatic initialization (see Advanced Usage).

### 2. Create a Module

Implement the `CommPath.Module` interface:

```kotlin
class UserModule : CommPath.Module {
    
    override val id: String = "user_module"
    
    override fun onRegister() {
        // Called when module is registered
    }
    
    override fun onUnregister() {
        // Called when module is unregistered
    }
    
    override suspend fun onReceive(message: Message) {
        // Handle fire-and-forget messages
        when (message.action) {
            "update_user" -> {
                val userId = message.data["userId"] as? String
                // Update user...
            }
        }
    }
    
    override suspend fun onRequest(request: Request, timeoutMs: Long): Response {
        // Handle request-response pattern
        return when (request.action) {
            "get_user" -> {
                val user = getUser(request.data["userId"] as? String)
                Response.success(mapOf("user" to user))
            }
            else -> Response.error("Unknown action")
        }
    }
}
```

### 3. Register the Module

```kotlin
val userModule = UserModule()
CommPath.registerModule(userModule)
```

### 4. Send Messages

**Fire-and-forget (Message):**

```kotlin
// Send to specific module
CommPath.send(
    Message(
        targetModuleId = "user_module",
        action = "update_user",
        data = mapOf("userId" to "123", "name" to "John")
    )
)

// Broadcast to all modules
CommPath.send(
    Message(
        targetModuleId = null,
        action = "app_background",
        data = emptyMap()
    )
)
```

**Request-Response (Request):**

```kotlin
// Using coroutines
lifecycleScope.launch {
    val response = CommPath.request(
        Request(
            targetModuleId = "user_module",
            action = "get_user",
            data = mapOf("userId" to "123")
        )
    )
    
    if (response.isSuccess) {
        val user = response.data["user"]
        // Handle success
    } else {
        // Handle error
        Log.e("TAG", "Error: ${response.error}")
    }
}

// Using callback (non-suspending)
CommPath.request(
    Request(targetModuleId = "user_module", action = "get_user"),
    callback = { response ->
        if (response.isSuccess) {
            // Handle response
        }
    }
)
```

### 5. Listen to Events

```kotlin
// Collect from shared flow
lifecycleScope.launch {
    CommPath.messageBus.collect { message ->
        when (message.action) {
            "user_updated" -> {
                // Handle broadcast message
            }
        }
    }
}

// Access module state directly
val userModule = CommPath.getModule<UserModule>("user_module")
userModule?.userCount?.collect { count ->
    // Update UI
}
```

## API Reference

### CommPath (Object)

The main entry point for all communication.

| Method | Description |
|--------|-------------|
| `init(context: Context)` | Initialize CommPath with application context |
| `registerModule(module: Module)` | Register a module |
| `unregisterModule(moduleId: String)` | Unregister a module |
| `send(message: Message)` | Send a fire-and-forget message |
| `request(request: Request, timeoutMs: Long): Response` | Send a request and await response |
| `request(request: Request, callback, timeoutMs)` | Send request with callback |
| `broadcast(event: Event)` | Broadcast event to all modules |
| `getModule<T>(moduleId: String): T?` | Get registered module by ID |
| `hasModule(moduleId: String): Boolean` | Check if module is registered |
| `getRegisteredModules(): List<String>` | Get all registered module IDs |

### Data Classes

**Message** - Fire-and-forget communication
```kotlin
data class Message(
    val targetModuleId: String?,  // null for broadcast
    val action: String,
    val data: Map<String, Any> = emptyMap(),
    val timestamp: Long,
    val messageId: String
)
```

**Request** - Request-Response pattern
```kotlin
data class Request(
    val targetModuleId: String,
    val action: String,
    val data: Map<String, Any> = emptyMap(),
    val requestId: String,
    val timestamp: Long
)
```

**Response** - Request response
```kotlin
data class Response(
    val requestId: String,
    val data: Map<String, Any> = emptyMap(),
    val error: String? = null,
    val timestamp: Long
) {
    val isSuccess: Boolean get() = error == null
    
    companion object {
        fun success(data: Map<String, Any> = emptyMap()): Response
        fun error(message: String): Response
    }
}
```

**Event** - Simple event structure
```kotlin
data class Event(
    val type: String,
    val data: Map<String, Any> = emptyMap(),
    val timestamp: Long
)
```

### Module Interface

All communication modules must implement this interface:

```kotlin
interface Module {
    val id: String
    
    fun onRegister()          // Called when registered
    fun onUnregister()        // Called when unregistered
    suspend fun onReceive(message: Message)   // Handle messages
    suspend fun onRequest(request: Request, timeoutMs: Long): Response  // Handle requests
}
```

## Advanced Usage

### Automatic Initialization

CommPath can be automatically initialized using AndroidX Startup:

1. Add the `CommPathInitializer` class (included in the library)
2. Add to your `AndroidManifest.xml`:

```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="merge">
    <meta-data
        android:name="com.damian.commpath.CommPathInitializer"
        android:value="androidx.startup" />
</provider>
```

### Threading

CommPath uses `Dispatchers.Main.immediate` by default for all callbacks. If you need background processing, use `withContext` inside your module:

```kotlin
override suspend fun onRequest(request: Request, timeoutMs: Long): Response {
    return withContext(Dispatchers.IO) {
        // Perform network or database operation
        val result = performWork()
        Response.success(mapOf("result" to result))
    }
}
```

### Timeout Configuration

Default timeout is 5 seconds. Customize per request:

```kotlin
val response = CommPath.request(
    request,
    timeoutMs = 10000  // 10 seconds
)
```

### Module State Sharing

Modules can expose `StateFlow` or `SharedFlow` to share state:

```kotlin
class UserModule : CommPath.Module {
    private val _userCount = MutableStateFlow(0)
    val userCount: StateFlow<Int> = _userCount.asStateFlow()
    
    // Other implementation...
}
```

Consumers can observe:

```kotlin
val module = CommPath.getModule<UserModule>("user_module")
module?.userCount?.collect { count ->
    // React to state changes
}
```

## Best Practices

1. **Unique Module IDs**: Ensure each module has a unique ID to avoid conflicts
2. **Lifecycle Management**: Unregister modules when no longer needed (e.g., in `onDestroy()`)
3. **Error Handling**: Always handle errors in `onRequest` and provide meaningful error messages
4. **Data Validation**: Validate input data in requests and messages
5. **Timeouts**: Set appropriate timeouts for requests based on expected operation duration
6. **Thread Safety**: CommPath is thread-safe, but your module implementation should also be

## Architecture

```
┌─────────────────┐
│   Sample App    │
│  (UI Layer)     │
└────────┬────────┘
         │ uses
         ▼
┌─────────────────┐
│   CommPath      │◄── Single Entry Point
│   (Singleton)   │
└────────┬────────┘
         │ routes
         ▼
┌─────────────────┐
│   UserModule    │
│   (Module 1)    │
├─────────────────┤
│   AuthModule    │
│   (Module 2)    │
├─────────────────┤
│   DataModule    │
│   (Module 3)    │
└─────────────────┘
```

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
Feel free to use in your projects.

## Support

If you encounter any issues or have questions:
- Open an issue on GitHub
- Check the demo app for usage examples
- Review the source code documentation

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

---

**Made with ❤️ by Damian Tochi**

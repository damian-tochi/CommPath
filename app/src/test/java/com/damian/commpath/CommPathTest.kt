package com.damian.commpath

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Unit tests for CommPath library.
 * Tests core functionality including initialization, module registration,
 * message passing, and request-response patterns.
 */
@RunWith(RobolectricTestRunner::class)
class CommPathTest {
    
    private lateinit var testModule: TestModule
    
    @Before
    fun setup() {
        // Reset CommPath before each test
        CommPath.clear()
        testModule = TestModule()
        // Initialize CommPath
        CommPath.init()
        CommPath.registerModule(testModule)
    }
    
    @After
    fun tearDown() {
        CommPath.clear()
    }
    
    @Test
    fun `init should set initialized to true`() = runBlocking {
        assertTrue(CommPath.isInitialized.value)
    }
    
    @Test
    fun `registerModule should add module to registry`() = runBlocking {
        val moduleId = testModule.id
        assertTrue(CommPath.hasModule(moduleId))
        assertSame(testModule, CommPath.getModule<TestModule>(moduleId))
    }
    
    @Test
    fun `unregisterModule should remove module from registry`() = runBlocking {
        CommPath.unregisterModule(testModule.id)
        assertFalse(CommPath.hasModule(testModule.id))
        assertNull(CommPath.getModule<TestModule>(testModule.id))
    }
    
    @Test
    fun `getRegisteredModules should return all registered module IDs`() = runBlocking {
        val modules = CommPath.getRegisteredModules()
        assertTrue(modules.contains(testModule.id))
    }
    
    @Test
    fun `send message to specific module should call onReceive`() = runBlocking {
        val message = CommPath.Message(
            targetModuleId = testModule.id,
            action = "test_action",
            data = mapOf("key" to "value")
        )
        
        CommPath.send(message)
        
        // Small delay to allow async processing
        delay(50)
        
        assertEquals("test_action", testModule.lastReceivedAction)
        assertEquals("value", testModule.lastReceivedData["key"])
    }
    
    @Test
    fun `broadcast message should deliver to all modules`() = runBlocking {
        val secondModule = SecondTestModule()
        CommPath.registerModule(secondModule)
        
        val message = CommPath.Message(
            targetModuleId = null,
            action = "broadcast_action",
            data = emptyMap()
        )
        
        CommPath.send(message)
        delay(50)
        
        assertEquals("broadcast_action", testModule.lastReceivedAction)
        assertEquals("broadcast_action", secondModule.lastReceivedAction)
    }
    
    @Test
    fun `request should return response from module`() = runBlocking {
        val request = CommPath.Request(
            targetModuleId = testModule.id,
            action = "get_data",
            data = mapOf("id" to "123")
        )
        
        val response = CommPath.request(request)
        
        assertTrue(response.isSuccess)
        assertEquals("response_data", response.data["result"])
    }
    
    @Test
    fun `request to non-existent module should throw exception`() = runBlocking {
        val request = CommPath.Request(
            targetModuleId = "non_existent",
            action = "get_data"
        )
        
        try {
            CommPath.request(request)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }
    
    @Test
    fun `request with callback should invoke callback`() = runBlocking {
        val request = CommPath.Request(
            targetModuleId = testModule.id,
            action = "get_data"
        )
        
        var callbackInvoked = false
        var responseReceived: CommPath.Response? = null
        
        CommPath.request(request, callback = { response ->
            callbackInvoked = true
            responseReceived = response
        })
        
        delay(50)
        
        assertTrue(callbackInvoked)
        assertNotNull(responseReceived)
        assertTrue(responseReceived!!.isSuccess)
    }
    
    @Test
    fun `request with timeout should fail if module takes too long`() = runBlocking {
        // Register a slow module
        val slowModule = SlowTestModule(delayMs = 2000)
        CommPath.registerModule(slowModule)
        
        val request = CommPath.Request(
            targetModuleId = slowModule.id,
            action = "slow_action"
        )
        
        // Should timeout after 100ms
        val response = CommPath.request(request, timeoutMs = 100)
        
        assertFalse(response.isSuccess)
        assertNotNull(response.error)
    }
    
    @Test
    fun `module onRegister should be called`() = runBlocking {
        val module = TestModule()
        assertFalse(testModule.isRegistered)
        
        CommPath.registerModule(module)
        assertTrue(module.isRegistered)
    }
    
    @Test
    fun `module onUnregister should be called`() = runBlocking {
        assertTrue(testModule.isRegistered)
        
        CommPath.unregisterModule(testModule.id)
        assertFalse(testModule.isRegistered)
    }
    
    @Test
    fun `send before init should throw exception`() = runBlocking {
        CommPath.clear()
        
        try {
            CommPath.send(
                CommPath.Message(
                    targetModuleId = "test",
                    action = "test"
                )
            )
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // Expected
        }
    }
    
    @Test
    fun `request before init should throw exception`() = runBlocking {
        CommPath.clear()
        
        try {
            CommPath.request(
                CommPath.Request(
                    targetModuleId = "test",
                    action = "test"
                )
            )
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            // Expected
        }
    }
    
    @Test
    fun `messageBus should emit messages`() = runBlocking {
        val messages = mutableListOf<CommPath.Message>()
        val job = launch {
            CommPath.messageBus.collect { messages.add(it) }
        }
        
        val message = CommPath.Message(
            targetModuleId = testModule.id,
            action = "collect_test"
        )
        CommPath.send(message)
        
        delay(50)
        job.cancel()
        
        assertTrue(messages.isNotEmpty())
        assertEquals("collect_test", messages.last().action)
    }
    
    @Test
    fun `getModule with wrong type should return null`() = runBlocking {
        val wrongType = CommPath.getModule<TestModule>("nonexistent")
        assertNull(wrongType)
    }
    
    @Test
    fun `broadcast should send to all modules`() = runBlocking {
        val module2 = SecondTestModule()
        CommPath.registerModule(module2)
        
        val event = CommPath.Event(
            type = "test_event",
            data = mapOf("event" to "data")
        )
        
        CommPath.broadcast(event)
        delay(50)
        
        assertEquals("test_event", testModule.lastReceivedAction)
        assertEquals("test_event", module2.lastReceivedAction)
    }
}

/**
 * Test implementation of CommPath.Module
 */
class TestModule : CommPath.Module {
    override val id: String = "test_module"
    var isRegistered = false
        private set
    
    var lastReceivedAction: String? = null
        private set
    var lastReceivedData: Map<String, Any> = emptyMap()
        private set
    
    override fun onRegister() {
        isRegistered = true
    }
    
    override fun onUnregister() {
        isRegistered = false
    }
    
    override suspend fun onReceive(message: CommPath.Message) {
        lastReceivedAction = message.action
        lastReceivedData = message.data
    }
    
    override suspend fun onRequest(request: CommPath.Request, timeoutMs: Long): CommPath.Response {
        return CommPath.Response.success(
            mapOf("result" to "response_data")
        )
    }
}

/**
 * Second test module for broadcast testing
 */
class SecondTestModule : CommPath.Module {
    override val id: String = "second_module"
    var isRegistered = false
        private set
    var lastReceivedAction: String? = null
        private set
    
    override fun onRegister() {
        isRegistered = true
    }
    
    override fun onUnregister() {
        isRegistered = false
    }
    
    override suspend fun onReceive(message: CommPath.Message) {
        lastReceivedAction = message.action
    }
    
    override suspend fun onRequest(request: CommPath.Request, timeoutMs: Long): CommPath.Response {
        return CommPath.Response.success()
    }
}

/**
 * Slow module for timeout testing
 */
class SlowTestModule(private val delayMs: Long = 2000) : CommPath.Module {
    override val id: String = "slow_module"
    var isRegistered = false
        private set
    
    override fun onRegister() {
        isRegistered = true
    }
    
    override fun onUnregister() {
        isRegistered = false
    }
    
    override suspend fun onReceive(message: CommPath.Message) {
        // Do nothing
    }
    
    override suspend fun onRequest(request: CommPath.Request, timeoutMs: Long): CommPath.Response {
        kotlinx.coroutines.delay(delayMs)
        return CommPath.Response.success()
    }
}

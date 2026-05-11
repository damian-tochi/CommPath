package com.damian.commpath.demoapp

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.damian.commpath.CommPath
import com.damian.commpath.sample.ExampleModule
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    private lateinit var tvUserCount: TextView
    private lateinit var tvStatus: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        tvUserCount = findViewById(R.id.tvUserCount)
        tvStatus = findViewById(R.id.tvStatus)
        
        // Initialize CommPath
        CommPath.init()
        
        // Register example module
        val exampleModule = ExampleModule()
        CommPath.registerModule(exampleModule)
        
        setupUI()
        observeModule()
    }
    
    private fun setupUI() {
        findViewById<Button>(R.id.btnIncrement).setOnClickListener {
            // Send message (fire-and-forget)
            CommPath.send(
                CommPath.Message(
                    targetModuleId = "example_module",
                    action = "increment_user"
                )
            )
        }
        
        findViewById<Button>(R.id.btnDecrement).setOnClickListener {
            CommPath.send(
                CommPath.Message(
                    targetModuleId = "example_module",
                    action = "decrement_user"
                )
            )
        }
        
        findViewById<Button>(R.id.btnRequestCount).setOnClickListener {
            // Request-Response pattern using coroutine
            lifecycleScope.launch {
                try {
                    val response = CommPath.request(
                        CommPath.Request(
                            targetModuleId = "example_module",
                            action = "get_user_count"
                        )
                    )
                    if (response.isSuccess) {
                        val count = response.data["count"] as? Int ?: 0
                        tvStatus.text = "User count: $count"
                    } else {
                        tvStatus.text = "Error: ${response.error}"
                    }
                } catch (e: Exception) {
                    tvStatus.text = "Exception: ${e.message}"
                }
            }
        }
        
        findViewById<Button>(R.id.btnAddUsers).setOnClickListener {
            lifecycleScope.launch {
                val response = CommPath.request(
                    CommPath.Request(
                        targetModuleId = "example_module",
                        action = "add_users",
                        data = mapOf("amount" to 5)
                    )
                )
                if (response.isSuccess) {
                    val newCount = response.data["newCount"] as? Int ?: 0
                    tvStatus.text = "Added 5 users. New count: $newCount"
                }
            }
        }
        
        findViewById<Button>(R.id.btnTransaction).setOnClickListener {
            lifecycleScope.launch {
                val response = CommPath.request(
                    CommPath.Request(
                        targetModuleId = "example_module",
                        action = "process_transaction",
                        data = mapOf("transactionId" to "txn_12345")
                    )
                )
                if (response.isSuccess) {
                    val status = response.data["status"] as? String ?: "unknown"
                    val txnId = response.data["transactionId"] as? String ?: "unknown"
                    tvStatus.text = "Transaction $txnId: $status"
                }
            }
        }
    }
    
    private fun observeModule() {
        // Observe StateFlow from module
        lifecycleScope.launch {
            (CommPath.getModule<ExampleModule>("example_module"))?.userCount?.collect { count ->
                tvUserCount.text = "User Count: $count"
            }
        }
        
        // Listen to broadcast messages
        lifecycleScope.launch {
            CommPath.messageBus.collect { message ->
                if (message.action == "user_count_response") {
                    val count = message.data["count"] as? Int ?: 0
                    Toast.makeText(
                        this@MainActivity,
                        "Broadcast: User count is $count",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
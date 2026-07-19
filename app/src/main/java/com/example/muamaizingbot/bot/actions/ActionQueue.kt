package com.example.muamaizingbot.bot.actions

import android.util.Log
import com.example.muamaizingbot.bot.BotDiagnosticJournal
import java.util.ArrayDeque
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object ActionQueue {

    private const val TAG = "ActionQueue"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val pending = ArrayDeque<BotAction>()

    @Volatile
    var isRunning: Boolean = false
        private set

    fun enqueue(action: BotAction) {
        enqueueAll(listOf(action))
    }

    fun enqueueAll(actions: List<BotAction>) {
        if (actions.isEmpty()) {
            return
        }
        scope.launch {
            mutex.withLock {
                pending.addAll(actions)
            }
            if (!isRunning) {
                runWorker()
            }
        }
    }

    suspend fun executeSequence(actions: List<BotAction>): Boolean {
        if (actions.isEmpty()) {
            return true
        }
        Log.d(TAG, "[ACTION_QUEUE] sequence start count=${actions.size}")
        for ((index, action) in actions.withIndex()) {
            Log.d(TAG, "[ACTION_QUEUE] step=${index + 1}/${actions.size} action=$action")
            if (!ActionExecutor.execute(action)) {
                Log.w(TAG, "[ACTION_QUEUE] sequence failed step=${index + 1} action=$action")
                return false
            }
        }
        Log.d(TAG, "[ACTION_QUEUE] sequence done success=true")
        return true
    }

    fun clear() {
        scope.launch {
            mutex.withLock {
                pending.clear()
            }
            Log.d(TAG, "[ACTION_QUEUE] cleared")
        }
    }

    private suspend fun runWorker() {
        if (isRunning) {
            return
        }
        isRunning = true
        try {
            while (true) {
                val action = mutex.withLock {
                    pending.pollFirst()
                } ?: break

                Log.d(TAG, "[ACTION_QUEUE] dequeue action=$action")
                BotDiagnosticJournal.record(TAG, "dequeue $action")
                if (!ActionExecutor.execute(action)) {
                    Log.w(TAG, "[ACTION_QUEUE] stopped on failure action=$action")
                    BotDiagnosticJournal.record(TAG, "failed $action")
                    mutex.withLock { pending.clear() }
                    break
                }
            }
        } finally {
            isRunning = false
        }
    }
}

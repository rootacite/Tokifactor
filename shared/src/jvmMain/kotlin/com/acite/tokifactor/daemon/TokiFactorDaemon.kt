package com.acite.tokifactor.daemon

import com.acite.tokifactor.DaemonGraph
import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object TokiFactorDaemon {
    fun run() {
        val graph = createGraph<DaemonGraph>()
        val engine = graph.chatEngine
        engine.setFrontendActive(false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val notifier = LinuxNotifier(scope) { senderId ->
            withContext(Dispatchers.Main.immediate) {
                engine.peerAvatars[senderId]?.copyOf()
            }
        }
        val hub = DaemonStateHub(engine, scope)
        val server = DaemonSocketServer(
            socketFile = daemonSocketFile(),
            hub = hub,
            commandHandler = DaemonCommandHandler(engine, graph.settingsStore),
            scope = scope,
        )

        scope.launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            engine.inboundNotices.collect { notice -> notifier.notify(notice) }
        }
        hub.start()
        server.start()
        engine.start()

        Runtime.getRuntime().addShutdownHook(
            Thread {
                server.close()
                runBlocking {
                    withTimeoutOrNull(2_000L) {
                        try {
                            graph.mqttService.disconnect()
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        )

        println("tokifactord: started")
        runBlocking { awaitCancellation() }
    }
}

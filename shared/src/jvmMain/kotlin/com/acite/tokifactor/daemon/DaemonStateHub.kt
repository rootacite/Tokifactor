package com.acite.tokifactor.daemon

import com.acite.tokifactor.services.ChatEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface DaemonClientHub {
    suspend fun attach(): DaemonStateHub.AttachedClient
    fun detach(client: DaemonStateHub.AttachedClient)
}

class DaemonStateHub(
    private val engine: ChatEngine,
    private val scope: CoroutineScope,
) : DaemonClientHub {
    private val lock = Any()
    private val clients = mutableSetOf<ClientSink>()
    private var revision = 0L

    fun start() {
        scope.launch(Dispatchers.Main.immediate, start = CoroutineStart.UNDISPATCHED) {
            engine.events.collect { engineEvent ->
                val event = engineEvent.toDaemonEvent() ?: return@collect
                val frame = DaemonServerFrame(
                    type = DaemonProtocol.TYPE_EVENT,
                    revision = synchronized(lock) { ++revision },
                    event = event,
                )
                val line = DaemonJson.encodeToString(DaemonServerFrame.serializer(), frame)
                synchronized(lock) {
                    clients.forEach { it.enqueue(line) }
                }
            }
        }
    }

    override suspend fun attach(): AttachedClient = withContext(Dispatchers.Main.immediate) {
        synchronized(lock) {
            val sink = ClientSink()
            clients += sink
            val snapshot = DaemonServerFrame(
                type = DaemonProtocol.TYPE_SNAPSHOT,
                revision = revision,
                snapshot = engine.snapshot(),
            )
            AttachedClient(
                line = DaemonJson.encodeToString(DaemonServerFrame.serializer(), snapshot),
                sink = sink,
            )
        }
    }

    override fun detach(client: AttachedClient) {
        synchronized(lock) { clients -= client.sink }
        client.sink.close()
    }

    class AttachedClient(
        val line: String,
        val sink: ClientSink,
    )

    class ClientSink {
        private val lock = Any()
        private val pending = ArrayDeque<String>()
        private var active = false
        val channel = Channel<String>(Channel.UNLIMITED)

        fun enqueue(line: String) {
            synchronized(lock) {
                if (active) {
                    channel.trySend(line)
                } else {
                    pending.addLast(line)
                }
            }
        }

        fun activate() {
            synchronized(lock) {
                active = true
                while (pending.isNotEmpty()) {
                    channel.trySend(pending.removeFirst())
                }
            }
        }

        suspend fun send(line: String) {
            channel.send(line)
        }

        fun close() {
            channel.close()
        }
    }
}

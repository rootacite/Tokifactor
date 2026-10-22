package com.acite.tokifactor.daemon

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files

class DaemonSocketServer(
    private val socketFile: File,
    private val hub: DaemonClientHub,
    private val commandHandler: DaemonCommandExecutor,
    private val scope: CoroutineScope,
) {
    @Volatile
    private var serverChannel: ServerSocketChannel? = null

    fun start() {
        prepareSocketPath()
        val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
        server.bind(UnixDomainSocketAddress.of(socketFile.toPath()))
        setSocketPermissions(socketFile)
        serverChannel = server
        println("tokifactord: listening on ${socketFile.absolutePath}")
        scope.launch(Dispatchers.IO) {
            while (scope.isActive) {
                val client = try {
                    server.accept()
                } catch (_: Exception) {
                    break
                }
                scope.launch(Dispatchers.IO) { handleClient(client) }
            }
        }
    }

    fun close() {
        try {
            serverChannel?.close()
        } catch (_: Exception) {
        }
        serverChannel = null
        Files.deleteIfExists(socketFile.toPath())
    }

    private fun prepareSocketPath() {
        if (!socketFile.exists()) return
        try {
            SocketChannel.open(StandardProtocolFamily.UNIX).use { probe ->
                probe.connect(UnixDomainSocketAddress.of(socketFile.toPath()))
            }
            error("tokifactord is already running at ${socketFile.absolutePath}")
        } catch (e: IllegalStateException) {
            throw e
        } catch (_: Exception) {
            Files.deleteIfExists(socketFile.toPath())
        }
    }

    private suspend fun handleClient(channel: SocketChannel) = coroutineScope {
        val reader = BufferedReader(InputStreamReader(Channels.newInputStream(channel), Charsets.UTF_8))
        val writer = BufferedWriter(OutputStreamWriter(Channels.newOutputStream(channel), Charsets.UTF_8))
        val writeLock = Mutex()
        var attached: DaemonStateHub.AttachedClient? = null

        suspend fun send(frame: DaemonServerFrame) {
            writeLock.withLock {
                writer.write(DaemonJson.encodeToString(DaemonServerFrame.serializer(), frame))
                writer.newLine()
                writer.flush()
            }
        }

        try {
            val helloLine = reader.readLine() ?: return@coroutineScope
            val hello = try {
                DaemonJson.decodeFromString(DaemonClientFrame.serializer(), helloLine)
            } catch (_: Exception) {
                send(DaemonServerFrame(type = DaemonProtocol.TYPE_ERROR, ok = false, error = "invalid hello"))
                return@coroutineScope
            }
            if (hello.type != DaemonProtocol.TYPE_HELLO || hello.version != DaemonProtocol.VERSION) {
                send(
                    DaemonServerFrame(
                        type = DaemonProtocol.TYPE_ERROR,
                        ok = false,
                        error = "unsupported protocol version",
                    )
                )
                return@coroutineScope
            }

            val client = hub.attach()
            attached = client
            writeLock.withLock {
                writer.write(client.line)
                writer.newLine()
                writer.flush()
            }
            client.sink.activate()

            launch {
                try {
                    for (line in client.sink.channel) {
                        writeLock.withLock {
                            writer.write(line)
                            writer.newLine()
                            writer.flush()
                        }
                    }
                } catch (_: Exception) {
                }
            }

            while (scope.isActive) {
                val line = reader.readLine() ?: break
                val frame = try {
                    DaemonJson.decodeFromString(DaemonClientFrame.serializer(), line)
                } catch (_: Exception) {
                    continue
                }
                if (frame.type != DaemonProtocol.TYPE_COMMAND) continue
                val result = commandHandler.handle(frame)
                send(
                    DaemonServerFrame(
                        type = if (result.ok) DaemonProtocol.TYPE_ACK else DaemonProtocol.TYPE_ERROR,
                        requestId = frame.requestId,
                        ok = result.ok,
                        error = result.error,
                    )
                )
            }
        } catch (_: Exception) {
        } finally {
            attached?.let { hub.detach(it) }
            try {
                channel.close()
            } catch (_: Exception) {
            }
        }
    }
}

package com.acite.tokifactor.daemon

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DaemonSocketServerTest {
    @Test
    fun helloReceivesSnapshotAndCommandReceivesAck() = runBlocking {
        val dir = Files.createTempDirectory("tokifactord-socket-test").toFile()
        val socketFile = File(dir, "tokifactord.sock")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val sink = DaemonStateHub.ClientSink()
        val snapshot = DaemonServerFrame(
            type = DaemonProtocol.TYPE_SNAPSHOT,
            revision = 3L,
            snapshot = DaemonSnapshot(status = "Connected", connected = true),
        )
        val hub = object : DaemonClientHub {
            override suspend fun attach(): DaemonStateHub.AttachedClient = DaemonStateHub.AttachedClient(
                line = DaemonJson.encodeToString(DaemonServerFrame.serializer(), snapshot),
                sink = sink,
            )

            override fun detach(client: DaemonStateHub.AttachedClient) {
                client.sink.close()
            }
        }
        val server = DaemonSocketServer(
            socketFile = socketFile,
            hub = hub,
            commandHandler = DaemonCommandExecutor { DaemonCommandResult(true) },
            scope = scope,
        )

        try {
            server.start()
            withTimeout(3_000L) {
                while (!socketFile.exists()) delay(20L)
            }

            SocketChannel.open(StandardProtocolFamily.UNIX).use { channel ->
                channel.connect(UnixDomainSocketAddress.of(socketFile.toPath()))
                val writer = BufferedWriter(
                    OutputStreamWriter(Channels.newOutputStream(channel), Charsets.UTF_8)
                )
                writer.write(
                    DaemonJson.encodeToString(
                        DaemonClientFrame.serializer(),
                        DaemonClientFrame(type = DaemonProtocol.TYPE_HELLO),
                    )
                )
                writer.newLine()
                writer.write(
                    DaemonJson.encodeToString(
                        DaemonClientFrame.serializer(),
                        DaemonClientFrame(command = DaemonProtocol.COMMAND_PING, requestId = "r1"),
                    )
                )
                writer.newLine()
                writer.flush()

                val reader = BufferedReader(
                    InputStreamReader(Channels.newInputStream(channel), Charsets.UTF_8)
                )
                val snapshotLine = withContext(Dispatchers.IO) {
                    withTimeout(3_000L) { reader.readLine() }
                }
                val ackLine = withContext(Dispatchers.IO) {
                    withTimeout(3_000L) { reader.readLine() }
                }
                val snapshotFrame = DaemonJson.decodeFromString(
                    DaemonServerFrame.serializer(),
                    snapshotLine,
                )
                val ack = DaemonJson.decodeFromString(DaemonServerFrame.serializer(), ackLine)

                assertEquals(DaemonProtocol.TYPE_SNAPSHOT, snapshotFrame.type)
                assertEquals(3L, snapshotFrame.revision)
                assertTrue(snapshotFrame.snapshot?.connected == true)
                assertEquals(DaemonProtocol.TYPE_ACK, ack.type)
                assertEquals("r1", ack.requestId)
            }
        } finally {
            server.close()
            scope.cancel()
            dir.deleteRecursively()
        }
    }
}

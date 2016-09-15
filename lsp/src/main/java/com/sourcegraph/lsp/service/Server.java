package com.sourcegraph.lsp.service;

import com.sourcegraph.lsp.LanguageServer;
import com.sourcegraph.lsp.NoCheckAliveLanguageServerEndpoint;
import io.typefox.lsapi.services.json.MessageJsonHandler;
import io.typefox.lsapi.services.json.StreamMessageReader;
import io.typefox.lsapi.services.json.StreamMessageWriter;
import io.typefox.lsapi.services.transport.io.ConcurrentMessageReader;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.Channels;
import java.util.concurrent.*;

/**
 *  This server listens at [address]:port for incoming connections and handles LSP requests
 */
@Service
public class Server {

    private static final Logger LOGGER = LoggerFactory.getLogger(Server.class);

    @Autowired
    private LanguageServer languageServer;

    @Value("${server.port:2088}")
    private int port;

    @Value("${server.address:}")
    private String address;

    @Value("${server.connectors.core.size:10}")
    private int numConnectors;

    @Value("${server.connectors.max.size:50}")
    private int maxConnectors;

    public void open() throws IOException {
        InetAddress address = null;
        if (!StringUtils.isEmpty(this.address)) {
            address = InetAddress.getByName(this.address);
        }

        ExecutorService executorService = new ThreadPoolExecutor(numConnectors,
                maxConnectors,
                Integer.MAX_VALUE,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>());

        MessageJsonHandler jsonHandler = new MessageJsonHandler();

        SocketAddress socketAddress = new InetSocketAddress(address, port);
        AsynchronousServerSocketChannel socket = AsynchronousServerSocketChannel.open();
        socket.bind(socketAddress);

        LOGGER.info("LSP server is listening on {}:{}", this.address, this.port);

        while (true) {
            AsynchronousSocketChannel channel;
            try {
                channel = socket.accept().get();
            } catch (InterruptedException | ExecutionException e) {
                LOGGER.error("Failed to accept incoming connection", e);
                socket.close();
                break;
            }
            String id;
            try {
                id = channel.getRemoteAddress().toString();
            } catch (IOException e) {
                LOGGER.error("Failed to accept incoming connection", e);
                break;
            }
            LOGGER.info("New connection from {}", id);
            InputStream in = Channels.newInputStream(channel);
            StreamMessageReader reader = new StreamMessageReader(in, jsonHandler);
            ConcurrentMessageReader concurrentReader = new ConcurrentMessageReader(reader, executorService);
            OutputStream out = Channels.newOutputStream(channel);
            StreamMessageWriter writer = new StreamMessageWriter(out, jsonHandler);

            new NoCheckAliveLanguageServerEndpoint(languageServer, executorService).connect(concurrentReader, writer);
            concurrentReader.join();
            try {
                channel.close();
            } catch (IOException ex) {
                // ignore
            }
            LOGGER.info("Closed connection from {}", id);
        }
    }

}

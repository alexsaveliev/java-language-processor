package com.sourcegraph.lsp.single.service;

import com.sourcegraph.lsp.common.LanguageServer;
import io.typefox.lsapi.services.json.MessageJsonHandler;
import io.typefox.lsapi.services.json.StreamMessageReader;
import io.typefox.lsapi.services.json.StreamMessageWriter;
import io.typefox.lsapi.services.transport.io.ConcurrentMessageReader;
import io.typefox.lsapi.services.transport.server.LanguageServerEndpoint;
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
import java.net.Socket;
import java.net.SocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 *  This server connects to remove [address]:port and handles LSP requests
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
        Socket socket = new Socket();
        socket.connect(socketAddress);
        InputStream in = socket.getInputStream();
        StreamMessageReader reader = new StreamMessageReader(in, jsonHandler);
        ConcurrentMessageReader concurrentReader = new ConcurrentMessageReader(reader, executorService);
        OutputStream out = socket.getOutputStream();
        StreamMessageWriter writer = new StreamMessageWriter(out, jsonHandler);

        new LanguageServerEndpoint(languageServer, executorService).connect(concurrentReader, writer);
        concurrentReader.join();
        try {
            socket.close();
        } catch (IOException ex) {
            // ignore
        }

        LOGGER.info("LSP server is communicating with {}:{}", this.address, this.port);
    }


}

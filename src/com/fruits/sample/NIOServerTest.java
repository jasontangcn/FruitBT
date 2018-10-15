package com.fruits.sample;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class NIOServerTest {
	public static void main(String[] args) throws Exception {
		Selector selector = Selector.open();
		ServerSocketChannel serverChannel = ServerSocketChannel.open();
		serverChannel.configureBlocking(false);
		serverChannel.socket().setReuseAddress(true);
		serverChannel.socket().bind(new InetSocketAddress("127.0.0.1", 8888));
		serverChannel.register(selector, SelectionKey.OP_ACCEPT);

		for (;;) {
			selector.select();
			System.out.println("I am back, I have selected something.");
			Iterator selectedKeys = selector.selectedKeys().iterator();
			while (selectedKeys.hasNext()) {
				SelectionKey key = (SelectionKey) selectedKeys.next();
				selectedKeys.remove();

				if (!key.isValid())
					continue;

				if (key.isAcceptable()) {
					ServerSocketChannel serverSocket = (ServerSocketChannel) key.channel();
					SocketChannel socketChannel = serverSocket.accept();
					socketChannel.configureBlocking(false);
					System.out.println("Accepted : " + socketChannel.socket().getRemoteSocketAddress());
					socketChannel.register(selector, SelectionKey.OP_READ);
					//socketChannel.register(selector, SelectionKey.OP_WRITE);
					//Thread.sleep(120 * 1000);
				} else if (key.isReadable()) {
					System.out.println("Start to read data.");
					SocketChannel socketChannel = (SocketChannel) key.channel();
					System.out.println("This channel is open? " + socketChannel.isOpen());
					ByteBuffer buffer = ByteBuffer.allocate(1024);
					while (socketChannel.read(buffer) > 0) {
						System.out.println(new String(buffer.array()));
						buffer.clear();
					}
					System.out.println("I am going to sleep for 30 seconds.");
					Thread.sleep(30 * 1000);
					System.out.println("30 seconds passed, I am wakeup.");
					System.out.println("This channel is open? " + socketChannel.isOpen());
					socketChannel.register(selector, SelectionKey.OP_WRITE);
					socketChannel.close();

				} else if (key.isWritable()) {
					System.out.println("Ready to write data");

					SocketChannel socketChannel = (SocketChannel)key.channel();
					System.out.println("This channel is open? " + socketChannel.isOpen());
					ByteBuffer buffer = ByteBuffer.allocate(1024);
					for(int i = 0; i < 10; i++) {
						buffer.clear();
						buffer.put(("Hello from server [" + i + "].").getBytes());
						buffer.flip();
						socketChannel.write(buffer);
						//Thread.sleep(5 * 1000);
					}

					//socketChannel.close();
				}

			}
		}
	}
}

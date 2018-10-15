package com.fruits.sample;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class NIOClientTest {
	public static void main(String[] args) throws Exception {
		Selector selector = Selector.open();
		SocketChannel socketChannel = SocketChannel.open();
		socketChannel.configureBlocking(false);
		socketChannel.socket().setReuseAddress(true);
		//socketChannel.bind(new InetSocketAddress("127.0.0.1",6666));
		socketChannel.connect(new InetSocketAddress("127.0.0.1", 8888));
		socketChannel.register(selector, SelectionKey.OP_CONNECT);

		for (;;) {
			selector.select();
			System.out.println("I am Client, I am back, I have selected something.");
			Iterator selectedKeys = selector.selectedKeys().iterator();
			while (selectedKeys.hasNext()) {
				SelectionKey key = (SelectionKey) selectedKeys.next();
				selectedKeys.remove();
				if (!key.isValid())
					continue;
				if (key.isConnectable()) {
					SocketChannel channel = (SocketChannel) key.channel();
					if (channel.finishConnect()) {
						channel.register(selector, SelectionKey.OP_WRITE);
						//channel.register(selector, SelectionKey.OP_WRITE);
						System.out.println("Connected to " + socketChannel.socket().getRemoteSocketAddress());
					}
				} else if (key.isReadable()) {
					System.out.println("Start to read data from server.");
					SocketChannel channel = (SocketChannel) key.channel();
					ByteBuffer buffer = ByteBuffer.allocate(18);
					int n = channel.read(buffer);
					//if(n == -1) channel.close();
				} else if (key.isWritable()) {
					SocketChannel channel = (SocketChannel) key.channel();
					System.out.println("This channel is open? " + channel.isOpen());
					ByteBuffer buffer = ByteBuffer.allocate(1024);
					buffer.put("Hello Stranger!".getBytes());
					buffer.flip();
					System.out.println("Writing data to server.");
					while (buffer.hasRemaining()) {
						channel.write(buffer);
					}
					System.out.println("Completed writing data.");
					System.out.println("I am going to sleep for 15 seconds.");
					//channel.close();
					Thread.sleep(15 * 1000);
					System.out.println("15 seconds passed, I am weakup.");
					//channel.register(selector, SelectionKey.OP_READ);
					// channel.close();
					// key.cancel();
				}
			}
		}
	}
}

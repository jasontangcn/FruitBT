package com.fruits.bt;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

public class Helper {
	public static boolean createFile(String filePath) throws IOException {
		File file = new File(filePath);
		if (file.exists())
			return false;

		File parent = file.getParentFile();
		if (parent != null)
			Files.createDirectories(Paths.get(parent.getPath()));
		Files.createFile(Paths.get(file.getPath()));

		return true;
	}

	public static void closeChannel(SocketChannel channel) {
		try {
			if (channel != null && channel.isOpen())
				channel.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void closeServerChannel(ServerSocketChannel serverChannel) {
		try {
			if (serverChannel != null && serverChannel.isOpen())
				serverChannel.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void closeSelector(Selector selector) {
		try {
			if (selector != null && selector.isOpen())
				selector.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static boolean isInterested(Bitmap a, Bitmap b) {
		System.out
				.println("a-> " + a + " [length = " + a.length() + ", size = " + a.size() + "], b-> " + b + " [length = " + b.length() + ", size = " + b.size() + "].");
		// TODO: validate parameters.
		boolean interested = false;
		for (int i = 0; i < b.size(); i++) {
			if (b.get(i) && !a.get(i)) {
				interested = true;
				break;
			}
		}
		System.out.println("a is interested in b? " + interested);
		return interested;
	}
}

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
	public static byte[] genClientId() {
		ByteBuffer buffer = ByteBuffer.allocate(20);
		buffer.put("-FRT-".getBytes());
		
    String ip = "";
    try {
    	ip = InetAddress.getLocalHost().getHostAddress();
    }catch(IOException e) {
    	e.printStackTrace();
    }
    Map<String, String> env = System.getenv();
    String userDomain = env.get("USERDOMAIN");
    String computerName = env.get("COMPUTERNAME");
    String userName = env.get("USERNAME");
    
    StringBuffer sb = new StringBuffer();
    
    if(userDomain != null && userDomain.length() > 0) {
    	sb.append(userDomain).append("-");
    }
    
    if(computerName != null && computerName.length() > 0) {
    	sb.append(computerName).append("-");
    }
    
    if(userName != null && userName.length() > 0) {
    	sb.append(userName);
    }
    
    byte[] bytes = sb.toString().getBytes();
    for(byte bt : bytes) {
    	if(buffer.hasRemaining()) {
    		buffer.put(bt);
    	}else {
    		break;
    	}
    }
    
    while(buffer.hasRemaining()) {
    	buffer.putInt((byte)'-');
    }
    
    return buffer.array();
	}
	
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

	public static boolean isInterested(Bitmap p, Bitmap k) {
		System.out
				.println("p-> " + p + " [length = " + p.length() + ", size = " + p.size() + "], p-> " + k + " [length = " + k.length() + ", size = " + k.size() + "].");
		// TODO: validate parameters.
		boolean interested = false;
		for (int i = 0; i < k.size(); i++) {
			if (k.get(i) && !p.get(i)) {
				interested = true;
				break;
			}
		}
		System.out.println("a is interested in b? " + interested);
		return interested;
	}
}

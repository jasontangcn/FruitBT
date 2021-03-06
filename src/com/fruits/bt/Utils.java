package com.fruits.bt;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.turn.ttorrent.bcodec.BDecoder;
import com.turn.ttorrent.bcodec.BEValue;

public class Utils {
	static final Logger logger = LoggerFactory.getLogger(Utils.class);
	
	public static byte[] getSHA1(byte[] data) throws NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance("SHA1");
		md.update(data);
		return md.digest();
	}

	public static String bytes2HexString(byte[] data) {
		if(data == null) return null;
		String hexChars = "0123456789abcdef";
		StringBuilder sb = new StringBuilder();
		for (byte v : data) {
			sb.append(hexChars.charAt((v >> 4) & 0x0f));
			sb.append(hexChars.charAt(v & 0x0f));
		}
		return sb.toString();
	}

	public static byte[] hexStringToBytes(String hexString) {
		hexString = hexString.toUpperCase();
		int length = hexString.length() / 2;
		char[] hexChars = hexString.toCharArray();
		byte[] d = new byte[length];
		for (int i = 0; i < length; i++) {
			int pos = i * 2;
			d[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));
		}
		return d;
	}

	private static byte charToByte(char c) {
		return (byte) "0123456789ABCDEF".indexOf(c);
	}

	// TODO: Rewrite, looks this code is too complex.
	public static ByteBuffer readFile(String filePath) throws IOException {
		FileInputStream fis = null;
		FileChannel channel = null;
		ByteArrayOutputStream os = new ByteArrayOutputStream();

		try {
			File file = new File(filePath);
			if (!file.exists())
				return null;

			fis = new FileInputStream(file);
			channel = fis.getChannel();

			ByteBuffer buffer = ByteBuffer.allocate(1024);

			while (-1 != channel.read(buffer)) {
				buffer.flip();
				byte[] bytes = new byte[buffer.remaining()];
				buffer.get(bytes);
				os.write(bytes);
				buffer.clear();
			}
		} finally {
			if (channel != null && channel.isOpen()) {
				try {
					channel.close();
				} catch (IOException e) {
					logger.error("", e);
				}
			}

			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {
					logger.error("", e);
				}
			}

			if (os != null) {
				try {
					os.close();
				} catch (IOException e) {
					logger.error("", e);
				}
			}
		}

		return ByteBuffer.wrap(os.toByteArray());
	}

	public static CharBuffer readTextFile(String filePath, String charset) throws IOException, CharacterCodingException {
		ByteBuffer content = readFile(filePath);
		CharsetDecoder decoder = Charset.forName(charset).newDecoder();
		return decoder.decode(content);
	}
}

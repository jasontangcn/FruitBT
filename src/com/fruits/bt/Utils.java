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

import com.turn.ttorrent.bcodec.BDecoder;
import com.turn.ttorrent.bcodec.BEValue;

public class Utils {
	public static byte[] getSHA1(byte[] data) throws NoSuchAlgorithmException {
		MessageDigest md = MessageDigest.getInstance("SHA1");
		md.update(data);
		return md.digest();
	}

	public static String bytes2HexString(byte[] data) {
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
			if ((channel != null) && (channel.isOpen())) {
				try {
					channel.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			if (os != null) {
				try {
					os.close();
				} catch (IOException e) {
					e.printStackTrace();
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

	public static void main(String[] args) throws IOException {
		Map<String, BEValue> values = BDecoder.bdecode(Utils.readFile("doc\\ubuntu-18.04.1-desktop-amd64.iso.torrent")).getMap();
		String announce = values.get("announce").getString();
		BEValue announceList = values.get("announce-list");
		long creationDate = values.get("creation date").getLong();
		String comment = values.get("comment").getString();
		Map<String, BEValue> info = values.get("info").getMap();
		int length = info.get("length").getInt();
		String name = info.get("name").getString();
		int pieceLength = info.get("piece length").getInt();
		byte[] hashs = info.get("pieces").getBytes();

		System.out.println(announce);
		System.out.println(announceList);
		System.out.println(creationDate);
		System.out.println(comment);
		System.out.println(length);
		System.out.println(name);
		System.out.println(pieceLength);
		System.out.println(hashs.length);

		System.out.println(values.size());
		System.out.println(info.size());
		// BEValue creationDate = info.get("length");

	}
}

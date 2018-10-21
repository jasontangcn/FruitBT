package com.fruits.sample;

import java.io.IOException;
import java.util.Map;

import com.fruits.bt.Utils;
import com.turn.ttorrent.bcodec.BDecoder;
import com.turn.ttorrent.bcodec.BEValue;

public class UtilsTest {
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

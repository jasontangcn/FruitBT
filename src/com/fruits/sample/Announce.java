package com.fruits.sample;

import java.io.File;
import java.io.FileInputStream;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Map;

import com.fruits.bt.Utils;
import com.turn.ttorrent.bcodec.BDecoder;
import com.turn.ttorrent.bcodec.BEValue;
import com.turn.ttorrent.bcodec.BEncoder;

public class Announce {
	public static final String METAINFO_ANNOUNCE = "announce";
	public static final String METAINFO_ANNOUNCE_LIST = "announce list";
	public static final String METAINFO_CREATION_DATE = "creation date";
	public static final String METAINFO_CREATED_BY = "created by";
	public static final String METAINFO_COMMENT = "comment";
	public static final String METAINFO_INFO = "info";
	public static final String METAINFO_INFO_PIECE_LENGTH = "piece length"; // 2^18 = 256K, BitTorrent 3.2 or older uses 1M.
	public static final String METAINFO_INFO_PIECES = "pieces"; // one piece, one SHA1 hash(20 bytes).
	public static final String METAINFO_INFO_PRIVATE = "private"; // clients get peers only from tracker
	public static final String METAINFO_INFO_NAME = "name"; // file name or directory name
	public static final String METAINFO_INFO_LENGTH = "length"; // length of the file if only one file to be downloaded.
	public static final String METAINFO_INFO_MD5SUM = "md5sum"; // 
	public static final String METAINFO_INFO_FILES = "files"; //
	public static final String METAINFO_INFO_FILES_LENGTH = "length"; // 
	public static final String METAINFO_INFO_FILES_PATH = "path"; // 
	public static final String METAINFO_INFO__FILES_MD5SUM = "md5sum"; // 

	public static void main(String[] args) throws Exception {
		File seed = new File("doc\\Wireshark-win32-1.10.0.exe.torrent");
		if (seed.exists()) {
			FileInputStream fis = new FileInputStream(seed);
			BDecoder decoder = new BDecoder(fis);
			Map<String, BEValue> metainfo = decoder.bdecode().getMap();

			String announce = metainfo.get(Announce.METAINFO_ANNOUNCE).getString();
			Map<String, BEValue> info = metainfo.get(Announce.METAINFO_INFO).getMap();
			byte[] sha1hash = Utils.getSHA1(BEncoder.bencode(info).array());

			System.out.println(sha1hash.length);
			System.out.println(new String(sha1hash, "UTF-8"));
			System.out.println(new String(sha1hash, "UTF-16").length());
			System.out.println(new String(sha1hash, "ISO-8859-1"));
			System.out.println(new String(sha1hash, "ISO-8859-1").length());
			System.out.println(Utils.bytes2HexString(sha1hash));
			System.out.println(Charset.defaultCharset());

			System.out.println(announce);

			String info_hash = URLEncoder.encode(new String(sha1hash, "ISO-8859-1"), "ISO-8859-1");
			//       76        BF        8B C6 4B 4B F1 98 8C 6B 5C BD 54 15 36 DC 10 0F 22 B0
			//0111 0110 1011 1111 1000 1011
			//64 + 32 +16 + 4 +2 = 118
			//%B3 %C8 %F8 %E5 %0D %3F %3Fp %11W %F2 %C2Q~ %EExX %8BH %F2

			String peer_id = URLEncoder.encode(
					new String(Utils.hexStringToBytes("5FAC36EF8B2E2D0F1B4D5FAC36EF8B2E2D0F1B4D"), "ISO-8859-1"), "ISO-8859-1");

			System.out.println(info_hash);
			System.out.println(peer_id);

			String ip = "10.245.29.219";
			String port = "5555";
			String uploaded = "0";
			String downloaded = "0";
			String left = "0";

			//String request = "http://torrent.ubuntu.com:6969/announce?info_hash=e84213a794f3ccd890382a54a64ca68b7e925433&peer_id=Pi314159265358979323&port=5555&&uploaded=0&downloaded=0&left=0&event=started";

			//String request = "http://192.168.0.100/announce?info_hash=%B3%C8%F8%E5%0D%3F%3Fp%11W%F2%C2Q%7E%EExX%8BH%F2&peer_id=_%AC6%EF%8B.-%0F%1BM_%AC6%EF%8B.-%0F%1BM&port=5555&uploaded=0&downloaded=0&left=22238848&event=started";
			//BitComet
			//http://192.168.0.100:6969/announce?info_hash=%B3%C8%F8%E5%0D%3F%3Fp%11W%F2%C2Q~%EExX%8BH%F2&peer_id=-BC0152-%EA%0D%CC%0A%0CF%86%EEl%3D%9A%04&port=25146&natmapped=1&localip=192.168.0.100&port_type=lan&uploaded=0&downloaded=0&left=0&numwant=200&compact=1&no_peer_id=1&key=16264&event=started

			//d8:completei1e10:incompletei1e8:intervali1800e5:peersld2:ip13:192.168.0.1007:peer id20:M4-0-3--3b14882a02f84:porti6881eeee

			//fis.close();
		}
	}
}

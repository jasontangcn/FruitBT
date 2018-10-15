package com.fruits.bt;

import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.turn.ttorrent.bcodec.BDecoder;
import com.turn.ttorrent.bcodec.BEValue;
import com.turn.ttorrent.bcodec.BEncoder;

public class TorrentSeed implements Serializable {
	private static final long serialVersionUID = -2977695181171516029L;

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
	public static final String METAINFO_INFO_FILES_MD5SUM = "md5sum"; // 

	// Seed does not include slice length.
	public static final int DEFAULT_SLICE_LENGTH = 16 * 1024; // in bytes.

	private String announce;
	//file length
	private long length;
	private int pieceLength; // piece: 256K = 262144 bytes, slice : 16k
	// TODO: Change the default slice length to 16K.
	// To make the demo simpler, I use 16K * 4 as the default slice length.
	private int sliceLength = 16 * 1024 * 4; // It should = DEFAULT_SLICE_LENGTH;
	private String name;
	private List<String> pieceHashs;
	private String md5sum;
	private String infoHash;

	public String getAnnounce() {
		return announce;
	}

	public void setAnnounce(String announce) {
		this.announce = announce;
	}

	public long getLength() {
		return length;
	}

	public void setLength(long length) {
		this.length = length;
	}

	public int getPieceLength() {
		return pieceLength;
	}

	public void setPieceLength(int pieceLength) {
		this.pieceLength = pieceLength;
	}

	public int getSliceLength() {
		return sliceLength;
	}

	public void setSliceLength(int sliceLength) {
		this.sliceLength = sliceLength;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setPieceHashs(List<String> pieceHashs) {
		this.pieceHashs = pieceHashs;
	}

	public List<String> getPieceHashs() {
		return pieceHashs;
	}

	public String getMD5sum() {
		return md5sum;
	}

	public void setMD5sum(String md5sum) {
		this.md5sum = md5sum;
	}

	public String getInfoHash() {
		return infoHash;
	}

	public void setInfoHash(String infoHash) {
		this.infoHash = infoHash;
	}

	@Override
	public String toString() {
		return "TorrentSeed [announce = " + announce + ", length = " + length + ", pieceLength = " + pieceLength + ", sliceLength = " + sliceLength + ", name = "
				+ name + ", pieceHashs = " + pieceHashs + ", md5sum = " + md5sum + ", infoHash = " + infoHash + "].\n";
	}

	public static TorrentSeed parseSeedFile(String seedFilePath) throws IOException {
		ByteBuffer seedFile = Utils.readFile(seedFilePath);
		if (seedFile == null)
			return null;

		Map<String, BEValue> values = BDecoder.bdecode(seedFile).getMap();
		// "announce"
		BEValue annouceValue = values.get(TorrentSeed.METAINFO_ANNOUNCE);
		// "announce-list"
		BEValue announceListValue = values.get(TorrentSeed.METAINFO_ANNOUNCE_LIST);
		// "creation date"
		BEValue creationDateValue = values.get(TorrentSeed.METAINFO_CREATION_DATE);
		// "comment"
		BEValue commentValue = values.get(TorrentSeed.METAINFO_COMMENT);

		String announce = null;
		if (annouceValue != null)
			announce = annouceValue.getString();

		long creationDate = -1;
		//TODO: NULL?
		if (creationDateValue != null)
			creationDate = creationDateValue.getLong();

		String comment = null; //
		if (commentValue != null)
			comment = commentValue.getString();

		// "info"
		Map<String, BEValue> info = values.get(TorrentSeed.METAINFO_INFO).getMap();
		//TODO: Is it a long or int?
		// "length"
		long length = info.get(TorrentSeed.METAINFO_INFO_LENGTH).getLong();
		// "name"
		String name = info.get(TorrentSeed.METAINFO_INFO_NAME).getString();
		// "piece length"
		//TODO: Is it a int or long?
		int pieceLength = info.get(TorrentSeed.METAINFO_INFO_PIECE_LENGTH).getInt();

		// "pieces"
		byte[] pieceHashsBytes = info.get(TorrentSeed.METAINFO_INFO_PIECES).getBytes();
		List<String> pieceHashs = new ArrayList<String>();
		for (int i = 0; i < (pieceHashsBytes.length / 20); i++) {
			byte[] pieceHashByte = Arrays.copyOfRange(pieceHashsBytes, i * 20, (i + 1) * 20);
			pieceHashs.add(Utils.bytes2HexString(pieceHashByte));
		}

		TorrentSeed torrentSeed = new TorrentSeed();
		torrentSeed.setAnnounce(announce);

		torrentSeed.setLength(length);
		torrentSeed.setPieceLength(pieceLength);
		torrentSeed.setPieceHashs(pieceHashs);
		torrentSeed.setName(name);

		try {
			byte[] infoHash = Utils.getSHA1(BEncoder.bencode(values.get(TorrentSeed.METAINFO_INFO).getMap()).array());
			torrentSeed.setInfoHash(Utils.bytes2HexString(infoHash));
		}catch(Exception e) {
			throw new IOException(e);
		}

		return torrentSeed;
	}
}

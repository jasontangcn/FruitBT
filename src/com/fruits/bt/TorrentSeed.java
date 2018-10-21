package com.fruits.bt;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
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
	public static final String METAINFO_ENCODING = "encoding";

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
	private List<String> announceList;
	private long creationDate;
	private String createdBy;
	private String comment;
	private String encoding;

	private int pieceLength; // piece: 256K = 262144 bytes, slice : 16k
	private List<String> pieceHashs;
	private int pvt;
	// TODO: Change the default slice length to 16K.
	// To make the demo simpler, I use 16K * 4 as the default slice length.
	private int sliceLength = 16 * 1024 * 4; // It should = DEFAULT_SLICE_LENGTH;

	private String name;
	//file length
	private long length;
	private String md5sum;

	private List<FileInfo> fileInfos;

	private String infoHash;

	public String getAnnounce() {
		return announce;
	}

	public void setAnnounce(String announce) {
		this.announce = announce;
	}

	public List<String> getAnnounceList() {
		return announceList;
	}

	public void setAnnounceList(List<String> announceList) {
		this.announceList = announceList;
	}

	public long getCreationDate() {
		return creationDate;
	}

	public void setCreationDate(long creationDate) {
		this.creationDate = creationDate;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	public String getComment() {
		return comment;
	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	public String getEncoding() {
		return encoding;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	public int getPieceLength() {
		return pieceLength;
	}

	public void setPieceLength(int pieceLength) {
		this.pieceLength = pieceLength;
	}

	public void setPieceHashs(List<String> pieceHashs) {
		this.pieceHashs = pieceHashs;
	}

	public List<String> getPieceHashs() {
		return pieceHashs;
	}

	public int getPvt() {
		return pvt;
	}

	public void setPvt(int pvt) {
		this.pvt = pvt;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public long getLength() {
		return length;
	}

	public void setLength(long length) {
		this.length = length;
	}

	public String getMD5sum() {
		return md5sum;
	}

	public void setMD5sum(String md5sum) {
		this.md5sum = md5sum;
	}

	public int getSliceLength() {
		return sliceLength;
	}

	public void setSliceLength(int sliceLength) {
		this.sliceLength = sliceLength;
	}

	public List<FileInfo> getFileInfos() {
		return fileInfos;
	}

	public void setFileInfos(List<FileInfo> fileInfos) {
		this.fileInfos = fileInfos;
	}

	public String getInfoHash() {
		return infoHash;
	}

	public void setInfoHash(String infoHash) {
		this.infoHash = infoHash;
	}

	public boolean isDirectory() {
		if (this.length == -1 && this.fileInfos != null && this.fileInfos.size() > 0)
			return true;
		return false;
	}

	@Override
	public String toString() {
		return "TorrentSeed [announce=" + announce + ", announceList=" + announceList + ",\n" 
			+ "creationDate=" + creationDate + ", createdBy=" + createdBy + ", comment=" + comment + ", encoding=" + encoding + ", pieceLength=" + pieceLength + ",\n"
			+ "pieceHashs=" + pieceHashs + ",\n"
			+ "pvt=" + pvt + ", sliceLength=" + sliceLength + ", name=" + name + ", length=" + length + ", md5sum=" + md5sum + ",\n"
			+ "fileInfos=" + fileInfos + ",\n"
			+ "infoHash=" + infoHash + "]";
	}

	public static TorrentSeed parseSeedFile(String seedFilePath) throws IOException {
		ByteBuffer seedFile = Utils.readFile(seedFilePath);
		if (seedFile == null)
			return null;

		Map<String, BEValue> values = BDecoder.bdecode(seedFile).getMap();

		/*
		 * public static final String METAINFO_ANNOUNCE = "announce";
		 * public static final String METAINFO_ANNOUNCE_LIST = "announce list";
		 * public static final String METAINFO_CREATION_DATE = "creation date";
		 * public static final String METAINFO_CREATED_BY = "created by";
		 * public static final String METAINFO_COMMENT = "comment";
		 * 
		 */
		String announce = null;
		// "announce"
		BEValue annouceValue = values.get(TorrentSeed.METAINFO_ANNOUNCE);
		if (annouceValue != null)
			announce = annouceValue.getString();

		List<String> announceList = new ArrayList<String>();
		// "announce list"
		BEValue announceListValue = values.get(TorrentSeed.METAINFO_ANNOUNCE_LIST);
		if (announceListValue != null) {
			List<BEValue> vs = announceListValue.getList();
			for (BEValue v : vs) {
				announceList.add(v.getString());
			}
		}

		long creationDate = -1;
		// "creation date"
		BEValue creationDateValue = values.get(TorrentSeed.METAINFO_CREATION_DATE);
		//TODO: NULL?
		if (creationDateValue != null)
			creationDate = creationDateValue.getLong();

		String createdBy = null;
		// "creation date"
		BEValue createdByValue = values.get(TorrentSeed.METAINFO_CREATED_BY);
		if (createdByValue != null)
			createdBy = createdByValue.getString();

		String comment = null;
		// "comment"
		BEValue commentValue = values.get(TorrentSeed.METAINFO_COMMENT);
		if (commentValue != null)
			comment = commentValue.getString();

		String encoding = null;
		// "comment"
		BEValue encodingValue = values.get(TorrentSeed.METAINFO_ENCODING);
		if (encodingValue != null)
			encoding = encodingValue.getString();

		TorrentSeed seed = new TorrentSeed();
		seed.setAnnounce(announce);
		seed.setAnnounceList(announceList);
		seed.setCreationDate(creationDate);
		seed.setCreatedBy(createdBy);
		seed.setComment(comment);
		seed.setEncoding(encoding);

		/*
		 * public static final String METAINFO_INFO = "info";
		 * public static final String METAINFO_INFO_PIECE_LENGTH = "piece length"; // 2^18 = 256K, BitTorrent 3.2 or older uses 1M.
		 * public static final String METAINFO_INFO_PIECES = "pieces"; // one piece, one SHA1 hash(20 bytes).
		 * public static final String METAINFO_INFO_PRIVATE = "private"; // clients get peers only from tracker
		*/
		// "info"
		Map<String, BEValue> info = values.get(TorrentSeed.METAINFO_INFO).getMap();
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
		int pvt/*private*/ = -1;
		BEValue privateValue = info.get(TorrentSeed.METAINFO_INFO_PRIVATE);
		if (privateValue != null)
			pvt = privateValue.getInt();
		seed.setPieceLength(pieceLength);
		seed.setPieceHashs(pieceHashs);
		seed.setPvt(pvt);

		/*
		 * public static final String METAINFO_INFO_NAME = "name"; // file name or directory name
		 * public static final String METAINFO_INFO_LENGTH = "length"; // length of the file if only one file to be downloaded.
		 * public static final String METAINFO_INFO_MD5SUM = "md5sum"; // 
		 */
		// "name"
		String name = info.get(TorrentSeed.METAINFO_INFO_NAME).getString();
		//TODO: Is it a long or int?
		// "length"
		long length = -1;
		BEValue lengthValue = info.get(TorrentSeed.METAINFO_INFO_LENGTH);
		if (lengthValue != null)
			length = lengthValue.getLong();
		String md5sum = null;
		BEValue md5sumValue = info.get(TorrentSeed.METAINFO_INFO_MD5SUM);
		if (md5sumValue != null)
			md5sum = md5sumValue.getString();
		seed.setName(name);
		seed.setLength(length);
		seed.setMD5sum(md5sum);

		/*
		 * public static final String METAINFO_INFO_FILES = "files"; //
		 * public static final String METAINFO_INFO_FILES_LENGTH = "length"; // 
		 * public static final String METAINFO_INFO_FILES_PATH = "path"; // 
		 * public static final String METAINFO_INFO_FILES_MD5SUM = "md5sum"; // 
		*/
		List<FileInfo> fileInfos = new ArrayList<FileInfo>();
		BEValue filesValue = info.get(TorrentSeed.METAINFO_INFO_FILES);
		if (filesValue != null) {
			List<BEValue> fileList = filesValue.getList();
			for (BEValue fileValue : fileList) {
				Map<String, BEValue> fileMap = fileValue.getMap();
				long fileLength = fileMap.get(METAINFO_INFO_FILES_LENGTH).getLong();
				//String filePath = fileMap.get(METAINFO_INFO_FILES_PATH).getString();
				List<BEValue> filesPath = fileMap.get(METAINFO_INFO_FILES_PATH).getList();
				StringBuilder sb = new StringBuilder();
				sb.append(name);
				Iterator<BEValue> iterator = filesPath.iterator();
				while (iterator.hasNext()) {
					sb.append("\\").append(iterator.next().getString());
				}
				BEValue md5sumV = fileMap.get(METAINFO_INFO_FILES_MD5SUM);
				FileInfo fileInfo = new FileInfo();
				fileInfo.setLength(fileLength);
				//fileInfo.setPath(filePath);
				fileInfo.setPath(sb.toString());
				if (md5sumV != null)
					fileInfo.setMd5sum(md5sumV.getString());
				fileInfos.add(fileInfo);
			}
			seed.setFileInfos(fileInfos);
		}

		System.out.println("It's a directory : num of files: " + fileInfos.size() + ".");

		try {
			byte[] infoHash = Utils.getSHA1(BEncoder.bencode(values.get(TorrentSeed.METAINFO_INFO).getMap()).array());
			seed.setInfoHash(Utils.bytes2HexString(infoHash));
		} catch (Exception e) {
			throw new IOException(e);
		}

		return seed;
	}

	public static class FileInfo implements Serializable {
		private long length;
		private String path;
		private String md5sum;
		
		private transient FileChannel fileChannel;
		private long startPos; // inclusive
		private long endPos; // inclusive, if file length = 0, endPos = -1 aways.

		// TODO:
		// Need Multi thread consideration.
		private void open() throws IOException {
			if (this.fileChannel == null) {
				this.fileChannel = FileChannel.open(Paths.get(this.path), StandardOpenOption.READ, StandardOpenOption.WRITE);
			}
		}
		
		public void close() {
			System.out.println("FileInfo -> Closing temp file.");
			if (fileChannel != null && fileChannel.isOpen()) {
				try {
					fileChannel.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		public void delete() {
			File file = new File(this.path);
			if (file.exists()) {
				file.delete();
			}
		}
		
		public long getLength() {
			return length;
		}

		public void setLength(long length) {
			this.length = length;
		}

		public String getPath() {
			return path;
		}

		public void setPath(String path) {
			this.path = path;
		}

		public String getMd5sum() {
			return md5sum;
		}

		public void setMd5sum(String md5sum) {
			this.md5sum = md5sum;
		}
		
		public long getStartPos() {
			return startPos;
		}

		public void setStartPos(long startPos) {
			this.startPos = startPos;
		}

		public long getEndPos() {
			return endPos;
		}

		public void setEndPos(long endPos) {
			this.endPos = endPos;
		}

		public FileChannel getFileChannel() throws IOException {
			this.open();
			return this.fileChannel;
		}
		
		@Override
		public String toString() {
			return "\n" + "FileInfo [length=" + length + ", path=" + path + ", md5sum=" + md5sum + ", startPos=" + startPos + ", endPos=" + endPos + "]";
		}
	}
}

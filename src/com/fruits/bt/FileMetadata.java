package com.fruits.bt;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fruits.bt.TorrentSeed.FileInfo;

// TODO: Some days ago, if I open/write RandomAccessFile frequently, always got a exception.
//       Now if I always open a file for write/read, the issue disappear.
// Need to reproduce it.

public class FileMetadata implements Serializable {
	static final Logger logger = LoggerFactory.getLogger(FileMetadata.class);
	// TODO: VERY IMPORTANT!  
	// Re-design serialVersionUID.
	private static final long serialVersionUID = -8543915446727382746L;

	private final TorrentSeed seed;
	// The file to download, firstly it's a temp file, when downloading is completed, rename it.
	//private String filePath;
	//private transient FileChannel fileChannel;
	private List<FileInfo> fileInfos = new ArrayList<FileInfo>();

	private List<Piece> pieces;
	private int piecesCompleted;

	private long bytesWritten;
	private long bytesRead;

	// TODO: Need a Externalizable?
	// FileMetadata is de-serialized from disk, so this constructor will not called except the first time.
	public FileMetadata(TorrentSeed seed) throws IOException {
		this.seed = seed;
		initFileInfos();
		initPiecesAndSlices();
	}

	private void initFileInfos() throws IOException {
		if (seed.isDirectory()) {
			int pos = 0;
			List<FileInfo> infos = seed.getFileInfos();
			for (FileInfo info : infos) {
				info.setPath(Client.DOWNLOAD_DIR + File.separator + info.getPath());
				Helper.createFile(info.getPath());
				info.setStartPos(pos);
				if (info.getLength() == 0) {
					info.setEndPos(-1);
				} else {
					// inclusive
					info.setEndPos(pos + info.getLength() - 1); // inclusive
					pos += info.getLength();
				}
			}
			this.fileInfos = infos;
		} else {
			FileInfo fileInfo = new FileInfo();
			fileInfo.setPath(Client.DOWNLOAD_DIR + File.separator + seed.getName());
			Helper.createFile(fileInfo.getPath());
			fileInfo.setMd5sum(seed.getMD5sum());
			long length = seed.getLength();
			fileInfo.setLength(length);
			fileInfo.setStartPos(0);
			fileInfo.setEndPos(length - 1);
			this.fileInfos.add(fileInfo);
		}
	}

	private void initPiecesAndSlices() {
		// Read the pieces status from temp file or create it from scratch.
		long fileLength = 0;
		if (seed.isDirectory()) {
			List<FileInfo> fileInfos = seed.getFileInfos();
			for (FileInfo fileInfo : fileInfos) {
				fileLength += fileInfo.getLength();
			}
		} else {
			fileLength = seed.getLength();
		}
		// 256K, BitTorrent 3.2 and old versions = 1MB.
		int pieceLength = seed.getPieceLength(); // I created a demo seed, and piece length is 256K.                                  
		int sliceLength = seed.getSliceLength();

		if (pieceLength < sliceLength) {
			logger.trace("Piece length: {}, sliceLength: {}.", pieceLength, sliceLength);
			throw new RuntimeException("Piece length should not be less than slice length.");
		}
		// TODO: long->int?
		// Serious bug: fileLength > Integer.MAX_VALUE, convert it to int, it is negative value.
		//int piecesSize = (int) fileLength / pieceLength;
		//int lastPieceLength = (int) fileLength % pieceLength;
		int piecesSize = (int) (fileLength / pieceLength);
		int lastPieceLength = (int) (fileLength % pieceLength);

		pieces = new ArrayList<Piece>(piecesSize + ((lastPieceLength == 0) ? 0 : 1));

		for (int i = 0; i < piecesSize; i++) {
			int slicesSize = pieceLength / sliceLength;
			List<Slice> slices = new ArrayList<Slice>(slicesSize);
			for (int j = 0; j < slicesSize; j++) {
				Slice slice = new Slice(i, j * sliceLength, sliceLength);
				slices.add(slice);
			}
			Piece piece = new Piece(i, slices);
			piece.setSha1hash(seed.getPieceHashs().get(i));
			pieces.add(piece);
		}

		if (lastPieceLength != 0) {
			int lastPieceSlicesSize = lastPieceLength / sliceLength;
			int lastSliceLength = lastPieceLength % sliceLength;

			List<Slice> slices = new ArrayList<Slice>(lastPieceSlicesSize + ((lastSliceLength == 0) ? 0 : 1));
			for (int i = 0; i < lastPieceSlicesSize; i++) {
				Slice slice = new Slice(piecesSize, i * sliceLength, sliceLength);
				slices.add(slice);
			}
			if (lastSliceLength != 0) {
				Slice slice = new Slice(piecesSize, lastPieceSlicesSize * sliceLength, lastSliceLength);
				slices.add(slice);
			}

			Piece piece = new Piece(piecesSize, slices);
			piece.setSha1hash(seed.getPieceHashs().get(piecesSize));
			pieces.add(piece);
		}
	}

	public void delete() {
		for (FileInfo fileInfo : this.fileInfos)
			fileInfo.delete();
	}

	// TODO: Who and when to call it?
	// Added a hook for VM?
	public void finalize() {
		for (FileInfo fileInfo : this.fileInfos)
			fileInfo.close();
	}

	/*
	 * FileInfo [startPos, endPos]
	 *  0 - -1 (inclusive)(empty file) -> file0
	 *  0 - 2 (inclusive) -> file1
	 *  3 - 17 -> file2
	 * 18 - -1 (empty file) -> file3
	 * 18 - 26 -> file4
	 *
	 * Example:
	 * Slice(index: 1, begin: 4, length: 4), piece length = 12
	 * start: 16 - 19(inclusive) -> written to file2& (file3 skipped)& file4 
	 * 
	 * piece[1]
	 * slices [index, begin, length]	 
	 * 1 0 4
	 * 1 4 4 => absolute begin(inclusive): (12 * 1 + 4), absolute end(inclusive): (12 * 1 + 4 + 4 - 1)
	 * 1 8 4
	 * 
	 */
	public ByteBuffer readSlice(Slice slice) throws IOException {
		logger.trace("Thread : {} is reading slice, index = {}, begin = {}.", Thread.currentThread(), slice.getIndex(), slice.getBegin());

		int startPos/*inclusive*/ = this.seed.getPieceLength() * slice.getIndex() + slice.getBegin();
		int endPos/*inclusive*/ = startPos + slice.getLength() - 1;

		ByteBuffer data = ByteBuffer.allocate(slice.getLength());

		boolean started = false;
		for (int i = 0; i < this.fileInfos.size(); i++) {
			FileInfo info = fileInfos.get(i);
			if (info.getLength() == 0) // empty file, skip it.
				continue;

			long pos = -1;
			long bytesRead = -1;
			if (started) {
				pos = 0;
				bytesRead = info.getLength();
			} else {
				if (startPos <= info.getEndPos()) { // find the first file to write.
					started = true;
					// write how many bytes to this file?
					pos = startPos - info.getStartPos(); // start position in current file.
					bytesRead = (info.getEndPos() - startPos + 1); // at most bytes to read.
				}
			}
			if (pos != -1) {
				int size = Math.min(data.remaining(), (int) bytesRead);
				ByteBuffer buffer = ByteBuffer.allocate(size);
				FileChannel fileChannel = info.getFileChannel();
				try {
					fileChannel.position(pos); // Horrible bug! Did not set position.
					fileChannel.read(buffer);
					buffer.flip();
					data.put(buffer);
				} catch (IOException e) {
					logger.error("", e);
					// If write fails, ignore it, pls. never continue to update the metadata of pieces.
					return null;
				}
				if (!data.hasRemaining())
					break;
			}
		}

		this.bytesRead += slice.getLength();
		data.flip();
		return data;
	}

	/*
	 * Piece/Slice
	 *  file length: 26
	 * piece length: 12
	 * slice length: 4
	 * 
	 * piece[0]
	 * slices [index, begin, length] 
	 * 0 0 4
	 * 0 4 4
	 * 0 8 4
	 * 
	 * piece[1]
	 * slices [index, begin, length]	 
	 * 1 0 4
	 * 1 4 4 => absolute begin(inclusive): (12 * 1 + 4), absolute end(inclusive): (12 * 1 + 4 + 4 - 1)
	 * 1 8 4
	 * 
	 * piece[2]
	 * slices [index, begin, length]	 
	 * 2 0 2 => absolute begin(inclusive): (12 * 2 + 0), absolute end(inclusive): (12 * 2 + 0 + 2 - 1)
	 * 
	 * FileInfo [startPos, endPos]
	 *  0 - -1 (inclusive)(empty file) -> file0
	 *  0 - 2 (inclusive) -> file1
	 *  3 - 17 -> file2
	 * 18 - -1 (empty file) -> file3
	 * 18 - 26 -> file4
	 *
	 * Example:
	 * start: 16 - 19(inclusive) -> written to file2& (file3 skipped)& file4 
	 */
	public boolean writeSlice(int index, int begin, int length, ByteBuffer data) throws IOException {
		logger.trace("Thread : {} is writing slice, index = {}, begin = {}.", Thread.currentThread(), index, begin);

		int startPos/*inclusive*/ = this.seed.getPieceLength() * index + begin;
		int endPos/*inclusive*/ = startPos + length - 1;

		boolean started = false;
		for (int i = 0; i < this.fileInfos.size(); i++) {
			FileInfo info = fileInfos.get(i);
			if (info.getLength() == 0) // empty file, skip it.
				continue;

			long pos = -1;
			long bytesWrite = -1;
			if (started) {
				pos = 0;
				bytesWrite = info.getLength();
			} else {
				if (startPos <= info.getEndPos()) { // find the first file to write.
					started = true;
					// write how many bytes to this file?
					pos = startPos - info.getStartPos(); // start position in current file.
					bytesWrite = (info.getEndPos() - startPos + 1); // at most bytes to write.
				}
			}
			if (pos != -1) {
				int size = Math.min(data.remaining(), (int) bytesWrite);
				byte[] buffer = new byte[size];
				data.get(buffer);
				FileChannel fileChannel = info.getFileChannel();
				try {
					fileChannel.write(ByteBuffer.wrap(buffer), pos); // TODO: Perf Improvement!
					fileChannel.force(true); // TODO: more research?
				} catch (IOException e) {
					logger.error("", e);
					// If write fails, ignore it, pls. never continue to update the metadata of pieces.
					return false;
				}
				if (!data.hasRemaining())
					break;
			}
		}

		//file.seek(startPosition);
		//TODO: length or data.array().length?
		//file.write(data.array(), 0, length);
		this.pieces.get(index).setSliceCompleted(begin);
		boolean isPieceCompleted = pieces.get(index).isAllSlicesCompleted();
		if (isPieceCompleted) {
			List<Slice> slices = pieces.get(index).getSlices();
			int n = 0;
			for(Slice slice : slices) {
				n += slice.getLength();
			}
			
			ByteBuffer pieceData = ByteBuffer.allocate(n);
			
			for(Slice slice : slices) {
				ByteBuffer sliceData = this.readSlice(slice);
				//logger.debug("Preparing hash validation, slice index {}, begin {}, data read {}", slice.getIndex(), slice.getBegin(), sliceData.limit());
				pieceData.put(sliceData);
			}
			
			if(!pieceData.hasRemaining()) {
				logger.trace("Completely read the data of piece index = {}, length = {}", index, pieceData.limit());
				try {
				  byte[] sha1Hash = Utils.getSHA1(pieceData.array());
				  logger.trace("Piece index = {}, hash = {}.", index, Utils.bytes2HexString(sha1Hash));
				  byte[] hashFromSeed = this.pieces.get(index).getSha1hash();
				  logger.trace("Piece index = {}, hash from seed = {}.", index, Utils.bytes2HexString(hashFromSeed));
				  if(!Arrays.equals(sha1Hash, hashFromSeed)) {
				  	logger.warn("[Hash validation] piece index = {} failed.", index, new Exception("Discarded this piece."));
				  }else {
				  	logger.trace("Piece index = {}, hash validation succeeded.", index);
				  }
				}catch(NoSuchAlgorithmException ae) {
					logger.error("", ae);
				}
			}else {
				logger.error("[Hash valication] partial data of piece[index: {}] can not read, remaining = {}", index, pieceData.remaining());
			}
			
			piecesCompleted++;
		}

		this.bytesWritten += length;
		return isPieceCompleted;
	}

	// To support directory, so discard this function.
	// @ return Slices of current piece are completed?
	// TODO: VERY IMPORTANT!
	// Previou verion we got IOException.
	// Only if opening file fails, throw the IOException.
	/*
	public boolean writeSlice(int index, int begin, int length, ByteBuffer data) throws IOException {
		logger.trace("Thread : " + Thread.currentThread() + " is writing slice.");
		openFile();
		// int or long?
		int startPos = (this.seed.getPieceLength() * index) + begin; // 0 based ->
		try {
			this.fileChannel.write(data, startPos);
		} catch (IOException e) {
			logger.error("", e);
			// If write fails, ignore it, pls. never continue to update the metadata of pieces.
			return false;
		}
		//file.seek(startPosition);
		//TODO: length or data.array().length?
		//file.write(data.array(), 0, length);
		this.pieces.get(index).setSliceCompleted(begin);
		boolean isPieceCompleted = pieces.get(index).isAllSlicesCompleted();
		if (isPieceCompleted) {
			piecesCompleted++;
		}
	
		this.bytesWritten += length;
		return isPieceCompleted;
	}
	*/

	// To support directory, so discard this function.
	// In production, we should not open then close a file for writing/reading a slice.
	// The file should be always open for random access.
	/*
	public ByteBuffer readSlice(Slice slice) throws IOException {
		logger.trace("Thread : " + Thread.currentThread() + " is reading slice from " + this.filePath + ".");
	
		openFile();
	
		int startPos = (this.seed.getPieceLength() * slice.getIndex()) + slice.getBegin();
		//byte[] buffer = new byte[slice.getLength()];
		ByteBuffer buffer = ByteBuffer.allocate(slice.getLength());
		try {
			// TODO: Test code
			//RandomAccessFile tempFile = new RandomAccessFile("D:\\TorrentDownload\\Wireshark-win32-1.10.0.exe.X", "rw");
			//tempFile.seek(startPos);
			//tempFile.read(buffer);
			//tempFile.close();
			this.fileChannel.read(buffer, startPos);
		} catch (IOException e) {
			logger.error("", e);
			// If read fails, return null.
			return null;
		}
	
		this.bytesRead += slice.getLength();
		return buffer;
	}
	*/

	public boolean isAllPiecesCompleted() {
		return (this.piecesCompleted == this.pieces.size());
	}

	public float getPercentCompleted() {
		return this.piecesCompleted / this.pieces.size();
	}

	public Bitmap getBitfield() {
		// TODO: VERY IMPORTANT! 
		// BitSet do not have a fixed length/size, e.g. we have 85 pieces, but never bitfield.length or bitfield.size = 85.
		// BitSet
		// length() -> Returns the "logical size" of this BitSet: the index of the highest set bit in the BitSet plus one.
		// size() -> Returns the number of bits of space actually in use by this BitSet to represent bit values. 
		//           The maximum element in the set is the size - 1st element.
		// toByteArray -> Returns a new byte array containing all the bits in this bit set.
		// Interanlly BitSet use long[] as backend, so BitSet(nBits) -> new long[wordIndex(nbits-1) + 1].
		// So usually we should use length(), not size().
		Bitmap bitfield = new Bitmap(pieces.size());
		for (int i = 0; i < pieces.size(); i++) {
			if (pieces.get(i).isAllSlicesCompleted())
				bitfield.set(i);
		}
		logger.trace("FileMetadata.getBitfield-> size : {}, length = {}.", bitfield.size(), bitfield.length());
		return bitfield;
	}

	public Slice getNextIncompletedSlice() {
		for (Piece piece : pieces) {
			if (piece.isAllSlicesCompleted())
				continue;
			return piece.getNextIncompletedSlice();
		}
		return null;
	}

	public List<Slice> getNextBatchIncompletedSlices(int index, int batchSize) {
		return this.pieces.get(index).getNextBatchIncompletedSlices(batchSize);
	}

	public List<Slice> getIncompletedSlices() {
		List<Slice> incompleteds = new ArrayList<Slice>();
		for (Piece piece : pieces) {
			for (Slice slice : piece.getSlices()) {
				if (!slice.isCompleted())
					incompleteds.add(slice);
			}
		}
		return incompleteds;
	}

	public TorrentSeed getSeed() {
		return seed;
	}

	public long getBytesWritten() {
		return this.bytesWritten;
	}

	public long getBytesRead() {
		return this.bytesRead;
	}

	@Override
	public String toString() {
		return "FileMetadata [seed=" + seed + ", fileInfos=" + fileInfos + ", pieces=" + pieces + ", piecesCompleted=" + piecesCompleted + ", bytesWritten="
				+ bytesWritten + ", bytesRead=" + bytesRead + "]";
	}
}

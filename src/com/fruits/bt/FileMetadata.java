package com.fruits.bt;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

// TODO: Some days ago, if I open/write RandomAccessFile frequently, always got a exception.
//       Now if I always open a file for write/read, the issue disappear.
// Need to reproduce it.

public class FileMetadata implements Serializable {
	// TODO: VERY IMPORTANT!  
	// Re-design serialVersionUID.
	private static final long serialVersionUID = -8543915446727382746L;

	private final TorrentSeed seed;
	// The file to download, firstly it's a temp file, when downloading is completed, rename it.
	private String filePath;
	private List<Piece> pieces;
	private int piecesCompleted;

	private transient FileChannel tempFile;

	// TODO: Need a Externalizable?
	// FileMetadata is de-serialized from disk, so this constructor will not called except the first time.
	public FileMetadata(TorrentSeed seed) throws IOException {
		this.seed = seed;
		this.filePath = Client.DOWNLOAD_TEMP_DIR + File.separator + seed.getInfoHash() + ".tmp";

		File file = new File(this.filePath);
		if (!file.exists()) {
			file.createNewFile();
		}

		initPiecesAndSlices();
	}

	private void initPiecesAndSlices() {
		// Read the pieces status from temp file or create it from scratch.
		long fileLength = seed.getLength();
		// 256K, BitTorrent 3.2 and old versions = 1MB.
		int pieceLength = seed.getPieceLength(); // I created a demo seed, and piece length is 256K.                                  
		int sliceLength = seed.getSliceLength();

		//TODO: long->int?
		int piecesSize = (int) fileLength / pieceLength;
		int lastPieceLength = (int) fileLength % pieceLength;

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

	// TODO:
	// Need Multi thread consideration.
	private void openTempFile() throws IOException {
		if (this.tempFile == null) {
			this.tempFile = FileChannel.open(Paths.get(this.filePath), StandardOpenOption.READ, StandardOpenOption.WRITE);
		}
	}
	
	// @ return Slices of current piece are completed?
	// TODO: VERY IMPORTANT!
	// Previou verion we got IOException.
	// Only if opening temp file fails, throw the IOException.
	public boolean writeSlice(int index, int begin, int length, ByteBuffer data) throws IOException {
		System.out.println("Thread : " + Thread.currentThread() + " is writing slice.");
		openTempFile();
		// int or long?
		int startPos = (this.seed.getPieceLength() * index) + begin; // 0 based ->
		try {
		  this.tempFile.write(data, startPos);
		}catch(IOException e) {
			e.printStackTrace();
			// If write fails, ignore it, pls. never continue to update the metadata of pieces.
			return false;
		}
		//tempFile.seek(startPosition);
		//TODO: length or data.array().length?
		//tempFile.write(data.array(), 0, length);
		this.pieces.get(index).setSliceCompleted(begin);
		boolean isPieceCompleted = pieces.get(index).isAllSlicesCompleted();
		if (isPieceCompleted) {
			piecesCompleted++;
		}
		
		/*
		 * This action should be performed when the file is completely downloaded.
			if(isAllPiecesCompleted()) {
				System.out.println("All of the pieces are completed.");
				File file = new File(filePath);
				String newFilePath = Client.DOWNLOAD_TEMP_DIR + "\\\\" + seed.getName();
				file.renameTo(new File(newFilePath));
				
				this.filePath = newFilePath;
			}
		*/
		return isPieceCompleted;
	}

	public boolean isAllPiecesCompleted() {
		return (this.piecesCompleted == this.pieces.size());
	}

	public BitSet getBitfield() {
		// TODO: VERY IMPORTANT! 
		// BitSet do not have a fixed length/size, e.g. we have 85 pieces, but never bitfield.length or bitfield.size = 85.
		// BitSet
		// length() -> Returns the "logical size" of this BitSet: the index of the highest set bit in the BitSet plus one.
		// size() -> Returns the number of bits of space actually in use by this BitSet to represent bit values. 
		//           The maximum element in the set is the size - 1st element.
		// toByteArray -> Returns a new byte array containing all the bits in this bit set.
		// Interanlly BitSet use long[] as backend, so BitSet(nBits) -> new long[wordIndex(nbits-1) + 1].
		// So usually we should use length(), not size().
		BitSet bitfield = new BitSet(pieces.size());
		for (int i = 0; i < pieces.size(); i++) {
			if (pieces.get(i).isAllSlicesCompleted())
				bitfield.set(i);
		}
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

	// In production, we should not open then close a file for writing/reading a slice.
	// The file should be always open for random access.
	public ByteBuffer readSlice(Slice slice) throws IOException {
		System.out.println("Thread : " + Thread.currentThread() + " is reading slice.");
		
		openTempFile();
		
		int startPos = (this.seed.getPieceLength() * slice.getIndex()) + slice.getBegin();
		//byte[] buffer = new byte[slice.getLength()];
		ByteBuffer buffer = ByteBuffer.allocate(slice.getLength());		
		try {
			// TODO: Test code
			//RandomAccessFile tempFile = new RandomAccessFile("D:\\TorrentDownload\\Wireshark-win32-1.10.0.exe.X", "rw");
			//tempFile.seek(startPos);
			//tempFile.read(buffer);
			//tempFile.close();
			this.tempFile.read(buffer, startPos);
		}catch(IOException e){
			e.printStackTrace();
			// If read fails, return null.
			return null;
		}
		return buffer;
	}

	public TorrentSeed getSeed() {
		return seed;
	}

	@Override
	public String toString() {
		return "FileMetadata [seed = " + seed + ", filePath = " + filePath + ",\n" 
	          + "pieces = " + pieces + ", piecesCompleted = " + piecesCompleted + "].\n";
	}
}

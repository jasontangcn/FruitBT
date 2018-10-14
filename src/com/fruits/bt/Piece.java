package com.fruits.bt;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Piece implements Serializable {
	private static final long serialVersionUID = 2969199847197526105L;
	
	private final int index;
	private String sha1hash;
	private final List<Slice> slices;

	public Piece(int index, List<Slice> slices) {
		this.index = index;
		this.slices = slices;
	}

	public Slice getSlice(int begin) {
		for(Slice slice : this.slices) {
			if(begin == slice.getBegin()) {
				return slice;
			}
		}
		return null;
	}
	
	public boolean setSliceCompleted(int begin) {
		Slice slice = getSlice(begin);
		if(null != slice) {
			slice.setCompleted(true);
			return true;
		}else {
			return false;
		}
	}
	
	public Slice getNextIncompletedSlice() {
		for(Slice slice : this.slices) {
			if(!slice.isCompleted()) return slice;
		}
		return null;
	}
	
	public List<Slice> getNextBatchIncompletedSlices(int batchSize) {
		List<Slice> slices = new ArrayList<Slice>();
		int n = 0;
		
		for(Slice slice : this.slices) {
			if(!slice.isCompleted()) {
				slices.add(slice);
				n++;
				if(batchSize == n)
					break;
			}
		}
		
		return slices;
	}
	
	public boolean isAllSlicesCompleted() {
		for(Slice slice : this.slices) {
			if(!slice.isCompleted()) return false;
		}
		return true;
	}
	
	// TODO: Check progress, if it's done validate the sha1hash.
	public void checkCompletedPiece() {
		
	}
	
	public int getIndex() {
		return index;
	}

	public String getSha1hash() {
		return sha1hash;
	}

	public void setSha1hash(String sha1hash) {
		this.sha1hash = sha1hash;
	}
	
	public List<Slice> getSlices() {
		return slices;
	}
	
	public String toString() {
		return "Piece: [index : " + index + ", " + slices + " ].\n";
	}
}

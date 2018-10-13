package com.fruits.bt;

import java.io.Serializable;

public class Slice implements Serializable {
	private static final long serialVersionUID = -1840946828264519185L;
	
	private final int index; // index of piece
	private final int begin; // offset in the piece.
	private final int length; // normally Seed.sliceLength = 16K except the last one.
	private boolean completed = false; //
	
	public Slice(int index, int begin, int length) {
		this.index = index;
		this.begin = begin;
		this.length = length;
	}
	
	public int getIndex() {
		return index;
	}

	public int getBegin() {
		return begin;
	}

	public int getLength() {
		return length;
	}

	public boolean isCompleted() {
		return completed;
	}
	public void setCompleted(boolean completed) {
		this.completed = completed;
	}
	
	@Override
	public String toString() {
		return "Slice: [begin : " + begin + " , length : " + length + " , completed : " + completed + "]";
	}
}

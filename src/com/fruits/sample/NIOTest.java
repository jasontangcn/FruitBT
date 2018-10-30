package com.fruits.sample;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class NIOTest {

	public static void main(String[] args) throws Exception {
		RandomAccessFile raf = new RandomAccessFile("D://abc.txt", "rw");
		raf.setLength(1024 * 1024); 
		raf.close();


		String s1 = "";
		String s2 = "";
		String s3 = "";
		String s4 = "";
		String s5 = "";


		new FileWriteThread(1024 * 1, s1.getBytes()).start(); 
		new FileWriteThread(1024 * 2, s2.getBytes()).start();
		new FileWriteThread(1024 * 3, s3.getBytes()).start(); 
		new FileWriteThread(1024 * 4, s4.getBytes()).start(); 
		new FileWriteThread(1024 * 5, s5.getBytes()).start();
	}

	static class FileWriteThread extends Thread {
		private int skip;
		private byte[] content;

		public FileWriteThread(int skip, byte[] content) {
			this.skip = skip;
			this.content = content;
		}

		public void run() {
			RandomAccessFile raf = null;
			try {
				raf = new RandomAccessFile("D://abc.txt", "rw");
				raf.seek(skip);
				raf.write(content);
				try {
					Thread.sleep(10 * 1000);
				} catch (Exception e) {

				}
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block  
				e.printStackTrace();
			} finally {
				try {
					raf.close();
				} catch (Exception e) {
				}
			}
		}
	}
}

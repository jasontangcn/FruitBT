package com.fruits.sample;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class Test {

	public static void main(String[] args) throws IOException {
		/*
		//%B3%C8%F8%E5%0D%3F%3Fp%11W%F2%C2Q~%EExX%8BH%F2
		//URLEncoder
		System.out.println(URLEncoder.encode("76BF8BC64B4BF1988C6B5CBD541536DC100F22B0"));
		System.out.println(URLDecoder.decode("%B3%C8%F8%E5%0D%3F%3Fp%11W%F2%C2Q~%EExX%8BH%F2"));

		//\x12\x34\x56\x78\x9a\xbc\xde\xf1\x23\x45\x67\x89\xab\xcd\xef\x12\x34\x56\x78\x9a
		//
		//%124Vx%9A%BC%DE%F1%23Eg%89%AB%CD%EF%124Vx%9A
		 * 
		 */
		
		//File file1 = new File("D:\\TorrentDownload\\a\\b\\m\\t\\x.txt");
		//File file2 = new File("D:\\TorrentDownload\\a\\c\\d\\f");
		//System.out.println(file2.mkdirs());
		//System.out.println(file1.getPath());
		//System.out.println(file1.getParent());
		//System.out.println(file1.getParentFile());
		//Files.createDirectory(dir, attrs)
		//if(!file1.exists())
			//file1.createNewFile();
		/*
		File file1 = new File("x.txt");
		System.out.println(file1);
		System.out.println(file1.getAbsoluteFile().getParentFile().getParentFile());
		*/
		/*
		File file = new File("D:\\TorrentDownload\\a\\xx");
		System.out.println(file.exists());
		*/
		System.out.println("1234567890".getBytes().length);
	}

}

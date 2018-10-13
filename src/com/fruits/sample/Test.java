package com.fruits.sample;

import java.net.URLDecoder;
import java.net.URLEncoder;

public class Test {

	public static void main(String[] args) {
		//%B3%C8%F8%E5%0D%3F%3Fp%11W%F2%C2Q~%EExX%8BH%F2
		//URLEncoder
		System.out.println(URLEncoder.encode("76BF8BC64B4BF1988C6B5CBD541536DC100F22B0"));
		System.out.println(URLDecoder.decode("%B3%C8%F8%E5%0D%3F%3Fp%11W%F2%C2Q~%EExX%8BH%F2"));
		
		
		//\x12\x34\x56\x78\x9a\xbc\xde\xf1\x23\x45\x67\x89\xab\xcd\xef\x12\x34\x56\x78\x9a
		//
		//%124Vx%9A%BC%DE%F1%23Eg%89%AB%CD%EF%124Vx%9A
	}

}

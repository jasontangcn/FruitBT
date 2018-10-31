package com.fruits.sample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fruits.bt.PeerFinder;

public class LogbackTest {
	static final Logger logger = LoggerFactory.getLogger(LogbackTest.class);
	
	public static void main(String[] args) {
		logger.trace("{}{}","Test", new RuntimeException("Hello"), new RuntimeException("Hello1"));
	}

}

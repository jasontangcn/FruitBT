package com.fruits.sample;

import java.util.BitSet;

public class JavaAPITest {

	public static void main(String[] args) throws Exception {

		BitSet bitset = new BitSet(85);
		System.out.println(bitset.toByteArray());
		/*
		System.out.println(bitset.length());
		System.out.println(bitset.size());
		System.out.println(bitset.get(2));
		System.out.println(bitset.get(bitset.size() - 1));
		System.out.println(bitset.get(2000));
		System.out.println(bitset.length());
		System.out.println(bitset.size());
		
		*/
		/*
		for (int i = 0; i < 85; i++) {
			if ((i % 2) == 0)
				bitset.set(i);
			else
				bitset.set(i, false);
		}
		
		System.out.println(bitset.length());
		System.out.println(bitset.toByteArray().length);
		*/

		// System.out.println("BitTorrent protocol".getBytes().length);

		/*
		Timer timer = new Timer();
		int i = 0;
		timer.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				try {
					System.out.println(Thread.currentThread() + "," + System.currentTimeMillis());
					Random rand = new Random();
		
					int randNum = rand.nextInt(2);
					if (randNum % 2 == 0)
						Thread.currentThread().sleep(3 * 1000L);
		
				} catch (Exception e) {
					e.printStackTrace();
				}
		
			}
		}, 0L, 1 * 1000);
		
		System.out.println(Thread.currentThread() + "," + System.currentTimeMillis());
		*/

		/*
		ScheduledExecutorService executor = Executors.newScheduledThreadPool(10);
		executor.scheduleAtFixedRate(new Runnable() {
			public void run() {
				try {
					System.out.println(Thread.currentThread() + "," + System.currentTimeMillis());
					
					//Random rand = new Random();
					//int randNum = rand.nextInt(2);
					//if (randNum % 2 == 0)
						Thread.currentThread().sleep(4 * 1000L);
		
				} catch (Exception e) {
					e.printStackTrace();
				}
		
			}}, 0L, 2, TimeUnit.SECONDS);
		  */
		/*
		Rate[] rates = {new Rate(false, 10f), new Rate(false, 20f), new Rate(true, 0.5f), new Rate(false, 0.1f), new Rate(true, 30f), new Rate(true, 30f), new Rate(false, 15f), new Rate(true, 34f)};
		Arrays.sort(rates, new Comparator<Rate>() {
			public int compare(Rate o1, Rate o2) {
				if(o1.isFlag() == o2.isFlag()) {
					return Float.compare(o2.getSpeed(), o1.getSpeed());
				}else {
					if(o1.isFlag()) {
						return -1;
					}else {
						return 1;
					}
				}
			}
		});
		
		for(Rate rate : rates) {
			System.out.println(rate);
		}
		*/
	}

	public static class Rate {
		private boolean flag;
		private float speed;

		public Rate(boolean flag, float speed) {
			this.flag = flag;
			this.speed = speed;
		}

		public boolean isFlag() {
			return flag;
		}

		public void setFlag(boolean flag) {
			this.flag = flag;
		}

		public float getSpeed() {
			return speed;
		}

		public void setSpeed(float speed) {
			this.speed = speed;
		}

		@Override
		public String toString() {
			return "Rate [flag=" + flag + ", speed=" + speed + "]";
		}
	}
}

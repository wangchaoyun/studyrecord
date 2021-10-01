package com.city.rose.common;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadDemo  {

    public static void main(String[] args) {
        //单线程的线程池
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        //根据实际情况动态调整线程数量的线程池
        ExecutorService cachedThreadPool = Executors.newCachedThreadPool();
        //固定线程数量的线程池
        ExecutorService fixedThreadPool = Executors.newFixedThreadPool(5);
        //定时任务线程池
        Executors.newScheduledThreadPool(1);

        Thread newThread = new Thread();
        cachedThreadPool.submit(newThread);
    }
}

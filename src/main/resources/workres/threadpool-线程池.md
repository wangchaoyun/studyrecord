# 线程池笔记记录

## 一、概述

### 1.1 主要描述java线程池

​	线程池的作用：池化技术 主要解决线程的创建和销毁带来的性能损耗；方便线程的管理，避免无限制创建线程

### 1.2 java中JDK Executors默认的现场池方法

​	A、单个线程的线程池方法

```java
ExecutorService executorService = Executors.newSingleThreadExecutor();
```

- 创建单个线程的线程池，可以保证所有任务按照任务的提交顺序执行。

- 阻塞队列使用 DelayedWorkQueue()，长度**MAX_VALUE** ；可能会堆积大量请求，从而导致OOM异常

  B、固定大小的线程池

  ```java
  ExecutorService executorService = Executors.newFixedThreadPool(10);
  ```

- 固定长度线程池，核心线程数和最大线程数固定，阻塞队列长队**MAX_VALUE**  

- 阻塞队列 LinkedBlockingQueue **MAX_VALUE**  可能会堆积大量请求，从而导致OOM异常

    C、newCachedThreadPool 缓存线程数

```java
ExecutorService executorService = Executors.newCachedThreadPool();
```

- 允许的创建线程数量为Integer.MAX_VALUE，可能会创建大量的线程，从而导致OOM。

​	D、 定时任务线程池

```java
ExecutorService executorService = Executors.newScheduledThreadPool();
```

- 允许的创建线程数量为Integer.MAX_VALUE，可能会创建大量的线程，从而导致OOM。

### 1.3 ThreadPoolExecutor 类，线程池参数信息

建议使用原始创建方法，：

```java
public ThreadPoolExecutor(
    int corePoolSize,  //核心线程数，会一致存在，除非allowCoreThreadTimeOut=true
    int maximumPoolSize, //线程池允许最大线程数
    long keepAliveTime,//线程数超过corePoolSize才使用， 空闲线程保存最大时间
    TimeUnit unit, //keepAliveTime 的时间单位
    BlockingQueue<Runnable> workQueue, //工作队列，保存未执行的Runnable任务
    ThreadFactory threadFactory,//创建线程的工厂类
    RejectedExecutionHandler handler)；//线程已满，采用的拒绝策略
```

workQueue阻塞队列：

- DelayedWorkQueue 延迟队列
- ArrayBlockingQueue：基于数组结构的有界阻塞队列，FIFO
- LinkedBlockingQueue：基于链表的阻塞队列，FIFO，先进先出，吞吐量高于ArrayBlockingQueue
- SynchronousQueue：一个不存储元素的阻塞队列，每个插入要等另一个线程调用移除操作
- priorityBlockingQuene：具有优先级的无界阻塞队列

线程池的四种拒绝策略，第五种为自定义开放策略：

1. ThreadPoolExecutor.AbortPolicy:丢弃任务并抛出RejectedExecutionException异常。 默认策略 
2. ThreadPoolExecutor.DiscardPolicy：也是丢弃任务，但是不抛出异常。 静默策略
3. ThreadPoolExecutor.DiscardOldestPolicy：丢弃队列最前面的任务，然后重新尝试执行任务（重复此过程） 
4. ThreadPoolExecutor.CallerRunsPolicy：由调用线程（主线程）处理该任务自定义策略，实现
5. RejectedExecutionHandler接口，并实现rejectedExecution方法。

### 1.4 线程池的运行状态

​	其中AtomicInteger变量ctl的功能非常强大：利用低29位表示线程池中线程数，通过高3位表示线程池的运行状态：
 1、RUNNING：`-1 << COUNT_BITS`，即高3位为111，该状态的线程池会接收新任务，并处理阻塞队列中的任务；
 2、SHUTDOWN： `0 << COUNT_BITS`，即高3位为000，该状态的线程池不会接收新任务，但会处理阻塞队列中的任务；
 3、STOP ： `1 << COUNT_BITS`，即高3位为001，该状态的线程不会接收新任务，也不会处理阻塞队列中的任务，而且会中断正在运行的任务；
 4、TIDYING ： `2 << COUNT_BITS`，即高3位为010，所有的任务都销毁。
 5、TERMINATED： `3 << COUNT_BITS`，即高3位为011；线程池处在TIDYING状态时，执行完terminated()之后，就会由 TIDYING -> TERMINATED。

### 1.5 线程池的原理

​	主要看 ThreadPoolExecutor类的实现逻辑。

​	1、线程的提交

​		Executor.execute() ；通过该方法提交的任务必须实现Runnabel接口，该方法提交的任务不能获取返回值；无法判断任务是否执行成功。

​		ExecutorService.submit()；通过该方法提交的任务可以获取任务执行完的返回值。

​	2、具体实现

​		Executor.execute()：

```java
public void execute(Runnable command) {
        if (command == null)
            throw new NullPointerException();
        int c = ctl.get();
        if (workerCountOf(c) < corePoolSize) {
            if (addWorker(command, true))
                return;
            c = ctl.get();
        }
        if (isRunning(c) && workQueue.offer(command)) {
            int recheck = ctl.get();
            if (! isRunning(recheck) && remove(command))
                reject(command);
            else if (workerCountOf(recheck) == 0)
                addWorker(null, false);
        }
        else if (!addWorker(command, false))
            reject(command);
    }
```

具体流程：**先添加核心线程队列，然后添加阻塞队列，然后按照最大线程创建，然后拒绝。**

1、workerCountOf方法根据ctl的低29位，得到线程池的当前线程数，如果线程数小于corePoolSize，使用addWorker方法创建线程执行任务，否则执行步骤2.

2、如果线程处于running状态，且把任务成功提交到阻塞队列，执行步骤3，否则执行步骤4；

3、再次检查线程池状态，线程池没有running状态且成功从阻塞队列中删除任务，则执行reject方法处理任务；

4、执行addWorker方法创建新线程执行任务，如果addWorker失败，则执行reject方法处理任务。

**addWorker 的工作原理：**

```java
private boolean addWorker(Runnable firstTask, boolean core) {
        retry:
        for (;;) {
            int c = ctl.get();
            int rs = runStateOf(c);
            // Check if queue empty only if necessary.
            if (rs >= SHUTDOWN &&
                ! (rs == SHUTDOWN &&
                   firstTask == null &&
                   ! workQueue.isEmpty()))
                return false;

            for (;;) {
                int wc = workerCountOf(c);
                if (wc >= CAPACITY ||
                    wc >= (core ? corePoolSize : maximumPoolSize))
                    return false;
                if (compareAndIncrementWorkerCount(c))
                    break retry;
                c = ctl.get();  // Re-read ctl
                if (runStateOf(c) != rs)
                    continue retry;
                // else CAS failed due to workerCount change; retry inner loop
            }
        }

        boolean workerStarted = false;
        boolean workerAdded = false;
        Worker w = null;
        try {
            w = new Worker(firstTask);
            final Thread t = w.thread;
            if (t != null) {
                final ReentrantLock mainLock = this.mainLock;
                mainLock.lock();
                try {
                    // Recheck while holding lock.
                    // Back out on ThreadFactory failure or if
                    // shut down before lock acquired.
                    int rs = runStateOf(ctl.get());

                    if (rs < SHUTDOWN ||
                        (rs == SHUTDOWN && firstTask == null)) {
                        if (t.isAlive()) // precheck that t is startable
                            throw new IllegalThreadStateException();
                        workers.add(w);
                        int s = workers.size();
                        if (s > largestPoolSize)
                            largestPoolSize = s;
                        workerAdded = true;
                    }
                } finally {
                    mainLock.unlock();
                }
                if (workerAdded) {
                    t.start();
                    workerStarted = true;
                }
            }
        } finally {
            if (! workerStarted)
                addWorkerFailed(w);
        }
        return workerStarted;
    }
```

1、首先判断线程池状态是否大于等于SHUTDOWN，则不处理提交的事务，直接返回；

2、根据core值判断当前线程数是否是核心线程数，是否还能创建线程，可以的话，跳出循环，创建新线程。

3、创建线程时，线程池的工作线程以woker来实现，在ReentrantLock 锁的保护下，插入到HashSet并启动Woker。Woker通过AQS来实现。
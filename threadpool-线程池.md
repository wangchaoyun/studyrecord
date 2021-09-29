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
 4、TIDYING ： `2 << COUNT_BITS`，即高3位为010；
 5、TERMINATED： `3 << COUNT_BITS`，即高3位为011；

### 1.5 线程池的原理
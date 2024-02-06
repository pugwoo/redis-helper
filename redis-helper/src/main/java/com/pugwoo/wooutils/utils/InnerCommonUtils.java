package com.pugwoo.wooutils.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 内部常用工具类
 */
public class InnerCommonUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(InnerCommonUtils.class);

    private static final List<String> ipList = new ArrayList<>();

    static {
        String regex = "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
        Pattern pattern = Pattern.compile(regex);

        try {
            for (Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces(); interfaces.hasMoreElements();) {
                NetworkInterface i = interfaces.nextElement();
                for (Enumeration<InetAddress> addresses = i.getInetAddresses(); addresses.hasMoreElements();) {
                    InetAddress address = addresses.nextElement();
                    if (pattern.matcher(address.getHostAddress()).find() && !address.getHostAddress().startsWith("127.")) {
                        ipList.add(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            LOGGER.error("get local ip error", e);
        }
    }

    public static boolean isBlank(String str) {
        if (str == null) {
            return true;
        }
        int len = str.length();
        for (int i = 0; i < len; i++) {
            if (!Character.isWhitespace(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    /**
     * 获得本机的ipv4的所有ip列表，排除本机ip 127.开头的
     */
    public static List<String> getIpv4IPs() throws SocketException {
        return ipList;
    }

    /**
     * 创建一个线程池，一些默认配置：
     * 1）空闲线程存活时间为60秒
     * 2）拒绝策略：用默认，抛出RejectedExecutionException异常
     * @param coreSize
     * @param queueSize 任务排队队列最大长度
     * @param maxSize
     * @param threadNamePrefix 线程前缀名称
     */
    public static ThreadPoolExecutor createThreadPool(int coreSize, int queueSize, int maxSize, String threadNamePrefix) {
        return new ThreadPoolExecutor(
                coreSize, maxSize,
                60, // 空闲线程存活时间
                TimeUnit.SECONDS, // 存活时间单位
                queueSize <= 0 ? new SynchronousQueue<>() : new LinkedBlockingQueue<>(queueSize),
                new MyThreadFactory(threadNamePrefix)
        );
    }

    /**
     * 创建一个线程池，一些默认配置：
     * 1）空闲线程存活时间为60秒
     * 2）拒绝策略：用默认，抛出RejectedExecutionException异常
     * @param coreSize
     * @param queueSize 任务排队队列最大长度
     * @param maxSize
     * @param threadNamePrefix 线程前缀名称
     * @param threadPriority 线程优先级
     */
    public static ThreadPoolExecutor createThreadPool(int coreSize, int queueSize, int maxSize, String threadNamePrefix,
                                                      Integer threadPriority) {
        return new ThreadPoolExecutor(
                coreSize, maxSize,
                60, // 空闲线程存活时间
                TimeUnit.SECONDS, // 存活时间单位
                queueSize <= 0 ? new SynchronousQueue<>() : new LinkedBlockingQueue<>(queueSize),
                new MyThreadFactory(threadNamePrefix, threadPriority)
        );
    }

    /**
     * 等待所有的future调用完成
     */
    public static List<?> waitAllFuturesDone(List<Future<?>> futures) {
        if (futures == null) {
            return new ArrayList<>();
        }
        List<Object> result = new ArrayList<>();
        for (Future<?> future : futures) {
            try {
                result.add(future.get());
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    private static class MyThreadFactory implements ThreadFactory {

        private final AtomicInteger count = new AtomicInteger(1);
        private final String threadNamePrefix;
        private final Integer threadPriority;

        public MyThreadFactory(String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
            this.threadPriority = null;
        }

        public MyThreadFactory(String threadNamePrefix, Integer threadPriority) {
            this.threadNamePrefix = threadNamePrefix;
            this.threadPriority = threadPriority;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, threadNamePrefix + "-" + count.getAndIncrement());
            if (threadPriority != null) {
                thread.setPriority(threadPriority);
            }
            return thread;
        }
    }

}

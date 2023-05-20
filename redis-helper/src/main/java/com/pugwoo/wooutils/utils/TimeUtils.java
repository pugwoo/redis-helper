package com.pugwoo.wooutils.utils;

import com.pugwoo.wooutils.redis.RedisSyncAspect;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author firefly
 */
public class TimeUtils {


    private static final Logger LOGGER = LoggerFactory.getLogger(RedisSyncAspect.class);

    /**
     * Log the execution time of the code inside the given Runnable using SLF4J, along with the class name and line
     * number
     * where the method was called.
     *
     * @param codeToTrack The code to be executed and have its execution time logged.
     * @param info The info text to be displayed along with the execution time.
     */
    public static <T> T printExecutionTime(String info, Supplier<T> codeToTrack) {
        StackTraceElement caller = Thread.currentThread().getStackTrace()[2];
        String callerInfo = String.format("%s:%d", caller.getClassName(), caller.getLineNumber());

        long startTime = System.nanoTime();
        T result = codeToTrack.get();
        long endTime = System.nanoTime();
        long durationInMillis = TimeUnit.MILLISECONDS.convert(endTime - startTime, TimeUnit.NANOSECONDS);
        LOGGER.info("{} - {}: {} ms", callerInfo, info, durationInMillis);

        return result;
    }

    /**
     * Log the execution time of the code inside the given Runnable using SLF4J, along with the class name and line number
     * where the method was called.
     *
     * @param codeToTrack The code to be executed and have its execution time logged.
     * @param info        The info text to be displayed along with the execution time.
     */
    public static void printExecutionTime( String info,Runnable codeToTrack) {
        StackTraceElement caller = Thread.currentThread().getStackTrace()[2];
        String callerInfo = String.format("%s:%d", caller.getClassName(), caller.getLineNumber());

        long startTime = System.nanoTime();
        codeToTrack.run();
        long endTime = System.nanoTime();
        long durationInMillis = TimeUnit.MILLISECONDS.convert(endTime - startTime, TimeUnit.NANOSECONDS);
        LOGGER.info("{} - {}: {} ms", callerInfo, info, durationInMillis);
    }
}

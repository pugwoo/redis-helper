package com.pugwoo.redishelpertest.redis.sync;

import com.pugwoo.wooutils.redis.Synchronized;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

@Service
public class HeartbeatTestService {

    // 等待时间故意设置很长，一直要等到可以执行为止
    @Synchronized(namespace = "heartbeat", heartbeatExpireSecond = 7, waitLockMillisecond = 10000000)
    public void longTask() {

        String uuid = UUID.randomUUID().toString();

        System.out.println(new Date() + "任务开始" + uuid);
        System.out.println(new Date() + "现在开始睡眠 15秒" + uuid);

        try {
            Thread.sleep(15 * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println(new Date() + "睡眠结束，任务结束" + uuid);
    }

}

package com.antz.distributed.lock.AntzDistributedLock;

import com.antz.distributed.lock.redis.RedisLock;
import com.antz.distributed.lock.service.IBizService;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.lang.Nullable;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @program: Antz-DistributedLock
 * @description:
 * @author: huanghuang@rewin.com.cn
 * @Create: 2019-05-12 17:16
 **/
@Slf4j
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest
public class MultiTheadTest {

    @Autowired
    private IBizService bizService;

    @Autowired
    private RedisTemplate redisTemplate;

    private int i = 0;

    @Test
    public void RedisLockedTest() {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        while (true) {
            executorService.execute(() -> {
                bizService.add();
            });
        }
    }

    @Test
    public void RedisSetNXTest() {
        ExecutorService executorService = Executors.newFixedThreadPool(1);
        while (true) {
            executorService.execute(() -> {
                Object obj = null;
                try {
                    obj = redisTemplate.execute(new RedisCallback() {
                        @Nullable
                        @Override
                        public Object doInRedis(RedisConnection connection) throws DataAccessException {
                            StringRedisSerializer serializer = new StringRedisSerializer();
                            boolean success = connection.setNX(serializer.serialize("key"), serializer.serialize("12312"));
                            connection.close();
                            return success;
                        }
                    });
                    System.out.println(obj);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

        }

    }

}

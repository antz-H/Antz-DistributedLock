package com.antz.distributed.lock.service.impl;

import com.antz.distributed.lock.redis.RedisLock;
import com.antz.distributed.lock.service.IBizService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * @program: Antz-DistributedLock
 * @description:
 * @author: huanghuang@rewin.com.cn
 * @Create: 2019-05-12 16:42
 **/
@Slf4j
@Service
public class BizServiceImpl implements IBizService {
    @Autowired
    private RedisTemplate redisTemplate;

    private int i = 0;

    @Override
    public String add() {
        log.info("开始");
        RedisLock redisLock = new RedisLock(redisTemplate, "biz");
        try {
            if (redisLock.lock()) {
                i++;
                log.info("当前线程，{}；当前i值：{}", Thread.currentThread().getId(), i);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            redisLock.unlock();
        }
        return "success";
    }

    @Override
    public String plus() {
        return null;
    }
}

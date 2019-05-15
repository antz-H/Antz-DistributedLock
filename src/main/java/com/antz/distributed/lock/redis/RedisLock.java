package com.antz.distributed.lock.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * @program: Antz-DistributedLock
 * @description:
 * @author: huang7230468@163.com
 * @Create: 2019-05-12 13:58
 **/
@Slf4j
public class RedisLock {
    /**
     * 采用spring-data中RedisTemplate实现redis操作
     */
    private RedisTemplate redisTemplate;

    public static final int DEFAULT_ACQUIRY_RESOLUTION_MILLIS = 100;

    /**
     * redis lock key
     */
    private String lockKey;
    /**
     * 客户端唯一标识key，这里采用UUID生成，可自定义
     */
    private String uniqueClientID;

    private long expires ;

    /**
     * 锁超时时间，防止线程获取锁之后不主动释放锁，后续线程持续等待
     */
    private int expireMsecs = 60 * 1000;

    /**
     * 锁等待时间，防止线程饥饿（通过线程延迟）
     */
    private int timeoutMsecs = 10 * 1000;

    /**
     * 锁状态，默认为false
     */
    private boolean locked = false;

    public RedisLock(RedisTemplate redisTemplate, String lockKey) {
        this.redisTemplate = redisTemplate;
        this.lockKey = lockKey;
        this.uniqueClientID = UUID.randomUUID().toString();
    }

    public RedisLock(RedisTemplate redisTemplate, String lockKey, int expireMsecs) {
        this(redisTemplate, lockKey);
        this.expireMsecs = expireMsecs;
    }

    /**
     * @param redisTemplate
     * @param lockKey
     * @param expireMsecs   锁超时时间，获取锁后，锁的超时机制
     * @param timeoutMsecs  锁等待时间，即等待获取锁的最大时间
     */
    public RedisLock(RedisTemplate redisTemplate, String lockKey, int expireMsecs, int timeoutMsecs) {
        this(redisTemplate, lockKey, expireMsecs);
        this.timeoutMsecs = timeoutMsecs;
    }

    /**
     * 获取锁
     * 实现思路
     *
     * @return
     */
    public boolean lock() throws InterruptedException {
        int timeout = timeoutMsecs;
        while (timeout >= 0) {
            this.expires = System.currentTimeMillis() + expireMsecs + 1;
            String expiresStr = String.valueOf(expires);
            //尝试设置锁
            if (this.setNX(lockKey, expiresStr)) {
                this.locked = true;
                return true;
            }
            String currentValueStr = this.get(lockKey);
            //currentValueStr小于当前时间，说明已经超时，那么可以用新值来替换旧值
            if (currentValueStr != null && Long.valueOf(currentValueStr) < System.currentTimeMillis()) {
                String oldValueStr = this.getSet(lockKey, expiresStr);
                //这里可能会由于多个客户端产生一个竞态条件，A、B同时执行getSet，若A的expiresStr覆盖了B的expiresStr，且这时B符合if条件获得了锁，
                //那么B的expiresStr，可能会和之前的设置的有一点小误差，但是不影响
                if (oldValueStr != null && oldValueStr.equals(currentValueStr)) {
                    locked = true;
                    return true;
                }
            }
            //可重试的次数
            timeout -= DEFAULT_ACQUIRY_RESOLUTION_MILLIS;
            if (timeout >= 0) {
                //延迟获取锁，防止饥饿线程，这里采用减去随机数，随机的等待时间在一定程度上保证线程公平性
                Thread.sleep(DEFAULT_ACQUIRY_RESOLUTION_MILLIS + (int) (Math.random() * 10));
            }
        }
        return false;
    }

    /**
     * 释放锁
     *
     * @return
     */
    public void unlock() {
        if (locked) {
            String ValueStr = this.get(lockKey);
            String expiresStr = String.valueOf(this.expires);
            //删除锁前，先校验是否是自己的锁
            if (ValueStr != null && ValueStr.equals(expiresStr)) {
                delete(lockKey);
            }
            locked = false;
        }
    }

    /**
     * setNX（SET if Not eXists）
     * 若key存在，则不操作
     * 若key不存在，则设置为value
     *
     * @param key
     * @param value
     * @return 若key存在：返回false
     * 若key不存在：返回true
     */
    private boolean setNX(final String key, final String value) {
        Object obj = null;
        try {
            obj = redisTemplate.execute(new RedisCallback() {
                @Nullable
                @Override
                public Object doInRedis(RedisConnection connection) throws DataAccessException {
                    StringRedisSerializer serializer = new StringRedisSerializer();
                    boolean success = connection.setNX(serializer.serialize(key), serializer.serialize(value));
                    connection.close();
                    return success;
                }
            });
        } catch (Exception e) {
            log.error("redis setNX value error, key:{} ,value:{},error:{}", key, value, e);
        }
        return obj != null ? (Boolean) obj : false;
    }

    private String get(final String key) {
        Object obj = null;
        try {
            obj = redisTemplate.execute(new RedisCallback() {
                @Nullable
                @Override
                public Object doInRedis(RedisConnection connection) throws DataAccessException {
                    StringRedisSerializer serializer = new StringRedisSerializer();
                    byte[] bytes = connection.get(serializer.serialize(key));
                    connection.close();
                    if (bytes == null) {
                        return null;
                    }
                    return serializer.deserialize(bytes);
                }
            });
        } catch (Exception e) {
            log.error("redis get value error, key:{}, error:{}", key, e);
        }
        return obj == null ? null : obj.toString();
    }

    /**
     * 将给定key的值设置为value，并返回key的旧值
     *
     * @param key
     * @param value
     * @return 成功：key对应的旧值
     * 失败：null
     */
    private String getSet(final String key, final String value) {
        Object obj = null;
        try {
            obj = redisTemplate.execute(new RedisCallback() {
                @Nullable
                @Override
                public Object doInRedis(RedisConnection connection) throws DataAccessException {
                    StringRedisSerializer serializer = new StringRedisSerializer();
                    byte[] bytes = connection.getSet(serializer.serialize(key), serializer.serialize(value));
                    connection.close();
                    if (bytes == null) {
                        return null;
                    }
                    return serializer.deserialize(bytes);
                }
            });
        } catch (Exception e) {
            log.error("redis getSet value error, key:{}, value:{}, error:{}", key, value, e);
        }
        return obj == null ? null : obj.toString();
    }

    private boolean delete(final String key) {
        Object obj = null;
        try {
            obj = redisTemplate.execute(new RedisCallback() {
                @Nullable
                @Override
                public Object doInRedis(RedisConnection connection) throws DataAccessException {
                    StringRedisSerializer serializer = new StringRedisSerializer();
                    Long i = connection.del(serializer.serialize(key));
                    connection.close();
                    if (i > 0) {
                        return true;
                    }
                    return false;
                }
            });
        } catch (Exception e) {
            log.error("redis delete key error, key:{}, error:{}", key, e);
        }
        return obj == null ? false : (boolean) obj;
    }

    private String combineRedisValue(String value) {
        return value + ":" + uniqueClientID;
    }

    private String sparatedRedisValue(String value) {
        return value == null ? "" : value.split(":")[0];
    }


}


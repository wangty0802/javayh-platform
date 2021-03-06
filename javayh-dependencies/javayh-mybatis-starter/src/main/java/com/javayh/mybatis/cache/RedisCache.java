package com.javayh.mybatis.cache;

import com.javayh.common.selector.SpringSelector;
import com.javayh.common.util.log.Log;
import com.javayh.common.util.spring.SpringUtils;
import com.javayh.redis.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * <p>
 *     Custom cache
 * </p>
 *
 * @author Dylan-haiji
 * @version 1.0.0
 * @since 2020-05-11
 */
@Slf4j
@Configuration
public class RedisCache implements Cache {

    private String id;

    /** 读写锁*/
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock(true);

    public RedisCache() {
    }

    public RedisCache(final String id) {
        if (id == null) {
            throw new IllegalArgumentException("Cache instances require an ID");
        }
        this.id = id;
    }
    @Autowired
    private RedisUtil redisUtil;
    private static RedisCache redisCache ;
    //通过@PostConstruct实现初始化bean之前进行的操作
    @PostConstruct
    public void init() {
        redisCache = this;
        redisCache.redisUtil = this.redisUtil;
    }
    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public void putObject(Object key, Object value) {
        if (value != null) {
            //向Redis中添加数据，有效时间是12小时
            redisCache.redisUtil.setObj(key.toString(),value,43200);
            log.debug(value.toString());
        }
    }

    @Override
    public Object getObject(Object key) {
        try {
            if (key != null && readWriteLock.readLock().tryLock()) {
                return redisCache.redisUtil.get(key.toString());
            }
        } catch (Exception e) {
            Log.error("Mybatis Get Cache",e.getStackTrace());
        }finally {
            log.debug(key.toString());
            readWriteLock.readLock().unlock();
        }
        return null;
    }

    @Override
    public Object removeObject(Object key) {
        try {
            if (key != null  && readWriteLock.writeLock().tryLock()) {
                redisCache.redisUtil.del(key.toString());
            }
        } catch (Exception e) {
            Log.error("Mybatis Del Cache",e.getStackTrace());
        }finally {
            log.debug(key.toString());
            readWriteLock.writeLock().unlock();
        }
        return null;
    }

    @Override
    public void clear() {
        try {
            Set<String> keys = redisCache.redisUtil.keys(this.id);
            if (!CollectionUtils.isEmpty(keys) && readWriteLock.writeLock().tryLock()) {
                redisCache.redisUtil.del(keys);
            }
        } catch (Exception e) {
            Log.error("Mybatis Clear Cache",e.getStackTrace());
        }finally {
            readWriteLock.writeLock().unlock();
        }
    }

    @Override
    public int getSize() {
        return redisCache.redisUtil.execute();
    }

    @Override
    public ReadWriteLock getReadWriteLock() {
        return this.readWriteLock;
    }

}

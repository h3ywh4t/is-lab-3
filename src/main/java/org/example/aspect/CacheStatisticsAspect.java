package org.example.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManagerFactory;

@Aspect
@Component
public class CacheStatisticsAspect {

    private static final Logger logger = LoggerFactory.getLogger(CacheStatisticsAspect.class);

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Value("${app.cache.statistics.enabled:false}")
    private boolean statisticsEnabled;

    @Around("@annotation(org.example.annotation.LogCacheStatistics)")
    public Object logCacheStatistics(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!statisticsEnabled) {
            return joinPoint.proceed();
        }

        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        
        long secondLevelCacheHitCountBefore = statistics.getSecondLevelCacheHitCount();
        long secondLevelCacheMissCountBefore = statistics.getSecondLevelCacheMissCount();
        long queryCacheHitCountBefore = statistics.getQueryCacheHitCount();
        long queryCacheMissCountBefore = statistics.getQueryCacheMissCount();

        Object result = joinPoint.proceed();

        long secondLevelCacheHitCountAfter = statistics.getSecondLevelCacheHitCount();
        long secondLevelCacheMissCountAfter = statistics.getSecondLevelCacheMissCount();
        long queryCacheHitCountAfter = statistics.getQueryCacheHitCount();
        long queryCacheMissCountAfter = statistics.getQueryCacheMissCount();

        long l2CacheHits = secondLevelCacheHitCountAfter - secondLevelCacheHitCountBefore;
        long l2CacheMisses = secondLevelCacheMissCountAfter - secondLevelCacheMissCountBefore;
        long queryCacheHits = queryCacheHitCountAfter - queryCacheHitCountBefore;
        long queryCacheMisses = queryCacheMissCountAfter - queryCacheMissCountBefore;

        logger.info("L2 JPA Cache Statistics for method: {}", joinPoint.getSignature().toShortString());
        logger.info("L2 Cache Hits: {}", l2CacheHits);
        logger.info("L2 Cache Misses: {}", l2CacheMisses);
        logger.info("Query Cache Hits: {}", queryCacheHits);
        logger.info("Query Cache Misses: {}", queryCacheMisses);
        
        if (l2CacheHits + l2CacheMisses > 0) {
            double l2HitRatio = (double) l2CacheHits / (l2CacheHits + l2CacheMisses) * 100;
            logger.info("L2 Cache Hit Ratio: {:.2f}%", l2HitRatio);
        }
        
        if (queryCacheHits + queryCacheMisses > 0) {
            double queryHitRatio = (double) queryCacheHits / (queryCacheHits + queryCacheMisses) * 100;
            logger.info("Query Cache Hit Ratio: {:.2f}%", queryHitRatio);
        }
        logger.info("================================================");

        return result;
    }
}
package com.project.airBnbApp.aspect;

import com.project.airBnbApp.annotation.RateLimit;
import com.project.airBnbApp.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitAspect {

    private final StringRedisTemplate redisTemplate;

    @Around("@annotation(rateLimit)")
    public Object checkRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        HttpServletRequest request = ((ServletRequestAttributes)
                RequestContextHolder.currentRequestAttributes()).getRequest();

        String ip = getClientIp(request);
        String key = "rate_limit:" + ip + ":" + joinPoint.getSignature().getName();

        long now = System.currentTimeMillis();
        long windowStart = now - (rateLimit.windowSeconds() * 1000L);

        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

        // remove requests outside the sliding window
        zSetOps.removeRangeByScore(key, 0, windowStart);

        Long count = zSetOps.zCard(key);

        if (count != null && count >= rateLimit.maxRequests()) {
            log.warn("Rate limit exceeded for IP: {} on endpoint: {}", ip, joinPoint.getSignature().getName());
            throw new RateLimitExceededException(
                    "Too many requests. Please try again in " + rateLimit.windowSeconds() + " seconds."
            );
        }

        // record this request with its timestamp as score
        zSetOps.add(key, String.valueOf(now), now);
        redisTemplate.expire(key, rateLimit.windowSeconds(), TimeUnit.SECONDS);

        return joinPoint.proceed();
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

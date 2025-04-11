//package no.gunbang.market.common.aop.aspect;
//
//import io.micrometer.core.instrument.MeterRegistry;
//import lombok.RequiredArgsConstructor;
//import org.aspectj.lang.ProceedingJoinPoint;
//import org.aspectj.lang.annotation.Around;
//import org.aspectj.lang.annotation.Aspect;
//import org.springframework.stereotype.Component;
//
//@Aspect
//@Component
//@RequiredArgsConstructor
//public class RedisValueOperationAspect {
//
//    private final MeterRegistry meterRegistry;
//
//    @Around("execution(* org.springframework.data.redis.core.ValueOperations.get(..)) && args(key)")
//    public Object monitorGet(ProceedingJoinPoint joinPoint, Object key) throws Throwable {
//        meterRegistry.counter("redis_key_access_total", "key", key.toString(), "command", "GET").increment();
//        return joinPoint.proceed();
//    }
//
//    @Around("execution(* org.springframework.data.redis.core.ValueOperations.set(..)) && args(key, value)")
//    public Object monitorSet(ProceedingJoinPoint joinPoint, Object key, Object value) throws Throwable {
//        meterRegistry.counter("redis_key_access_total", "key", key.toString(), "command", "SET").increment();
//        return joinPoint.proceed();
//    }
//}

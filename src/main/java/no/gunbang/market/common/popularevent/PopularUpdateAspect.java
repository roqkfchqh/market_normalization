package no.gunbang.market.common.popularevent;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Aspect
@Component
@RequiredArgsConstructor
public class PopularUpdateAspect {

    private final StringRedisTemplate redisTemplate;
    private static final String STREAM_KEY = "stream:popular:update";

    @Around("@annotation(triggerPopularUpdate)")
    public Object triggerPopularUpdate(ProceedingJoinPoint joinPoint, TriggerPopularUpdate triggerPopularUpdate) throws Throwable {
        Object result = joinPoint.proceed();

        //Stream 에 이벤트 push
        Map<String, String> data = Map.of(
                "event", triggerPopularUpdate.target().name()
        );

        redisTemplate.opsForStream().add(STREAM_KEY, data);
        return result;
    }
}


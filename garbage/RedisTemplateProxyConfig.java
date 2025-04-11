//package no.gunbang.market.common.config;
//
//import io.micrometer.core.instrument.MeterRegistry;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.data.redis.connection.BitFieldSubCommands;
//import org.springframework.data.redis.connection.RedisConnectionFactory;
//import org.springframework.data.redis.core.RedisOperations;
//import org.springframework.data.redis.core.StringRedisTemplate;
//import org.springframework.data.redis.core.ValueOperations;
//
//import java.time.Duration;
//import java.util.Collection;
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.TimeUnit;
//
//@Configuration
//public class RedisTemplateProxyConfig {
//
//    @Bean
//    public StringRedisTemplate stringRedisTemplate(
//            RedisConnectionFactory redisConnectionFactory,
//            MeterRegistry meterRegistry
//    ) {
//        StringRedisTemplate delegate = new StringRedisTemplate(redisConnectionFactory);
//
//        return new StringRedisTemplate(redisConnectionFactory) {
//            @Override
//            public ValueOperations<String, String> opsForValue() {
//                ValueOperations<String, String> original = delegate.opsForValue();
//
//                return new ValueOperations<>() {
//                    @Override
//                    public String get(Object key) {
//                        meterRegistry.counter("redis_key_access_total", "key", key.toString(), "command", "GET").increment();
//                        return original.get(key);
//                    }
//
//                    @Override
//                    public void set(String key, String value) {
//                        meterRegistry.counter("redis_key_access_total", "key", key, "command", "SET").increment();
//                        original.set(key, value);
//                    }
//
//                    @Override
//                    public String getAndDelete(String key) {
//                        return "";
//                    }
//
//                    @Override
//                    public String getAndExpire(String key, long timeout, TimeUnit unit) {
//                        return "";
//                    }
//
//                    @Override
//                    public String getAndExpire(String key, Duration timeout) {
//                        return "";
//                    }
//
//                    @Override
//                    public String getAndPersist(String key) {
//                        return "";
//                    }
//
//                    @Override
//                    public void set(String key, String value, long timeout, TimeUnit unit) {
//                    }
//
//                    @Override public Boolean setIfAbsent(String key, String value) {
//                        return original.setIfAbsent(key, value);
//                    }
//
//                    @Override
//                    public Boolean setIfAbsent(String key, String value, long timeout, TimeUnit unit) {
//                        return null;
//                    }
//
//                    @Override public Boolean setIfPresent(String key, String value) {
//                        return original.setIfPresent(key, value);
//                    }
//
//                    @Override
//                    public Boolean setIfPresent(String key, String value, long timeout, TimeUnit unit) {
//                        return null;
//                    }
//
//                    @Override
//                    public void multiSet(Map<? extends String, ? extends String> map) {
//
//                    }
//
//                    @Override
//                    public Boolean multiSetIfAbsent(Map<? extends String, ? extends String> map) {
//                        return null;
//                    }
//
//                    @Override public Long increment(String key) {
//                        return original.increment(key);
//                    }
//
//                    @Override public Long increment(String key, long delta) {
//                        return original.increment(key, delta);
//                    }
//
//                    @Override public Double increment(String key, double delta) {
//                        return original.increment(key, delta);
//                    }
//
//                    @Override
//                    public Long decrement(String key) {
//                        return 0L;
//                    }
//
//                    @Override
//                    public Long decrement(String key, long delta) {
//                        return 0L;
//                    }
//
//                    @Override public Integer append(String key, String value) {
//                        return original.append(key, value);
//                    }
//
//                    @Override
//                    public String get(String key, long start, long end) {
//                        return "";
//                    }
//
//                    @Override
//                    public void set(String key, String value, long offset) {
//
//                    }
//
//                    @Override public String getAndSet(String key, String value) {
//                        return original.getAndSet(key, value);
//                    }
//
//                    @Override
//                    public List<String> multiGet(Collection<String> keys) {
//                        return List.of();
//                    }
//
//                    @Override public Long size(String key) {
//                        return original.size(key);
//                    }
//
//                    @Override public Boolean setBit(String key, long offset, boolean value) {
//                        return original.setBit(key, offset, value);
//                    }
//
//                    @Override public Boolean getBit(String key, long offset) {
//                        return original.getBit(key, offset);
//                    }
//
//                    @Override
//                    public List<Long> bitField(String key, BitFieldSubCommands subCommands) {
//                        return List.of();
//                    }
//
//                    @Override
//                    public RedisOperations<String, String> getOperations() {
//                        return null;
//                    }
//                };
//            }
//        };
//    }
//}

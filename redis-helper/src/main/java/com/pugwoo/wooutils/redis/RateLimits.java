package com.pugwoo.wooutils.redis;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface RateLimits {

    RateLimit[] value();

}

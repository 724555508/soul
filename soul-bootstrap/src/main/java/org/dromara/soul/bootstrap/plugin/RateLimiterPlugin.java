package org.dromara.soul.bootstrap.plugin;

import org.dromara.soul.common.dto.RuleData;
import org.dromara.soul.common.dto.SelectorData;
import org.dromara.soul.common.dto.convert.RateLimiterHandle;
import org.dromara.soul.common.enums.PluginTypeEnum;
import org.dromara.soul.common.utils.GsonUtils;
import org.dromara.soul.web.cache.LocalCacheManager;
import org.dromara.soul.web.plugin.AbstractSoulPlugin;
import org.dromara.soul.web.plugin.SoulPluginChain;
import org.dromara.soul.web.plugin.ratelimter.RedisRateLimiter;
import org.dromara.soul.web.result.SoulResultEnum;
import org.dromara.soul.web.result.SoulResultUtils;
import org.dromara.soul.web.result.SoulResultWarp;
import org.dromara.soul.web.support.HostAddressUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

@Component("diyRateLimiterPlugin")
public class RateLimiterPlugin extends AbstractSoulPlugin{
	
	
	private static final Logger LOGGER = LoggerFactory.getLogger(RateLimiterPlugin.class);
	
	private final RedisRateLimiter redisRateLimiter;

	public RateLimiterPlugin(LocalCacheManager localCacheManager , RedisRateLimiter redisRateLimiter) {
		super(localCacheManager);
		this.redisRateLimiter= redisRateLimiter;
	}

	@Override
	public PluginTypeEnum pluginType() {
		return PluginTypeEnum.FUNCTION;
	}

	@Override
	public int getOrder() {
		return 15;
	}

	@Override
	public String named() {
		return "ip_rate_limiter";
	}

	@Override
	protected Mono<Void> doExecute(ServerWebExchange exchange, SoulPluginChain chain, SelectorData selector,
			RuleData rule) {
		final String handle = rule.getHandle();
		final RateLimiterHandle limiterHandle = GsonUtils.getInstance().fromJson(handle, RateLimiterHandle.class);
		String acquireIp = HostAddressUtils.acquireIp(exchange);
		return redisRateLimiter.isAllowed(rule.getId() + acquireIp , limiterHandle.getReplenishRate() , limiterHandle.getBurstCapacity())
                .flatMap(response -> {
                    if (!response.isAllowed()) {
                    	LOGGER.info("ip {} 请求数量过多，已拦截",acquireIp);
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        Object error = SoulResultWarp.error(SoulResultEnum.TOO_FAST_REQUEST.getCode(), SoulResultEnum.TOO_FAST_REQUEST.getMsg(), null);
                        return SoulResultUtils.result(exchange, error);
                    }
                    return chain.execute(exchange);
                });
	}

}

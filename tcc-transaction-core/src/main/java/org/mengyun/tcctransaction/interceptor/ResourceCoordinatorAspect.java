package org.mengyun.tcctransaction.interceptor;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

/**
 * Created by changmingxie on 11/8/15.
 * 资源协调者拦截器
 * 通过 org.aspectj.lang.annotation.@Pointcut + org.aspectj.lang.annotation.@Around 注解，
 * 配置对 @Compensable 注解的方法进行拦截，调用 ResourceCoordinatorInterceptor#interceptTransactionContextMethod(...)
 * 方法进行处理。
 */
@Aspect
public abstract class ResourceCoordinatorAspect {
    /**
     * 拦截器
     */
    private ResourceCoordinatorInterceptor resourceCoordinatorInterceptor;

    @Pointcut("@annotation(org.mengyun.tcctransaction.api.Compensable)")
    public void transactionContextCall() {

    }

    @Around("transactionContextCall()")
    public Object interceptTransactionContextMethod(ProceedingJoinPoint pjp) throws Throwable {
        return resourceCoordinatorInterceptor.interceptTransactionContextMethod(pjp);
    }

    public void setResourceCoordinatorInterceptor(ResourceCoordinatorInterceptor resourceCoordinatorInterceptor) {
        this.resourceCoordinatorInterceptor = resourceCoordinatorInterceptor;
    }

    public abstract int getOrder();
}

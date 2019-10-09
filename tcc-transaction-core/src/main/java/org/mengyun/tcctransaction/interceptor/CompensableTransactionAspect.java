package org.mengyun.tcctransaction.interceptor;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

/**
 * Created by changmingxie on 10/30/15.
 * 可补偿事务拦截器
 */
@Aspect
public abstract class CompensableTransactionAspect {

    private CompensableTransactionInterceptor compensableTransactionInterceptor;

    public void setCompensableTransactionInterceptor(CompensableTransactionInterceptor compensableTransactionInterceptor) {
        this.compensableTransactionInterceptor = compensableTransactionInterceptor;
    }
    //切点，拦截有Compensable注解的方法
    @Pointcut("@annotation(org.mengyun.tcctransaction.api.Compensable)")
    public void compensableService() {

    }
    //环绕切面
    @Around("compensableService()")
    public Object interceptCompensableMethod(ProceedingJoinPoint pjp) throws Throwable {
        //对方法进行执行
        return compensableTransactionInterceptor.interceptCompensableMethod(pjp);
    }

    public abstract int getOrder();
}

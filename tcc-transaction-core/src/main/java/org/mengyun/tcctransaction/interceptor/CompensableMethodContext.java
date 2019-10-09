package org.mengyun.tcctransaction.interceptor;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.mengyun.tcctransaction.api.Compensable;
import org.mengyun.tcctransaction.api.Propagation;
import org.mengyun.tcctransaction.api.TransactionContext;
import org.mengyun.tcctransaction.api.UniqueIdentity;
import org.mengyun.tcctransaction.common.MethodRole;
import org.mengyun.tcctransaction.support.FactoryBuilder;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

/**
 * Created by changming.xie on 04/04/19.
 */
public class CompensableMethodContext {

    ProceedingJoinPoint pjp = null;

    Method method = null;

    Compensable compensable = null;

    Propagation propagation = null;

    TransactionContext transactionContext = null;

    public CompensableMethodContext(ProceedingJoinPoint pjp) {
        this.pjp = pjp;
        //获取@Compensable注解
        this.method = getCompensableMethod();
        this.compensable = method.getAnnotation(Compensable.class);
        this.propagation = compensable.propagation();
        //获取事务上下文
        this.transactionContext = FactoryBuilder.factoryOf(compensable.transactionContextEditor()).getInstance().get(pjp.getTarget(), method, pjp.getArgs());

    }

    public Compensable getAnnotation() {
        return compensable;
    }

    public Propagation getPropagation() {
        return propagation;
    }

    public TransactionContext getTransactionContext() {
        return transactionContext;
    }

    public Method getMethod() {
        return method;
    }

    public Object getUniqueIdentity() {
        Annotation[][] annotations = this.getMethod().getParameterAnnotations();

        for (int i = 0; i < annotations.length; i++) {
            for (Annotation annotation : annotations[i]) {
                if (annotation.annotationType().equals(UniqueIdentity.class)) {

                    Object[] params = pjp.getArgs();
                    Object unqiueIdentity = params[i];

                    return unqiueIdentity;
                }
            }
        }

        return null;
    }

    /**
     * 获取@Compensable注解方法
     * @return
     */
    private Method getCompensableMethod() {
        //代理方法对象
        Method method = ((MethodSignature) (pjp.getSignature())).getMethod();

        if (method.getAnnotation(Compensable.class) == null) {
            try {
                method = pjp.getTarget().getClass().getMethod(method.getName(), method.getParameterTypes());
            } catch (NoSuchMethodException e) {
                return null;
            }
        }
        return method;
    }

    /**
     * 计算方法类型
     * 根据不同方法类型，做不同的事务处理
     * 方法类型为MethodRole.ROOT时，发起根事务，判断条件如下二选一：
     *      1.事务传播级别为Propagation.REQUIRED，并且当前没有事务
     *      2.事务传播级别为Propagation.REQUIRES_NEW)，新建事务，如果当前存在事务，把当前事务挂起。此时事务管理器的当前线程事务队列可能
     *      会存在多个事务。
     *          方法类型为MethodRole.ROOT时，发起的分支事务，判断条件如下二选一：
     *              事务传播级别为Propagation.REQUIRED，并且当前不存在事务，并且方法参数传递了事务上下文
     *              事务传播级别为 Propagation.PROVIDER，并且当前不存在事务，并且方法参数传递了事务上下文。
     *              当前不存在事务，方法参数传递了事务上下文是什么意思？当跨服务远程调用时，被调用服务本身( 服务提供者 )
     *              不在事务中，通过传递事务上下文参数，融入当前事务。
     *                  方法类型为 MethodType.Normal时，不进行事务处理
     *                  MethodType.CONSUMER 项目已经不再使用，猜测已废弃。
     * @param isTransactionActive
     * @return
     */
    public MethodRole getMethodRole(boolean isTransactionActive) {
        /**
         * Propagation.REQUIRED:支持当前事务，当前没有事务，就新建一个事务
         * Propagation.REQUIRES_NEW:新建事务，如果当前存在事务，把当前事务挂起
         */
        if ((propagation.equals(Propagation.REQUIRED) && !isTransactionActive && transactionContext == null) ||
                propagation.equals(Propagation.REQUIRES_NEW)) {
            return MethodRole.ROOT;

            /**
             * Propagation.REQUIRED:支持当前事务
             * Propagation.MANDATORY：支持当前事务
             */
        } else if ((propagation.equals(Propagation.REQUIRED) || propagation.equals(Propagation.MANDATORY)) && !isTransactionActive && transactionContext != null) {
            return MethodRole.PROVIDER;
        } else {
            return MethodRole.NORMAL;
        }
    }

    public Object proceed() throws Throwable {
        return this.pjp.proceed();
    }
}
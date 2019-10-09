package org.mengyun.tcctransaction.interceptor;

import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.aspectj.lang.ProceedingJoinPoint;
import org.mengyun.tcctransaction.NoExistedTransactionException;
import org.mengyun.tcctransaction.SystemException;
import org.mengyun.tcctransaction.Transaction;
import org.mengyun.tcctransaction.TransactionManager;
import org.mengyun.tcctransaction.api.TransactionStatus;
import org.mengyun.tcctransaction.utils.ReflectionUtils;
import org.mengyun.tcctransaction.utils.TransactionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by changmingxie on 10/30/15.
 */
public class CompensableTransactionInterceptor {

    static final Logger logger = Logger.getLogger(CompensableTransactionInterceptor.class.getSimpleName());

    private TransactionManager transactionManager;

    private Set<Class<? extends Exception>> delayCancelExceptions = new HashSet<Class<? extends Exception>>();

    public void setTransactionManager(TransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public void setDelayCancelExceptions(Set<Class<? extends Exception>> delayCancelExceptions) {
        this.delayCancelExceptions.addAll(delayCancelExceptions);
    }
    //执行拦截
    public Object interceptCompensableMethod(ProceedingJoinPoint pjp) throws Throwable {
        //获取带@Compensable注解方法
        CompensableMethodContext compensableMethodContext = new CompensableMethodContext(pjp);
        //当前线程是否在事务中
        boolean isTransactionActive = transactionManager.isTransactionActive();
        //判断事务上下文是否合法
        if (!TransactionUtils.isLegalTransactionContext(isTransactionActive, compensableMethodContext)) {
            throw new SystemException("no active compensable transaction while propagation is mandatory for method " + compensableMethodContext.getMethod().getName());
        }
        //处理
        switch (compensableMethodContext.getMethodRole(isTransactionActive)) {
            case ROOT:
                return rootMethodProceed(compensableMethodContext);
            case PROVIDER:
                //当方法类型为Propagation.PROVIDER时，服务提供者参与TCC整体流程
                return providerMethodProceed(compensableMethodContext);
            default:
                //当方法类型为 Propagation.NORMAL 时，执行方法原逻辑，不进行事务处理
                return pjp.proceed();
        }
    }


    private Object rootMethodProceed(CompensableMethodContext compensableMethodContext) throws Throwable {

        Object returnValue = null;

        Transaction transaction = null;
        //获取异步提交标识
        boolean asyncConfirm = compensableMethodContext.getAnnotation().asyncConfirm();
        //获取异步取消标识
        boolean asyncCancel = compensableMethodContext.getAnnotation().asyncCancel();
        //添加延迟取消异常
        Set<Class<? extends Exception>> allDelayCancelExceptions = new HashSet<Class<? extends Exception>>();
        //添加标签中设置的延迟异常
        allDelayCancelExceptions.addAll(this.delayCancelExceptions);
        allDelayCancelExceptions.addAll(Arrays.asList(compensableMethodContext.getAnnotation().delayCancelExceptions()));

        try {
            //发起根事务 TCC try阶段
            transaction = transactionManager.begin(compensableMethodContext.getUniqueIdentity());

            try {
                //执行方法原逻辑 执行 try
                returnValue = compensableMethodContext.proceed();
            } catch (Throwable tryingException) {
                /**
                 * 当原逻辑执行异常时，TCC try阶段失败，调用TransactionManager#rollback方法，TCC Cancel阶段，回滚事务。此处#
                 * isDelayCancelException（）方法，判断异常是否为延迟取消回滚异常，部分异常不适合立即回滚事务。
                 */
                //是否延迟回滚
                if (!isDelayCancelException(tryingException, allDelayCancelExceptions)) {

                    logger.warn(String.format("compensable transaction trying failed. transaction content:%s", JSON.toJSONString(transaction)), tryingException);
                    //回滚事务
                    transactionManager.rollback(asyncCancel);
                }

                throw tryingException;
            }
            //提交事务
            transactionManager.commit(asyncConfirm);

        } finally {
            //将事务从当前线程事务队列移除
            transactionManager.cleanAfterCompletion(transaction);
        }

        return returnValue;
    }

    /**
     * 1.当事务处于 TransactionStatus.TRYING 时，调用 TransactionManager#propagationExistBegin(...) 方法，
     * 传播发起分支事务。发起分支事务完成后，调用 ProceedingJoinPoint#proceed() 方法，执行方法原逻辑( 即 Try 逻辑 )。
     *      为什么要传播发起分支事务？在根事务进行 Confirm / Cancel 时，调用根事务上的参与者们提交或回滚事务时，
     *      进行远程服务方法调用的参与者，可以通过自己的事务编号关联上传播的分支事务( 两者的事务编号相等 )，进行事务的提交或回滚。
     * 2.当事务处于 TransactionStatus.CONFIRMING 时，调用 TransactionManager#commit() 方法，提交事务。
     * 3.当事务处于 TransactionStatus.CANCELLING 时，调用 TransactionManager#rollback() 方法，提交事务。
     * 4.调用 TransactionManager#cleanAfterCompletion(...) 方法，将事务从当前线程事务队列移除，避免线程冲突。
     * 5.当事务处于 TransactionStatus.CONFIRMING / TransactionStatus.CANCELLING 时，调用 ReflectionUtils#getNullValue(...)
     * 方法，返回空值。为什么返回空值？Confirm / Cancel 相关方法，是通过 AOP 切面调用，只调用，不处理返回值，但是又不能没有返回值，因此直接返回空。
     * @param compensableMethodContext
     * @return
     * @throws Throwable
     */
    private Object providerMethodProceed(CompensableMethodContext compensableMethodContext) throws Throwable {

        Transaction transaction = null;


        boolean asyncConfirm = compensableMethodContext.getAnnotation().asyncConfirm();

        boolean asyncCancel = compensableMethodContext.getAnnotation().asyncCancel();

        try {

            switch (TransactionStatus.valueOf(compensableMethodContext.getTransactionContext().getStatus())) {
                case TRYING:
                    //传播发起分支事务
                    transaction = transactionManager.propagationNewBegin(compensableMethodContext.getTransactionContext());
                    return compensableMethodContext.proceed();
                case CONFIRMING:
                    try {
                        //传播获取分支事务
                        transaction = transactionManager.propagationExistBegin(compensableMethodContext.getTransactionContext());
                        //提交事务
                        transactionManager.commit(asyncConfirm);
                    } catch (NoExistedTransactionException excepton) {
                        //the transaction has been commit,ignore it.
                    }
                    break;
                case CANCELLING:

                    try {
                        //传播获取分支事务
                        transaction = transactionManager.propagationExistBegin(compensableMethodContext.getTransactionContext());
                        //回滚事务
                        transactionManager.rollback(asyncCancel);
                    } catch (NoExistedTransactionException exception) {
                        //the transaction has been rollback,ignore it.
                    }
                    break;
            }

        } finally {
            //将事务从当前线程事务队列移除
            transactionManager.cleanAfterCompletion(transaction);
        }
        //返回空值
        Method method = compensableMethodContext.getMethod();

        return ReflectionUtils.getNullValue(method.getReturnType());
    }

    private boolean isDelayCancelException(Throwable throwable, Set<Class<? extends Exception>> delayCancelExceptions) {

        if (delayCancelExceptions != null) {
            for (Class delayCancelException : delayCancelExceptions) {

                Throwable rootCause = ExceptionUtils.getRootCause(throwable);

                if (delayCancelException.isAssignableFrom(throwable.getClass())
                        || (rootCause != null && delayCancelException.isAssignableFrom(rootCause.getClass()))) {
                    return true;
                }
            }
        }

        return false;
    }

}

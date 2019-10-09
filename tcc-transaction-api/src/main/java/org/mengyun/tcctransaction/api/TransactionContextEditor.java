package org.mengyun.tcctransaction.api;

import java.lang.reflect.Method;

/**
 * Created by changming.xie on 1/18/17.
 * 事务上下文编辑器
 */
public interface TransactionContextEditor {
    /**
     * 从参数中获取事务上下文
     * @param target 对象
     * @param method 方法
     * @param args 参数
     * @return 事务上下文
     */
    public TransactionContext get(Object target, Method method, Object[] args);

    /**
     * 设置事务上下文到参数中
     * @param transactionContext
     * @param target
     * @param method
     * @param args
     */
    public void set(TransactionContext transactionContext, Object target, Method method, Object[] args);

}

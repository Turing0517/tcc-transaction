package org.mengyun.tcctransaction.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;

/**
 * Created by changmingxie on 10/25/15.
 *  TCC-Transaction 基于org.mengyun.tcctransaction.api.@Compensable + org.aspectj.lang.annotation.@Aspect注解AOP切面
 *  实现业务方法的TCC事务拦截，同Spring 的 org.springframework.transaction.annotation.@Transactional 的实现
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
public @interface Compensable {
    /**
     * 传播级别
     * @return
     */
    public Propagation propagation() default Propagation.REQUIRED;

    /**
     * 确认执行行业方法
     * @return
     */
    public String confirmMethod() default "";

    /**
     * 取消执行业务方法
     * @return
     */
    public String cancelMethod() default "";

    /**
     * 事务上下文编辑
     * 用于设置和获取事务上下文
     * @return
     */
    public Class<? extends TransactionContextEditor> transactionContextEditor() default DefaultTransactionContextEditor.class;

    public Class<? extends Exception>[] delayCancelExceptions() default {};

    public boolean asyncConfirm() default false;

    public boolean asyncCancel() default false;

    /**
     * 无事务上下文编辑器实现。
     */
    class NullableTransactionContextEditor implements TransactionContextEditor {

        @Override
        public TransactionContext get(Object target, Method method, Object[] args) {
            return null;
        }

        @Override
        public void set(TransactionContext transactionContext, Object target, Method method, Object[] args) {

        }
    }

    /**
     * 默认事务上下文编辑器实现。
     */
    class DefaultTransactionContextEditor implements TransactionContextEditor {

        @Override
        public TransactionContext get(Object target, Method method, Object[] args) {
            int position = getTransactionContextParamPosition(method.getParameterTypes());

            if (position >= 0) {
                return (TransactionContext) args[position];
            }

            return null;
        }

        @Override
        public void set(TransactionContext transactionContext, Object target, Method method, Object[] args) {

            int position = getTransactionContextParamPosition(method.getParameterTypes());
            if (position >= 0) {
                args[position] = transactionContext;//设置方法参数
            }
        }

        public static int getTransactionContextParamPosition(Class<?>[] parameterTypes) {

            int position = -1;

            for (int i = 0; i < parameterTypes.length; i++) {
                if (parameterTypes[i].equals(org.mengyun.tcctransaction.api.TransactionContext.class)) {
                    position = i;
                    break;
                }
            }
            return position;
        }

        /**
         * 获得事务上下文在方法参数里的位置
         * @param args 参数类型集合
         * @return 位置
         */
        public static TransactionContext getTransactionContextFromArgs(Object[] args) {

            TransactionContext transactionContext = null;

            for (Object arg : args) {
                if (arg != null && org.mengyun.tcctransaction.api.TransactionContext.class.isAssignableFrom(arg.getClass())) {

                    transactionContext = (org.mengyun.tcctransaction.api.TransactionContext) arg;
                }
            }

            return transactionContext;
        }
    }
}
package org.mengyun.tcctransaction.recover;

import com.alibaba.fastjson.JSON;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.log4j.Logger;
import org.mengyun.tcctransaction.OptimisticLockException;
import org.mengyun.tcctransaction.Transaction;
import org.mengyun.tcctransaction.TransactionRepository;
import org.mengyun.tcctransaction.api.TransactionStatus;
import org.mengyun.tcctransaction.common.TransactionType;
import org.mengyun.tcctransaction.support.TransactionConfigurator;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * Created by changmingxie on 11/10/15.
 * 事务恢复逻辑
 * 事务信息被持久化到外部的存储器中。事务存储是事务恢复的基础。通过读取外部存储器中的异常事务，定时任务会按照一定频率对事务进行重试，
 * 直到事务完成或超过最大重试次数。
 */
public class TransactionRecovery {

    static final Logger logger = Logger.getLogger(TransactionRecovery.class.getSimpleName());

    private TransactionConfigurator transactionConfigurator;

    /**
     * 启动恢复事务逻辑
     */
    public void startRecover() {
        // 加载异常事务集合
        List<Transaction> transactions = loadErrorTransactions();
        // 恢复异常事务集合
        recoverErrorTransactions(transactions);
    }

    /**
     * 加载异常事务集合
     * 异常事务的定义：当前时间超过 - 事务变更时间( 最后执行时间 ) >= 事务恢复间隔( RecoverConfig#getRecoverDuration() )。
     * 这里有一点要注意，已完成的事务会从事务存储器删除。
     * @return
     */
    private List<Transaction> loadErrorTransactions() {


        long currentTimeInMillis = Calendar.getInstance().getTimeInMillis();

        TransactionRepository transactionRepository = transactionConfigurator.getTransactionRepository();
        RecoverConfig recoverConfig = transactionConfigurator.getRecoverConfig();

        return transactionRepository.findAllUnmodifiedSince(new Date(currentTimeInMillis - recoverConfig.getRecoverDuration() * 1000));
    }

    /**
     * 恢复异常事务集合
     * 1.当单个事务超过最大重试次数时，不再重试，只打印异常，此时需要人工介入解决。可以接入 ELK 收集日志监控报警。
     * 2.当分支事务超过最大可重试时间时，不再重试。可能有同学和我一开始理解的是相同的，实际分支事务对应的应用服务器也可以重试分支事务，
     * 不是必须根事务发起重试，从而一起重试分支事务。这点要注意下。
     * 3.当事务处于 TransactionStatus.CONFIRMING 状态时，提交事务，逻辑和 TransactionManager#commit() 类似。
     * 4.当事务处于 TransactionStatus.CONFIRMING 状态，或者事务类型为根事务，回滚事务，逻辑和 TransactionManager#rollback() 类似。
     * 这里加判断的事务类型为根事务，用于处理延迟回滚异常的事务的回滚。
     * @param transactions
     */
    private void recoverErrorTransactions(List<Transaction> transactions) {


        for (Transaction transaction : transactions) {
            //超过最大重试次数
            if (transaction.getRetriedCount() > transactionConfigurator.getRecoverConfig().getMaxRetryCount()) {

                logger.error(String.format("recover failed with max retry count,will not try again. txid:%s, status:%s,retried count:%d,transaction content:%s", transaction.getXid(), transaction.getStatus().getId(), transaction.getRetriedCount(), JSON.toJSONString(transaction)));
                continue;
            }
            //分支事务超过最大可重试时间
            if (transaction.getTransactionType().equals(TransactionType.BRANCH)
                    && (transaction.getCreateTime().getTime() +
                    transactionConfigurator.getRecoverConfig().getMaxRetryCount() *
                            transactionConfigurator.getRecoverConfig().getRecoverDuration() * 1000
                    > System.currentTimeMillis())) {
                continue;
            }
            //Confirm/Cancel
            try {
                //增加重试次数
                transaction.addRetriedCount();
                //Confirm
                if (transaction.getStatus().equals(TransactionStatus.CONFIRMING)) {

                    transaction.changeStatus(TransactionStatus.CONFIRMING);
                    transactionConfigurator.getTransactionRepository().update(transaction);
                    transaction.commit();
                    transactionConfigurator.getTransactionRepository().delete(transaction);
                //Cancel
                } else if (transaction.getStatus().equals(TransactionStatus.CANCELLING)
                        || transaction.getTransactionType().equals(TransactionType.ROOT)) {//处理延迟取消

                    transaction.changeStatus(TransactionStatus.CANCELLING);
                    transactionConfigurator.getTransactionRepository().update(transaction);
                    transaction.rollback();
                    transactionConfigurator.getTransactionRepository().delete(transaction);
                }

            } catch (Throwable throwable) {

                if (throwable instanceof OptimisticLockException
                        || ExceptionUtils.getRootCause(throwable) instanceof OptimisticLockException) {
                    logger.warn(String.format("optimisticLockException happened while recover. txid:%s, status:%s,retried count:%d,transaction content:%s", transaction.getXid(), transaction.getStatus().getId(), transaction.getRetriedCount(), JSON.toJSONString(transaction)), throwable);
                } else {
                    logger.error(String.format("recover failed, txid:%s, status:%s,retried count:%d,transaction content:%s", transaction.getXid(), transaction.getStatus().getId(), transaction.getRetriedCount(), JSON.toJSONString(transaction)), throwable);
                }
            }
        }
    }

    public void setTransactionConfigurator(TransactionConfigurator transactionConfigurator) {
        this.transactionConfigurator = transactionConfigurator;
    }
}

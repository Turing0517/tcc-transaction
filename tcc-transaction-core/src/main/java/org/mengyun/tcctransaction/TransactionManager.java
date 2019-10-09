package org.mengyun.tcctransaction;

import org.apache.log4j.Logger;
import org.mengyun.tcctransaction.api.TransactionContext;
import org.mengyun.tcctransaction.api.TransactionStatus;
import org.mengyun.tcctransaction.api.TransactionXid;
import org.mengyun.tcctransaction.common.TransactionType;

import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;

/**
 * Created by changmingxie on 10/26/15.
 * 事务管理器
 * 提供事务的获取、发起、提交、回滚、参与者的新增等等方法
 */
public class TransactionManager {

    static final Logger logger = Logger.getLogger(TransactionManager.class.getSimpleName());

    private TransactionRepository transactionRepository;
    /**
     * 当前线程事务队列
     * TCC-saction支持多个的事务独立存在，后创建的事务先提交，类似 Spring的org.springframework.transaction.annotation.Propagation.REQUERES_NEW
     */
    private static final ThreadLocal<Deque<Transaction>> CURRENT = new ThreadLocal<Deque<Transaction>>();

    private ExecutorService executorService;

    public void setTransactionRepository(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public void setExecutorService(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public TransactionManager() {


    }

    /**
     * 发起根事务
     * 该方法在调用方法类型为MethodType.ROOT并且事务处于Try阶段被调用。
     * @param uniqueIdentify
     * @return
     */
    public Transaction begin(Object uniqueIdentify) {
        //创建根事务
        Transaction transaction = new Transaction(uniqueIdentify,TransactionType.ROOT);
        //存储事务
        transactionRepository.create(transaction);
        //注册事务，
        registerTransaction(transaction);
        return transaction;
    }

    public Transaction begin() {
        Transaction transaction = new Transaction(TransactionType.ROOT);
        transactionRepository.create(transaction);
        registerTransaction(transaction);
        return transaction;
    }

    /**
     * 传播发起分支事务
     * 该方法在调用方法类型为MethodType.PROVIDER并且事务处于try阶段被调用。
     * @param transactionContext 分支事务
     * @return
     */
    public Transaction propagationNewBegin(TransactionContext transactionContext) {
        //创建分支事务
        Transaction transaction = new Transaction(transactionContext);
        //存储分支事务
        transactionRepository.create(transaction);
        //注册分支事务
        registerTransaction(transaction);
        return transaction;
    }

    /**
     * 传播获取分支事务
     * 该方法在调用方法类型为MethodType.PROVIDER 并且事务处于Confirm/Cancel阶段被调用。
     * @param transactionContext
     * @return
     * @throws NoExistedTransactionException
     */
    public Transaction propagationExistBegin(TransactionContext transactionContext) throws NoExistedTransactionException {
        //查询事务
        Transaction transaction = transactionRepository.findByXid(transactionContext.getXid());
        if (transaction != null) {
            //设置事务状态 状态为：CONFIRMING 或 CANCELLING
            transaction.changeStatus(TransactionStatus.valueOf(transactionContext.getStatus()));
            //注册事务
            registerTransaction(transaction);
            return transaction;
        } else {
            throw new NoExistedTransactionException();
        }
    }

    /**
     * 提交事务
     * 该方法在事务处于 Confirm/Cancel阶段被调用
     * @param asyncCommit
     */
    public void commit(boolean asyncCommit) {
        //获取事务
        final Transaction transaction = getCurrentTransaction();
        //设置事务状态为 CONFIRMING
        transaction.changeStatus(TransactionStatus.CONFIRMING);
        //更新 事务
        transactionRepository.update(transaction);
        //提交事务
        if (asyncCommit) {
            try {
                Long statTime = System.currentTimeMillis();

                executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        commitTransaction(transaction);
                    }
                });
                logger.debug("async submit cost time:" + (System.currentTimeMillis() - statTime));
            } catch (Throwable commitException) {
                logger.warn("compensable transaction async submit confirm failed, recovery job will try to confirm later.", commitException);
                throw new ConfirmingException(commitException);
            }
        } else {
            commitTransaction(transaction);
        }
    }

    /**
     * 回滚事务
     * 该阶段在事务处于Confirm/Cancel阶段被调用
     * @param asyncRollback
     */
    public void rollback(boolean asyncRollback) {
        //获取事务
        final Transaction transaction = getCurrentTransaction();
        //设置事务状态为CANCELLING
        transaction.changeStatus(TransactionStatus.CANCELLING);
        //更新事务
        transactionRepository.update(transaction);
        //判断是否异步回滚
        if (asyncRollback) {

            try {
                executorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        rollbackTransaction(transaction);
                    }
                });
            } catch (Throwable rollbackException) {
                logger.warn("compensable transaction async rollback failed, recovery job will try to rollback later.", rollbackException);
                throw new CancellingException(rollbackException);
            }
        } else {

            rollbackTransaction(transaction);
        }
    }


    private void commitTransaction(Transaction transaction) {
        try {
            //提交事务
            transaction.commit();
            //删除事务
            transactionRepository.delete(transaction);
        } catch (Throwable commitException) {
            logger.warn("compensable transaction confirm failed, recovery job will try to confirm later.", commitException);
            throw new ConfirmingException(commitException);
        }
    }

    private void rollbackTransaction(Transaction transaction) {
        try {
            //回滚事务
            transaction.rollback();
            //删除事务
            transactionRepository.delete(transaction);
        } catch (Throwable rollbackException) {
            logger.warn("compensable transaction rollback failed, recovery job will try to rollback later.", rollbackException);
            throw new CancellingException(rollbackException);
        }
    }

    /**
     * 获取事务
     * @return
     */
    public Transaction getCurrentTransaction() {
        if (isTransactionActive()) {
            return CURRENT.get().peek();//获取头部元素，该元素即是上面registerTransaction注册到队列头部
        }
        return null;
    }

    public boolean isTransactionActive() {
        Deque<Transaction> transactions = CURRENT.get();
        return transactions != null && !transactions.isEmpty();
    }

    /**
     * 注册事务到当前线程事务队列。
     * @param transaction 事务
     */
    private void registerTransaction(Transaction transaction) {

        if (CURRENT.get() == null) {
            CURRENT.set(new LinkedList<Transaction>());
        }

        CURRENT.get().push(transaction);//添加到头部
    }

    public void cleanAfterCompletion(Transaction transaction) {
        if (isTransactionActive() && transaction != null) {
            Transaction currentTransaction = getCurrentTransaction();
            if (currentTransaction == transaction) {
                CURRENT.get().pop();
                if (CURRENT.get().size() == 0) {
                    CURRENT.remove();
                }
            } else {
                throw new SystemException("Illegal transaction when clean after completion");
            }
        }
    }

    /**
     * 添加参与者到事务
     * @param participant 参与者
     */
    public void enlistParticipant(Participant participant) {
        //获取事务
        Transaction transaction = this.getCurrentTransaction();
        //添加参与者
        transaction.enlistParticipant(participant);
        //更新事务
        transactionRepository.update(transaction);
    }
}

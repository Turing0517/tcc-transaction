package org.mengyun.tcctransaction.repository;


import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.mengyun.tcctransaction.ConcurrentTransactionException;
import org.mengyun.tcctransaction.OptimisticLockException;
import org.mengyun.tcctransaction.Transaction;
import org.mengyun.tcctransaction.TransactionRepository;
import org.mengyun.tcctransaction.api.TransactionXid;

import javax.transaction.xa.Xid;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by changmingxie on 10/30/15.
 * 可缓存的事务存储器抽象类，实现增删改查事务时，同时缓存事务信息。
 */
public abstract class CachableTransactionRepository implements TransactionRepository {
    /**
     * 缓存过期时间
     */
    private int expireDuration = 120;
    /**
     * 缓存
     */
    private Cache<Xid, Transaction> transactionXidCompensableTransactionCache;

    /**
     * 调用 #doCreate(...) 方法，新增事务。新增成功后，调用 #putToCache(...) 方法，添加事务到缓存。
     * #doCreate(...) 为抽象方法，子类实现该方法，提供新增事务功能。
     * @param transaction 事务
     * @return
     */
    @Override
    public int create(Transaction transaction) {
        int result = doCreate(transaction);
        if (result > 0) {
            putToCache(transaction);
        } else {
            throw new ConcurrentTransactionException("transaction xid duplicated. xid:" + transaction.getXid().toString());
        }

        return result;
    }

    /**
     * 调用 #doUpdate(...) 方法，更新事务。
     * 若更新成功后，调用 #putToCache(...) 方法，添加事务到缓存。
     * 若更新失败后，抛出 OptimisticLockException 异常。有两种情况会导致更新失败：
     * (1) 该事务已经被提交，被删除；
     * (2) 乐观锁更新时，缓存的事务的版本号( Transaction.version )和存储器里的事务的版本号不同，更新失败。
     * 更新失败，意味着缓存已经不不一致，调用 #removeFromCache(...) 方法，移除事务从缓存中。
     * #doUpdate(...) 为抽象方法，子类实现该方法，提供更新事务功能。
     * @param transaction
     * @return
     */
    @Override
    public int update(Transaction transaction) {
        int result = 0;

        try {
            result = doUpdate(transaction);
            if (result > 0) {
                putToCache(transaction);
            } else {
                throw new OptimisticLockException();
            }
        } finally {
            if (result <= 0) { //更新失败，移除缓存。下次访问，从存储器读取
                removeFromCache(transaction);
            }
        }

        return result;
    }

    /**
     * 删除事务
     * 调用 #doDelete(...) 方法，删除事务。
     * 调用 #removeFromCache(...) 方法，移除事务从缓存中。
     * #doDelete(...) 为抽象方法，子类实现该方法，提供删除事务功能。
     * @param transaction
     * @return
     */
    @Override
    public int delete(Transaction transaction) {
        int result = 0;

        try {
            result = doDelete(transaction);

        } finally {
            removeFromCache(transaction);
        }
        return result;
    }

    /**
     * 调用 #findFromCache() 方法，优先从缓存中获取事务。
     * 调用 #doFindOne() 方法，缓存中事务不存在，从存储器中获取。获取到后，调用 #putToCache() 方法，添加事务到缓存中。
     * #doFindOne(...) 为抽象方法，子类实现该方法，提供查询事务功能。
     * @param transactionXid
     * @return
     */
    @Override
    public Transaction findByXid(TransactionXid transactionXid) {
        Transaction transaction = findFromCache(transactionXid);

        if (transaction == null) {
            transaction = doFindOne(transactionXid);

            if (transaction != null) {
                putToCache(transaction);
            }
        }

        return transaction;
    }

    /**
     * 调用 #findAllUnmodifiedSince(...) 方法，从存储器获取超过指定时间的事务集合。调用 #putToCache(...) 方法，
     * 循环事务集合添加到缓存。
     * #doFindAllUnmodifiedSince(...) 为抽象方法，子类实现该方法，提供获取超过指定时间的事务集合功能。
     * @param date
     * @return
     */
    @Override
    public List<Transaction> findAllUnmodifiedSince(Date date) {

        List<Transaction> transactions = doFindAllUnmodifiedSince(date);
        //添加到缓存
        for (Transaction transaction : transactions) {
            putToCache(transaction);
        }

        return transactions;
    }

    public CachableTransactionRepository() {
        transactionXidCompensableTransactionCache = CacheBuilder.newBuilder().expireAfterAccess(expireDuration, TimeUnit.SECONDS).maximumSize(1000).build();
    }

    /**
     * 添加到缓存
     * @param transaction 事务
     */
    protected void putToCache(Transaction transaction) {
        transactionXidCompensableTransactionCache.put(transaction.getXid(), transaction);
    }

    /**
     * 移除事务从缓存
     * @param transaction
     */
    protected void removeFromCache(Transaction transaction) {
        transactionXidCompensableTransactionCache.invalidate(transaction.getXid());
    }

    /**
     * 获得事务从缓存中
     *
     * @param transactionXid 事务编号
     * @return 事务
     */
    protected Transaction findFromCache(TransactionXid transactionXid) {
        return transactionXidCompensableTransactionCache.getIfPresent(transactionXid);
    }

    public void setExpireDuration(int durationInSeconds) {
        this.expireDuration = durationInSeconds;
    }

    /**
     * 新增事务
     * @param transaction 事务
     * @return
     */
    protected abstract int doCreate(Transaction transaction);

    /**
     * 更新事务
     * @param transaction
     * @return
     */
    protected abstract int doUpdate(Transaction transaction);

    /**
     * 删除事务
     * @param transaction
     * @return
     */
    protected abstract int doDelete(Transaction transaction);
    /**
     * 查询事务
     *
     * @param xid 事务编号
     * @return 事务
     */
    protected abstract Transaction doFindOne(Xid xid);

    /**
     * 获取超过指定时间的事务集合
     *
     * @param date 指定时间
     * @return 事务集合
     */
    protected abstract List<Transaction> doFindAllUnmodifiedSince(Date date);
}

package org.mengyun.tcctransaction;

import org.mengyun.tcctransaction.api.TransactionXid;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * Created by changmingxie on 11/12/15.
 * 事务存储器接口
 */
public interface TransactionRepository {
    /**
     * 新增事务
     * @param transaction 事务
     * @return 数量
     */
    int create(Transaction transaction);

    /**
     * 更新事务
     * @param transaction
     * @return
     */
    int update(Transaction transaction);

    /**
     * 删除事务
     * @param transaction
     * @return
     */
    int delete(Transaction transaction);

    /**
     * 获取事务
     * @param xid
     * @return
     */
    Transaction findByXid(TransactionXid xid);

    /**
     * 获取超时指定时间的事务集合
     * @param date
     * @return
     */
    List<Transaction> findAllUnmodifiedSince(Date date);
}

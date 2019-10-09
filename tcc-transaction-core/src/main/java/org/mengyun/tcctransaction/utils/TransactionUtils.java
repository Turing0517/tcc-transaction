package org.mengyun.tcctransaction.utils;

import org.mengyun.tcctransaction.api.Propagation;
import org.mengyun.tcctransaction.api.TransactionContext;
import org.mengyun.tcctransaction.interceptor.CompensableMethodContext;

/**
 * Created by changming.xie on 2/23/17.
 */
public class TransactionUtils {

    /**
     * 判断事务上下文是否合法
     * @param isTransactionActive 是否
     * @param compensableMethodContext
     * @return
     */
    public static boolean isLegalTransactionContext(boolean isTransactionActive, CompensableMethodContext compensableMethodContext) {


        if (compensableMethodContext.getPropagation().equals(Propagation.MANDATORY) && !isTransactionActive && compensableMethodContext.getTransactionContext() == null) {
            return false;
        }

        return true;
    }
}

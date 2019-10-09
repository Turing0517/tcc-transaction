package org.mengyun.tcctransaction.recover;

import java.util.Set;

/**
 * Created by changming.xie on 6/1/16.
 * 事务恢复配置接口
 */
public interface RecoverConfig {

    //最大重试次数，单个事务恢复最大重试次数。超过最大重试次数后，目前仅打出错误日志，下文会看到实现。
    public int getMaxRetryCount();
    //恢复间隔时间，单个事务恢复重试的间隔时间，单位：秒。
    public int getRecoverDuration();
    //表达式，定时任务 cron 表达式
    public String getCronExpression();
    //延迟取消异常集合
    public Set<Class<? extends Exception>> getDelayCancelExceptions();
    //设置延迟取消异常集合
    public void setDelayCancelExceptions(Set<Class<? extends Exception>> delayRecoverExceptions);

    public int getAsyncTerminateThreadPoolSize();
}

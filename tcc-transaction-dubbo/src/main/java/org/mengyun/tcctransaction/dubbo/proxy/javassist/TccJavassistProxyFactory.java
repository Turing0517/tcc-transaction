package org.mengyun.tcctransaction.dubbo.proxy.javassist;

import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.proxy.InvokerInvocationHandler;
import com.alibaba.dubbo.rpc.proxy.javassist.JavassistProxyFactory;

/**
 * Created by changming.xie on 1/14/17.
 * 项目启动时，调用 TccJavassistProxyFactory#getProxy(...) 方法，生成 Dubbo Service 调用 Proxy。
 */
public class TccJavassistProxyFactory extends JavassistProxyFactory {

    @SuppressWarnings("unchecked")
    public <T> T getProxy(Invoker<T> invoker, Class<?>[] interfaces) {
        return (T) TccProxy.getProxy(interfaces).newInstance(new InvokerInvocationHandler(invoker));
    }
}

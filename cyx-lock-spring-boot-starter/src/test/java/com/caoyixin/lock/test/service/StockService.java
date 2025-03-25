package com.caoyixin.lock.test.service;

import com.caoyixin.lock.annotation.CyxLock;
import com.caoyixin.lock.core.LockTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 用于测试的库存服务类
 */
@Service
public class StockService {
    private static final Logger logger = LoggerFactory.getLogger(StockService.class);

    // 模拟库存数据
    private final Map<String, AtomicInteger> stockMap = new ConcurrentHashMap<>();

    // 记录调用情况
    private final AtomicInteger decrementCallCount = new AtomicInteger(0);
    private final AtomicInteger incrementCallCount = new AtomicInteger(0);

    @Autowired
    private LockTemplate lockTemplate;

    /**
     * 重置服务状态
     */
    public void reset() {
        stockMap.clear();
        decrementCallCount.set(0);
        incrementCallCount.set(0);
    }

    /**
     * 初始化产品库存
     */
    public void initStock(String productId, int initialStock) {
        stockMap.put(productId, new AtomicInteger(initialStock));
    }

    /**
     * 获取产品当前库存
     */
    public int getStock(String productId) {
        AtomicInteger stock = stockMap.get(productId);
        return stock != null ? stock.get() : 0;
    }

    /**
     * 获取减少库存的调用次数
     */
    public int getDecrementCallCount() {
        return decrementCallCount.get();
    }

    /**
     * 获取增加库存的调用次数
     */
    public int getIncrementCallCount() {
        return incrementCallCount.get();
    }

    /**
     * 使用编程方式减少库存
     */
    public boolean decrementStockWithProgrammaticLock(String productId, int quantity) {
        decrementCallCount.incrementAndGet();

        // 使用编程式锁保护库存操作
        return lockTemplate.executeWithLock("stock:" + productId, 5000, 1000, () -> {
            return doDecrementStock(productId, quantity);
        });
    }

    /**
     * 使用注解方式减少库存
     */
    @CyxLock(name = "stock:#{#productId}", expire = 5000, acquireTimeout = 1000)
    public boolean decrementStock(String productId, int quantity) {
        decrementCallCount.incrementAndGet();
        return doDecrementStock(productId, quantity);
    }

    /**
     * 使用注解方式增加库存
     */
    @CyxLock(name = "stock:#{#productId}", expire = 5000, acquireTimeout = 1000)
    public boolean incrementStock(String productId, int quantity) {
        incrementCallCount.incrementAndGet();

        AtomicInteger stock = stockMap.get(productId);
        if (stock == null) {
            stockMap.put(productId, new AtomicInteger(quantity));
            logger.info("增加库存: 产品[{}] 增加 {} 个", productId, quantity);
            return true;
        }

        stock.addAndGet(quantity);
        logger.info("增加库存: 产品[{}] 增加 {} 个, 当前库存 {}", productId, quantity, stock.get());
        return true;
    }

    /**
     * 使用更复杂的SpEL表达式锁定多参数操作
     */
    @CyxLock(name = "inventory:transfer:#{#fromId}:#{#toId}", expire = 5000, acquireTimeout = 1000)
    public boolean transferStock(String fromId, String toId, int quantity) {
        decrementCallCount.incrementAndGet();
        incrementCallCount.incrementAndGet();

        // 检查源产品库存
        if (!doDecrementStock(fromId, quantity)) {
            return false;
        }

        // 增加目标产品库存
        AtomicInteger toStock = stockMap.get(toId);
        if (toStock == null) {
            stockMap.put(toId, new AtomicInteger(quantity));
        } else {
            toStock.addAndGet(quantity);
        }

        logger.info("库存转移: 从产品[{}]转移 {} 个到产品[{}]", fromId, quantity, toId);
        return true;
    }

    /**
     * 实际执行库存减少的方法
     */
    private boolean doDecrementStock(String productId, int quantity) {
        AtomicInteger stock = stockMap.get(productId);

        if (stock == null || stock.get() < quantity) {
            logger.warn("库存不足: 产品[{}] 当前库存 {}, 需要 {}",
                    productId, stock != null ? stock.get() : 0, quantity);
            return false;
        }

        int newStock = stock.addAndGet(-quantity);
        logger.info("减少库存: 产品[{}] 减少 {} 个, 剩余库存 {}", productId, quantity, newStock);
        return true;
    }
}
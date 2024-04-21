package ir.ramtung.tinyme.domain.entity;

import java.util.LinkedList;
import java.util.Queue;

public class InactiveOrderBook extends OrderBook {

    public InactiveOrderBook() {
        super();
    }


    public StopLimitOrder findFirstInactiveOrder(Side side, int lastTransactionPrice) {
        Queue<StopLimitOrder> queue = getQueue(side);
        StopLimitOrder currentOrder = null;
        for (StopLimitOrder order : queue) {
            if (!order.isActive(lastTransactionPrice)) {
                if (currentOrder == null || order.getPrice() < currentOrder.getPrice()) {
                    currentOrder = order;
                }
            }
        }
        return currentOrder;
    }


    @Override
    public StopLimitOrder findByOrderId(Side side, long orderId) {
        var queue = getQueue(side);
        return queue.stream()
                .filter(order -> order.getOrderId() == orderId)
                .findFirst()
                .map(order -> (StopLimitOrder) order)
                .orElse(null);
    }

}

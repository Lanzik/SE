package ir.ramtung.tinyme.domain.entity;

import java.util.LinkedList;

public class InactiveOrderBook extends OrderBook {

    public InactiveOrderBook() {
        super();
    }

    public StopLimitOrder peekFirstInactiveOrder(Side side, int lastTransactionPrice) {
        var queue = getQueue(side);
        StopLimitOrder stopLimitOrder = (StopLimitOrder) queue.getFirst();
        if (stopLimitOrder.mustBeActive(lastTransactionPrice)){
            return stopLimitOrder;
        }
        else{
            return null;
        }
    }

    public void ActivateQualifiedOrders(Side side, int lastTransactionPrice) {
        var queue = getQueue(side);
        while (!queue.isEmpty()) {}
    }

    @Override
    public StopLimitOrder findByOrderId(Side side, long orderId) {
        var queue = getQueue(side);
        for (Order order : queue) {
            if (order.getOrderId() == orderId)
                return (StopLimitOrder) order;
        }
        return null;
    }

}

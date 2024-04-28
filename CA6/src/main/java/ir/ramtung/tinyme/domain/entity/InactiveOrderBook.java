package ir.ramtung.tinyme.domain.entity;

import java.util.LinkedList;
import java.util.*;


public class InactiveOrderBook extends OrderBook {

    public InactiveOrderBook() {
        super();
    }

    public StopLimitOrder peekFirstInactiveOrder(Side side, int lastTransactionPrice) {
        var queue = getQueue(side);
        StopLimitOrder stopLimitOrder = (StopLimitOrder) queue.getFirst();
        if (stopLimitOrder.isInactive(lastTransactionPrice)){
            return stopLimitOrder;
        }
        else{
            return null;
        }
    }

    public void activateEligibleOrders(Side side, int lastTransactionPrice) {
        var queue = getQueue(side);
        while (!queue.isEmpty()) {
            // Activation logic goes here
        }
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


package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.domain.entity.StopLimitOrder;
import ir.ramtung.tinyme.messaging.Message;

import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.MatchResult;

@Getter
@Builder
public class Security {
    private String isin;
    @Builder.Default
    private int tickSize = 1;
    @Builder.Default
    private int lotSize = 1;
    @Builder.Default
    private OrderBook orderBook = new OrderBook();
    @Builder.Default
    private InactiveOrderBook inactiveOrderBook = new InactiveOrderBook();
    @Builder.Default
    private int lastTransactionPrice = 0;
    @Builder.Default
    private final LinkedList<Order> activeOrders = new LinkedList<>();


    public MatchResult newOrder(EnterOrderRq enterOrderRq, Broker broker, Shareholder shareholder, Matcher matcher) throws InvalidRequestException {
        if (enterOrderRq.getSide() == Side.SELL &&
                !shareholder.hasEnoughPositionsOn(this,
                orderBook.totalSellQuantityByShareholder(shareholder) + enterOrderRq.getQuantity()))
            return MatchResult.notEnoughPositions();
        Order order;
        if (enterOrderRq.getPeakSize() == 0 && enterOrderRq.getStopPrice() == 0)
            order = new Order(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder, enterOrderRq.getEntryTime(), enterOrderRq.getMinimumExecutionQuantity());
        else if (enterOrderRq.getPeakSize() != 0 && enterOrderRq.getStopPrice() == 0)
            order = new IcebergOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getEntryTime(), enterOrderRq.getPeakSize(), enterOrderRq.getMinimumExecutionQuantity());
        else if (enterOrderRq.getStopPrice() != 0 && enterOrderRq.getPeakSize() == 0) {
            StopLimitOrder stopLimitOrder = new StopLimitOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getEntryTime(), enterOrderRq.getMinimumExecutionQuantity(),
                    enterOrderRq.getStopPrice());
            if ( (stopLimitOrder.getSide() == Side.BUY && stopLimitOrder.getStopPrice() <= lastTransactionPrice) || (stopLimitOrder.getSide() == Side.SELL && stopLimitOrder.getStopPrice() >= lastTransactionPrice) ){
                order = stopLimitOrder;
            }
            else{
                if (stopLimitOrder.getSide() == Side.BUY) {
                    if (!stopLimitOrder.getBroker().hasEnoughCredit(stopLimitOrder.getValue())) {
                        return MatchResult.notEnoughCredit();
                    }
                }
                inactiveOrderBook.enqueue(stopLimitOrder);
                return MatchResult.queuedAsInactiveOrder();
            }
        }
        else
            throw new InvalidRequestException(Message.ORDER_CANNOT_BE_BOTH_A_STOP_LIMIT_AND_AN_ICEBERG);
        return matcher.processOrder(order);
    }


    public MatchResult handleOrderCreation(EnterOrderRequest enterOrderRequest, Brokerage intermediary, Shareholder investor, OrderProcessor orderProcessor) throws InvalidOrderException {
    if (enterOrderRequest.getSide() == Side.SELL &&
            !investor.hasSufficientPositionsWithin(this,
                    orderBook.totalSellQuantityByInvestor(investor) + enterOrderRequest.getQuantity())) {
        return MatchResult.insufficientPositions();
    }

    Order order;
    if (enterOrderRequest.getPeakSize() == 0 && enterOrderRequest.getStopPrice() == 0) {
        order = createSimpleOrder(enterOrderRequest.getOrderId(), this, enterOrderRequest.getSide(),
                enterOrderRequest.getQuantity(), enterOrderRequest.getPrice(), intermediary, investor, enterOrderRequest.getEntryTime(), enterOrderRequest.getMinimumExecutionQuantity());
    } else if (enterOrderRequest.getPeakSize() != 0 && enterOrderRequest.getStopPrice() == 0) {
        order = createIcebergOrder(enterOrderRequest.getOrderId(), this, enterOrderRequest.getSide(),
                enterOrderRequest.getQuantity(), enterOrderRequest.getPrice(), intermediary, investor,
                enterOrderRequest.getEntryTime(), enterOrderRequest.getPeakSize(), enterOrderRequest.getMinimumExecutionQuantity());
    } else if (enterOrderRequest.getStopPrice() != 0 && enterOrderRequest.getPeakSize() == 0) {
        StopLimitOrder stopLimitOrder = createStopLimitOrder(enterOrderRequest.getOrderId(), this, enterOrderRequest.getSide(),
                enterOrderRequest.getQuantity(), enterOrderRequest.getPrice(), intermediary, investor,
                enterOrderRequest.getEntryTime(), enterOrderRequest.getMinimumExecutionQuantity(),
                enterOrderRequest.getStopPrice());
        if ((stopLimitOrder.getSide() == Side.BUY && stopLimitOrder.getStopPrice() <= lastTransactionPrice) || (stopLimitOrder.getSide() == Side.SELL && stopLimitOrder.getStopPrice() >= lastTransactionPrice)) {
            order = stopLimitOrder;
        } else {
            if (stopLimitOrder.getSide() == Side.BUY) {
                if (!stopLimitOrder.getBroker().hasSufficientCredit(stopLimitOrder.getValue())) {
                    return MatchResult.insufficientCredit();
                }
            }
            inactiveOrderBook.enqueue(stopLimitOrder);
            return MatchResult.queuedAsInactive();
        }
    } else {
        throw new InvalidOrderException(Message.ORDER_CANNOT_BE_BOTH_STOP_LIMIT_AND_ICEBERG);
    }
    return orderProcessor.processOrder(order);
}

private Order createSimpleOrder(String orderId, OrderBook orderBook, Side side, int quantity, double price, Brokerage intermediary, Shareholder investor, long entryTime, int minimumExecutionQuantity) {
   return new Order(orderId, orderBook, side, quantity, price, intermediary, investor, entryTime, minimumExecutionQuantity);
}

private Order createIcebergOrder(String orderId, OrderBook orderBook, Side side, int quantity, double price, Brokerage intermediary, Shareholder investor, long entryTime, int peakSize, int minimumExecutionQuantity) {
    // Implementation for creating an iceberg order
}

private StopLimitOrder createStopLimitOrder(String orderId, OrderBook orderBook, Side side, int quantity, double price, Brokerage intermediary, Shareholder investor, long entryTime, int minimumExecutionQuantity, double stopPrice) {
    // Implementation for creating a stop-limit order
}


    public void deleteOrder(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
        Order order = orderBook.findByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
        if (order == null)
            order = inactiveOrderBook.findByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
        if (order == null)
            throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
        if (order.getSide() == Side.BUY)
            order.getBroker().increaseCreditBy(order.getValue());
        orderBook.removeByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
    }

    public MatchResult updateOrder(EnterOrderRq updateOrderRq, Matcher matcher) throws InvalidRequestException {
        Order order = orderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        if (order == null)
            throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
        if ((order instanceof IcebergOrder) && updateOrderRq.getPeakSize() == 0)
            throw new InvalidRequestException(Message.INVALID_PEAK_SIZE);
        if (!(order instanceof IcebergOrder) && updateOrderRq.getPeakSize() != 0)
            throw new InvalidRequestException(Message.CANNOT_SPECIFY_PEAK_SIZE_FOR_A_NON_ICEBERG_ORDER);
        if (order.getMinimumExecutionQuantity() != updateOrderRq.getMinimumExecutionQuantity())
            throw new InvalidRequestException(Message.CANNOT_CHANGE_MINIMUM_EXECUTION_QUANTITY);
        if (updateOrderRq.getPeakSize() != 0 && updateOrderRq.getStopPrice() != 0)
            throw new InvalidRequestException(Message.ORDER_CANNOT_BE_BOTH_A_STOP_LIMIT_AND_AN_ICEBERG);
        order.markAsUpdated();

        if (updateOrderRq.getSide() == Side.SELL &&
                !order.getShareholder().hasEnoughPositionsOn(this,
                orderBook.totalSellQuantityByShareholder(order.getShareholder()) - order.getQuantity() + updateOrderRq.getQuantity()))
            return MatchResult.notEnoughPositions();

        boolean losesPriority = order.isQuantityIncreased(updateOrderRq.getQuantity())
                || updateOrderRq.getPrice() != order.getPrice()
                || ((order instanceof IcebergOrder icebergOrder) && (icebergOrder.getPeakSize() < updateOrderRq.getPeakSize()));

        if (updateOrderRq.getSide() == Side.BUY) {
            order.getBroker().increaseCreditBy(order.getValue());
        }
        Order originalOrder = order.snapshot();
        order.updateFromRequest(updateOrderRq);
        if (!losesPriority) {
            if (updateOrderRq.getSide() == Side.BUY) {
                order.getBroker().decreaseCreditBy(order.getValue());
            }
            return MatchResult.executed(null, List.of());
        }
        else
            order.markAsNew();

        orderBook.removeByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        MatchResult matchResult = matcher.processOrder(order);
        if (matchResult.outcome() != MatchingOutcome.EXECUTED) {
            orderBook.enqueue(originalOrder);
            if (updateOrderRq.getSide() == Side.BUY) {
                originalOrder.getBroker().decreaseCreditBy(originalOrder.getValue());
            }
        }
        else
            if(!matchResult.getTrades().isEmpty())
                lastTransactionPrice = matchResult.getTrades().getLast().getPrice();
        if (updateOrderRq.getStopPrice() != 0) {
            Order stopLimit = inactiveOrderBook.findByOrderId(updateOrderRq.getSide(), updateOrderRq.getOrderId());
        if(stopLimit == null)
            throw new InvalidRequestException(Message.ORDER_NOT_FOUND_OR_ACTIVTED_BEFOR); 
            stopLimit.updateFromRequest(updateOrderRq);
        }

        return matchResult;
    }

    public void updateActiveOrders(MatchResult matchResult) {
        int previousTransactionPrice = lastTransactionPrice;
        lastTransactionPrice = matchResult.getTrades().getLast().getPrice();
        if (lastTransactionPrice == previousTransactionPrice) {
            return;
        }
        Side targetSide = (lastTransactionPrice - previousTransactionPrice) > 0 ? Side.BUY : Side.SELL;
        findExecutableOrders(targetSide);
    }

    private void findExecutableOrders(Side side) {
        while (inactiveOrderBook.hasOrderOfType(side)) {
            StopLimitOrder stopLimitOrder = inactiveOrderBook.peekFirstInactiveOrder(side, lastTransactionPrice);
            if (stopLimitOrder == null)
                return;
            activeOrders.add(stopLimitOrder);
            inactiveOrderBook.removeFirst(side);
        }
    }



    public LinkedList<MatchResult> handleExecutableOrders(Matcher matcher) {
        LinkedList<MatchResult> outcomes = new LinkedList<>();
        while (!activeOrders.isEmpty()) {
            Order order = activeOrders.pollFirst();
            MatchResult result = matcher.processOrder(order);
            if (!result.getTrades().isEmpty()) {
                updateActiveOrders(result);
            }
            outcomes.add(result);
        }
        return outcomes;
    }



}



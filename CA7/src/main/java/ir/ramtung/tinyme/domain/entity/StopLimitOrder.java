package ir.ramtung.tinyme.domain.entity;


import lombok.EqualsAndHashCode;

import lombok.Getter;

import lombok.ToString;

import ir.ramtung.tinyme.messaging.request.EnterOrderRq;

import java.time.LocalDateTime;


@Getter

@EqualsAndHashCode(callSuper = true)

@ToString(callSuper = true)

public class StopLimitOrder extends Order {


    protected int stopPrice;


    StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, OrderStatus status, int minimumExecutionQuantity, int stopPrice) {

        super(orderId, security, side, quantity, price, broker, shareholder, entryTime, status, minimumExecutionQuantity);

        this.stopPrice = stopPrice;

    }


    StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, int minimumExecutionQuantity, int stopPrice) {

        this(orderId, security, side, quantity, price, broker, shareholder, entryTime, OrderStatus.NEW, minimumExecutionQuantity, stopPrice);

    }
    
    @Override
    public boolean queuesBefore(Order order) {
        StopLimitOrder stopLimitOrder = (StopLimitOrder) order;
        return side == Side.BUY ? stopPrice < stopLimitOrder.stopPrice : stopPrice > stopLimitOrder.stopPrice;
    }
    
    public boolean isInactive(int lastTransactionPrice) {
        return side == Side.BUY ? stopPrice < lastTransactionPrice : stopPrice > lastTransactionPrice;
    }
    


    @Override

    public void updateFromRequest(EnterOrderRq updateOrderRq) {

        quantity = updateOrderRq.getQuantity();

        price = updateOrderRq.getPrice();

        stopPrice = updateOrderRq.getStopPrice();

    }


}

package ir.ramtung.tinyme.domain.entity;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import java.time.LocalDateTime;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class StopLimitOrder extends Order{

    protected int StopPrice;
    private int stopPrice;

    StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, OrderStatus status, int minimumExecutionQuantity, int stopPrice) {
        super(orderId, security, side, quantity, price, broker, shareholder, entryTime, status, minimumExecutionQuantity);
        this.StopPrice = stopPrice;
    }

    StopLimitOrder(long orderId, Security security, Side side, int quantity, int price, Broker broker, Shareholder shareholder, LocalDateTime entryTime, int minimumExecutionQuantity, int stopPrice){
        this(orderId, security, side, quantity, price, broker, shareholder, entryTime, OrderStatus.NEW, minimumExecutionQuantity, stopPrice);
        }

    @Override
    public boolean queuesBefore(Order order) {
        if (!(order instanceof StopLimitOrder)) {
            return false;
        }
        StopLimitOrder stopLimitOrder = (StopLimitOrder) order;
        return compareStopPrices(side, stopPrice, stopLimitOrder.stopPrice);
    }

    private boolean compareStopPrices(Side side, int stopPrice1, int stopPrice2) {
        switch (side) {
            case BUY:
                return stopPrice1 < stopPrice2;
            case SELL:
                return stopPrice1 > stopPrice2;
            default:
                throw new IllegalStateException("Unexpected Side value: " + side);
        }
    }

    public boolean isActive(int lastTransactionPrice){
        if (side == Side.BUY)
            return StopPrice < lastTransactionPrice;
        else
            return StopPrice > lastTransactionPrice;
    }   

    @Override
    public void updateFromRequest(EnterOrderRq updateOrderRq) {
        quantity = updateOrderRq.getQuantity();
        price = updateOrderRq.getPrice();
        StopPrice = updateOrderRq.getStopPrice();
    }

}


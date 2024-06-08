package ir.ramtung.tinyme.domain.entity;

import ir.ramtung.tinyme.messaging.exception.InvalidRequestException;
import ir.ramtung.tinyme.messaging.request.DeleteOrderRq;
import ir.ramtung.tinyme.messaging.request.EnterOrderRq;
import ir.ramtung.tinyme.domain.service.Matcher;
import ir.ramtung.tinyme.messaging.Message;
import lombok.Builder;
import lombok.Getter;
import ir.ramtung.tinyme.messaging.request.MatchingState;

import java.util.*;

import org.apache.commons.lang3.ObjectUtils.Null;

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
    private ArrayList<MatchResult> matchResults = new ArrayList<>();
    @Builder.Default
    private MatchingState matchingState = MatchingState.CONTINUOUS;
    @Builder.Default
    private int indicativeOpeningPrice = 0 ;
    @Builder.Default
    private int highestQuantity = 0;



    private boolean checkPosition(EnterOrderRq enterOrderRq , Shareholder shareholder){
        if (enterOrderRq.getSide() == Side.SELL &&
                !shareholder.hasEnoughPositionsOn(this,
                        orderBook.totalSellQuantityByShareholder(shareholder) + enterOrderRq.getQuantity())) {
            return false;
        }
        else{
            return true;
        }
    }

    public MatchResult newOrder(EnterOrderRq enterOrderRq, Broker broker, Shareholder shareholder, Matcher matcher) {
        if(!checkPosition(enterOrderRq, shareholder)){
            return MatchResult.notEnoughPositions();
        }
        
        Order order;

        if ((enterOrderRq.getPeakSize() == 0) && (enterOrderRq.getStopPrice() == 0)){
            order = new Order(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder, enterOrderRq.getEntryTime(),OrderStatus.NEW ,enterOrderRq.getMinimumExecutionQuantity());
        }
        else if (enterOrderRq.getStopPrice() != 0){
            order = new StopLimitOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getEntryTime(), enterOrderRq.getStopPrice() );
            order.setRequestId(enterOrderRq.getRequestId());
            if (!checkOrderPossibility(order)) {
                return (order.getSide() == Side.BUY) ? MatchResult.notEnoughCredit() : MatchResult.notEnoughPositions();
            }
            if((order instanceof StopLimitOrder) && !((StopLimitOrder)order).checkActivation(orderBook.getLastTradePrice())){
                return handleInactiveStopLimitOrder(order);
            }
        }
        else {
            order = new IcebergOrder(enterOrderRq.getOrderId(), this, enterOrderRq.getSide(),
                    enterOrderRq.getQuantity(), enterOrderRq.getPrice(), broker, shareholder,
                    enterOrderRq.getEntryTime(), enterOrderRq.getPeakSize(), OrderStatus.NEW,enterOrderRq.getMinimumExecutionQuantity());
        }

        if(matchingState == MatchingState.CONTINUOUS){
            MatchResult matchResult = matcher.execute(order);
            return matchResult;
        }
        else {
            MatchResult matchResult = matcher.auctionAddToQueue(order);
            return matchResult;
        }
        
        
    }

    public void deleteOrder(DeleteOrderRq deleteOrderRq) throws InvalidRequestException {
        Order order = orderBook.findByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
        if (order == null) {
            order = orderBook.findInActiveByOrderId(deleteOrderRq.getSide() , deleteOrderRq.getOrderId());
        }
        if (order == null)
            throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
        if (order.getSide() == Side.BUY )
            order.getBroker().increaseCreditBy(order.getValue());
        if (!orderBook.removeByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId())){
            if (matchingState == MatchingState.CONTINUOUS){
                orderBook.removeInActiveStopLimitByOrderId(deleteOrderRq.getSide(), deleteOrderRq.getOrderId());
            }
            else{
                if (order.getSide() == Side.BUY ){
                    order.getBroker().decreaseCreditBy(order.getValue());
                }
            }
            
        }
    }

    private Order getOrderForUpdate(EnterOrderRq upEnterOrderRq) throws InvalidRequestException{
        Order order;
        order = orderBook.findInActiveByOrderId(upEnterOrderRq.getSide(), upEnterOrderRq.getOrderId());
        if (order == null){
            order = orderBook.findByOrderId(upEnterOrderRq.getSide(), upEnterOrderRq.getOrderId());
        }
        if (order == null) {
            throw new InvalidRequestException(Message.ORDER_ID_NOT_FOUND);
        }
        return order;
    }
    public MatchResult updateOrder(EnterOrderRq updateOrderRq, Matcher matcher) throws InvalidRequestException {
        Order order = getOrderForUpdate(updateOrderRq);
        validateOrderUpdate(order, updateOrderRq);
        updateOrderAttributes(order, updateOrderRq);
        MatchResult matchResult = executeOrderUpdates(order, matcher);
        return matchResult;
    }

    private void validateOrderUpdate(Order order, EnterOrderRq updateOrderRq) throws InvalidRequestException {
        if (order instanceof IcebergOrder && updateOrderRq.getPeakSize() == 0) {
            throw new InvalidRequestException(Message.INVALID_PEAK_SIZE);
        }
    }

    private void updateOrderAttributes(Order order, EnterOrderRq updateOrderRq) {

        order.setPrice(updateOrderRq.getPrice());
        order.setQuantity(updateOrderRq.getQuantity());
        order.setMinimumExecutionQuantity(updateOrderRq.getMinimumExecutionQuantity());

    }

    private MatchResult executeOrderUpdates(Order order, Matcher matcher) {
        if (matchingState == MatchingState.CONTINUOUS) {
            return matcher.execute(order);
        } else {
            return matcher.auctionAddToQueue(order);
        }
    }



    public boolean checkOrderPossibility(Order order){
        if(order.getSide() == Side.BUY){
            return order.getBroker().hasEnoughCredit(order.getValue());
        }
        else{
            return order.getShareholder().hasEnoughPositionsOn(order.getSecurity(), order.getQuantity());
        }
    }
    
    private MatchResult handleInactiveStopLimitOrder(Order order){
        if(order.getSide()==Side.BUY){
            order.getBroker().decreaseCreditBy(order.getValue());
        }
        orderBook.enqueueInactiveStopLimitOrder(order);
        return MatchResult.inactiveOrderEnqueued();
    }
    public MatchResult ChangeMatchStateRq(MatchingState state, Matcher matcher) {

        // Handle state change based on current state and requested state
        switch (matchingState) {
            case CONTINUOUS:
                if (state == MatchingState.AUCTION) {
                    updateIndicativeOpeningPrice();
                    MatchResult matchResult = openingProcess(matcher);
                    matchingState = MatchingState.AUCTION;
                    return matchResult;
                }
                // No change needed for CONTINUOUS to CONTINUOUS
                break;
            case AUCTION:
                if (state == MatchingState.CONTINUOUS) {
                    // No action needed (auction already in progress) - optional logging/message
                } else if (state == MatchingState.AUCTION) {
                    updateIndicativeOpeningPrice();
                    MatchResult matchResult = openingProcess(matcher);
                    matchingState = MatchingState.AUCTION;
                    return matchResult;
                }
                break;
            default:
                // Handle unexpected state or log an error message
                throw new IllegalArgumentException("Invalid MatchingState: " + matchingState);
        }

        // If no state change occurred, return null (or consider returning a specific result for clarity)
        return null;
    }

    private MatchResult openingProcess(Matcher matcher) {
        updateIndicativeOpeningPrice();

        List<Order> orders = new ArrayList<>();
        orders.addAll(orderBook.getSellQueue());
        orders.addAll(orderBook.getBuyQueue());
        orders.sort(Comparator.comparing(Order::getPrice).reversed());

        LinkedList<Trade> trades = new LinkedList<>();
        for (Order order : orders) {
            MatchResult matchResult = matcher.auctionExecute(order, indicativeOpeningPrice);
            if (matchResult.trades().isEmpty()) {
                continue;
            }
            trades.addAll(matchResult.trades());
        }
        orderBook.getSellQueue().removeIf(order -> order.getQuantity() == 0);
        orderBook.getBuyQueue().removeIf(order -> order.getQuantity() == 0);

        return MatchResult.traded(trades);
    }

    private int getTotalQuantityInOrderList(LinkedList <Order> orders){
        int  sumQuantity = 0;
        for (Order order : orders){
            sumQuantity += order.getTotalQuantity();
        }
        return sumQuantity;
    }

    private int findOverallQuantityTraded(int selectedOpenPrice ){
        LinkedList <Order> selectedBuyOrders = new LinkedList<>();
        for (Order order : orderBook.getBuyQueue()){
            if(order.getPrice() >= selectedOpenPrice){
                selectedBuyOrders.add(order);
            }
        }
        LinkedList <Order> selectedSellOrders = new LinkedList<>();
        for (Order order : orderBook.getSellQueue()){
            if(order.getPrice() <= selectedOpenPrice){
                selectedSellOrders.add(order);
            }
        }
        int sumQuantityInSellQueue = getTotalQuantityInOrderList(selectedSellOrders);
        int sumQuantityInBuyQueue = getTotalQuantityInOrderList(selectedBuyOrders);
        return Math.min(sumQuantityInSellQueue , sumQuantityInBuyQueue);
    }

    private int findClosestToLastTradePrice(LinkedList<Integer> openPrices ){
        int minDistance = Integer.MAX_VALUE;
        int minElement = Integer.MAX_VALUE;
        for (int price : openPrices){
            int distance = Math.abs(price - (int)orderBook.getLastTradePrice());
            if(distance < minDistance){
                minDistance = distance;
                minElement = price;
            }
            else if (distance == minDistance && price < minElement){
                minElement = price;
            }
        }
        return minElement;
    }

    public int findBestAuctionPrice(LinkedList <Integer> allOrdersPrices){
        if (allOrdersPrices.size() == 0){
            return 0;
        }
        System.out.println(allOrdersPrices);
        int min = Collections.min(allOrdersPrices);
        int max = Collections.max(allOrdersPrices);
        int maxQuantityTraded = 0;
        LinkedList<Integer> bestOpenPrices = new LinkedList<>();
        for ( int i = min ; i <= max ; i++){
            int overallQuantityTraded = findOverallQuantityTraded(i);
            if(overallQuantityTraded > maxQuantityTraded){
                maxQuantityTraded = overallQuantityTraded;
                bestOpenPrices.clear();
                bestOpenPrices.add(i);
            }
            else if (overallQuantityTraded == maxQuantityTraded && overallQuantityTraded != 0 ){
                bestOpenPrices.add(i);
            }
        }
        highestQuantity = maxQuantityTraded;
        return findClosestToLastTradePrice(bestOpenPrices );
    }

    public int updateIndicativeOpeningPrice( ){
        LinkedList <Integer> allOrdersPrices = new LinkedList<>() ;

        for (Order buyOrder : orderBook.getBuyQueue()) {
            allOrdersPrices.add(buyOrder.getPrice());
        }
        for (Order sellOrder : orderBook.getSellQueue()) {
            allOrdersPrices.add(sellOrder.getPrice());
        }
        int bestAuctionPrice = findBestAuctionPrice(allOrdersPrices);
        if (bestAuctionPrice == Integer.MAX_VALUE){
            bestAuctionPrice = 0;
        }
        indicativeOpeningPrice = bestAuctionPrice;
        return bestAuctionPrice;
    }

    
}



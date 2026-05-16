package com.aquariux.technical.assessment.trade.service.impl;

import com.aquariux.technical.assessment.trade.dto.request.TradeRequest;
import com.aquariux.technical.assessment.trade.dto.response.TradeResponse;
import com.aquariux.technical.assessment.trade.entity.CryptoPair;
import com.aquariux.technical.assessment.trade.entity.CryptoPrice;
import com.aquariux.technical.assessment.trade.entity.Trade;
import com.aquariux.technical.assessment.trade.enums.TradeType;
import com.aquariux.technical.assessment.trade.mapper.CryptoPairMapper;
import com.aquariux.technical.assessment.trade.mapper.CryptoPriceMapper;
import com.aquariux.technical.assessment.trade.mapper.TradeMapper;
import com.aquariux.technical.assessment.trade.mapper.UserWalletMapper;
import com.aquariux.technical.assessment.trade.service.TradeServiceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TradeServiceImpl implements TradeServiceInterface {

    private final TradeMapper tradeMapper;
    private final CryptoPairMapper cryptoPairMapper;
    private final CryptoPriceMapper cryptoPriceMapper;
    private final UserWalletMapper userWalletMapper;

    @Override
    @Transactional
    public TradeResponse executeTrade(TradeRequest tradeRequest) {
        validateRequest(tradeRequest);

        String pairName = tradeRequest.getPairName().trim().toUpperCase(Locale.ROOT);
        CryptoPair pair = cryptoPairMapper.findActiveByPairName(pairName);
        if (pair == null) {
            throw badRequest("Unsupported or inactive trading pair: " + pairName);
        }

        CryptoPrice latestPrice = cryptoPriceMapper.findLatestPriceByPairName(pairName);
        if (latestPrice == null) {
            throw badRequest("No market price available for pair: " + pairName);
        }

        TradeType tradeType = tradeRequest.getTradeType();
        BigDecimal quantity = tradeRequest.getQuantity();
        BigDecimal executionPrice = TradeType.BUY.equals(tradeType)
                ? latestPrice.getAskPrice()
                : latestPrice.getBidPrice();
        String priceSource = TradeType.BUY.equals(tradeType)
                ? latestPrice.getAskSource()
                : latestPrice.getBidSource();
        BigDecimal totalAmount = quantity.multiply(executionPrice);

        if (TradeType.BUY.equals(tradeType)) {
            debitOrReject(tradeRequest.getUserId(), pair.getQuoteSymbolId(), totalAmount, pair.getQuoteSymbol());
            credit(tradeRequest.getUserId(), pair.getBaseSymbolId(), quantity);
        } else {
            debitOrReject(tradeRequest.getUserId(), pair.getBaseSymbolId(), quantity, pair.getBaseSymbol());
            credit(tradeRequest.getUserId(), pair.getQuoteSymbolId(), totalAmount);
        }

        LocalDateTime tradeTime = LocalDateTime.now();
        Trade trade = new Trade();
        trade.setUserId(tradeRequest.getUserId());
        trade.setCryptoPairId(pair.getId());
        trade.setTradeType(tradeType.name());
        trade.setQuantity(quantity);
        trade.setPrice(executionPrice);
        trade.setTotalAmount(totalAmount);
        trade.setTradeTime(tradeTime);
        tradeMapper.insert(trade);

        return mapToResponse(trade, pairName, tradeType, priceSource);
    }

    private void validateRequest(TradeRequest tradeRequest) {
        if (tradeRequest == null) {
            throw badRequest("Trade request is required");
        }
        if (tradeRequest.getUserId() == null) {
            throw badRequest("userId is required");
        }
        if (tradeRequest.getPairName() == null || tradeRequest.getPairName().isBlank()) {
            throw badRequest("pairName is required");
        }
        if (tradeRequest.getTradeType() == null) {
            throw badRequest("tradeType is required");
        }
        if (tradeRequest.getQuantity() == null || tradeRequest.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw badRequest("quantity must be greater than zero");
        }
    }

    private void debitOrReject(Long userId, Long symbolId, BigDecimal amount, String symbol) {
        int updatedRows = userWalletMapper.debitIfSufficient(userId, symbolId, amount);
        if (updatedRows == 0) {
            throw badRequest("Insufficient " + symbol + " balance");
        }
    }

    private void credit(Long userId, Long symbolId, BigDecimal amount) {
        userWalletMapper.insertIfAbsent(userId, symbolId);
        int updatedRows = userWalletMapper.credit(userId, symbolId, amount);
        if (updatedRows == 0) {
            throw badRequest("Unable to update credited wallet");
        }
    }

    private TradeResponse mapToResponse(Trade trade, String pairName, TradeType tradeType, String priceSource) {
        TradeResponse response = new TradeResponse();
        response.setTradeId(trade.getId());
        response.setUserId(trade.getUserId());
        response.setPairName(pairName);
        response.setTradeType(tradeType);
        response.setQuantity(trade.getQuantity());
        response.setPrice(trade.getPrice());
        response.setTotalAmount(trade.getTotalAmount());
        response.setPriceSource(priceSource);
        response.setTradeTime(trade.getTradeTime());
        return response;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}

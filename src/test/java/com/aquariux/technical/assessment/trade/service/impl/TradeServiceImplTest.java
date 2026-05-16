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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradeServiceImplTest {

    @Mock
    private TradeMapper tradeMapper;

    @Mock
    private CryptoPairMapper cryptoPairMapper;

    @Mock
    private CryptoPriceMapper cryptoPriceMapper;

    @Mock
    private UserWalletMapper userWalletMapper;

    @InjectMocks
    private TradeServiceImpl tradeService;

    private CryptoPair btcUsdtPair;
    private CryptoPrice btcUsdtPrice;

    @BeforeEach
    void setUp() {
        btcUsdtPair = new CryptoPair();
        btcUsdtPair.setId(1L);
        btcUsdtPair.setBaseSymbolId(1L);
        btcUsdtPair.setQuoteSymbolId(3L);
        btcUsdtPair.setPairName("BTCUSDT");
        btcUsdtPair.setBaseSymbol("BTC");
        btcUsdtPair.setQuoteSymbol("USDT");
        btcUsdtPair.setActive(true);

        btcUsdtPrice = new CryptoPrice();
        btcUsdtPrice.setCryptoPairId(1L);
        btcUsdtPrice.setPairName("BTCUSDT");
        btcUsdtPrice.setBidPrice(new BigDecimal("44900.00"));
        btcUsdtPrice.setAskPrice(new BigDecimal("45000.00"));
        btcUsdtPrice.setBidSource("BINANCE");
        btcUsdtPrice.setAskSource("HUOBI");
    }

    @Test
    void executeTrade_Buy_ShouldDebitQuoteWalletCreditBaseWalletAndInsertTrade() {
        TradeRequest request = request(TradeType.BUY, "0.10000000");
        when(cryptoPairMapper.findActiveByPairName("BTCUSDT")).thenReturn(btcUsdtPair);
        when(cryptoPriceMapper.findLatestPriceByPairName("BTCUSDT")).thenReturn(btcUsdtPrice);
        when(userWalletMapper.debitIfSufficient(1L, 3L, new BigDecimal("4500.0000000000"))).thenReturn(1);
        when(userWalletMapper.credit(1L, 1L, new BigDecimal("0.10000000"))).thenReturn(1);
        when(tradeMapper.insert(any(Trade.class))).thenAnswer(invocation -> {
            Trade trade = invocation.getArgument(0);
            trade.setId(99L);
            return 1;
        });

        TradeResponse response = tradeService.executeTrade(request);

        assertThat(response.getTradeId()).isEqualTo(99L);
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getPairName()).isEqualTo("BTCUSDT");
        assertThat(response.getTradeType()).isEqualTo(TradeType.BUY);
        assertThat(response.getPrice()).isEqualTo(new BigDecimal("45000.00"));
        assertThat(response.getTotalAmount()).isEqualTo(new BigDecimal("4500.0000000000"));
        assertThat(response.getPriceSource()).isEqualTo("HUOBI");

        verify(userWalletMapper).debitIfSufficient(1L, 3L, new BigDecimal("4500.0000000000"));
        verify(userWalletMapper).insertIfAbsent(1L, 1L);
        verify(userWalletMapper).credit(1L, 1L, new BigDecimal("0.10000000"));

        ArgumentCaptor<Trade> tradeCaptor = ArgumentCaptor.forClass(Trade.class);
        verify(tradeMapper).insert(tradeCaptor.capture());
        assertThat(tradeCaptor.getValue().getTradeType()).isEqualTo("BUY");
        assertThat(tradeCaptor.getValue().getTotalAmount()).isEqualTo(new BigDecimal("4500.0000000000"));
    }

    @Test
    void executeTrade_Sell_ShouldDebitBaseWalletCreditQuoteWalletAndInsertTrade() {
        TradeRequest request = request(TradeType.SELL, "0.20000000");
        when(cryptoPairMapper.findActiveByPairName("BTCUSDT")).thenReturn(btcUsdtPair);
        when(cryptoPriceMapper.findLatestPriceByPairName("BTCUSDT")).thenReturn(btcUsdtPrice);
        when(userWalletMapper.debitIfSufficient(1L, 1L, new BigDecimal("0.20000000"))).thenReturn(1);
        when(userWalletMapper.credit(1L, 3L, new BigDecimal("8980.0000000000"))).thenReturn(1);
        when(tradeMapper.insert(any(Trade.class))).thenAnswer(invocation -> {
            Trade trade = invocation.getArgument(0);
            trade.setId(100L);
            return 1;
        });

        TradeResponse response = tradeService.executeTrade(request);

        assertThat(response.getTradeId()).isEqualTo(100L);
        assertThat(response.getTradeType()).isEqualTo(TradeType.SELL);
        assertThat(response.getPrice()).isEqualTo(new BigDecimal("44900.00"));
        assertThat(response.getTotalAmount()).isEqualTo(new BigDecimal("8980.0000000000"));
        assertThat(response.getPriceSource()).isEqualTo("BINANCE");

        verify(userWalletMapper).debitIfSufficient(1L, 1L, new BigDecimal("0.20000000"));
        verify(userWalletMapper).insertIfAbsent(1L, 3L);
        verify(userWalletMapper).credit(1L, 3L, new BigDecimal("8980.0000000000"));
    }

    @Test
    void executeTrade_WhenBalanceIsInsufficient_ShouldNotInsertTrade() {
        TradeRequest request = request(TradeType.BUY, "1.00000000");
        when(cryptoPairMapper.findActiveByPairName("BTCUSDT")).thenReturn(btcUsdtPair);
        when(cryptoPriceMapper.findLatestPriceByPairName("BTCUSDT")).thenReturn(btcUsdtPrice);
        when(userWalletMapper.debitIfSufficient(1L, 3L, new BigDecimal("45000.0000000000"))).thenReturn(0);

        assertThatThrownBy(() -> tradeService.executeTrade(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Insufficient USDT balance");

        verify(userWalletMapper, never()).credit(any(), any(), any());
        verify(tradeMapper, never()).insert(any());
    }

    @Test
    void executeTrade_WhenRequestIsInvalid_ShouldReject() {
        TradeRequest request = request(TradeType.BUY, "0");

        assertThatThrownBy(() -> tradeService.executeTrade(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("quantity must be greater than zero");

        verify(tradeMapper, never()).insert(any());
    }

    private TradeRequest request(TradeType tradeType, String quantity) {
        TradeRequest request = new TradeRequest();
        request.setUserId(1L);
        request.setPairName("btcusdt");
        request.setTradeType(tradeType);
        request.setQuantity(new BigDecimal(quantity));
        return request;
    }
}

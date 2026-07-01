package it.mazzoni.vis.portfolio;

import it.mazzoni.vis.domain.entity.PriceQuote;
import it.mazzoni.vis.domain.entity.Security;
import it.mazzoni.vis.domain.repository.PriceQuoteRepository;
import it.mazzoni.vis.domain.repository.SecurityRepository;
import it.mazzoni.vis.portfolio.dto.LiquidityResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiquidityServiceTest {
    @Mock SecurityRepository securities;
    @Mock PriceQuoteRepository quotes;

    @Test
    void classifiesLiquidModerateAndIlliquidByDaysToLiquidate() {
        LiquidityService service = new LiquidityService(securities, quotes);
        Security security = security("AAA");
        when(securities.findBySymbol("AAA")).thenReturn(Optional.of(security));
        when(quotes.findBySecurityAndQuoteDateBetweenOrderByQuoteDateDesc(org.mockito.ArgumentMatchers.eq(security),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(quote("10", 1000)));

        LiquidityResult liquid = service.assess("AAA", new BigDecimal("400"));
        LiquidityResult moderate = service.assess("AAA", new BigDecimal("10000"));
        LiquidityResult illiquid = service.assess("AAA", new BigDecimal("25000"));

        assertEquals("LIQUID", liquid.classification());
        assertEquals("MODERATE", moderate.classification());
        assertEquals("ILLIQUID", illiquid.classification());
    }

    private Security security(String symbol) {
        Security security = new Security();
        security.setSymbol(symbol);
        security.setCompanyName(symbol);
        return security;
    }

    private PriceQuote quote(String close, long volume) {
        PriceQuote quote = new PriceQuote();
        quote.setClose(new BigDecimal(close));
        quote.setVolume(volume);
        quote.setQuoteDate(LocalDate.now());
        return quote;
    }
}

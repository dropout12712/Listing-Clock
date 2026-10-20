package net.listingclock.core;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class PriceTest {
    @ParameterizedTest @CsvSource({"$ 100K,100000", "$ 1.25M,1250000"})
    void acceptsSpaceAfterCurrencySymbol(String input, String expected) {
        assertEquals(0, new BigDecimal(expected).compareTo(Price.parse(input).orElseThrow()));
    }

    @ParameterizedTest @CsvSource({"100,100", "1.5k,1500", "$2M,2000000", "'1,234.50',1234.50", "0.25b,250000000", "1t,1000000000000", "1.01,1.01"})
    void parsesExactPrices(String input, String expected) {
        assertEquals(0, new BigDecimal(expected).compareTo(Price.parse(input).orElseThrow()));
    }

    @ParameterizedTest @ValueSource(strings = {"", "0", "-2", "NaN", "Infinity", "1e6", "1kk", "1,23", "1.2.3", "1 000", "12345678901234567890123456789", "0.000000001", "500 extra"})
    void rejectsInvalid(String price) { assertTrue(Price.parse(price).isEmpty()); }
}

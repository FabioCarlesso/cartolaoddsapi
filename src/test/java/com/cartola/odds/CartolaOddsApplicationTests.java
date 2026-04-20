package com.cartola.odds;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "odds.api.key=TEST_KEY",
        "cartola.api.base-url=https://api.cartola.globo.com",
        "odds.api.base-url=https://api.the-odds-api.com/v4"
})
class CartolaOddsApplicationTests {

    @Test
    void contextLoads() {
    }
}

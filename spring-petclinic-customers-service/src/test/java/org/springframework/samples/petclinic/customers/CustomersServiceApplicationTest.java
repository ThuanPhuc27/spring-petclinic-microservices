package org.springframework.samples.petclinic.customers;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class CustomersServiceApplicationTest {

    @Test
    void mainMethodStartsApplication() {
        assertDoesNotThrow(() -> {
            ConfigurableApplicationContext context = SpringApplication.run(
                CustomersServiceApplication.class, 
                "--spring.profiles.active=test",
                "--server.port=0"
            );
            context.close(); // Tắt context ngay sau khi khởi động
        });
    }
}
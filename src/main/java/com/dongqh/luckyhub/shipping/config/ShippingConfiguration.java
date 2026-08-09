package com.dongqh.luckyhub.shipping.config;

import com.dongqh.luckyhub.shipping.crypto.AddressCipher;
import com.dongqh.luckyhub.shipping.crypto.AesGcmAddressCipher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ShippingProperties.class)
public class ShippingConfiguration {

    @Bean
    public AddressCipher addressCipher(ShippingProperties properties) {
        return new AesGcmAddressCipher(properties);
    }
}

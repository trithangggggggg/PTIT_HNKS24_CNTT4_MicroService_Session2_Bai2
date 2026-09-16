package com.storex.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import java.util.Collections;
import java.util.List;

@Component
public class ProductClientFallbackFactory implements FallbackFactory<ProductClient> {

    private static final Logger logger = LoggerFactory.getLogger(ProductClientFallbackFactory.class);

    @Override
    public ProductClient create(Throwable cause) {
        return new ProductClient() {
            @Override
            public ProductInfo getById(Long id) {
                logger.error("Error occurred while calling getById from product-service for id: {}", id, cause);
                // Giả sử ProductInfo có method fallback như code ban đầu
                return ProductInfo.fallback(id);
            }

            @Override
            public List<ProductInfo> getAll() {
                logger.error("Error occurred while calling getAll from product-service", cause);
                return Collections.emptyList();
            }
        };
    }
}

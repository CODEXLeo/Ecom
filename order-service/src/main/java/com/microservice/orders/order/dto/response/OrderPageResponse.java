package com.microservice.orders.order.dto.response;

import java.util.List;

public record OrderPageResponse(

        List<OrderSummaryResponse> content,

        int page,

        int size,

        long totalElements,

        int totalPages,

        boolean first,

        boolean last,

        boolean empty

) {
}